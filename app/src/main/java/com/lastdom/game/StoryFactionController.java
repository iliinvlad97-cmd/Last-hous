package com.lastdom.game;

/** Faction quests use existing story dialogue cursors, real expedition returns and atomic saves. */
final class StoryFactionController {
  final StoryController story;
  final GameController game;
  final StoryState plot;
  final StoryFactionState state;

  StoryFactionController(StoryController story) {
    this.story = story;
    game = story.game;
    plot = story.state;
    state = plot.factions;
  }

  boolean start() {
    if (state.started || plot.investigation != StoryInvestigationController.Phase.COMPLETE)
      return false;
    state.started = true;
    plot.questId = "quest.city_divided";
    plot.chapterId = "chapter.city_conflicts";
    boolean publicSignal =
        plot.decisionId.equals(StoryConfig.SHARE.id) && plot.flags.contains(StoryFlags.SHARE);
    boolean privateSignal =
        plot.decisionId.equals(StoryConfig.SECRET.id) && plot.flags.contains(StoryFlags.SECRET);
    boolean publicDocs =
        plot.investigationDecision.equals(StoryInvestigationConfig.SHARE)
            && plot.flags.contains(StoryInvestigationConfig.SHARE);
    boolean privateDocs =
        plot.investigationDecision.equals(StoryInvestigationConfig.KEEP)
            && plot.flags.contains(StoryInvestigationConfig.KEEP);
    int signal = publicSignal ? 1 : privateSignal ? -1 : 0,
        docs = publicDocs ? 1 : privateDocs ? -1 : 0;
    change(
        "faction.initial.union",
        StoryFactionConfig.UNION,
        StoryFactionConfig.INITIAL_SIGNAL * signal + StoryFactionConfig.INITIAL_DOCUMENTS * docs,
        "Прежние решения: распространение сигнала и документов");
    change(
        "faction.initial.garrison",
        StoryFactionConfig.GARRISON,
        -StoryFactionConfig.INITIAL_SIGNAL * signal - StoryFactionConfig.INITIAL_DOCUMENTS * docs,
        "Прежние решения: осторожность с опасной информацией");
    change(
        "faction.initial.heirs",
        StoryFactionConfig.HEIRS,
        StoryFactionConfig.INITIAL_HEIRS * docs,
        "Прежнее решение о технических документах");
    for (StoryFaction f : StoryFactionConfig.ALL)
      if (!plot.messages.contains(f.contact.event.id)) plot.messages.add(f.contact.event.id);
    story.message(StoryFactionConfig.ALL[0].contact.event);
    game.addLog("Сюжет: «Город расколот» — получены обращения трёх фракций.");
    syncMap();
    return true;
  }

  void activate() {
    boolean changed = start();
    syncMap();
    if (changed) game.save();
  }

  void syncMap() {
    for (StoryFaction f : StoryFactionConfig.ALL) {
      MapLocation m = game.expeditionController.location(f.locationId);
      m.setState(
          !state.started
              ? MapLocation.State.LOCKED
              : state.completed.contains(f.id)
                  ? MapLocation.State.SEARCHED
                  : MapLocation.State.AVAILABLE);
      m.setDepletion(0);
    }
  }

  boolean visible(String id) {
    return state.started && StoryFactionConfig.target(id) != null;
  }

  void change(String action, String id, int delta, String reason) {
    if (!state.applied.add(action)) return;
    int old = state.reputation.getOrDefault(id, 0),
        next = StoryFactionConfig.clamp((long) old + delta);
    state.reputation.put(id, next);
    String line =
        StoryFactionConfig.faction(id).name
            + ": "
            + (next - old >= 0 ? "+" : "")
            + (next - old)
            + " · "
            + reason;
    state.history.add(line);
    game.addLog("Фракции: " + line);
  }

  void dialogueComplete(StoryDialogue d) {
    for (StoryFaction f : StoryFactionConfig.ALL)
      if (f.contact == d) {
        state.contacts.add(f.id);
        plot.objectives.add("faction.contact." + f.id);
        for (StoryFaction next : StoryFactionConfig.ALL)
          if (!state.contacts.contains(next.id)) {
            story.message(next.contact.event);
            return;
          }
      }
  }

  String reason(String id) {
    StoryFaction f = StoryFactionConfig.target(id);
    if (f == null) return "";
    if (!state.started) return "Завершите «Следы прошлого»";
    if (!state.contacts.contains(f.id))
      return "Сначала поговорите: " + f.representative + " · Журнал → Сюжет → Фракции";
    if (state.completed.contains(f.id)) return "Фракционное задание уже выполнено";
    if (state.supplied.contains(f.id))
      return "Маршрут проверен. Передайте припасы из раздела «Фракции»";
    return "";
  }

