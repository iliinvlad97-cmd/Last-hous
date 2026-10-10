package com.lastdom.game;

/** Campaign transitions only; no Canvas, system clock, duplicate residents or resource awards. */
final class StoryController {
  final GameController game;
  final StoryState state;
  boolean prompt;
  final StoryInvestigationController investigation;

  StoryController(GameController game) {
    this.game = game;
    state = game.story;
    investigation = new StoryInvestigationController(this);
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
    investigation.syncMap();
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
    activateVoices();
    investigation.activate();
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

  void message(StoryEvent e) {
    if (!state.messages.contains(e.id)) state.messages.add(e.id);
    state.pendingMessage = e.id;
    prompt = true;
  }

  void advanceMinute() {
    startVoices();
    investigation.advanceMinute();
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

  boolean publicBranch() {
    return state.decisionId.equals(StoryConfig.SHARE.id)
        && state.flags.contains(StoryFlags.SHARE)
        && !state.flags.contains(StoryFlags.SECRET);
  }

  String voicesBlockedReason() {
    if (state.voicesStage != 0) return "";
    if (state.phase != StoryState.Phase.CHAIN_COMPLETE)
      return "Завершите «Неизвестную частоту» и подтвердите первое решение";
    if (state.decisionId.isEmpty()) return "В сохранении нет подтверждённого первого решения";
    if (!state.decisionId.equals(StoryConfig.SHARE.id)
        && !state.decisionId.equals(StoryConfig.SECRET.id))
      return "Неизвестный ID первого решения: " + state.decisionId;
    if (state.flags.contains(StoryFlags.SHARE) && state.flags.contains(StoryFlags.SECRET))
      return "Сохранение содержит противоречащие флаги первого решения";
    String expected =
        state.decisionId.equals(StoryConfig.SHARE.id) ? StoryFlags.SHARE : StoryFlags.SECRET;
    if (!state.flags.contains(expected)) return "Нет флага сохранённого решения: " + expected;
    return "";
  }

  /** Reconcile existing saves without advancing time or replaying completed conversations. */
  void activateVoices() {
    if (state.voicesStage != 0) return;
    startVoices();
    if (state.voicesStage != 0) game.save();
  }

  void startVoices() {
    if (state.voicesStage != 0 || !voicesBlockedReason().isEmpty()) return;
    state.voicesStage = 1;
    state.questId = StoryConfig.VOICES.id;
    for (Resident r : game.people)
      for (String name : new String[] {"Иван", "Мария", "Сергей"})
        if (name.equals(r.name)) {
          state.residentIds.put(name, r.id);
          state.attitudes.put(r.id, StoryDialogue.INITIAL_TRUST);
        }
    message(StoryDialogue.INTRO.event);
    game.addLog("Сюжет: «Голоса в эфире» — новое сообщение Евы, радистки станции 17.");
  }

  int step(StoryDialogue d) {
    return state.dialogueSteps.getOrDefault(d.event.id, 0);
  }

  String dialogueText(StoryDialogue d, int index) {
    String key = d.event.id + "." + index;
    if (state.transcripts.containsKey(key)) return state.transcripts.get(key);
    StoryDialogue.Line line = d.lines[index];
    Resident r =
        game.expeditionController.resident(state.residentIds.getOrDefault(line.residentName, ""));
    boolean available =
        line.residentName.isEmpty()
            || (r != null && game.roomUpgradeController.unavailableReason(r).isEmpty());
    if (d == StoryDialogue.EVA && index == 1) {
      String answer = state.answers.get(d.event.id + ".0");
      String reaction =
          line.responses.length == 0 && d.lines[0].responses[0].equals(answer)
              ? "Вы правы: нужны доказательства, а не обещания. "
              : "Осторожность не означает обман. Я не раскрою неподтверждённые сведения. ";
      return line.speaker + " · " + line.role + "\n" + reaction + line.text;
    }
    return available
        ? line.speaker + " · " + line.role + "\n" + line.text
        : "Журнал убежища · нейтральная заметка\n"
            + line.residentName
            + " сейчас недоступен для разговора. Его мнение можно обсудить позднее; проверка"
            + " источника остаётся важной.";
  }

  String continueDialogue(String id, int expectedStep, int response) {
    StoryDialogue d = StoryDialogue.find(id);
    if (d == null
        || !state.messages.contains(id)
        || state.completedDialogues.contains(id)
        || step(d) != expectedStep
        || expectedStep < 0
        || expectedStep >= d.lines.length) return "Эта реплика уже завершена или недоступна";
    StoryDialogue.Line line = d.lines[expectedStep];
    if (line.responses.length > 0 && (response < 0 || response >= line.responses.length))
      return "Выберите ответ";
    String key = id + "." + expectedStep;
    state.transcripts.put(key, dialogueText(d, expectedStep));
    if (line.responses.length > 0) {
      state.answers.put(key, line.responses[response]);
      int delta = response == 0 ? StoryDialogue.EVIDENCE_TRUST : StoryDialogue.GUARDED_TRUST;
      state.evaTrust = Math.max(0, Math.min(100, state.evaTrust + delta));
      game.addLog(
          "Сюжет: ответ Еве «"
              + line.responses[response]
              + "». Доверие "
              + (delta > 0 ? "+" : "")
              + delta
              + ".");
    }
    state.dialogueSteps.put(id, expectedStep + 1);
    if (expectedStep + 1 == d.lines.length) {
      state.completedDialogues.add(id);
      if (d == StoryDialogue.INTRO) state.objectives.add("voices.message");
      else if (d == StoryDialogue.RESIDENTS) state.objectives.add("voices.discussion");
      else if (d == StoryDialogue.EVA) state.objectives.add("voices.eva");
      else if (d == StoryDialogue.PUBLIC || d == StoryDialogue.SECRET)
        state.objectives.add("voices.consequence");
      else if (d == StoryDialogue.HOOK) state.objectives.add("voices.hook");
      state.readMessages.add(id);
      if (state.pendingMessage.equals(id)) state.pendingMessage = "";
      game.addLog("Сюжет: разговор «" + d.event.source + "» завершён.");
      StoryDialogue next = null;
      if (d == StoryDialogue.INTRO) {
        state.voicesStage = 2;
        next = StoryDialogue.RESIDENTS;
      } else if (d == StoryDialogue.RESIDENTS) {
        state.voicesStage = 3;
        next = StoryDialogue.EVA;
      } else if (d == StoryDialogue.EVA) {
        state.voicesStage = 4;
        next = publicBranch() ? StoryDialogue.PUBLIC : StoryDialogue.SECRET;
      } else if (d == StoryDialogue.PUBLIC || d == StoryDialogue.SECRET) {
        boolean shared = d == StoryDialogue.PUBLIC;
        state.flags.add(shared ? StoryFlags.PUBLIC_SIGNAL : StoryFlags.SECRET_SIGNAL);
        state.evaTrust =
            Math.max(
                0,
                Math.min(
                    100,
                    state.evaTrust
                        + (shared ? -StoryDialogue.BRANCH_TRUST : StoryDialogue.BRANCH_TRUST)));
        String sergey = state.residentIds.get("Сергей");
        if (sergey != null)
          state.attitudes.put(
              sergey,
              Math.max(
                  0,
                  Math.min(
                      100,
                      state.attitudes.getOrDefault(sergey, 50)
                          + (shared ? -StoryDialogue.BRANCH_TRUST : StoryDialogue.BRANCH_TRUST))));
        if (!shared) state.items.add("encrypted_station17_fragment");
        game.addLog(
            "Сюжет: «"
                + d.event.source
                + "». Доверие Евы "
                + (shared ? "−5" : "+5")
                + "; отношение Сергея "
                + (shared ? "−5" : "+5")
                + " к прежнему решению.");
        state.voicesStage = 5;
        next = StoryDialogue.HOOK;
      } else if (d == StoryDialogue.HOOK) {
        state.voicesStage = 6;
        game.addLog(
            "Сюжет: «Голоса в эфире» завершено. Зацепка — архив станции 17; Ева готова передать"
                + " дальнейшие сведения.");
      }
      investigation.dialogueComplete(d);
      if (d == StoryDialogue.HOOK) investigation.start();
      if (next != null) message(next.event);
      else prompt = !state.pendingMessage.isEmpty();
    }
    game.save();
    game.invalidate();
    return "";
  }

  void research(Expedition e, boolean early) {
    if (StoryInvestigationConfig.target(e.locationId)) {
      investigation.research(e, early);
      return;
    }
    if (!StoryConfig.RADIO.equals(e.locationId) || state.attemptResults.containsKey(e.id)) return;
    boolean success = !early && game.rnd.nextDouble() < StoryConfig.recoveryChance(game, e);
    state.attemptResults.put(e.id, success);
  }

  String expeditionReason() {
    return expeditionReason(StoryConfig.RADIO);
  }

  String expeditionReason(String id) {
    if (StoryInvestigationConfig.target(id)) return investigation.reason(id);
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
    startVoices();
    game.addLog(
        "Последний сигнал: " + c.label + ". Решение сохранено; дальнейшее выживание продолжается.");
    game.save();
    game.invalidate();
    return "";
  }

  void read(String id) {
    if (StoryDialogue.find(id) != null) return;
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
