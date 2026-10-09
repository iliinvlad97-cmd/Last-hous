package com.lastdom.game;

/** Normalized world coordinates share one projection for art, markers, regions and input. */
final class OnlineWorldGeometry {
  static final class Point {
    final float x, y;

    Point(float x, float y) {
      coordinate(x);
      coordinate(y);
      this.x = x;
      this.y = y;
    }
  }

  static final class Shape {
    private final float[] coordinates;

    Shape(float... coordinates) {
      if (coordinates.length < 4 || coordinates.length % 2 != 0)
        throw new IllegalArgumentException("A shape needs coordinate pairs");
      this.coordinates = coordinates.clone();
      for (float value : coordinates) coordinate(value);
    }

    int size() {
      return coordinates.length / 2;
    }

    float x(int i) {
      return coordinates[i * 2];
    }

    float y(int i) {
      return coordinates[i * 2 + 1];
    }

    boolean contains(float x, float y) {
      boolean inside = false;
      for (int i = 0, j = size() - 1; i < size(); j = i++) {
        float ax = x(j), ay = y(j), bx = x(i), by = y(i);
        float cross = (x - ax) * (by - ay) - (y - ay) * (bx - ax);
        if (Math.abs(cross) < .000001f
            && x >= Math.min(ax, bx)
            && x <= Math.max(ax, bx)
            && y >= Math.min(ay, by)
            && y <= Math.max(ay, by)) return true;
        if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside;
      }
      return inside;
    }
  }

  static final class Block {
    final float x, y, width, height;
    final boolean industrial;

    Block(float x, float y, float width, float height, boolean industrial) {
      coordinate(x);
      coordinate(y);
      coordinate(x + width);
      coordinate(y + height);
      if (width <= 0 || height <= 0) throw new IllegalArgumentException("Empty block");
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.industrial = industrial;
    }
  }

  static void coordinate(float value) {
    if (!Float.isFinite(value) || value < 0 || value > 1)
      throw new IllegalArgumentException("World coordinates must be normalized");
  }

  final float left = 16, right = 404, top = 112, bottom, height;
  final float panelTop, panelBottom, contentTop, contentBottom;

  OnlineWorldGeometry(float height) {
    if (!Float.isFinite(height) || height < 360)
      throw new IllegalArgumentException("Invalid viewport");
    this.height = height;
    bottom = height - 88;
    panelTop = Math.max(12, Math.max(height - 610, Math.min(108, height - 360)));
    panelBottom = height - 86;
    contentTop = panelTop + 89;
    contentBottom = panelBottom - 148;
  }

  float x(float normalized) {
    return left + normalized * (right - left);
  }

  float y(float normalized) {
    return top + normalized * (bottom - top);
  }

  boolean inMap(float x, float y) {
    return x >= left && x <= right && y >= top && y <= bottom;
  }

  boolean hits(float x, float y, float nx, float ny) {
    float dx = x - x(nx), dy = y - y(ny);
    return dx * dx + dy * dy <= 25 * 25;
  }

  OnlineZone zoneAt(OnlineWorldState state, float x, float y) {
    if (!inMap(x, y)) return null;
    float nx = (x - left) / (right - left), ny = (y - top) / (bottom - top);
    for (OnlineZone zone : state.zones) if (zone.boundary.contains(nx, ny)) return zone;
    return null;
  }

  boolean secondary(float x, float y) {
    return x >= 34 && x <= 386 && y >= panelBottom - 122 && y <= panelBottom - 66;
  }

  boolean secondaryLeft(float x, float y, boolean trade) {
    return secondary(x, y) && x <= (trade ? 300 : 204);
  }

  boolean secondaryRight(float x, float y, boolean trade) {
    return secondary(x, y) && x >= (trade ? 310 : 216);
  }

  boolean closeButton(float x, float y) {
    return x >= 34 && x <= 386 && y >= panelBottom - 60 && y <= panelBottom - 4;
  }

  int visibleLines() {
    return Math.max(1, (int) ((contentBottom - contentTop) / 19));
  }
}