  String description(String id) {
    StoryFaction f = StoryFactionConfig.target(id);
    return f == null ? "" : f.quest.name + " · " + f.quest.objectives.get(0).text;
  }

  void research(Expedition e, boolean early) {
    if (!plot.attemptResults.containsKey(e.id))
      plot.attemptResults.put(
          e.id, !early && game.rnd.nextDouble() < StoryConfig.recoveryChance(game, e));
  }

  void advanceMinute() {
    start();
    for (Expedition e : game.expeditions) {
      StoryFaction f = StoryFactionConfig.target(e.locationId);
      if (f == null
          || !state.started
          || e.state() != Expedition.State.COMPLETED
          || !e.rewardCredited
          || !plot.processedReturns.add(e.id)) continue;
      if (!Boolean.TRUE.equals(plot.attemptResults.get(e.id))) {
        game.addLog("Фракции: " + f.quest.name + " — цель не достигнута, можно повторить попытку.");
        continue;
      }
      if (state.completed.contains(f.id)) continue;
      if (f.id.equals(StoryFactionConfig.UNION)) {
        state.supplied.add(f.id);
        if (deliveryReason().isEmpty()) deliver();
        else
          game.addLog(
              "Гуманитарный маршрут проверен. Припасы пока не переданы: "
                  + deliveryReason()
                  + ". Передайте позже из сюжетного журнала.");
      } else complete(f);
    }
    syncMap();
  }

  String deliveryReason() {
    if (!state.supplied.contains(StoryFactionConfig.UNION))
      return "Сначала успешно верните отряд с Перекрёстка";
    if (state.completed.contains(StoryFactionConfig.UNION)) return "Помощь уже передана";
    if (game.food < StoryFactionConfig.FOOD || game.water < StoryFactionConfig.WATER)
      return "Нужно 2 еды и 2 воды; сейчас " + game.food + " еды и " + game.water + " воды";
    return "";
  }

  private void deliver() {
    game.food -= StoryFactionConfig.FOOD;
    game.water -= StoryFactionConfig.WATER;
    complete(StoryFactionConfig.ALL[0]);
  }

  String deliver(boolean confirmed) {
    if (!confirmed) return "Подтвердите передачу 2 еды и 2 воды";
    String reason = deliveryReason();
    if (!reason.isEmpty()) return reason;
    deliver();
    syncMap();
    game.save();
    game.invalidate();
    return "";
  }

  private void complete(StoryFaction f) {
    if (!state.completed.add(f.id)) return;
    plot.objectives.add("faction.done." + f.id);
    if (f.id.equals(StoryFactionConfig.HEIRS)) plot.items.add("faction.lost_protocol");
    change(
        "faction.quest." + f.id,
        f.id,
        StoryFactionConfig.QUEST_REPUTATION,
        "Выполнено: " + f.quest.name);
    game.addLog("Фракционное задание завершено: " + f.quest.name);
    finishCampaign();
  }

  private void finishCampaign() {
    if (state.completed.size() == 3
        && !state.side.isEmpty()
        && plot.flags.add("faction.city_divided.complete"))
      game.addLog(
          "Сюжет: «Город расколот» завершено. Все три задания выполнены; выбор стороны сохранён."
              + " Выживание продолжается.");
  }

  boolean canChoose() {
    if (!state.started || !state.side.isEmpty()) return false;
    for (StoryFaction f : StoryFactionConfig.ALL) if (!state.contacts.contains(f.id)) return false;
    return true;
  }

  String choose(String side, boolean confirmed) {
    if (!confirmed) return "Подтвердите выбор стороны";
    if (!canChoose()) return "Выбор уже принят или контакты ещё не установлены";
    if (StoryFactionConfig.faction(side) == null && !side.equals(StoryFactionConfig.NONE))
      return "Неизвестная сторона";
    state.side = side;
    plot.flags.add(StoryFactionConfig.FLAG + side);
    for (StoryFaction f : StoryFactionConfig.ALL)
      change(
          "faction.side." + f.id,
          f.id,
          side.equals(StoryFactionConfig.NONE)
              ? 0
              : side.equals(f.id)
                  ? StoryFactionConfig.SIDE_BONUS
                  : StoryFactionConfig.OTHER_SIDE_PENALTY,
          "Информация о подземном входе: " + StoryFactionConfig.sideLabel(side));
    plot.objectives.add("faction.side");
    finishCampaign();
    game.addLog(
        "Город расколот: решение сохранено. Будущая кампания доступна при любом выборе; гермодверь"
            + " остаётся закрытой.");
    game.save();
    game.invalidate();
    return "";
  }
}
