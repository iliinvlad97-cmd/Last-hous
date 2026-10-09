package com.lastdom.game;

import android.view.MotionEvent;
import java.util.*;

/** Story input and navigation; all durable actions are delegated to StoryController. */
final class StoryPanelController {
  enum Mode {
    JOURNAL,
    MESSAGE,
    SPECIALIST,
    CHOICE
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
  int scroll, revision;
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
    scroll = 0;
    result = choiceId = "";
    refresh();
  }

  void close() {
    open = false;
    choiceId = "";
    view.game.storyController.prompt = false;
  }

  boolean choiceMode() {
    return mode == Mode.CHOICE;
  }

  String title() {
    return mode == Mode.MESSAGE
        ? "РАДИОПЕРЕГОВОРЫ"
        : mode == Mode.SPECIALIST
            ? "РАСШИФРОВКА"
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
      row(e.source);
      row(e.text);
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
    } else if (mode == Mode.CHOICE) {
      row(
          "Часть координат «Рассвета» восстановлена. Решение сохраняется один раз и пока не"
              + " закрывает будущие главы.");
      row("Выберите вариант и подтвердите отдельной кнопкой.");
      for (StoryChoice choice : new StoryChoice[] {StoryConfig.SHARE, StoryConfig.SECRET})
        rows.add(new Row((choiceId.equals(choice.id) ? "✓ " : "") + choice.label, choice.id, true));
    } else {
      row("Глава 1 · Последний сигнал");
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

  String primary() {
    if (mode == Mode.MESSAGE) return "ПРОДОЛЖИТЬ";
    if (mode == Mode.CHOICE) return "ПОДТВЕРДИТЬ РЕШЕНИЕ";
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
      view.game.storyController.read(messageId);
      showJournal();
      return;
    }
    if (mode == Mode.SPECIALIST) {
      result = view.game.storyController.decode(selectedId);
      if (result.isEmpty()) showJournal();
      else refresh();
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
      if (mode == Mode.JOURNAL) showMessage(row.id);
      else if (mode == Mode.CHOICE) {
        choiceId = row.id;
        refresh();
      } else if (mode == Mode.SPECIALIST) {
        selectedId = row.id;
        refresh();
      }
    }
  }
}
