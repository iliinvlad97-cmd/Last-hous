package com.lastdom.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * A layered Canvas city: damaged streets, ruined blocks, fog, lights and small interactive markers.
 */
final class CityMapRenderer {
  private static final int INK = Color.rgb(216, 226, 230);
  private static final int MUTED = Color.rgb(135, 156, 168);
  private static final int WARM = Color.rgb(226, 157, 84);
  private static final int SAFE = Color.rgb(119, 181, 152);
  private static final float[][] BLOCKS = {
    {.04f, .03f, .10f, .12f}, {.43f, .03f, .12f, .10f}, {.86f, .03f, .10f, .14f},
    {.08f, .20f, .12f, .09f}, {.34f, .19f, .11f, .10f}, {.60f, .20f, .13f, .10f},
    {.82f, .23f, .12f, .10f}, {.04f, .33f, .10f, .14f}, {.32f, .34f, .09f, .13f},
    {.55f, .34f, .10f, .12f}, {.86f, .39f, .10f, .11f}, {.05f, .51f, .11f, .10f},
    {.25f, .50f, .11f, .09f}, {.52f, .50f, .12f, .09f}, {.76f, .51f, .16f, .08f},
    {.04f, .69f, .10f, .11f}, {.33f, .69f, .09f, .09f}, {.76f, .70f, .10f, .12f},
    {.18f, .84f, .13f, .10f}, {.66f, .86f, .13f, .08f}, {.86f, .86f, .10f, .08f}
  };
  private final GameView view;
  private final Paint paint = new Paint(3);

  CityMapRenderer(GameView view) {
    this.view = view;
  }

  void draw(Canvas canvas) {
    CityMapLayout layout = new CityMapLayout(view.H / view.scale);
    drawHeader(canvas);
    rounded(
        canvas, layout.left, layout.top, layout.right, layout.bottom, Color.rgb(19, 28, 37), 14);
    drawDistrict(canvas, layout);
    drawFog(canvas, layout);
    drawShelter(canvas, layout);
    for (MapLocation location : view.cityMap.locations) drawMarker(canvas, location, layout);
    view.expeditionRenderer.drawRoute(canvas, layout);
    text(canvas, "С", 378, layout.top + 23, 9, MUTED, true);
    line(canvas, 381, layout.top + 30, 381, layout.top + 49, 1, MUTED);
    line(canvas, 381, layout.top + 30, 377, layout.top + 36, 1, MUTED);
    line(canvas, 381, layout.top + 30, 385, layout.top + 36, 1, MUTED);
    text(canvas, "ВЫБЕРИТЕ ТОЧКУ ДЛЯ РАЗВЕДКИ", 30, layout.bottom - 11, 8, MUTED, false);
    view.hudRenderer.drawNav(canvas);
    if (view.cityMap.selected() != null) drawSelection(canvas, layout);
    view.expeditionPreparationRenderer.draw(canvas, layout);
    view.expeditionRenderer.drawPanel(canvas, layout);
  }

  private void drawHeader(Canvas canvas) {
    text(canvas, "КАРТА ГОРОДА", 20, 29, 18, INK, true);
    text(canvas, GameView.VERSION_LABEL, 20, 49, 9, MUTED, false);
    rounded(canvas, 284, 12, 400, 53, Color.rgb(28, 40, 51), 9);
    text(canvas, "Д" + view.game.day + "  " + view.game.clock(), 296, 30, 11, INK, true);
    text(canvas, view.game.phase(), 296, 46, 8, WARM, false);
  }

