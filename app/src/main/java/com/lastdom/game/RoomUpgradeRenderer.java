package com.lastdom.game;

import android.graphics.*;
import java.util.ArrayList;
import java.util.List;

/** Read-only room information, accessible fixed buttons and optional subtle artwork badges. */
final class RoomUpgradeRenderer {
  private final GameView view;
  private final ProductionRenderer production;

  RoomUpgradeRenderer(GameView view) {
    this.view = view;
    this.production = new ProductionRenderer(view);
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
            + effectValue(room, game.roomLevels[room])
            + (room == 5 ? "" : " от базовой"));
    RoomUpgradeTask active = game.roomUpgradeController.active();
    if (active != null && active.room == room) {
      Resident builder = game.expeditionController.resident(active.builderId);
      lines.add("УЛУЧШЕНИЕ ДО УРОВНЯ " + active.targetLevel);
      lines.add("Строитель: " + (builder == null ? "ожидание доступного жителя" : builder.name));
      lines.add("Прогресс: " + active.progress() + "% • осталось " + active.remaining() + " мин.");
      lines.add("Оплачено: " + active.paidCost + " материалов");
    } else if (game.roomLevels[room] < 3) {
      int target = game.roomLevels[room] + 1;
      ProductionController.UpgradeQuote quote =
          game.productionController.upgradeQuote(RoomUpgradeConfig.cost(room, target));
      lines.add("После улучшения: " + effectValue(room, target));
      lines.add("Стоимость: " + quote.cost + " материалов • доступно: " + game.mats);
      if (quote.saved > 0)
        lines.add("База: " + quote.base + " • экономия мастерской: " + quote.saved);
      lines.add("Время: " + RoomUpgradeConfig.minutes(room, target) + " игровых минут");
      if (game.mats < quote.cost) lines.add("Не хватает материалов: " + (quote.cost - game.mats));
      Resident builder = game.expeditionController.resident(view.roomUpgradePanel.builderId);
      if (builder != null) {
        lines.add("Строитель: " + builder.name);
        String reason = game.roomUpgradeController.unavailableReason(builder);
        if (!reason.isEmpty()) lines.add(reason);
      }
      if (active != null) lines.add("Идёт строительство: " + game.rooms[active.room]);
    } else lines.add("Максимальный уровень");
    production.append(lines, room);
    if (room == 4) {
      lines.add("Работают: " + game.occupants(room));
      lines.add(game.roomBonus(room));
    }
    if (room == 4) {
      lines.add("Прочность баррикад: " + game.raidController.durability + "%");
      lines.add("Сила защиты: " + Math.round(game.raidController.defensePower()));
      RaidState raid = game.raidController.active();
      StringBuilder defenders = new StringBuilder();
      if (raid != null)
        for (String id : raid.defenders.keySet()) {
          Resident defender = game.expeditionController.resident(id);
          if (defender != null) {
            if (defenders.length() > 0) defenders.append(", ");
            defenders.append(defender.name);
          }
        }
      lines.add("Защитники: " + (defenders.length() == 0 ? "—" : defenders));
      lines.add(
          "Ремонт: "
              + RaidConfig.REPAIR_AMOUNT
              + "% за "
              + RaidConfig.REPAIR_COST
              + " материалов • "
              + RaidConfig.REPAIR_MINUTES
              + " игровых минут.");
      if (game.raidController.repairing()) {
        BarricadeRepair repair = game.raidController.repair;
        Resident repairer = game.expeditionController.resident(repair.builderId);
        lines.add("Ремонтник: " + (repairer == null ? "—" : repairer.name));
        lines.add(
            "Ремонт "
                + repair.elapsed * 100 / RaidConfig.REPAIR_MINUTES
                + "% • осталось "
                + repair.remaining()
                + " мин.");
      }
    }
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
    button(
        c, 30, layout.secondaryTop, 206, 43, room == 4 ? "ОБОРОНА / РЕМОНТ" : "НАЗНАЧИТЬ", false);
    button(c, 214, layout.secondaryTop, 390, 43, "ЗАКРЫТЬ", false);
  }

  private String effectValue(int room, int level) {
    return room == 5
        ? SurvivalConfig.bedroomRecoveryPerHour(level) + " пунктов за игровой час"
        : RoomUpgradeConfig.percent(room, level) + "%";
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
      String rest = row;
      while (view.p.measureText(rest) > view.sy(350)) {
        int end = rest.length();
        while (end > 1 && view.p.measureText(rest.substring(0, end)) > view.sy(350)) end--;
        int space = rest.lastIndexOf(' ', end);
        if (space > 0) end = space;
        lines.add(rest.substring(0, end));
        rest = rest.substring(end).trim();
      }
      lines.add(rest);
    }
    view.roomUpgradePanel.lineCount = lines.size();
    int scroll =
        Math.max(
            0,
            Math.min(
                view.roomUpgradePanel.scroll, Math.max(0, lines.size() - layout.visibleLines)));
    view.roomUpgradePanel.scroll = scroll;
    for (int i = 0; i < layout.visibleLines && scroll + i < lines.size(); i++)
      if (lines.get(scroll + i).equals("ПРОИЗВОДСТВО / ПОЛЕЗНЫЙ ЭФФЕКТ")
          || lines.get(scroll + i).equals("УЛУЧШЕНИЕ ПОМЕЩЕНИЯ"))
        view.bold(c, lines.get(scroll + i), 34, layout.top + 93 + i * 18, 11, view.accent);
      else view.txt(c, lines.get(scroll + i), 34, layout.top + 93 + i * 18, 12, view.text);
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
