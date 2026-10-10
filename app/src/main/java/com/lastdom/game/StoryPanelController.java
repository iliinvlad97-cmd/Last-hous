package com.lastdom.game;

import android.view.MotionEvent;
import java.util.*;

/** Story input and navigation; all durable actions are delegated to StoryController. */
final class StoryPanelController {
  enum Mode {
    JOURNAL,
    MESSAGE,
    SPECIALIST,
    CHOICE,
    FACTION_CHOICE,
    INVESTIGATION_CHOICE
  }

  static final class Row {
    final String text, id;
    final boolean action;

    Row(String text, String id, boolean action) {
      this.text = text;
      this.id = id;
      this.action = action;
    }
  }

  final GameView view;
  boolean open;
  Mode mode = Mode.JOURNAL;
  String messageId = "", selectedId = "", choiceId = "", result = "";
  int scroll, revision, dialogueStep, response = -1;
  private float dragY, startY;
  private boolean dragging, moved;
  final List<Row> rows = new ArrayList<>();

  StoryPanelController(GameView view) {
    this.view = view;
  }

  void pending() {
    if (open) {
      if (view.game.storyController.prompt && !view.game.event && !view.defensePanel.open) {
        view.game.storyController.prompt = false;
        showMessage(view.game.story.pendingMessage);
      } else refresh();
      return;
    }
    if (view.game.storyController.prompt
        && view.game.screen != GameView.ONLINE_WORLD
        && !view.game.event
        && !view.game.gameOver
        && !view.defensePanel.open
        && !view.cityMap.eventPanel
        && !view.cityMap.expeditionPanel
        && !view.game.jobMenu
        && view.game.overlay == 0) {
      view.game.storyController.prompt = false;
      showMessage(view.game.story.pendingMessage);
    }
  }

  void showJournal() {
    view.game.storyController.activateVoices();
    view.game.storyController.investigation.activate();
    view.game.storyController.factions.activate();
    open = true;
    mode = Mode.JOURNAL;
    scroll = 0;
    result = selectedId = choiceId = "";
    refresh();
  }

  void showMessage(String id) {
    if (StoryConfig.event(id) == null || !view.game.story.messages.contains(id)) return;
    open = true;
    mode = Mode.MESSAGE;
    messageId = id;
    if (view.game.story.pendingMessage.equals(id)) view.game.storyController.prompt = false;
    scroll = 0;
    result = choiceId = "";
    response = -1;
    refresh();
  }

  void close() {
    open = false;
    choiceId = "";
    view.game.storyController.prompt = false;
  }

  boolean choiceMode() {
    return mode == Mode.CHOICE || mode == Mode.INVESTIGATION_CHOICE || mode == Mode.FACTION_CHOICE;
  }

  String title() {
    return mode == Mode.FACTION_CHOICE
        ? "ИНФОРМАЦИЯ О ВХОДЕ"
        : mode == Mode.MESSAGE
            ? "РАДИОПЕРЕГОВОРЫ"
            : mode == Mode.SPECIALIST
                ? "РАСШИФРОВКА"
                : mode == Mode.INVESTIGATION_CHOICE
                    ? "СУДЬБА ДОКУМЕНТОВ"
                    : mode == Mode.CHOICE ? "СУДЬБА КООРДИНАТ" : "СЮЖЕТ · ПОСЛЕДНИЙ СИГНАЛ";
  }

  void row(String text) {
    rows.add(new Row(text, "", false));
  }