  private void drawDistrict(Canvas c, CityMapLayout m) {
    // Roads are city artwork, not expedition routes or travel calculations.
    road(c, m, 38, .50f, .94f, .50f, .77f, .47f, .64f, .43f, .51f, .48f, .32f, .52f, .02f);
    road(c, m, 24, .02f, .65f, .22f, .61f, .48f, .66f, .71f, .61f, .98f, .66f);
    road(c, m, 23, .02f, .39f, .22f, .43f, .47f, .40f, .74f, .37f, .98f, .33f);
    road(c, m, 17, .24f, .02f, .26f, .18f, .24f, .31f, .20f, .43f, .22f, .61f, .15f, .81f);
    road(c, m, 17, .76f, .02f, .74f, .18f, .79f, .33f, .72f, .48f, .70f, .62f, .76f, .82f);
    // Broken lane markings and cratered junctions.
    for (int i = 0; i < 12; i++) {
      float y = m.y(.05f + i * .065f), x = m.x(.50f - (i > 5 ? .035f : 0));
      line(c, x, y, x - 1, y + 8, 1, Color.argb(95, 107, 117, 120));
    }
    for (int i = 0; i < 7; i++) {
      float x = m.x(.08f + i * .14f), y = m.y(.63f);
      fill(c, Color.rgb(14, 21, 28));
      c.drawOval(view.sy(x - 8), view.sy(y - 4), view.sy(x + 10), view.sy(y + 5), paint);
      line(c, x - 10, y + 7, x - 5, y + 2, 1, Color.rgb(65, 72, 76));
      line(c, x - 5, y + 2, x + 1, y + 6, 1, Color.rgb(65, 72, 76));
    }
    for (int i = 0; i < BLOCKS.length; i++) drawRuin(c, m, BLOCKS[i], i);
    glow(c, m.x(.48f), m.y(.82f), 37, Color.argb(72, 211, 151, 80));
    glow(c, m.x(.22f), m.y(.61f), 23, Color.argb(45, 228, 147, 65));
    glow(c, m.x(.73f), m.y(.37f), 21, Color.argb(34, 112, 172, 210));
    for (int i = 0; i < 3; i++) {
      float x = m.x(.12f + i * .31f), y = m.y(.53f - i * .05f);
      for (int plume = 0; plume < 4; plume++) {
        fill(c, Color.argb(16, 146, 167, 176));
        c.drawOval(
            view.sy(x - 12 - plume * 3),
            view.sy(y - 12 - plume * 13),
            view.sy(x + 14 + plume * 6),
            view.sy(y + 9 - plume * 9),
            paint);
      }
    }
    text(c, "СТАРЫЙ ПРОМРАЙОН", m.x(.09f), m.y(.49f), 6, MUTED, false);
    text(c, "ЖИЛЫЕ КВАРТАЛЫ", m.x(.57f), m.y(.77f), 6, MUTED, false);
  }

  private void road(Canvas c, CityMapLayout m, float width, float... points) {
    Path path = new Path();
    path.moveTo(view.sy(m.x(points[0])), view.sy(m.y(points[1])));
    for (int i = 2; i < points.length; i += 2)
      path.lineTo(view.sy(m.x(points[i])), view.sy(m.y(points[i + 1])));
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeWidth(view.sy(width + 4));
    paint.setColor(Color.rgb(12, 20, 27));
    c.drawPath(path, paint);
    paint.setStrokeWidth(view.sy(width));
    paint.setColor(Color.rgb(41, 51, 59));
    c.drawPath(path, paint);
    paint.setStrokeCap(Paint.Cap.BUTT);
    paint.setStyle(Paint.Style.FILL);
  }

  private void drawRuin(Canvas c, CityMapLayout m, float[] block, int index) {
    float x = m.x(block[0]), y = m.y(block[1]);
    float w = (m.right - m.left) * block[2], h = (m.bottom - m.top) * block[3];
    rounded(c, x + 4, y + 5, x + w + 4, y + h + 5, Color.argb(130, 5, 10, 16), 2);
    rounded(c, x, y, x + w, y + h, Color.rgb(45 + index % 3 * 3, 58, 68), 2);
    line(c, x + 2, y + 1, x + w - 2, y + 1, 1, Color.rgb(88, 101, 108));
    fill(c, Color.rgb(27, 38, 47));
    Path missingRoof = new Path();
    missingRoof.moveTo(view.sy(x + w * .55f), view.sy(y));
    missingRoof.lineTo(view.sy(x + w), view.sy(y + h * .08f));
    missingRoof.lineTo(view.sy(x + w), view.sy(y + h * .44f));
    missingRoof.lineTo(view.sy(x + w * .65f), view.sy(y + h * .26f));
    missingRoof.close();
    c.drawPath(missingRoof, paint);
    for (int window = 0; window < 3; window++) {
      float wx = x + 5 + window * (w - 10) / 3f;
      fill(c, (index + window) % 9 == 0 ? Color.rgb(160, 120, 77) : Color.rgb(15, 27, 36));
      c.drawRect(view.sy(wx), view.sy(y + h - 9), view.sy(wx + 3), view.sy(y + h - 5), paint);
    }
    line(c, x + w * .30f, y + 5, x + w * .45f, y + h * .46f, 1, Color.rgb(16, 27, 35));
    line(c, x + w * .45f, y + h * .46f, x + w * .36f, y + h * .63f, 1, Color.rgb(16, 27, 35));
    for (int rubble = 0; rubble < 3; rubble++) {
      float rx = x + rubble * 8 + 2;
      rounded(c, rx, y + h + 3, rx + 5, y + h + 7, Color.rgb(63, 71, 76), 1);
    }
  }

