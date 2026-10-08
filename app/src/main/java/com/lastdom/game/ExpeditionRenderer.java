package com.lastdom.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Travel drawings and overlays; interpolation is cosmetic and never advances simulation time. */
final class ExpeditionRenderer {
  private final GameView view;
  private final Paint paint = new Paint(3);
  private Expedition displayed;
  private Expedition.State displayedState;

  ExpeditionRenderer(GameView view) {
    this.view = view;
  }

  void drawRoute(Canvas c, CityMapLayout layout) {
    Expedition expedition = view.game.expeditionController.report();
    if (expedition == null) {
      displayed = null;
      return;
    }
    MapLocation target = view.game.expeditionController.location(expedition.locationId);
    if (displayed != expedition || displayedState != expedition.state()) {
      displayed = expedition;
      displayedState = expedition.state();
      view.cityMap.displayProgress = expedition.routeProgress();
    }
    float progress = view.cityMap.displayProgress;
    if (!view.game.paused && !view.game.event && !view.game.gameOver) {
      progress += (expedition.routeProgress() - progress) * .2f;
      if (Math.abs(progress - expedition.routeProgress()) < .0001f)
        progress = expedition.routeProgress();
      else view.postInvalidateOnAnimation();
    }
    view.cityMap.displayProgress = progress;
    float[][] points = ExpeditionConfig.route(target);
    Path path = new Path();
    path.moveTo(view.sy(layout.x(points[0][0])), view.sy(layout.y(points[0][1])));
    for (int i = 1; i < points.length; i++)
      path.lineTo(view.sy(layout.x(points[i][0])), view.sy(layout.y(points[i][1])));
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(view.sy(2));
    paint.setColor(view.accent);
    c.drawPath(path, paint);
    paint.setStyle(Paint.Style.FILL);
    float[] point = ExpeditionConfig.point(target, progress);
    float x = layout.x(point[0]), y = layout.y(point[1]);
    paint.setColor(view.bg);
    c.drawCircle(view.sy(x), view.sy(y), view.sy(14), paint);
    paint.setColor(view.accent);
    c.drawCircle(view.sy(x), view.sy(y), view.sy(10), paint);
    view.bold(c, "О", x - 4, y + 4, 11, view.bg);
    view.box(c, 30, layout.bottom - 53, 390, layout.bottom - 25, view.panel2, 8);
    view.txt(
        c,
        (expedition.state() == Expedition.State.EXPLORING
                ? "Отряд прибыл • Исследование"
                : expedition.phaseLabel())
            + " • "
            + Math.round(expedition.progress() * 100)
            + "% • "
            + expedition.remainingMinutes()
            + " мин.",
        42,
        layout.bottom - 35,
        10,
        view.accent);
  }

  void drawPanel(Canvas c, CityMapLayout layout) {
    Expedition expedition = view.game.expeditionController.report();
    if (!view.cityMap.expeditionPanel || expedition == null) return;
    MapLocation target = view.game.expeditionController.location(expedition.locationId);
    view.box(c, 0, 0, 420, view.H / view.scale, android.graphics.Color.argb(180, 8, 13, 18), 0);
    view.box(c, 18, layout.panelTop, 402, layout.panelBottom, view.panel, 16);
    float top = layout.panelTop;
    boolean results =
        expedition.state() == Expedition.State.AWAITING_RETURN
            || expedition.state() == Expedition.State.COMPLETED;
    view.bold(
        c,
        expedition.state() == Expedition.State.COMPLETED
            ? "ОТРЯД ВЕРНУЛСЯ"
            : results ? "ЭКСПЕДИЦИЯ — РЕЗУЛЬТАТЫ" : "ЭКСПЕДИЦИЯ",
        34,
        top + 35,
        results ? 15 : 18,
        view.text);
    view.bold(c, "×", 372, top + 31, 22, view.muted);
    view.bold(c, target.name, 34, top + 65, 14, view.accent);
    java.util.List<String> lines = new java.util.ArrayList<>();
    String names = "";
    for (String id : expedition.participantIds) {
      Resident resident = view.game.expeditionController.resident(id);
      if (resident != null) names += (names.isEmpty() ? "" : ", ") + resident.name;
    }
    lines.add("Отряд: " + names);
    lines.add("Статус: " + expedition.phaseLabel());
    if (results) {
      lines.add("НАЙДЕНО / ВЗЯТО С СОБОЙ");
      for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
        if (expedition.found.get(resource) > 0 || target.lootTable.max(resource) > 0)
          lines.add(
              resource.label
                  + ": "
                  + expedition.found.get(resource)
                  + " / "
                  + expedition.cargo.get(resource));
      lines.add("Вместимость отряда: " + expedition.capacity());
      lines.add(
          "Всего найдено: " + expedition.found.total() + " • Взято: " + expedition.cargo.total());
      if (expedition.found.total() > expedition.cargo.total())
        lines.add("Найдено больше припасов, чем отряд способен унести");
      lines.add("Событие: " + expedition.explorationEvent.resultMessage());
      lines.add("Истощение: " + target.depletion() + "%");
      if (expedition.state() == Expedition.State.COMPLETED) {
        lines.add("ДОСТАВЛЕНО В УБЕЖИЩЕ");
        for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
          if (expedition.cargo.get(resource) > 0)
            lines.add(resource.label + ": +" + expedition.cargo.get(resource));
        long duration = Math.max(0, expedition.completedMinute - expedition.departureMinute);
        lines.add("Продолжительность: " + duration / 60 + " ч " + duration % 60 + " мин.");
        lines.add("Усталость и потребности учтены за время пути");
        lines.add("Ресурсы уже начислены. Кнопка подтверждает просмотр.");
      } else lines.add("Припасы будут начислены после возвращения в убежище.");
    } else {
      if (expedition.state() == Expedition.State.EXPLORING)
        lines.add("Отряд прибыл. Исследование локации.");
      lines.add("Прогресс: " + Math.round(expedition.progress() * 100) + "%");
      lines.add("Осталось: " + expedition.remainingMinutes() + " игровых мин.");
      lines.add("Грузоподъёмность: " + expedition.capacity());
      if (expedition.explorationEvent.interactive() && expedition.explorationEvent.effectsApplied)
        lines.add("Решение события: " + expedition.explorationEvent.outcome.message);
      if (expedition.state() == Expedition.State.RETURNING)
        lines.add("Везёт припасы: " + expedition.cargo.total());
    }
    lines.add("СКЛАД ЭКСПЕДИЦИЙ");
    lines.add(
        "Медикаменты: " + view.game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE));
    lines.add(
        "Снаряжение: " + view.game.expeditionWarehouse.get(ExpeditionLoot.Resource.EQUIPMENT));
    MapPanelContent.draw(view, c, layout, lines);
    view.box(
        c,
        34,
        layout.panelBottom - 66,
        386,
        layout.panelBottom - 24,
        results ? view.accent : view.panel2,
        10);
    view.bold(
        c,
        expedition.state() == Expedition.State.AWAITING_RETURN
            ? "ВЕРНУТЬСЯ В УБЕЖИЩЕ"
            : expedition.state() == Expedition.State.COMPLETED ? "ЗАБРАТЬ ДОБЫЧУ" : "ЗАКРЫТЬ",
        results ? 105 : 176,
        layout.panelBottom - 40,
        11,
        results ? view.bg : view.text);
  }
}