  void refresh() {
    rows.clear();
    StoryState s = view.game.story;
    if (!result.isEmpty()) row(result);
    if (mode == Mode.MESSAGE) {
      StoryEvent e = StoryConfig.event(messageId);
      StoryDialogue d = StoryDialogue.find(messageId);
      if (d == null) {
        row(e.source);
        row(e.text);
      } else if (s.completedDialogues.contains(messageId)) {
        row("РАЗГОВОР ЗАВЕРШЁН · история");
        for (int i = 0; i < d.lines.length; i++) {
          String key = messageId + "." + i;
          row(s.transcripts.getOrDefault(key, view.game.storyController.dialogueText(d, i)));
          if (s.answers.containsKey(key)) row("Ваш ответ: " + s.answers.get(key));
        }
      } else {
        dialogueStep = view.game.storyController.step(d);
        if (dialogueStep >= d.lines.length) row("Разговор завершён");
        else {
          row("Реплика " + (dialogueStep + 1) + " / " + d.lines.length);
          row(view.game.storyController.dialogueText(d, dialogueStep));
          for (int i = 0; i < d.lines[dialogueStep].responses.length; i++)
            rows.add(
                new Row(
                    (response == i ? "✓ " : "") + d.lines[dialogueStep].responses[i],
                    Integer.toString(i),
                    true));
        }
      }
    } else if (mode == Mode.SPECIALIST) {
      row("Мастерская · 60 игровых минут. Специалист временно прекращает свою работу.");
      if (!view.game.storyController.workshopAvailable())
        row("Недоступно: мастерская повреждена или улучшается");
      for (Resident r : view.game.people) {
        if (!r.role.equals("Инженер") && !r.role.equals("Механик")) continue;
        String reason = view.game.storyController.specialistReason(r);
        rows.add(
            new Row(
                (selectedId.equals(r.id) ? "✓ " : "")
                    + r.name
                    + " · "
                    + r.role
                    + " · "
                    + r.job
                    + " · здоровье "
                    + r.health
                    + "% · усталость "
                    + r.fatigue
                    + "%"
                    + (reason.isEmpty() ? " · Выбрать" : " · " + reason),
                r.id,
                reason.isEmpty()));
      }
    } else if (mode == Mode.FACTION_CHOICE) {
      row(
          "Кому вы готовы доверить информацию о подземном входе? Любой выбор позволяет продолжить"
              + " кампанию.");
      for (String id :
          new String[] {
            StoryFactionConfig.UNION,
            StoryFactionConfig.GARRISON,
            StoryFactionConfig.HEIRS,
            StoryFactionConfig.NONE
          })
        rows.add(
            new Row(
                (choiceId.equals(id) ? "✓ " : "") + StoryFactionConfig.sideLabel(id), id, true));
    } else if (mode == Mode.INVESTIGATION_CHOICE) {
      row(
          "Гермодверь остаётся закрытой. Решение о документах не заменяет ваш первый выбор; оба"
              + " пути позволяют продолжить кампанию.");
      rows.add(
          new Row(
              (choiceId.equals(StoryInvestigationConfig.SHARE) ? "✓ " : "")
                  + "Поделиться найденными документами с выжившими",
              StoryInvestigationConfig.SHARE,
              true));
      rows.add(
          new Row(
              (choiceId.equals(StoryInvestigationConfig.KEEP) ? "✓ " : "")
                  + "Сохранить информацию для дальнейшего расследования",
              StoryInvestigationConfig.KEEP,
              true));
    } else if (mode == Mode.CHOICE) {
      row(
          "Часть координат «Рассвета» восстановлена. Решение сохраняется один раз и пока не"
              + " закрывает будущие главы.");
      row("Выберите вариант и подтвердите отдельной кнопкой.");
      for (StoryChoice choice : new StoryChoice[] {StoryConfig.SHARE, StoryConfig.SECRET})
        rows.add(new Row((choiceId.equals(choice.id) ? "✓ " : "") + choice.label, choice.id, true));
    } else {
      factionRows();
      if (s.investigation != StoryInvestigationController.Phase.DORMANT) {
        row("Глава 2 · СЛЕДЫ ПРОШЛОГО");
        row(view.game.storyController.investigation.objective());
        row(
            "Статус: "
                + (s.investigation == StoryInvestigationController.Phase.COMPLETE
                    ? "Выполнено"
                    : "Выполняется"));
        for (StoryObjective o : StoryInvestigationConfig.QUEST.objectives)
          row((s.objectives.contains(o.id) ? "✓ " : "○ ") + o.text);
        row("НАЙДЕННЫЕ ДОКУМЕНТЫ");
        for (String item : s.items) {
          String label = StoryInvestigationConfig.itemLabel(item);
          if (!label.isEmpty()) row("✓ " + label);
        }
        if (s.items.contains(StoryInvestigationConfig.KEY))
          row(
              view.game.storyController.investigation.description(
                  StoryInvestigationConfig.ENTRANCE));
        if (s.entranceInspected) row("Внешний осмотр выполнен. Внутрь комплекса пройти нельзя.");
        if (!s.investigationDecision.isEmpty())
          row(
              "Решение STORY 1.2: "
                  + (s.investigationDecision.equals(StoryInvestigationConfig.SHARE)
                      ? "Поделиться найденными документами с выжившими"
                      : "Сохранить информацию для дальнейшего расследования"));
        row("ИСТОРИЯ · первая глава");
      }
      row("Глава 1 · Последний сигнал");
      if (s.voicesStage == 0) {
        String reason = view.game.storyController.voicesBlockedReason();
        if (!reason.isEmpty()) row("Голоса в эфире · недоступно: " + reason);
      }
      if (s.voicesStage > 0) {
        row(
            "ГОЛОСА В ЭФИРЕ · "
                + (s.voicesStage == 6 ? "Выполнено" : "Этап " + s.voicesStage + " / 5"));
        String[] goals = {
          "",
          "Прочитать сообщение Евы",
          "Обсудить сигнал с жителями",
          "Поговорить с Евой",
          "Узнать последствия решения",
          "Прочитать зацепку",
          "«Голоса в эфире» завершено"
        };
        row(goals[s.voicesStage]);
        for (StoryObjective o : StoryConfig.VOICES.objectives)
          row((s.objectives.contains(o.id) ? "✓ " : "○ ") + o.text);
        row("Доверие Евы: " + s.evaTrust + " / 100 (сюжетное)");
        for (Map.Entry<String, String> person : s.residentIds.entrySet())
          row(
              "Отношение · "
                  + person.getKey()
                  + ": "
                  + s.attitudes.getOrDefault(person.getValue(), 50)
                  + " / 100");
        if (s.flags.contains(StoryFlags.PUBLIC_SIGNAL))
          row("Последствие: Сигнал услышан · PUBLIC_SIGNAL");
        if (s.flags.contains(StoryFlags.SECRET_SIGNAL))
          row("Последствие: Тихая частота · SECRET_SIGNAL");
      }
      row(s.phase == StoryState.Phase.DORMANT ? "Задание ещё не получено" : StoryConfig.FIRST.name);
      row(view.game.storyController.objective());
      row(
          "Статус: "
              + (s.phase == StoryState.Phase.DORMANT
                  ? "Ожидание условий"
                  : s.phase == StoryState.Phase.CHAIN_COMPLETE
                      ? "Первая цепочка выполнена"
                      : "Выполняется"));
      if (s.phase == StoryState.Phase.DECODING) {
        Resident r = view.game.expeditionController.resident(s.specialistId);
        row("Специалист: " + (r == null ? "—" : r.name));
        if (!view.game.storyController.workshopAvailable())
          row("Пауза: мастерская повреждена или улучшается");
      }
      if (s.items.contains(StoryConfig.CARRIER))
        row("Сюжетный предмет: повреждённый носитель данных (1)");
      for (StoryObjective o : StoryConfig.FIRST.objectives)
        row((s.objectives.contains(o.id) ? "✓ " : "○ ") + o.text);
      if (!s.decisionId.isEmpty())
        row(
            "Решение: "
                + (s.decisionId.equals(StoryConfig.SHARE.id)
                    ? StoryConfig.SHARE.label
                    : StoryConfig.SECRET.label));
      if (!s.messages.isEmpty()) {
        StoryEvent last = StoryConfig.event(s.messages.get(s.messages.size() - 1));
        row("ПОСЛЕДНЕЕ СООБЩЕНИЕ · " + last.source);
        row(last.text);
      }
      if (!s.answers.isEmpty()) {
        row("ОТВЕТЫ В РАЗГОВОРАХ");
        for (Map.Entry<String, String> a : s.answers.entrySet())
          row("Ева · ваш ответ: " + a.getValue());
      }
      row("СООБЩЕНИЯ · можно перечитать");
      for (int i = s.messages.size() - 1; i >= 0; i--) {
        StoryEvent e = StoryConfig.event(s.messages.get(i));
        rows.add(
            new Row(
                e.source
                    + " · "
                    + (s.readMessages.contains(e.id) ? "Прочитано" : "Новое сообщение"),
                e.id,
                true));
      }
    }
    revision++;
  }

