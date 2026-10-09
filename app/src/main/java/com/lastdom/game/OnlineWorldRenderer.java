package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/** Animated radio map; only reads snapshots and cosmetic state, never changes the simulation. */
final class OnlineWorldRenderer {
  private final GameView view;
  private final Paint paint = new Paint(3);
  private final Path path = new Path();
  private final OnlineWorldPanelRenderer panel;
  private final OnlineCombatRenderer combatRenderer;
  private final Map<String, String> labels = new HashMap<>();
  private Bitmap backdrop;
  private RectF backdropBounds;
  private OnlineWorldGeometry cachedGeometry;
  private float cachedScale;
  private OnlineWorldRepository.Snapshot labelsSnapshot;

  OnlineWorldRenderer(GameView view) {
    this.view = view;
    panel = new OnlineWorldPanelRenderer(view);
    combatRenderer = new OnlineCombatRenderer(view);
  }

  // An artist can replace only the backdrop. All normalized objects and hitboxes stay unchanged.
  void setBackdrop(Bitmap bitmap) {
    backdrop = bitmap;
  }

  void draw(Canvas c, OnlineWorldGeometry g) {
    OnlineWorldState state = view.onlineWorld.state;
    if (labelsSnapshot != state.snapshot) {
      labels.clear();
      for (OnlineZone zone : state.zones) labels.put(zone.id, zone.name.toUpperCase(Locale.ROOT));
      labelsSnapshot = state.snapshot;
    }
    if (cachedGeometry != g || cachedScale != view.scale) {
      cachedGeometry = g;
      cachedScale = view.scale;
      backdropBounds =
          new RectF(view.sy(g.left), view.sy(g.top), view.sy(g.right), view.sy(g.bottom));
    }
    view.box(c, 16, g.top, 404, g.bottom, Color.rgb(17, 26, 33), 13);
    c.save();
    c.clipRect(view.sy(g.left), view.sy(g.top), view.sy(g.right), view.sy(g.bottom));
    if (backdrop != null) {
      style(Color.rgb(255, 255, 255), Paint.Style.FILL, 0);
      c.drawBitmap(backdrop, null, backdropBounds, paint);
    } else drawBackdrop(c, g, state.snapshot);
    for (OnlineZone zone : state.zones) drawZone(c, g, zone, state);
    for (int i = 0; i < state.snapshot.towers.size(); i++)
      drawTower(c, g, state.snapshot.towers.get(i), state.animationSeconds + i);
    for (OnlineSquad squad : state.squads) {
      if (!squad.id.equals(state.squadId)) continue;
      shape(c, g, squad.route, false);
      style(Color.argb(115, 88, 156, 184), Paint.Style.STROKE, 1.5f);
      c.drawPath(path, paint);
    }
    for (int i = 0; i < state.gameplay.operations.size(); i++) {
      OnlineWorldGameplay.Operation operation = state.gameplay.operations.get(i);
      if (!operation.active()) continue;
      shape(c, g, operation.route.shape, false);
      style(
          alpha(
              operation.offer.kind == OnlineWorldGameplay.Kind.HELP ? view.good : view.accent, 170),
          Paint.Style.STROKE,
          2);
      c.drawPath(path, paint);
      float x = g.x(state.deliveryPositions[i * 2]), y = g.y(state.deliveryPositions[i * 2 + 1]);
      circle(c, x, y, 10, alpha(view.accent, 65), Paint.Style.FILL, 0);
      circle(
          c,
          x,
          y,
          5,
          operation.offer.kind == OnlineWorldGameplay.Kind.HELP ? view.good : view.accent,
          Paint.Style.FILL,
          0);
      circle(
          c,
          g.x(operation.route.shape.x(0)),
          g.y(operation.route.shape.y(0)),
          3,
          view.blue,
          Paint.Style.STROKE,
          1.5f);
      int end = operation.route.shape.size() - 1;
      circle(
          c,
          g.x(operation.route.shape.x(end)),
          g.y(operation.route.shape.y(end)),
          7,
          view.accent,
          Paint.Style.STROKE,
          1.5f);
    }
    for (int i = 0; i < state.shelters.size(); i++)
      drawShelter(c, g, state.shelters.get(i), i, state);
    for (int i = 0; i < state.squads.size(); i++) drawSquad(c, g, i, state);
    drawAtmosphere(c, g, state);
    c.restore();
    drawHeader(c, state);
    if (state.confirmationGlow > .01f)
      view.box(c, 18, 108, 402, 112, alpha(view.good, (int) (state.confirmationGlow * 200)), 2);
    view.hudRenderer.drawNav(c);
    if (state.selected()) {
      if (view.onlineWorld.combatPanel.active()) combatRenderer.draw(c, g);
      else panel.draw(c, g);
    }
  }

