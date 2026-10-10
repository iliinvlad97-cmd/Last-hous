package com.lastdom.game;

/** Investigation transitions reuse the shared story snapshot and existing solo expeditions. */
final class StoryInvestigationController {
  enum Phase {
    DORMANT,
    EVA,
    ARCHIVE,
    ARCHIVE_REVIEW,
    LAB,
    LAB_REVIEW,
    ENTRANCE,
    ENTRANCE_REVIEW,
    DECISION,
    COMPLETE
  }

  final StoryController story;
  final GameController game;
  final StoryState state;

  StoryInvestigationController(StoryController story) {
    this.story = story;
    game = story.game;
    state = story.state;
  }

  boolean start() {
    if (state.investigation != Phase.DORMANT
        || state.voicesStage != 6
        || !state.completedDialogues.contains(StoryDialogue.HOOK.event.id)) return false;
    state.investigation = Phase.EVA;
    state.chapterId = StoryInvestigationConfig.QUEST.chapterId;
    state.questId = StoryInvestigationConfig.QUEST.id;
    story.message(StoryInvestigationConfig.INTRO.event);
    game.addLog("Сюжет: «Следы прошлого» — Ева нашла упоминание городского архива.");
    return true;
  }

  void activate() {
    boolean changed = start();
    syncMap();
    if (changed) game.save();
  }

  boolean visible(String id) {
    if (id.equals(StoryInvestigationConfig.ARCHIVE)) return state.objectives.contains("dawn.eva");
    if (id.equals(StoryInvestigationConfig.LAB)) return state.objectives.contains("dawn.review");
    return id.equals(StoryInvestigationConfig.ENTRANCE)
        && state.items.contains(StoryInvestigationConfig.KEY);
  }

  void syncMap() {
    for (String id :
        new String[] {
          StoryInvestigationConfig.ARCHIVE,
          StoryInvestigationConfig.LAB,
          StoryInvestigationConfig.ENTRANCE
        }) {
      MapLocation m = game.expeditionController.location(id);
      String completed =
          id.equals(StoryInvestigationConfig.ARCHIVE)
              ? "dawn.archive"
              : id.equals(StoryInvestigationConfig.LAB) ? "dawn.lab" : "dawn.entrance";
      m.setState(
          !visible(id)
              ? MapLocation.State.LOCKED
              : state.objectives.contains(completed)
                  ? MapLocation.State.SEARCHED
                  : MapLocation.State.AVAILABLE);
      m.setDepletion(0);
    }
  }

  String target() {
    switch (state.investigation) {
      case ARCHIVE:
        return StoryInvestigationConfig.ARCHIVE;
      case LAB:
        return StoryInvestigationConfig.LAB;
      case ENTRANCE:
        return StoryInvestigationConfig.ENTRANCE;
      default:
        return "";
    }
  }

  String objective() {
    switch (state.investigation) {
      case DORMANT:
        return "Завершите «Голоса в эфире»";
      case EVA:
        return "Получить сведения Евы о городском архиве";
      case ARCHIVE:
        return "Доставить документы из городского архива";
      case ARCHIVE_REVIEW:
        return "Изучить архивные документы с Евой";
      case LAB:
        return "Вернуть журнал и ключ из исследовательского корпуса";
      case LAB_REVIEW:
        return "Обсудить технические записи";
      case ENTRANCE:
        return "Осмотреть внешний терминал подземного входа";
      case ENTRANCE_REVIEW:
        return "Обсудить закрытый комплекс с Сергеем";
      case DECISION:
        return "Решить судьбу найденных документов";
      default:
        return "«Следы прошлого» завершено. Гермодверь закрыта; продолжение появится позднее";
    }
  }

  String reason(String id) {
    if (!visible(id))
      return id.equals(StoryInvestigationConfig.ARCHIVE)
          ? "Сначала получите сведения Евы"
          : id.equals(StoryInvestigationConfig.LAB)
              ? "Сначала изучите архивные документы"
              : "Нужен фрагмент ключа из исследовательского корпуса";
    if (!id.equals(target()))
      return "Этот этап уже завершён или требует разговора. Откройте раздел «Сюжет»";
    return "";
  }

  String description(String id) {
    if (id.equals(StoryInvestigationConfig.ENTRANCE))
      return "Гермодверь: закрыта, запоры исправны. Доступ: нужны полный ключ и подтверждённый код;"
          + " найден только фрагмент. Разрешён внешний осмотр, проход внутрь пока"
          + " недоступен.";
    return id.equals(StoryInvestigationConfig.ARCHIVE)
        ? "Документы выдаются только после успешного возвращения отряда. Затем изучите их в разделе"
            + " «Сюжет»."
        : "Корпус дальше и опаснее архива; шанс восстановить записи ниже. Журнал и ключ выдаются"
            + " после успешного возвращения.";
  }