  private void drawFog(Canvas c, CityMapLayout m) {
    fill(c, Color.argb(115, 12, 23, 34));
    c.drawRect(view.sy(m.left), view.sy(m.top), view.sy(m.right), view.sy(m.y(.29f)), paint);
    for (int i = 0; i < 6; i++) {
      float x = m.x(.13f + i * .15f);
      fill(c, Color.argb(28, 109, 137, 156));
      c.drawOval(view.sy(x - 42), view.sy(m.y(.21f)), view.sy(x + 45), view.sy(m.y(.32f)), paint);
    }
    text(c, "НЕИССЛЕДОВАННЫЙ СЕКТОР", m.x(.23f), m.y(.04f), 7, MUTED, false);
  }

  private void drawShelter(Canvas c, CityMapLayout m) {
    float x = m.x(CityMapLayout.SHELTER_X), y = m.y(CityMapLayout.SHELTER_Y);
    glow(c, x, y, 36, Color.argb(70, 114, 184, 148));
    rounded(c, x - 25, y - 22, x + 25, y + 22, Color.rgb(25, 49, 49), 9);
    line(c, x - 17, y - 3, x, y - 15, 2, SAFE);
    line(c, x, y - 15, x + 17, y - 3, 2, SAFE);
    line(c, x - 12, y - 3, x - 12, y + 12, 2, SAFE);
    line(c, x + 12, y - 3, x + 12, y + 12, 2, SAFE);
    line(c, x - 12, y + 12, x + 12, y + 12, 2, SAFE);
    rounded(c, x - 3, y + 1, x + 3, y + 12, WARM, 1);
    centered(c, "УБЕЖИЩЕ", x, y - 32, 10, SAFE, true);
    centered(c, "БЕЗОПАСНАЯ ТОЧКА", x, y + 36, 7, SAFE, false);
  }