  private void drawHeader(Canvas c, OnlineWorldState state) {
    view.box(c, 20, 12, 96, 56, view.panel2, 9);
    view.bold(c, "‹ КАРТА", 30, 39, 11, view.text);
    view.bold(c, "ONLINE 0.3.1", 112, 29, 18, view.text);
    view.txt(c, "РАДИОСЕТЬ", 113, 49, 10, view.muted);
    view.box(c, 300, 12, 400, 56, Color.rgb(58, 44, 29), 9);
    view.bold(c, "ЗАПАСЫ", 308, 39, 11, view.accent);
    view.bold(c, state.connection.description, 20, 76, 12, view.accent);
    view.txt(c, "БЕЗОПАСНО", 20, 98, 10, view.good);
    view.txt(c, "PvE", 155, 98, 10, view.accent);
    view.txt(
        c,
        state.gameplay.combat.battles.stream().anyMatch(b -> b.active())
            ? "PvP: ДЕМО-БОЙ"
            : state.pvpEnabled ? "PvP: ДЕМО-ВКЛ" : "PvP: ВЫКЛ",
        244,
        98,
        10,
        state.pvpEnabled ? view.danger : view.muted);
  }

  private void drawBackdrop(
      Canvas c, OnlineWorldGeometry g, OnlineWorldRepository.Snapshot snapshot) {
    int road = 0;
    for (OnlineWorldGeometry.Shape street : snapshot.streets) {
      shape(c, g, street, false);
      style(Color.rgb(8, 16, 23), Paint.Style.STROKE, road < 3 ? 26 : 19);
      paint.setStrokeCap(Paint.Cap.ROUND);
      c.drawPath(path, paint);
      style(Color.rgb(43, 54, 61), Paint.Style.STROKE, road < 3 ? 21 : 14);
      c.drawPath(path, paint);
      paint.setStrokeCap(Paint.Cap.BUTT);
      // Broken lane markings derive from the repository's road segments.
      for (int segment = 1; segment < street.size(); segment++) {
        float ax = g.x(street.x(segment - 1)), ay = g.y(street.y(segment - 1));
        float bx = g.x(street.x(segment)), by = g.y(street.y(segment));
        float length = (float) Math.hypot(bx - ax, by - ay);
        for (float distance = 12; distance + 5 < length; distance += 30) {
          float t = distance / length, end = (distance + 5) / length;
          line(
              c,
              ax + (bx - ax) * t,
              ay + (by - ay) * t,
              ax + (bx - ax) * end,
              ay + (by - ay) * end,
              1,
              Color.argb(105, 119, 127, 121));
        }
      }
      road++;
    }
    int index = 0;
    for (OnlineWorldGeometry.Block block : snapshot.buildings) {
      float x = g.x(block.x),
          y = g.y(block.y),
          w = (g.right - g.left) * block.width,
          h = (g.bottom - g.top) * block.height;
      view.box(c, x + 4, y + 5, x + w + 4, y + h + 5, Color.argb(140, 4, 8, 12), 2);
      view.box(
          c,
          x,
          y,
          x + w,
          y + h,
          block.industrial ? Color.rgb(70, 65, 56) : Color.rgb(48, 62, 72),
          2);
      line(c, x + 2, y + 1, x + w - 2, y + 1, 1, Color.rgb(98, 108, 111));
      path.reset();
      path.moveTo(view.sy(x + w * .55f), view.sy(y));
      path.lineTo(view.sy(x + w), view.sy(y + h * .05f));
      path.lineTo(view.sy(x + w), view.sy(y + h * .48f));
      path.lineTo(view.sy(x + w * .68f), view.sy(y + h * .20f));
      path.close();
      style(Color.rgb(18, 28, 34), Paint.Style.FILL, 0);
      c.drawPath(path, paint);
      line(c, x + w * .24f, y + 4, x + w * .45f, y + h * .55f, 1, Color.rgb(15, 23, 29));
      for (int window = 0; window < 3; window++) {
        float wx = x + 3 + window * (w - 7) / 3;
        view.box(c, wx, y + h - 7, wx + 3, y + h - 3, Color.rgb(15, 25, 33), 0);
      }
      if (block.industrial) {
        view.box(c, x + w * .2f, y - 11, x + w * .32f, y + 2, Color.rgb(56, 61, 60), 1);
        line(c, x + w * .2f, y - 11, x + w * .32f, y - 11, 1, view.muted);
      }
      view.box(c, x + index % 4, y + h + 3, x + index % 4 + 6, y + h + 6, Color.rgb(68, 74, 75), 1);
      index++;
    }
  }