  void research(Expedition e, boolean early) {
    if (state.attemptResults.containsKey(e.id)) return;
    double chance =
        StoryConfig.recoveryChance(game, e)
            - (e.locationId.equals(StoryInvestigationConfig.LAB)
                ? StoryInvestigationConfig.LAB_RECOVERY_PENALTY
                : e.locationId.equals(StoryInvestigationConfig.ENTRANCE)
                    ? StoryInvestigationConfig.ENTRANCE_RECOVERY_PENALTY
                    : 0);
    state.attemptResults.put(
        e.id, !early && game.rnd.nextDouble() < Math.max(.1, Math.min(.95, chance)));
  }

  void advanceMinute() {
    start();
    for (Expedition e : game.expeditions) {
      if (!StoryInvestigationConfig.target(e.locationId)
          || e.state() != Expedition.State.COMPLETED
          || !e.rewardCredited
          || !state.processedReturns.add(e.id)) continue;
      if (!Boolean.TRUE.equals(state.attemptResults.get(e.id))) {
        game.addLog(
            "Следы прошлого: отряд вернулся без результата. Можно повторить попытку: "
                + game.expeditionController.location(e.locationId).name);
        continue;
      }
      if (!e.locationId.equals(target())) continue;
      if (e.locationId.equals(StoryInvestigationConfig.ARCHIVE)) {
        state.items.add(StoryInvestigationConfig.DOCUMENTS);
        state.items.add(StoryInvestigationConfig.CATASTROPHE);
        state.items.add(StoryInvestigationConfig.MENTION);
        state.objectives.add("dawn.archive");
        state.investigation = Phase.ARCHIVE_REVIEW;
        story.message(StoryInvestigationConfig.ARCHIVE_REVIEW.event);
        game.addLog(
            "Следы прошлого: доставлены архивные документы, запись о первых днях катастрофы и"
                + " упоминание проекта «Рассвет».");
      } else if (e.locationId.equals(StoryInvestigationConfig.LAB)) {
        state.items.add(StoryInvestigationConfig.JOURNAL);
        state.items.add(StoryInvestigationConfig.NOTES);
        state.items.add(StoryInvestigationConfig.KEY);
        state.objectives.add("dawn.lab");
        state.investigation = Phase.LAB_REVIEW;
        story.message(StoryInvestigationConfig.LAB_REVIEW.event);
        game.addLog(
            "Следы прошлого: доставлены исследовательский журнал, технические записи и фрагмент"
                + " ключа доступа.");
      } else {
        state.entranceInspected = true;
        state.objectives.add("dawn.entrance");
        state.investigation = Phase.ENTRANCE_REVIEW;
        story.message(StoryInvestigationConfig.ENTRANCE_REVIEW.event);
        game.addLog(
            "Следы прошлого: внешний осмотр завершён. Гермодверь остаётся закрытой; полного ключа и"
                + " кода допуска нет.");
      }
    }
    syncMap();
  }

  void dialogueComplete(StoryDialogue d) {
    if (d == StoryInvestigationConfig.INTRO) {
      state.objectives.add("dawn.eva");
      state.investigation = Phase.ARCHIVE;
    } else if (d == StoryInvestigationConfig.ARCHIVE_REVIEW) {
      state.objectives.add("dawn.review");
      state.investigation = Phase.LAB;
    } else if (d == StoryInvestigationConfig.LAB_REVIEW) {
      state.objectives.add("dawn.notes");
      state.investigation = Phase.ENTRANCE;
    } else if (d == StoryInvestigationConfig.ENTRANCE_REVIEW) {
      state.objectives.add("dawn.warning");
      state.investigation = Phase.DECISION;
    }
    syncMap();
  }

  String choose(String id, boolean confirmed) {
    if (!confirmed) return "Подтвердите решение о документах";
    if (state.investigation != Phase.DECISION || !state.investigationDecision.isEmpty())
      return "Решение уже принято или ещё недоступно";
    if (!id.equals(StoryInvestigationConfig.SHARE) && !id.equals(StoryInvestigationConfig.KEEP))
      return "Неизвестное решение";
    state.investigationDecision = id;
    state.flags.add(id);
    state.objectives.add("dawn.choice");
    state.investigation = Phase.COMPLETE;
    game.addLog(
        "Следы прошлого: "
            + (id.equals(StoryInvestigationConfig.SHARE)
                ? "поделиться найденными документами с выжившими"
                : "сохранить информацию для дальнейшего расследования")
            + ". Первое решение не изменено. Последствия для фракций появятся в будущем.");
    story.factions.start();
    game.save();
    game.invalidate();
    return "";
  }
}