  private void drawMarker(Canvas c, MapLocation location, CityMapLayout m) {
    float x = m.x(location.mapX), y = m.y(location.mapY);
    boolean locked = location.isLocked(), selected = view.cityMap.selected() == location;
    int outline = locked ? Color.rgb(104, 128, 145) : WARM;
    if (selected) glow(c, x, y, 31, Color.argb(85, 222, 153, 72));
    fill(c, locked ? Color.rgb(28, 42, 54) : Color.rgb(35, 44, 52));
    c.drawCircle(view.sy(x), view.sy(y), view.sy(21), paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(view.sy(selected ? 2.5f : 1.3f));
    paint.setColor(outline);
    c.drawCircle(view.sy(x), view.sy(y), view.sy(21), paint);
    paint.setStyle(Paint.Style.FILL);
    if (locked) drawLock(c, x, y, outline);
    else drawIcon(c, location.kind, x, y, INK);
    String[] lines = location.markerName.split("\n");
    for (int i = 0; i < lines.length; i++)
      centered(c, lines[i], x, y - 31 - (lines.length - 1 - i) * 10, 8, locked ? MUTED : INK, true);
    int risk = riskColor(location);
    String badge =
        locked ? "ЗАКРЫТО" : location.depleted() ? "ИСТОЩЕНА" : location.risk.markerLabel;
    rounded(c, x - 38, y + 26, x + 38, y + 42, Color.argb(215, 16, 26, 34), 5);
    centered(c, badge, x, y + 37, 7, locked ? MUTED : risk, true);
  }

  private void drawIcon(Canvas c, MapLocation.Kind kind, float x, float y, int color) {
    if (kind == MapLocation.Kind.PHARMACY || kind == MapLocation.Kind.HOSPITAL) {
      rounded(c, x - 3, y - 11, x + 3, y + 11, color, 1);
      rounded(c, x - 11, y - 3, x + 11, y + 3, color, 1);
      if (kind == MapLocation.Kind.HOSPITAL) {
        line(c, x - 14, y - 14, x - 14, y + 14, 1, color);
        line(c, x + 14, y - 14, x + 14, y + 14, 1, color);
      }
    } else if (kind == MapLocation.Kind.STORE) {
      line(c, x - 11, y - 7, x + 11, y - 7, 3, color);
      line(c, x - 9, y - 4, x - 9, y + 10, 2, color);
      line(c, x + 9, y - 4, x + 9, y + 10, 2, color);
      line(c, x - 9, y + 10, x + 9, y + 10, 2, color);
      line(c, x - 4, y + 3, x + 4, y + 3, 2, color);
    } else if (kind == MapLocation.Kind.GARAGE) {
      line(c, x - 8, y + 10, x + 7, y - 7, 3, color);
      line(c, x + 7, y - 7, x + 2, y - 10, 2, color);
      line(c, x + 7, y - 7, x + 11, y - 3, 2, color);
    } else if (kind == MapLocation.Kind.POLICE) {
      Path shield = new Path();
      shield.moveTo(view.sy(x), view.sy(y - 12));
      shield.lineTo(view.sy(x + 10), view.sy(y - 7));
      shield.lineTo(view.sy(x + 7), view.sy(y + 5));
      shield.lineTo(view.sy(x), view.sy(y + 12));
      shield.lineTo(view.sy(x - 7), view.sy(y + 5));
      shield.lineTo(view.sy(x - 10), view.sy(y - 7));
      shield.close();
      fill(c, color);
      c.drawPath(shield, paint);
      line(c, x, y - 4, x, y + 5, 2, Color.rgb(35, 44, 52));
    } else {
      rounded(c, x - 9, y - 10, x + 9, y - 1, color, 3);
      line(c, x - 6, y - 1, x - 9, y + 11, 2, color);
      line(c, x + 6, y - 1, x + 9, y + 11, 2, color);
      line(c, x - 7, y + 5, x + 7, y + 5, 1, color);
    }
  }

  private void drawLock(Canvas c, float x, float y, int color) {
    rounded(c, x - 8, y - 1, x + 8, y + 10, color, 2);
    paint.setColor(color);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(view.sy(2));
    c.drawArc(
        view.sy(x - 5), view.sy(y - 11), view.sy(x + 5), view.sy(y + 2), 180, 180, false, paint);
    paint.setStyle(Paint.Style.FILL);
  }

  private void drawSelection(Canvas c, CityMapLayout m) {
    MapLocation location = view.cityMap.selected();
    fill(c, Color.argb(155, 4, 10, 17));
    c.drawRect(0, 0, view.W, view.H, paint);
    rounded(c, 18, m.panelTop, 402, m.panelBottom, Color.rgb(24, 35, 46), 17);
    line(c, 35, m.panelTop + 2, 135, m.panelTop + 2, 2, WARM);
    text(c, "×", 372, m.panelTop + 31, 24, MUTED, true);
    if (location.isLocked()) {
      text(c, "РАЙОН НЕ ИССЛЕДОВАН", 34, m.panelTop + 35, 15, INK, true);
      text(c, location.name, 34, m.panelTop + 65, 14, MUTED, true);
      java.util.List<String> lines = locationLines(location);
      lines.add(0, "Исследуйте ближайшие районы, чтобы открыть путь.");
      MapPanelContent.draw(view, c, m, lines);
      rounded(c, 34, m.panelBottom - 66, 386, m.panelBottom - 24, Color.rgb(44, 59, 72), 10);
      centered(c, "ЗАКРЫТЬ", 210, m.panelBottom - 40, 11, INK, true);
    } else {
      text(c, location.name.toUpperCase(Locale.ROOT), 34, m.panelTop + 42, 15, INK, true);
      java.util.List<String> lines = locationLines(location);
      if (!view.cityMap.message.isEmpty()) lines.add(0, view.cityMap.message);
      MapPanelContent.draw(view, c, m, lines);
      rounded(
          c,
          34,
          m.panelBottom - 66,
          386,
          m.panelBottom - 24,
          location.depleted() ? Color.rgb(44, 59, 72) : WARM,
          10);
      centered(
          c,
          location.depleted() ? "ИСТОЩЕНА" : "ПОДГОТОВИТЬ ЭКСПЕДИЦИЮ",
          210,
          m.panelBottom - 40,
          11,
          location.depleted() ? MUTED : Color.rgb(27, 32, 36),
          true);
    }
  }

  private java.util.List<String> locationLines(MapLocation location) {
    java.util.List<String> lines = new java.util.ArrayList<>();
    lines.add("Добыча: " + location.loot);
    lines.add("Расстояние: " + location.distance.label);
    lines.add("Риск: " + location.risk.label);
    lines.add("Статус: " + location.statusLabel());
    lines.add("Истощение: " + location.depletion() + "%");
    lines.add(
        "Путь туда / обратно: "
            + ExpeditionConfig.oneWayMinutes(location)
            + " / "
            + ExpeditionConfig.oneWayMinutes(location)
            + " мин.");
    lines.add("Исследование: " + ExpeditionConfig.explorationMinutes(location) + " мин.");
    lines.add("ВОЗМОЖНАЯ ДОБЫЧА");
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
      if (location.lootTable.max(resource) > 0)
        lines.add(
            resource.label
                + ": "
                + location.lootTable.min(resource)
                + "–"
                + location.lootTable.max(resource));
    lines.add("Базовые диапазоны без бонусов и истощения.");
    lines.add(
        "СКЛАД ЭКСПЕДИЦИЙ: медикаменты "
            + view.game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE)
            + ", снаряжение "
            + view.game.expeditionWarehouse.get(ExpeditionLoot.Resource.EQUIPMENT));

    return lines;
  }