  private void drawZone(Canvas c, OnlineWorldGeometry g, OnlineZone zone, OnlineWorldState state) {
    int color = zoneColor(zone.type);
    shape(c, g, zone.boundary, true);
    style(alpha(color, 24), Paint.Style.FILL, 0);
    c.drawPath(path, paint);
    boolean selected = zone.id.equals(state.zoneId);
    float pulse = (float) (.7 + .3 * Math.sin(state.animationSeconds * 2.2));
    style(
        alpha(color, selected ? 120 + (int) (100 * state.selectionStrength * pulse) : 105),
        Paint.Style.STROKE,
        selected ? 1.5f + state.selectionStrength : 1);
    c.drawPath(path, paint);
    center(
        c,
        labels.get(zone.id),
        g.x(zone.labelPosition.x),
        g.y(zone.labelPosition.y),
        8,
        alpha(color, selected ? 255 : 215));
    if (zone.type == OnlineZone.Type.PVE) {
      OnlineCoopExpedition expedition = state.coop(zone.id);
      if (expedition != null)
        center(
            c,
            expedition.active()
                ? "ОТРЯД: " + expedition.elapsedMinutes * 100 / expedition.durationMinutes + "%"
                : "ОТЧЁТ ГОТОВ",
            g.x(zone.labelPosition.x),
            g.y(zone.labelPosition.y) + 14,
            8,
            view.good);
    }
  }

  private void drawShelter(
      Canvas c, OnlineWorldGeometry g, OnlineShelter shelter, int index, OnlineWorldState state) {
    float x = g.x(shelter.position.x), y = g.y(shelter.position.y);
    boolean selected = shelter.id.equals(state.shelterId);
    float pulse = (float) (.5 + .5 * Math.sin(state.animationSeconds * 2 + index));
    circle(
        c,
        x,
        y,
        selected ? 23 + 3 * state.selectionStrength * pulse : 22,
        Color.argb(selected ? 45 + (int) (45 * state.selectionStrength) : 32, 207, 153, 80),
        Paint.Style.FILL,
        0);
    view.box(c, x - 17, y - 16, x + 17, y + 17, Color.rgb(34, 43, 45), 6);
    line(c, x - 12, y - 2, x, y - 11, 2, view.accent);
    line(c, x, y - 11, x + 12, y - 2, 2, view.accent);
    line(c, x - 9, y - 1, x - 9, y + 11, 1.5f, view.text);
    line(c, x + 9, y - 1, x + 9, y + 11, 1.5f, view.text);
    line(c, x - 9, y + 11, x + 9, y + 11, 1.5f, view.text);
    float light =
        (float)
            (.72
                + .18 * Math.sin(state.animationSeconds * 2.0 + index * .9)
                + .10 * Math.sin(state.animationSeconds * .7 + index));
    view.box(c, x - 3, y + 1, x + 3, y + 9, Color.argb((int) (255 * light), 229, 174, 93), 1);
    center(c, shelter.name, x, y + 32, 9, selected ? view.accent : view.text);
    circle(
        c,
        x + 19,
        y - 13,
        3,
        Color.argb(180 + (int) (75 * pulse), 115, 186, 149),
        Paint.Style.FILL,
        0);
  }