  void factionRows() {
    StoryFactionState s = view.game.story.factions;
    if (!s.started) return;
    row("ГОРОД РАСКОЛОТ · ФРАКЦИИ");
    row(
        view.game.story.flags.contains("faction.city_divided.complete")
            ? "Статус: выполнено"
            : "Статус: выполняется");
    row(
        s.side.isEmpty()
            ? "Познакомьтесь с тремя представителями и решите, кому доверить сведения."
            : "Информация доверена: " + StoryFactionConfig.sideLabel(s.side));
    if (view.game.storyController.factions.canChoose())
      rows.add(new Row("ВЫБРАТЬ СТОРОНУ", "faction.choose", true));
    for (StoryFaction f : StoryFactionConfig.ALL) {
      int rep = s.reputation.getOrDefault(f.id, 0);
      row(f.name + " · " + rep + " / 100 · " + StoryFactionConfig.tier(rep));
      row(f.description);
      rows.add(new Row("РАЗГОВОР · " + f.representative, f.contact.event.id, true));
      row(
          f.quest.name
              + " · "
              + (s.completed.contains(f.id)
                  ? "Выполнено"
                  : s.contacts.contains(f.id) ? "Доступно" : "Сначала установите контакт"));
      row(f.quest.objectives.get(0).text);
      if (s.contacts.contains(f.id) && !s.completed.contains(f.id))
        rows.add(new Row("К МЕСТУ ЗАДАНИЯ", "faction.map." + f.id, true));
    }
    if (s.supplied.contains(StoryFactionConfig.UNION)
        && !s.completed.contains(StoryFactionConfig.UNION)) {
      row(view.game.storyController.factions.deliveryReason());
      rows.add(new Row("ПЕРЕДАТЬ 2 ЕДЫ И 2 ВОДЫ", "faction.deliver", true));
    }
    if (view.game.story.items.contains("faction.lost_protocol"))
      row("Найдено: утерянный технический протокол (1)");
    row("ИСТОРИЯ ОТНОШЕНИЙ");
    for (String line : s.history) row(line);
  }

