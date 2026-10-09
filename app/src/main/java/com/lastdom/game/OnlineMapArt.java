package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/** Original procedural art. Projected static paths rebuild only on snapshot/viewport changes. */
final class OnlineMapArt {
  private static final class Mark {
    final Path path;
    final int color;
    final float width;

    Mark(Path path, int color, float width) {
      this.path = path;
      this.color = color;
      this.width = width;
    }
  }

  private final List<Mark> marks = new ArrayList<>();
  private final Map<String, Path> zones = new HashMap<>();
  private final EnumMap<OnlineCityEvent.Type, Path> icons =
      new EnumMap<>(OnlineCityEvent.Type.class);
  private final Paint paint = new Paint(3);
  private OnlineWorldRepository.Snapshot snapshot;
  private OnlineWorldGeometry geometry;
  private float scale;

  void prepare(OnlineWorldGeometry g, OnlineWorldRepository.Snapshot snapshot, float scale) {
    if (geometry == g && this.snapshot == snapshot && this.scale == scale) return;
    this.geometry = g;
    this.snapshot = snapshot;
    this.scale = scale;
    marks.clear();
    zones.clear();
    icons.clear();
    int road = 0;
    for (OnlineWorldGeometry.Shape street : snapshot.streets) {
      Path route = project(street, false);
      add(route, Color.rgb(7, 15, 20), road < 3 ? 30 : 23);
      add(route, Color.rgb(36, 46, 52), road < 3 ? 25 : 18);
      add(route, Color.rgb(55, 64, 66), road < 3 ? 20 : 14);
      int segment = 0;
      for (int i = 1; i < street.size(); i++) {
        float ax = g.x(street.x(i - 1)),
            ay = g.y(street.y(i - 1)),
            bx = g.x(street.x(i)),
            by = g.y(street.y(i));
        float length = (float) Math.hypot(bx - ax, by - ay);
        for (float d = 12; d + 5 < length; d += 30) {
          float t = d / length, end = (d + 5) / length;
          stroke(
              Color.argb(95, 158, 151, 131),
              1,
              ax + (bx - ax) * t,
              ay + (by - ay) * t,
              ax + (bx - ax) * end,
              ay + (by - ay) * end);
        }
        float t = .28f + (segment++ % 3) * .12f, x = ax + (bx - ax) * t, y = ay + (by - ay) * t;
        stroke(
            Color.argb(145, 17, 25, 29), 1, x - 4, y - 3, x + 1, y + 1, x + 5, y - 2, x + 8, y + 2);
        polygon(
            Color.argb(55, 13, 22, 28), x - 9, y + 3, x + 8, y + 2, x + 11, y + 5, x - 5, y + 7);
      }
      road++;
    }
    int index = 0;
    for (OnlineWorldGeometry.Block b : snapshot.buildings) {
      float x = g.x(b.x),
          y = g.y(b.y),
          w = (g.right - g.left) * b.width,
          h = (g.bottom - g.top) * b.height;
      for (int shadow = 3; shadow >= 1; shadow--)
        rectangle(
            x + shadow * 2,
            y + shadow * 2,
            x + w + shadow * 2 + 1,
            y + h + shadow * 2 + 1,
            Color.argb(22 + shadow * 7, 0, 0, 0));
      polygon(
          b.industrial ? Color.rgb(75, 70, 61) : Color.rgb(53, 67, 76),
          x,
          y,
          x + w * .78f,
          y,
          x + w,
          y + h * .13f,
          x + w,
          y + h,
          x,
          y + h);
      polygon(
          Color.rgb(31, 42, 48),
          x + w * .63f,
          y + 1,
          x + w,
          y + h * .13f,
          x + w,
          y + h * .52f,
          x + w * .72f,
          y + h * .25f);
      stroke(Color.rgb(114, 117, 105), 1, x + 2, y + 2, x + w * .72f, y + 2);
      stroke(
          Color.rgb(22, 32, 36),
          1,
          x + w * .2f,
          y + 3,
          x + w * .34f,
          y + h * .22f,
          x + w * .23f,
          y + h * .38f,
          x + w * .41f,
          y + h * .58f);
      stroke(
          Color.rgb(19, 28, 34),
          1,
          x + w * .8f,
          y + h * .4f,
          x + w * .6f,
          y + h * .54f,
          x + w * .7f,
          y + h * .67f);
      rectangle(x + w * .36f, y + h * .22f, x + w * .62f, y + h * .38f, Color.rgb(37, 48, 54));
      stroke(Color.rgb(86, 95, 92), 1, x + w * .38f, y + h * .24f, x + w * .58f, y + h * .24f);
      for (int window = 0; window < 3; window++) {
        float wx = x + 3 + window * (w - 7) / 3;
        rectangle(wx, y + h - 8, wx + 3, y + h - 3, Color.rgb(13, 24, 31));
      }
      if (b.industrial) {
        rectangle(x + w * .2f, y - 11, x + w * .32f, y + 2, Color.rgb(59, 66, 65));
        stroke(Color.rgb(101, 107, 95), 1, x + w * .2f, y - 11, x + w * .32f, y - 11);
      }
      for (int rubble = 0; rubble < 3; rubble++) {
        float rx = x + 3 + rubble * w * .22f, ry = y + h + 3 + (index + rubble) % 3;
        polygon(
            Color.rgb(75 - rubble * 7, 80 - rubble * 7, 76 - rubble * 7),
            rx,
            ry,
            rx + 4,
            ry - 1,
            rx + 6,
            ry + 2,
            rx + 1,
            ry + 3);
      }
      index++;
    }
    for (OnlineZone zone : snapshot.zones) zones.put(zone.id, project(zone.boundary, true));
    for (OnlineCityEvent.Type type : OnlineCityEvent.Type.values()) {
      float x = g.x(type.zoneId.equals("pve_industry") ? .27f : .75f),
          y = g.y(type.zoneId.equals("pve_industry") ? .52f : .585f);
      Path icon = new Path();
      switch (type) {
        case INFECTED:
          for (int claw = -1; claw <= 1; claw++) {
            icon.moveTo(px(x + claw * 5 - 2), px(y - 6));
            icon.lineTo(px(x + claw * 5 + 1), px(y + 6));
          }
          break;
        case FIRE:
          points(
              icon, true, x, y - 8, x - 5, y, x - 4, y + 5, x, y + 8, x + 5, y + 3, x + 3, y - 3,
              x + 1, y);
          break;
        case DISTRESS:
          points(icon, false, x - 6, y, x + 6, y);
          points(icon, false, x, y - 6, x, y + 6);
          break;
        case WAREHOUSE:
          points(icon, true, x - 6, y - 4, x + 6, y - 4, x + 6, y + 6, x - 6, y + 6);
          points(icon, false, x - 6, y - 4, x, y, x + 6, y - 4);
          points(icon, false, x, y, x, y + 6);
          break;
        case STORM:
          for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3 - Math.PI / 2;
            points(
                icon,
                true,
                x + (float) Math.cos(a) * 2,
                y + (float) Math.sin(a) * 2,
                x + (float) Math.cos(a - .45) * 8,
                y + (float) Math.sin(a - .45) * 8,
                x + (float) Math.cos(a + .45) * 8,
                y + (float) Math.sin(a + .45) * 8);
          }
          break;
        default:
          break;
      }
      icons.put(type, icon);
    }
  }

  void draw(Canvas c) {
    for (int i = 0; i < marks.size(); i++) {
      Mark mark = marks.get(i);
      paint.setStyle(mark.width == 0 ? Paint.Style.FILL : Paint.Style.STROKE);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeWidth(px(mark.width));
      paint.setColor(mark.color);
      c.drawPath(mark.path, paint);
    }
  }

  Path zone(String id) {
    return zones.get(id);
  }

  Path icon(OnlineCityEvent.Type type) {
    return icons.get(type);
  }

  private float px(float v) {
    return v * scale;
  }

  private void add(Path path, int color, float width) {
    marks.add(new Mark(path, color, width));
  }

  private Path project(OnlineWorldGeometry.Shape shape, boolean closed) {
    Path p = new Path();
    p.moveTo(px(geometry.x(shape.x(0))), px(geometry.y(shape.y(0))));
    for (int i = 1; i < shape.size(); i++)
      p.lineTo(px(geometry.x(shape.x(i))), px(geometry.y(shape.y(i))));
    if (closed) p.close();
    return p;
  }

  private void points(Path p, boolean close, float... xy) {
    p.moveTo(px(xy[0]), px(xy[1]));
    for (int i = 2; i < xy.length; i += 2) p.lineTo(px(xy[i]), px(xy[i + 1]));
    if (close) p.close();
  }

  private void polygon(int color, float... xy) {
    Path p = new Path();
    points(p, true, xy);
    add(p, color, 0);
  }

  private void stroke(int color, float width, float... xy) {
    Path p = new Path();
    points(p, false, xy);
    add(p, color, width);
  }

  private void rectangle(float l, float t, float r, float b, int color) {
    polygon(color, l, t, r, t, r, b, l, b);
  }
}
