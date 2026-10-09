package com.lastdom.game;

/** Campaign transitions only; no Canvas, system clock, duplicate residents or resource awards. */
final class StoryController {
  final GameController game;
  final StoryState state;
  boolean prompt;

  StoryController(GameController game) {
    this.game = game;
    state = game.story;
  }

  boolean busy(Resident r) {
    return r != null && state.phase == StoryState.Phase.DECODING && state.specialistId.equals(r.id);
  }

  boolean generatorAvailable() {
    return game.roomCondition[0] > 0
        && game.roomLevels[0] > 0
        && (game.power > 0 || game.productionController.energyPerDay() > 0);
  }

  void reset() {
    state.reset();
    prompt = false;
    syncMap();
  }

  void restore() {
    syncMap();
    if (state.phase == StoryState.Phase.DECODING) {
      Resident r = game.expeditionController.resident(state.specialistId);
      if (r == null || game.isOnExpedition(r) || game.isBuilding(r) || game.isDefending(r)) {
        state.phase = StoryState.Phase.DECODE_READY;
        state.specialistId = "";
      } else r.job = "Расшифровка";
    }
    prompt = !state.pendingMessage.isEmpty();
  }

  void syncMap() {
    MapLocation m = game.expeditionController.location(StoryConfig.RADIO);
    m.setState(
        state.flags.contains(StoryFlags.SIGNAL)
            ? MapLocation.State.AVAILABLE
            : MapLocation.State.LOCKED);
    m.setDepletion(0);
  }

  private void message(StoryEvent e) {
    if (!state.messages.contains(e.id)) state.messages.add(e.id);
    state.pendingMessage = e.id;
    prompt = true;
  }

  void advanceMinute() {
    if (state.phase == StoryState.Phase.DORMANT
        && game.day >= StoryConfig.START_DAY
        && generatorAvailable()
        && !game.gameOver) {
      state.phase = StoryState.Phase.SEARCHING;
      state.questId = StoryConfig.FIRST.id;
      state.flags.add(StoryFlags.SIGNAL);
      syncMap();
      message(StoryConfig.SIGNAL);
      game.addLog(
          "Последний сигнал: необычная передача станции 17. Задание «Неизвестная частота».");
    }
    for (Expedition e : game.expeditions)
      if (StoryConfig.RADIO.equals(e.locationId)
          && e.state() == Expedition.State.COMPLETED
          && e.rewardCredited
          && state.processedReturns.add(e.id)) {
        boolean success = Boolean.TRUE.equals(state.attemptResults.get(e.id));
        if (success && !state.items.contains(StoryConfig.CARRIER)) {
          state.items.add(StoryConfig.CARRIER);
          state.flags.add(StoryFlags.CARRIER);
          state.objectives.add("carrier");
          state.phase = StoryState.Phase.DECODE_READY;
          message(StoryConfig.FOUND);
          game.addLog(
              "Последний сигнал: повреждённый носитель доставлен. Следующая цель — расшифровать"
                  + " координаты.");
        } else if (!success)
          game.addLog(
              "Радиостанция: носитель не удалось восстановить. Можно повторить экспедицию тем же"
                  + " обычным способом.");
      }
    if (state.phase == StoryState.Phase.DECODING && workshopAvailable()) {
      state.decodeElapsed = Math.min(StoryConfig.DECODE_MINUTES, state.decodeElapsed + 1);
      if (state.decodeElapsed == StoryConfig.DECODE_MINUTES) {
        Resident r = game.expeditionController.resident(state.specialistId);
        if (r != null) r.job = state.previousJob;
        state.specialistId = "";
        state.phase = StoryState.Phase.DECISION;
        state.flags.add(StoryFlags.COORDINATES);
        state.objectives.add("decode");
        message(StoryConfig.DECODED);
        game.addLog("Последний сигнал: часть координат объекта «Рассвет» расшифрована.");
      }
    }
  }

