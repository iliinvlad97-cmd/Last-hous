package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/** Animated radio map; only reads snapshots and cosmetic state, never changes the simulation. */
final class OnlineWorldRenderer {
  private final GameView view;
  private final OnlineUiStyle ui;
  private float ghostOpacity, ghostHeight;
  private final OnlineMapArt art = new OnlineMapArt();
  private OnlineWorldGameplay.Data mapData;
  private final List<Badge> badges = new ArrayList<>();
  private final Map<String, String> coopLabels = new HashMap<>();

  private static final class Badge {
    final OnlineCityEvent event;
    final String label, count;

    Badge(OnlineCityEvent e, long minute, int n) {
      event = e;
      label =
          e.state == OnlineCityEvent.State.ACTIVE
              ? "ОПЕРАЦИЯ"
              : Math.max(0, e.expiresMinute - minute) + " мин.";
      count = n > 1 ? n + " события · список" : "";
    }
  }

  private final Paint paint = new Paint(3);
  private final Path path = new Path();
  private final OnlineWorldPanelRenderer panel;
  private final OnlineCombatRenderer combatRenderer;
  private final OnlineCivicRenderer civicRenderer;
  private final Map<String, String> labels = new HashMap<>();
  private Bitmap backdrop;
  private RectF backdropBounds;
  private OnlineWorldGeometry cachedGeometry;
  private float cachedScale;
  private OnlineWorldRepository.Snapshot labelsSnapshot;

  OnlineWorldRenderer(GameView view) {
    this.view = view;
    ui = new OnlineUiStyle(view);
    panel = new OnlineWorldPanelRenderer(view, ui);
    combatRenderer = new OnlineCombatRenderer(view, ui);
    civicRenderer = new OnlineCivicRenderer(view, ui);
  }

  // An artist can replace only the backdrop. All normalized objects and hitboxes stay unchanged.
  void setBackdrop(Bitmap bitmap) {
    backdrop = bitmap;
  }

  void draw(Canvas c, OnlineWorldGeometry g) {
    OnlineWorldState state = view.onlineWorld.state;
    art.prepare(g, state.snapshot, view.scale);
    prepareStatus(state);
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
    ui.box(c, 16, g.top, 404, g.bottom, Color.rgb(17, 26, 33), 13);
    c.save();
    c.clipRect(view.sy(g.left), view.sy(g.top), view.sy(g.right), view.sy(g.bottom));
    if (backdrop != null) {
      style(Color.rgb(255, 255, 255), Paint.Style.FILL, 0);
      c.drawBitmap(backdrop, null, backdropBounds, paint);
    } else art.draw(c);
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
    drawCityEvents(c, g, state);
    for (int i = 0; i < state.shelters.size(); i++)
      drawShelter(c, g, state.shelters.get(i), i, state);
    for (int i = 0; i < state.squads.size(); i++) drawSquad(c, g, i, state);
    drawAtmosphere(c, g, state);
    c.restore();
    drawHeader(c, state);
    if (state.confirmationGlow > .01f)
      ui.box(c, 18, 108, 402, 112, alpha(view.good, (int) (state.confirmationGlow * 200)), 2);
    view.hudRenderer.drawNav(c);
    if (!state.selected()
        && state.cardOpacity > .01f
        && ghostOpacity > 0
        && ghostHeight == g.height) ui.drawGhost(c, state.cardOpacity / ghostOpacity);
    else if (!state.selected()) ui.clearGhost();
    if (state.selected()) {
      ui.beginCard();
      if (view.onlineWorld.civicPanel.active()) civicRenderer.draw(c, g);
      else if (view.onlineWorld.combatPanel.active()) combatRenderer.draw(c, g);
      else panel.draw(c, g);
      ui.endCard();
      ghostOpacity = state.cardOpacity;
      ghostHeight = g.height;
    }
  }