  private int riskColor(MapLocation location) {
    switch (location.risk) {
      case LOW:
        return SAFE;
      case LOW_MEDIUM:
        return Color.rgb(192, 181, 111);
      case HIGH:
        return Color.rgb(222, 113, 96);
      default:
        return WARM;
    }
  }

  private void row(Canvas c, String label, String value, float y, int color) {
    text(c, label, 34, y, 10, MUTED, false);
    text(c, value, 134, y, 11, color, true);
  }

  private void glow(Canvas c, float x, float y, float radius, int color) {
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(
        new RadialGradient(
            view.sy(x),
            view.sy(y),
            view.sy(radius),
            color,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP));
    c.drawCircle(view.sy(x), view.sy(y), view.sy(radius), paint);
    paint.setShader(null);
  }

  private void fill(Canvas c, int color) {
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(color);
  }

  private void rounded(Canvas c, float l, float t, float r, float b, int color, float radius) {
    fill(c, color);
    c.drawRoundRect(
        view.sy(l), view.sy(t), view.sy(r), view.sy(b), view.sy(radius), view.sy(radius), paint);
  }

  private void line(Canvas c, float x1, float y1, float x2, float y2, float width, int color) {
    paint.setColor(color);
    paint.setStrokeWidth(view.sy(width));
    c.drawLine(view.sy(x1), view.sy(y1), view.sy(x2), view.sy(y2), paint);
  }

  private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
    fill(c, color);
    paint.setTextSize(view.sy(size));
    paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
    c.drawText(value, view.sy(x), view.sy(y), paint);
  }

  private void centered(
      Canvas c, String value, float x, float y, float size, int color, boolean bold) {
    paint.setTextSize(view.sy(size));
    paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
    text(c, value, x - paint.measureText(value) / view.scale / 2f, y, size, color, bold);
  }

  private void wrap(
      Canvas c, String value, float x, float y, float right, float size, int color, float step) {
    paint.setTextSize(view.sy(size));
    paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
    String line = "";
    for (String word : value.split(" ")) {
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (!line.isEmpty() && paint.measureText(candidate) > view.sy(right - x)) {
        text(c, line, x, y, size, color, false);
        y += step;
        line = word;
      } else line = candidate;
    }
    if (!line.isEmpty()) text(c, line, x, y, size, color, false);
  }
}
