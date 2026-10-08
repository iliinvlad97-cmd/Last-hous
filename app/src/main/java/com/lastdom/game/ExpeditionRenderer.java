package com.lastdom.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Travel drawings and overlays; interpolation is cosmetic and never advances simulation time. */
final class ExpeditionRenderer {
  private final GameView view;
  private final Paint paint = new Paint(3);
  private Expedition displayed;

  ExpeditionRenderer(GameView view) {
    this.view = view;
  }

  void drawRoute(Canvas c, CityMapLayout layout) {
    Expedition expedition = view.game.expeditionController.active();
    if (expedition == null) {
      displayed = null;
      return;
    }
    MapLocation target = view.game.expeditionController.location(expedition.locationId);
    if (displayed != expedition) {
      displayed = expedition;
      view.cityMap.displayProgress = expedition.progress();
    }
    float progress = view.cityMap.displayProgress;
    if (!view.game.paused && !view.game.event && !view.game.gameOver) {
      progress += (expedition.progress() - progress) * .2f;
      if (Math.abs(progress - expedition.progress()) < .0001f) progress = expedition.progress();
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
        "ОТРЯД • "
            + Math.round(expedition.progress() * 100)
            + "% • "
            + (expedition.state() == Expedition.State.AT_LOCATION
                ? "ПРИБЫЛ"
                : expedition.remainingMinutes() + " мин. до цели"),
        42,
        layout.bottom - 35,
        11,
        view.accent);
  }

  void drawPanel(Canvas c, CityMapLayout layout) {
    Expedition expedition = view.game.expeditionController.active();
    if (!view.cityMap.expeditionPanel || expedition == null) return;
    MapLocation target = view.game.expeditionController.location(expedition.locationId);
    view.box(c, 0, 0, 420, view.H / view.scale, android.graphics.Color.argb(180, 8, 13, 18), 0);
    view.box(c, 18, layout.panelTop, 402, layout.panelBottom, view.panel, 16);
    float top = layout.panelTop;
    view.bold(c, "ЭКСПЕДИЦИЯ", 34, top + 35, 18, view.text);
    view.bold(c, "×", 372, top + 31, 22, view.muted);
    view.bold(c, target.name, 34, top + 65, 14, view.accent);
    String names = "";
    for (String id : expedition.participantIds) {
      Resident resident = view.game.expeditionController.resident(id);
      if (resident != null) names += (names.isEmpty() ? "" : ", ") + resident.name;
    }
    view.wrap(c, "Отряд: " + names, 34, top + 93, 386, 12, view.text, 17);
    view.txt(
        c,
        expedition.state() == Expedition.State.AT_LOCATION ? "Этап: прибытие" : "Этап: путь к цели",
        34,
        top + 129,
        12,
        view.text);
    view.txt(
        c,
        "До прибытия: " + expedition.remainingMinutes() + " игровых мин.",
        34,
        top + 153,
        12,
        view.text);
    view.bar(c, 34, top + 167, 386, 7, Math.round(expedition.progress() * 100), view.accent);
    view.txt(
        c,
        "Прогресс: " + Math.round(expedition.progress() * 100) + "% • " + expedition.state().name(),
        34,
        top + 193,
        10,
        view.muted);
    if (expedition.state() == Expedition.State.AT_LOCATION)
      view.wrap(
          c,
          "Отряд прибыл. Исследование локации появится в следующем этапе",
          34,
          top + 216,
          386,
          11,
          view.accent,
          16);
    view.box(c, 34, layout.panelBottom - 66, 386, layout.panelBottom - 24, view.panel2, 10);
    view.bold(c, "ЗАКРЫТЬ", 176, layout.panelBottom - 40, 11, view.text);
  }
}