  private void drawHeader(Canvas c, OnlineWorldState state) {
    ui.box(c, 20, 12, 96, 56, view.panel2, 9);
    ui.bold(c, "‹ КАРТА", 30, 39, 11, view.text);
    ui.bold(c, "ONLINE 0.5", 112, 29, 18, view.text);
    ui.txt(
        c,
        state.noticeSeconds > 0
            ? state.notice
            : state.connection == OnlineWorldState.Connection.DEMO
                ? "ДЕМО-РЕЖИМ"
                : state.connection.description,
        112,
        49,
        9,
        view.accent);
    ui.box(c, 300, 12, 400, 56, Color.rgb(58, 44, 29), 9);
    ui.bold(c, "ЗАПАСЫ", 308, 39, 11, view.accent);
    tab(c, 20, 140, "КАРТА", !view.onlineWorld.civicPanel.active());
    tab(
        c,
        146,
        266,
        "СОЮЗ",
        state.panel == OnlineWorldState.Panel.ALLIANCE
            || state.panel == OnlineWorldState.Panel.ALLIANCE_NAME);
    tab(
        c,
        272,
        400,
        "СОБЫТИЯ",
        view.onlineWorld.civicPanel.active()
            && state.panel != OnlineWorldState.Panel.ALLIANCE
            && state.panel != OnlineWorldState.Panel.ALLIANCE_NAME);
  }

  private void tab(Canvas c, float left, float right, String label, boolean selected) {
    ui.box(c, left, 60, right, 108, selected ? Color.rgb(61, 46, 30) : view.panel2, 8);
    ui.bold(c, label, left + 18, 90, 11, selected ? view.accent : view.muted);
  }

  private void drawCityEvents(Canvas c, OnlineWorldGeometry g, OnlineWorldState state) {
    for (OnlineCivicPanelController.Motion motion : view.onlineWorld.civicPanel.routes.values()) {
      shape(c, g, motion.route.shape, false);
      style(alpha(view.good, 170), Paint.Style.STROKE, 2);
      c.drawPath(path, paint);
      circle(
          c, g.x(motion.position[0]), g.y(motion.position[1]), 5, view.good, Paint.Style.FILL, 0);
    }
    for (Badge badge : badges) {
      OnlineCityEvent e = badge.event;
      float x = g.x(e.x()), y = g.y(e.y());
      int color = e.type.zoneId.equals("pve_industry") ? view.accent : view.danger;
      float pulse = (float) (.5 + .5 * Math.sin(state.animationSeconds * 2.2));
      circle(c, x, y, 14 + 3 * pulse, alpha(color, 45 + (int) (45 * pulse)), Paint.Style.FILL, 0);
      circle(c, x, y, 10, color, Paint.Style.STROKE, 2);
      style(color, Paint.Style.STROKE, 1.5f);
      c.drawPath(art.icon(e.type), paint);
      center(c, badge.label, x, y + 26, 9, color);
      if (!badge.count.isEmpty()) center(c, badge.count, x, y + 38, 8, view.muted);
    }
  }

  private void prepareStatus(OnlineWorldState state) {
    if (mapData == state.gameplay) return;
    mapData = state.gameplay;
    badges.clear();
    coopLabels.clear();
    for (OnlineZone z : state.zones) {
      OnlineCoopExpedition e = state.coop(z.id);
      if (e != null)
        coopLabels.put(
            z.id,
            e.active()
                ? "ОТРЯД: " + e.elapsedMinutes * 100 / e.durationMinutes + "%"
                : "ОТЧЁТ ГОТОВ");
    }
    Set<String> seen = new HashSet<>();
    for (int i = state.gameplay.civic.events.size() - 1; i >= 0; i--) {
      OnlineCityEvent e = state.gameplay.civic.events.get(i);
      if (e.state != OnlineCityEvent.State.AVAILABLE && e.state != OnlineCityEvent.State.ACTIVE
          || !seen.add(e.type.zoneId)) continue;
      int count = 0;
      for (OnlineCityEvent other : state.gameplay.civic.events)
        if (other.type.zoneId.equals(e.type.zoneId)
            && (other.state == OnlineCityEvent.State.AVAILABLE
                || other.state == OnlineCityEvent.State.ACTIVE)) count++;
      badges.add(new Badge(e, state.gameplay.civic.minute, count));
    }
  }