  void research(Expedition e, boolean early) {
    if (!StoryConfig.RADIO.equals(e.locationId) || state.attemptResults.containsKey(e.id)) return;
    boolean success = !early && game.rnd.nextDouble() < StoryConfig.recoveryChance(game, e);
    state.attemptResults.put(e.id, success);
  }

  String expeditionReason() {
    if (!state.flags.contains(StoryFlags.SIGNAL)) return "Сигнал ещё не получен";
    if (state.items.contains(StoryConfig.CARRIER))
      return "Носитель уже доставлен. Откройте раздел «Сюжет»";
    return "";
  }

  boolean workshopAvailable() {
    RoomUpgradeTask t = game.roomUpgradeController.active();
    return game.roomLevels[3] > 0 && game.roomCondition[3] > 0 && (t == null || t.room != 3);
  }

  String specialistReason(Resident r) {
    String reason = game.roomUpgradeController.unavailableReason(r);
    if (!reason.isEmpty()) return reason;
    if (!r.role.equals("Инженер") && !r.role.equals("Механик")) return "Нужен инженер или механик";
    if (r.health < SurvivalConfig.TREAT_START || r.fatigue >= SurvivalConfig.REST_START)
      return "Сначала нужны лечение или отдых";
    return "";
  }

  String decode(String id) {
    if (game.gameOver) return "Игра завершена";
    if (state.phase != StoryState.Phase.DECODE_READY || !state.items.contains(StoryConfig.CARRIER))
      return "Расшифровка сейчас недоступна";
    if (!workshopAvailable()) return "Мастерская недоступна: проверьте состояние и строительство";
    Resident r = game.expeditionController.resident(id);
    String reason = specialistReason(r);
    if (!reason.isEmpty()) return reason;
    state.specialistId = r.id;
    state.previousJob = r.job;
    r.job = "Расшифровка";
    state.decodeElapsed = 0;
    state.decodeStart = game.expeditionController.now();
    state.phase = StoryState.Phase.DECODING;
    game.addLog("Расшифровка координат: " + r.name + ", 60 игровых минут.");
    game.save();
    game.invalidate();
    return "";
  }

  String choose(String id, boolean confirmed) {
    if (!confirmed) return "Подтвердите сюжетное решение";
    if (state.phase != StoryState.Phase.DECISION || !state.decisionId.isEmpty())
      return "Решение уже принято или ещё недоступно";
    StoryChoice c =
        StoryConfig.SHARE.id.equals(id)
            ? StoryConfig.SHARE
            : StoryConfig.SECRET.id.equals(id) ? StoryConfig.SECRET : null;
    if (c == null) return "Неизвестное решение";
    state.decisionId = c.id;
    state.flags.add(c.flag);
    state.objectives.add("choice");
    state.phase = StoryState.Phase.CHAIN_COMPLETE;
    game.addLog(
        "Последний сигнал: " + c.label + ". Решение сохранено; дальнейшее выживание продолжается.");
    game.save();
    game.invalidate();
    return "";
  }

  void read(String id) {
    if (StoryConfig.event(id) == null || !state.messages.contains(id)) return;
    state.readMessages.add(id);
    if (id.equals(StoryConfig.SIGNAL.id)) state.objectives.add("signal");
    if (state.pendingMessage.equals(id)) state.pendingMessage = "";
    prompt = false;
    game.save();
  }

  String objective() {
    switch (state.phase) {
      case DORMANT:
        return "Ожидание сигнала: день 3 и работающий генератор";
      case SEARCHING:
        return "Найти носитель и вернуть отряд с радиостанции";
      case DECODE_READY:
        return "Расшифровать координаты в мастерской";
      case DECODING:
        return "Расшифровка: " + state.decodeElapsed + " / 60 игровых минут";
      case DECISION:
        return "Решить, кому передать координаты";
      default:
        return "Первая цепочка завершена. Выживание продолжается";
    }
  }
}
