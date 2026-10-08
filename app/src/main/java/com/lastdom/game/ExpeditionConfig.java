package com.lastdom.game;

/** Balance and normalized road waypoints shared by travel, drawing and touch routing. */
final class ExpeditionConfig {
  static final int MAX_PARTICIPANTS = 3;
  static final int MAX_ACTIVE = 1;

  static int oneWayMinutes(MapLocation location) {
    switch (location.distance) {
      case NEAR:
        return 45;
      case MEDIUM:
        return 90;
      default:
        return 150;
    }
  }

  static int explorationMinutes(MapLocation location) {
    switch (location.risk) {
      case LOW:
        return 30;
      case LOW_MEDIUM:
        return 45;
      case MEDIUM:
        return 60;
      default:
        return 90;
    }
  }

  static double negativeProbability(MapLocation location, boolean guard) {
    double base;
    switch (location.risk) {
      case LOW:
        base = .05;
        break;
      case LOW_MEDIUM:
        base = .10;
        break;
      case MEDIUM:
        base = .15;
        break;
      default:
        base = .25;
    }
    return base * (guard ? .85 : 1);
  }

  static float[][] route(MapLocation target) {
    return new float[][] {
      {CityMapLayout.SHELTER_X, CityMapLayout.SHELTER_Y},
      {.50f, .77f},
      {.47f, .64f},
      {target.mapX, target.mapY}
    };
  }

  static float[] point(MapLocation target, float progress) {
    float[][] points = route(target);
    float total = 0;
    for (int i = 1; i < points.length; i++) total += length(points[i - 1], points[i]);
    float remaining = Math.max(0, Math.min(1, progress)) * total;
    for (int i = 1; i < points.length; i++) {
      float length = length(points[i - 1], points[i]);
      if (remaining <= length) {
        float t = length == 0 ? 1 : remaining / length;
        return new float[] {
          points[i - 1][0] + (points[i][0] - points[i - 1][0]) * t,
          points[i - 1][1] + (points[i][1] - points[i - 1][1]) * t
        };
      }
      remaining -= length;
    }
    return new float[] {target.mapX, target.mapY};
  }

  private static float length(float[] a, float[] b) {
    return (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
  }
}