  void journalAction(String id) {
    if (id.equals("faction.choose")) {
      mode = Mode.FACTION_CHOICE;
      choiceId = "";
      scroll = 0;
      refresh();
    } else if (id.equals("faction.deliver")) {
      result = view.game.storyController.factions.deliver(true);
      refresh();
    } else if (id.startsWith("faction.map.")) {
      StoryFaction f = StoryFactionConfig.faction(id.substring(12));
      if (f != null) {
        view.cityMap.closeSelection();
        view.cityMap.districtsLayer = false;
        view.cityMap.districtFilterId = "";
        view.cityMap.focusedLocationId = f.locationId;
        view.game.screen = GameView.CITY_MAP;
        view.game.overlay = 0;
        close();
      }
    } else showMessage(id);
  }

  String primary() {
    if (mode == Mode.FACTION_CHOICE) return "ПОДТВЕРДИТЬ РЕШЕНИЕ";
    if (mode == Mode.JOURNAL
        && view.game.story.factions.started
        && view.game.story.pendingMessage.isEmpty())
      return view.game.storyController.factions.canChoose()
          ? "ВЫБРАТЬ СТОРОНУ"
          : "ПРОДОЛЖИТЬ ВЫЖИВАНИЕ";
    if (mode == Mode.MESSAGE)
      return view.game.story.completedDialogues.contains(messageId)
          ? "К СЮЖЕТНОМУ ЖУРНАЛУ"
          : "ПРОДОЛЖИТЬ";
    if (mode == Mode.JOURNAL
        && !view.game.story.pendingMessage.isEmpty()
        && view.game.story.voicesStage > 0) return "ОТКРЫТЬ РАЗГОВОР";
    if (mode == Mode.CHOICE || mode == Mode.INVESTIGATION_CHOICE) return "ПОДТВЕРДИТЬ РЕШЕНИЕ";
    if (mode == Mode.JOURNAL
        && view.game.story.investigation == StoryInvestigationController.Phase.DECISION)
      return "РЕШИТЬ СУДЬБУ ДОКУМЕНТОВ";
    if (mode == Mode.JOURNAL && !view.game.storyController.investigation.target().isEmpty())
      return "К СЮЖЕТНОЙ ЛОКАЦИИ";
    if (mode == Mode.SPECIALIST) return "НАЧАТЬ РАСШИФРОВКУ";
    StoryState.Phase p = view.game.story.phase;
    return p == StoryState.Phase.SEARCHING
        ? "К РАДИОСТАНЦИИ"
        : p == StoryState.Phase.DECODE_READY
            ? "ВЫБРАТЬ СПЕЦИАЛИСТА"
            : p == StoryState.Phase.DECISION
                ? "ПРИНЯТЬ РЕШЕНИЕ"
                : p == StoryState.Phase.DECODING ? "РАСШИФРОВКА ИДЁТ" : "ПРОДОЛЖИТЬ ВЫЖИВАНИЕ";
  }