  private void drawZone(Canvas c, OnlineWorldGeometry g, OnlineZone zone, OnlineWorldState state) {
    int color = zoneColor(zone.type);
    Path zonePath = art.zone(zone.id);
    style(alpha(color, 24), Paint.Style.FILL, 0);
    c.drawPath(zonePath, paint);
    boolean selected = zone.id.equals(state.zoneId);
    float pulse = (float) (.7 + .3 * Math.sin(state.animationSeconds * 2.2));
    style(
        alpha(color, selected ? 120 + (int) (100 * state.selectionStrength * pulse) : 105),
        Paint.Style.STROKE,
        selected ? 1.5f + state.selectionStrength : 1);
    c.drawPath(zonePath, paint);
    center(
        c,
        labels.get(zone.id),
        g.x(zone.labelPosition.x),
        g.y(zone.labelPosition.y),
        8,
        alpha(color, selected ? 255 : 215));
    String progress = coopLabels.get(zone.id);
    if (progress != null)
      center(c, progress, g.x(zone.labelPosition.x), g.y(zone.labelPosition.y) + 14, 8, view.good);
    if (zone.type == OnlineZone.Type.PVP) {
      float x = g.x(zone.labelPosition.x) - 12, y = g.y(zone.labelPosition.y) + 14;
      line(c, x, y - 5, x - 5, y + 4, 1.2f, view.danger);
      line(c, x - 5, y + 4, x + 5, y + 4, 1.2f, view.danger);
      line(c, x + 5, y + 4, x, y - 5, 1.2f, view.danger);
      ui.txt(c, "PvP", x + 9, y + 4, 8, view.danger);
    }
  }

  private void drawShelter(
      Canvas c, OnlineWorldGeometry g, OnlineShelter shelter, int index, OnlineWorldState state) {
    float x = g.x(shelter.position.x), y = g.y(shelter.position.y);
    if (state.gameplay.civic.alliance != null) {
      OnlineAllianceMember member = state.gameplay.civic.alliance.member(shelter.id);
      if (member != null && member.invitation == OnlineAllianceMember.Invitation.ACCEPTED)
        circle(c, x, y, 26, alpha(view.good, 170), Paint.Style.STROKE, 2);
    }
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
    ui.box(c, x - 17, y - 16, x + 17, y + 17, Color.rgb(34, 43, 45), 6);
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
    circle(c, x, y + 4, 18, Color.argb((int) (22 * light), 229, 174, 93), Paint.Style.FILL, 0);
    circle(c, x, y + 4, 10, Color.argb((int) (30 * light), 229, 174, 93), Paint.Style.FILL, 0);
    ui.box(c, x - 3, y + 1, x + 3, y + 9, Color.argb((int) (255 * light), 229, 174, 93), 1);
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
    float second = (pulse + .5f) % 1;
    circle(
        c,
        x,
        y - 13,
        10 + 19 * second,
        Color.argb((int) (65 * Math.sin(Math.PI * second)), 104, 165, 185),
        Paint.Style.STROKE,
        1);
    line(c, x - 10, y + 15, x + 10, y + 15, 2, Color.rgb(61, 87, 96));
    line(c, x - 6, y + 8, x + 4, y - 6, 1, view.blue);
    line(c, x + 6, y + 8, x - 4, y - 6, 1, view.blue);
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
    int sources = 0;
    for (OnlineWorldGeometry.Block b : state.snapshot.buildings) {
      if (!b.industrial || sources++ >= 3) continue;
      float bx = g.x(b.x) + (g.right - g.left) * b.width * .26f, by = g.y(b.y) - 12;
      for (int puff = 0; puff < 2; puff++) {
        float t = (float) ((state.animationSeconds * .12 + puff * .5 + sources * .19) % 1);
        float x = bx + 5 * t, y = by - 22 * t, r = 4 + 9 * t;
        style(Color.argb((int) (24 * Math.sin(t * Math.PI)), 165, 161, 146), Paint.Style.FILL, 0);
        c.drawOval(
            view.sy(x - r), view.sy(y - r * .45f), view.sy(x + r), view.sy(y + r * .45f), paint);
      }
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
    view.p.setTypeface(OnlineUiStyle.REGULAR);
    ui.txt(c, text, x - view.p.measureText(text) / view.scale / 2, y, size, color);
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
