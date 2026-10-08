package com.lastdom.game;

import android.graphics.*;
import java.util.ArrayList;
import java.util.List;

/** Read-only room information, accessible fixed buttons and optional subtle artwork badges. */
final class RoomUpgradeRenderer {
  private final GameView view;

  RoomUpgradeRenderer(GameView view) {
    this.view = view;
  }

  void draw(Canvas c) {
    GameController game = view.game;
    int room = game.selectedRoom;
    if (!RoomUpgradeConfig.valid(room)) return;
    RoomUpgradeLayout layout = new RoomUpgradeLayout(view.H / view.scale);
    view.overlayRenderer.dimForOverlay(c);
    view.box(c, 18, layout.top, 402, layout.bottom, view.panel, 18);
    view.bold(c, "×", 372, layout.top + 31, 22, view.muted);
    view.bold(
        c,
        view.roomUpgradePanel.choosing
            ? "ВЫБОР СТРОИТЕЛЯ"
            : game.rooms[room].toUpperCase(java.util.Locale.ROOT),
        30,
        layout.top + 35,
        17,
        view.text);
    view.txt(
        c,
        "Уровень " + game.roomLevels[room] + " • состояние " + game.roomCondition[room] + "%",
        30,
        layout.top + 63,
        12,
        view.accent);
    if (view.roomUpgradePanel.choosing) {
      drawBuilders(c, layout);
      return;
    }
    List<String> lines = new ArrayList<>();
    lines.add(
        RoomUpgradeConfig.effect(room)
            + ": "
            + game.roomUpgradeController.percent(room)
            + "% от базовой");
    lines.add((room == 5 ? "Отдыхают: " : "Работают: ") + game.occupants(room));
    for (Resident resident : game.people)
      if (game.homeRoomFor(resident) == room && game.survivalController.working(resident))
        lines.add(
            resident.name
                + ": эффективность "
                + game.survivalController.efficiencyPercent(resident)
                + "% до бонуса комнаты");
    if (room == 2)
      for (Resident resident : game.people)
        if (game.survivalController.treating(resident)) lines.add("Лечится: " + resident.name);
    lines.add(game.roomBonus(room));
    RoomUpgradeTask active = game.roomUpgradeController.active();
    if (active != null && active.room == room) {
      Resident builder = game.expeditionController.resident(active.builderId);
      lines.add("УЛУЧШЕНИЕ ДО УРОВНЯ " + active.targetLevel);
      lines.add("Строитель: " + (builder == null ? "ожидание доступного жителя" : builder.name));
      lines.add("Прогресс: " + active.progress() + "%");
      lines.add(
          "Осталось: "
              + active.remaining()
              + " игровых минут ("
              + game.formatBuild(active.remaining())
              + ")");
      lines.add("После завершения: " + RoomUpgradeConfig.percent(room, active.targetLevel) + "%");
      lines.add("Материалы уже списаны. Строительство идёт по игровому времени.");
    } else if (game.roomLevels[room] < 3) {
      int target = game.roomLevels[room] + 1,
          cost = RoomUpgradeConfig.cost(room, target),
          minutes = RoomUpgradeConfig.minutes(room, target);
      lines.add(
          "После улучшения: "
              + RoomUpgradeConfig.percent(room, target)
              + "% (+"
              + (RoomUpgradeConfig.percent(room, target) - 100)
              + "% к базе)");
      lines.add("Стоимость: " + cost + " материалов • доступно: " + game.mats);
      lines.add("Время: " + minutes + " игровых минут (" + game.formatBuild(minutes) + ")");
      if (game.mats < cost) lines.add("Не хватает материалов: " + (cost - game.mats));
      if (active != null) lines.add("Идёт строительство: " + game.rooms[active.room]);
      Resident builder = game.expeditionController.resident(view.roomUpgradePanel.builderId);
      lines.add("Строитель: " + (builder == null ? "выберите жителя" : builder.name));
      if (builder != null) {
        String reason = game.roomUpgradeController.unavailableReason(builder);
        if (!reason.isEmpty()) lines.add(reason);
      }
      lines.add("Стоимость списывается при подтверждении начала строительства.");
    } else lines.add("Максимальный уровень");
    if (!view.roomUpgradePanel.message.isEmpty()) lines.add(view.roomUpgradePanel.message);
    body(c, layout, lines);
    String blocked = game.roomUpgradeController.blockedReason(room);
    String button =
        game.roomLevels[room] >= 3
            ? "Максимальный уровень"
            : active != null
                ? (active.room == room ? "СТРОИТЕЛЬСТВО ИДЁТ" : "ДРУГАЯ КОМНАТА СТРОИТСЯ")
                : view.roomUpgradePanel.builderId.isEmpty()
                    ? "УЛУЧШИТЬ ДО УРОВНЯ " + (game.roomLevels[room] + 1)
                    : "НАЧАТЬ СТРОИТЕЛЬСТВО";
    button(c, 30, layout.actionTop, 390, 48, button, blocked.isEmpty());
    button(c, 30, layout.secondaryTop, 206, 43, "НАЗНАЧИТЬ", false);
    button(c, 214, layout.secondaryTop, 390, 43, "ЗАКРЫТЬ", false);
  }