  boolean primaryEnabled() {
    if (mode == Mode.FACTION_CHOICE)
      return !choiceId.isEmpty() && view.game.storyController.factions.canChoose();
    if (mode == Mode.INVESTIGATION_CHOICE)
      return !choiceId.isEmpty()
          && view.game.story.investigation == StoryInvestigationController.Phase.DECISION
          && view.game.story.investigationDecision.isEmpty();
    StoryDialogue d = StoryDialogue.find(messageId);
    if (mode == Mode.MESSAGE
        && d != null
        && !view.game.story.completedDialogues.contains(messageId))
      return dialogueStep < d.lines.length
          && (d.lines[dialogueStep].responses.length == 0 || response >= 0);
    if (mode == Mode.CHOICE)
      return !choiceId.isEmpty()
          && view.game.story.phase == StoryState.Phase.DECISION
          && view.game.story.decisionId.isEmpty();
    if (mode == Mode.SPECIALIST)
      return !selectedId.isEmpty()
          && view.game.story.phase == StoryState.Phase.DECODE_READY
          && view.game.storyController.workshopAvailable()
          && view.game
              .storyController
              .specialistReason(view.game.expeditionController.resident(selectedId))
              .isEmpty();
    return true;
  }

  void primaryAction() {
    if (mode == Mode.FACTION_CHOICE) {
      result = view.game.storyController.factions.choose(choiceId, true);
      if (result.isEmpty()) showJournal();
      else refresh();
      return;
    }
    if (mode == Mode.JOURNAL
        && view.game.story.factions.started
        && view.game.story.pendingMessage.isEmpty()) {
      if (view.game.storyController.factions.canChoose()) journalAction("faction.choose");
      else close();
      return;
    }
    if (mode == Mode.INVESTIGATION_CHOICE) {
      result = view.game.storyController.investigation.choose(choiceId, true);
      if (result.isEmpty()) {
        showJournal();
        result = "Решение о документах сохранено. Гермодверь остаётся закрытой.";
      }
      refresh();
      return;
    }
    if (mode == Mode.CHOICE) {
      result = view.game.storyController.choose(choiceId, true);
      if (result.isEmpty()) {
        showJournal();
        result = "Решение сохранено. Выживание продолжается.";
      }
      refresh();
      return;
    }
    if (mode == Mode.MESSAGE) {
      StoryDialogue d = StoryDialogue.find(messageId);
      if (d != null && !view.game.story.completedDialogues.contains(messageId)) {
        result = view.game.storyController.continueDialogue(messageId, dialogueStep, response);
        response = -1;
        scroll = 0;
        if (result.isEmpty() && view.game.story.completedDialogues.contains(messageId)) {
          String next = view.game.story.pendingMessage;
          view.game.storyController.prompt = false;
          if (!next.isEmpty()) showMessage(next);
          else showJournal();
        } else refresh();
      } else {
        view.game.storyController.read(messageId);
        showJournal();
      }
      return;
    }
    if (mode == Mode.SPECIALIST) {
      result = view.game.storyController.decode(selectedId);
      if (result.isEmpty()) showJournal();
      else refresh();
      return;
    }
    if (!view.game.story.pendingMessage.isEmpty() && view.game.story.voicesStage > 0) {
      showMessage(view.game.story.pendingMessage);
      return;
    }
    if (view.game.story.investigation == StoryInvestigationController.Phase.DECISION) {
      mode = Mode.INVESTIGATION_CHOICE;
      scroll = 0;
      choiceId = "";
      refresh();
      return;
    }
    String destination = view.game.storyController.investigation.target();
    if (!destination.isEmpty()) {
      view.cityMap.closeSelection();
      view.cityMap.districtsLayer = false;
      view.cityMap.districtFilterId = "";
      view.cityMap.focusedLocationId = destination;
      view.game.screen = GameView.CITY_MAP;
      view.game.overlay = 0;
      close();
      return;
    }
    switch (view.game.story.phase) {
      case SEARCHING:
        view.cityMap.closeSelection();
        view.cityMap.districtsLayer = false;
        view.cityMap.districtFilterId = "";
        view.cityMap.focusedLocationId = StoryConfig.RADIO;
        view.game.screen = GameView.CITY_MAP;
        view.game.overlay = 0;
        close();
        break;
      case DECODE_READY:
        mode = Mode.SPECIALIST;
        scroll = 0;
        refresh();
        break;
      case DECISION:
        mode = Mode.CHOICE;
        scroll = 0;
        refresh();
        break;
      default:
        close();
    }
  }