  private void drawTower(
      Canvas c, OnlineWorldGeometry g, OnlineWorldGeometry.Point point, double seconds) {
    float x = g.x(point.x), y = g.y(point.y), pulse = (float) ((seconds / 2.8) % 1);
    circle(
        c,
        x,
        y - 13,
        10 + 19 * pulse,
        Color.argb((int) (100 * Math.sin(Math.PI * pulse)), 104, 165, 185),
        Paint.Style.STROKE,
        1);
    line(c, x, y - 15, x - 9, y + 13, 1.5f, view.blue);
    line(c, x, y - 15, x + 9, y + 13, 1.5f, view.blue);
    line(c, x - 7, y + 6, x + 7, y + 6, 1, view.blue);
    line(c, x - 4, y - 5, x + 4, y - 5, 1, view.blue);
    line(c, x, y - 22, x, y - 14, 1.5f, view.text);
    circle(c, x, y - 22, 2, view.accent, Paint.Style.FILL, 0);
  }

  private void drawSquad(Canvas c, OnlineWorldGeometry g, int index, OnlineWorldState state) {
    float x = g.x(state.squadPositions[index * 2]), y = g.y(state.squadPositions[index * 2 + 1]);
    circle(c, x, y, 10, Color.argb(65, 108, 180, 209), Paint.Style.FILL, 0);
    circle(c, x, y, 5, Color.rgb(129, 205, 230), Paint.Style.FILL, 0);
    line(c, x - 3, y, x + 3, y, 1, view.bg);
    line(c, x, y - 3, x, y + 3, 1, view.bg);
    center(c, "ДОЗОР", x, y - 13, 8, view.blue);
  }

  private void drawAtmosphere(Canvas c, OnlineWorldGeometry g, OnlineWorldState state) {
    int i = 0;
    for (OnlineWorldGeometry.Point seed : state.snapshot.mist) {
      float drift = (float) Math.sin(state.animationSeconds * .13 + i) * 13;
      float x = g.x(seed.x) + drift, y = g.y(seed.y);
      style(Color.argb(16, 152, 171, 180), Paint.Style.FILL, 0);
      c.drawOval(view.sy(x - 36), view.sy(y - 9), view.sy(x + 36), view.sy(y + 9), paint);
      i++;
    }
  }

  private void shape(
      Canvas c, OnlineWorldGeometry g, OnlineWorldGeometry.Shape shape, boolean closed) {
    path.reset();
    path.moveTo(view.sy(g.x(shape.x(0))), view.sy(g.y(shape.y(0))));
    for (int i = 1; i < shape.size(); i++)
      path.lineTo(view.sy(g.x(shape.x(i))), view.sy(g.y(shape.y(i))));
    if (closed) path.close();
  }

  private void style(int color, Paint.Style style, float width) {
    paint.setStyle(style);
    paint.setColor(color);
    paint.setStrokeWidth(view.sy(width));
  }

  private void circle(
      Canvas c, float x, float y, float radius, int color, Paint.Style style, float width) {
    style(color, style, width);
    c.drawCircle(view.sy(x), view.sy(y), view.sy(radius), paint);
  }

  private void line(Canvas c, float x, float y, float x2, float y2, float width, int color) {
    style(color, Paint.Style.STROKE, width);
    c.drawLine(view.sy(x), view.sy(y), view.sy(x2), view.sy(y2), paint);
  }

  private void center(Canvas c, String text, float x, float y, int size, int color) {
    view.p.setTextSize(view.sy(size));
    view.p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
    view.txt(c, text, x - view.p.measureText(text) / view.scale / 2, y, size, color);
  }

  static int zoneColor(OnlineZone.Type type) {
    return type == OnlineZone.Type.SAFE
        ? Color.rgb(111, 175, 140)
        : type == OnlineZone.Type.PVE ? Color.rgb(199, 147, 77) : Color.rgb(190, 85, 77);
  }

  static int alpha(int color, int alpha) {
    return (color & 0xffffff) | (alpha << 24);
  }
}