  private void drawBuilders(Canvas c, RoomUpgradeLayout layout) {
    int pages = Math.max(1, (view.game.people.size() + layout.capacity - 1) / layout.capacity);
    int page = Math.min(view.roomUpgradePanel.page, pages - 1);
    for (int row = 0; row < layout.capacity; row++) {
      int i = page * layout.capacity + row;
      if (i >= view.game.people.size()) break;
      Resident resident = view.game.people.get(i);
      float top = layout.rowTop + row * 64;
      String reason = view.game.roomUpgradeController.unavailableReason(resident);
      view.box(c, 30, top, 390, top + 56, view.panel2, 9);
      view.residentRenderer.drawMiniPortrait(c, resident, 50, top + 24, i);
      view.bold(
          c,
          resident.name + " • " + resident.role,
          74,
          top + 20,
          12,
          reason.isEmpty() ? view.text : view.muted);
      view.txt(
          c,
          "Здоровье " + resident.health + " • усталость " + resident.fatigue,
          74,
          top + 36,
          9,
          view.muted);
      view.txt(
          c,
          reason.isEmpty() ? "Доступен • " + resident.job : reason,
          74,
          top + 50,
          9,
          reason.isEmpty() ? view.good : view.danger);
    }
    view.txt(c, "‹ Назад", 34, layout.pageY, 11, view.muted);
    view.txt(c, (page + 1) + " / " + pages, 190, layout.pageY, 11, view.text);
    view.txt(c, "Далее ›", 326, layout.pageY, 11, view.muted);
    button(c, 30, layout.actionTop, 390, 48, "НАЗАД К КОМНАТЕ", false);
    button(c, 30, layout.secondaryTop, 390, 43, "ЗАКРЫТЬ", false);
  }

  private void body(Canvas c, RoomUpgradeLayout layout, List<String> rows) {
    List<String> lines = new ArrayList<>();
    view.p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
    view.p.setTextSize(view.sy(12));
    for (String row : rows) {
      String line = "";
      for (String word : row.split(" ")) {
        if (!line.isEmpty() && view.p.measureText(line + " " + word) > view.sy(350)) {
          lines.add(line);
          line = "";
        }
        line += (line.isEmpty() ? "" : " ") + word;
      }
      lines.add(line);
    }
    view.roomUpgradePanel.lineCount = lines.size();
    int scroll =
        Math.min(view.roomUpgradePanel.scroll, Math.max(0, lines.size() - layout.visibleLines));
    for (int i = 0; i < layout.visibleLines && scroll + i < lines.size(); i++)
      view.txt(c, lines.get(scroll + i), 34, layout.top + 93 + i * 18, 12, view.text);
    if (lines.size() > layout.visibleLines)
      view.txt(
          c,
          "↑ ↓ Прокрутите панель • "
              + (scroll + 1)
              + "–"
              + Math.min(scroll + layout.visibleLines, lines.size())
              + " / "
              + lines.size(),
          34,
          layout.actionTop - 10,
          9,
          view.muted);
  }

  private void button(
      Canvas c, float left, float top, float right, float height, String text, boolean primary) {
    view.box(c, left, top, right, top + height, primary ? view.accent : view.panel2, 10);
    view.bold(c, text, left + 14, top + height / 2 + 5, 11, primary ? view.bg : view.text);
  }

  void drawIndicators(Canvas c, float top, float bottom) {
    RoomUpgradeTask active = view.game.roomUpgradeController.active();
    for (int room = 0; room < 6; room++) {
      boolean building = active != null && active.room == room;
      if (view.game.roomLevels[room] == 1 && !building) continue;
      float[] rect = ShelterGeometry.fullSceneRoomRect(room, top, bottom);
      float left = rect[0] + 4, y = rect[1] + 4, width = building ? 82 : 48;
      view.box(c, left, y, left + width, y + 18, Color.argb(195, 14, 18, 22), 5);
      view.bold(
          c,
          building
              ? "↑ " + active.targetLevel + " • " + active.progress() + "%"
              : "УР. " + view.game.roomLevels[room],
          left + 6,
          y + 12,
          9,
          building ? view.accent : view.good);
    }
  }
}
