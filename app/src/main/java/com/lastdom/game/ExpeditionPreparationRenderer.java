package com.lastdom.game;

import android.graphics.Canvas;

/** Read-only UI of the actual shelter residents, not copies of their state. */
final class ExpeditionPreparationRenderer {
  private final GameView view;

  ExpeditionPreparationRenderer(GameView view) {
    this.view = view;
  }

  void draw(Canvas c, CityMapLayout map) {
    CityMapController ui = view.cityMap;
    MapLocation target = ui.preparation();
    if (target == null) return;
    ExpeditionPreparationLayout layout = new ExpeditionPreparationLayout(map.panelBottom);
    view.box(c, 0, 0, 420, view.H / view.scale, android.graphics.Color.argb(205, 9, 14, 19), 0);
    view.box(c, 18, 70, 402, layout.bottom, view.panel, 16);
    view.bold(c, "ПОДГОТОВКА ЭКСПЕДИЦИИ", 30, 102, 17, view.text);
    view.bold(c, target.name, 30, 130, 14, view.accent);
    view.txt(c, target.distance.label + " • Риск: " + target.risk.label, 30, 155, 11, view.muted);
    int minutes = ExpeditionConfig.oneWayMinutes(target);
    view.txt(
        c,
        "Путь: " + minutes + " мин. туда / " + minutes + " мин. обратно",
        30,
        177,
        11,
        view.text);
    view.txt(c, "Возвращение будет доступно на следующем этапе", 30, 194, 9, view.muted);
    int pages = Math.max(1, (view.game.people.size() + layout.capacity - 1) / layout.capacity);
    int page = Math.max(0, Math.min(ui.page, pages - 1));
    for (int row = 0; row < layout.capacity; row++) {
      int index = page * layout.capacity + row;
      if (index >= view.game.people.size()) break;
      Resident resident = view.game.people.get(index);
      float top = layout.rowTop(row);
      boolean checked = ui.selectedIds.contains(resident.id);
      String reason = view.game.expeditionController.unavailableReason(resident);
      view.box(
          c,
          30,
          top,
          390,
          top + 50,
          checked ? android.graphics.Color.rgb(70, 58, 41) : view.panel2,
          9);
      view.bold(
          c,
          (checked ? "✓ " : "○ ") + resident.name + " • " + resident.role,
          40,
          top + 17,
          11,
          reason.isEmpty() ? view.text : view.muted);
      view.txt(
          c,
          "Здоровье: " + resident.health + " • Усталость: " + resident.fatigue,
          40,
          top + 32,
          10,
          view.muted);
      view.txt(
          c,
          reason.isEmpty() ? "Занятие: " + resident.job + " • Доступен" : reason,
          40,
          top + 45,
          9,
          reason.isEmpty() ? view.good : view.danger);
    }
    view.txt(c, "‹ Назад", 34, layout.pageY, 11, view.muted);
    view.txt(c, (page + 1) + " / " + pages, 194, layout.pageY, 10, view.muted);
    view.txt(c, "Далее ›", 330, layout.pageY, 11, view.muted);
    String names = "";
    for (String id : ui.selectedIds) {
      Resident resident = view.game.expeditionController.resident(id);
      if (resident != null) names += (names.isEmpty() ? "" : ", ") + resident.name;
    }
    view.txt(
        c,
        "Отряд " + ui.selectedIds.size() + "/3: " + (names.isEmpty() ? "не выбран" : names),
        30,
        layout.messageY,
        11,
        view.accent);
    view.wrap(c, ui.message, 30, layout.messageY + 15, 390, 10, view.danger, 12);
    view.box(c, 30, layout.backTop, 390, layout.backTop + 36, view.panel2, 9);
    view.bold(c, "ВЕРНУТЬСЯ К КАРТЕ", 128, layout.backTop + 24, 10, view.text);
    view.box(
        c,
        30,
        layout.sendTop,
        390,
        layout.sendTop + 40,
        ui.selectedIds.isEmpty() ? view.panel2 : view.accent,
        9);
    view.bold(
        c,
        "ОТПРАВИТЬ ЭКСПЕДИЦИЮ",
        112,
        layout.sendTop + 26,
        11,
        ui.selectedIds.isEmpty() ? view.muted : view.bg);
  }
}