  void touch(int action, float x, float y, StoryPanelLayout l, StoryRenderer renderer) {
    if (action == MotionEvent.ACTION_DOWN) {
      dragging = y >= l.contentTop && y <= l.contentBottom;
      dragY = startY = y;
      moved = false;
      return;
    }
    if (action == MotionEvent.ACTION_MOVE && dragging) {
      if (Math.abs(y - startY) > 8) moved = true;
      scroll = Math.max(0, Math.min(renderer.maxScroll(l), scroll + (int) (dragY - y)));
      dragY = y;
      return;
    }
    if (action == MotionEvent.ACTION_CANCEL) {
      dragging = moved = false;
      return;
    }
    if (action != MotionEvent.ACTION_UP) return;
    dragging = false;
    if (moved) {
      moved = false;
      return;
    }
    if (x < 26 || x > 394) return;
    if (y >= l.closeTop && y <= l.closeTop + 56) {
      close();
      return;
    }

    if (y >= l.primaryTop && y <= l.primaryTop + 56) {
      if (primaryEnabled()) primaryAction();
      return;
    }
    if (y >= l.contentTop && y <= l.contentBottom) {
      int index = renderer.rowAt(y - l.contentTop + scroll);
      if (index < 0 || index >= rows.size()) return;
      Row row = rows.get(index);
      if (!row.action) return;
      if (mode == Mode.MESSAGE) {
        response = Integer.parseInt(row.id);
        refresh();
      } else if (mode == Mode.JOURNAL) journalAction(row.id);
      else if (choiceMode()) {
        choiceId = row.id;
        refresh();
      } else if (mode == Mode.SPECIALIST) {
        selectedId = row.id;
        refresh();
      }
    }
  }
}
