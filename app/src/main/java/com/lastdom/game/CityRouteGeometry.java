package com.lastdom.game;

/** Cached arc lengths of the existing road polyline; sampling allocates no objects. */
final class CityRouteGeometry {
  final float[][] points;
  final double[] distances;
  final double length;

  CityRouteGeometry(float[][] points, float height) {
    this.points = points;
    distances = new double[points.length];
    for (int i = 1; i < points.length; i++)
      distances[i] = distances[i - 1] + CityRoutePlanner.length(points[i - 1], points[i], height);
    length = points.length == 0 ? 0 : distances[points.length - 1];
  }

  boolean point(float progress, float[] out) {
    if (points.length == 0) return false;
    if (progress <= 0 || length == 0) {
      out[0] = points[0][0];
      out[1] = points[0][1];
      return true;
    }
    if (progress >= 1) {
      out[0] = points[points.length - 1][0];
      out[1] = points[points.length - 1][1];
      return true;
    }
    double distance = Math.max(0, Math.min(1, progress)) * length;
    int low = 1, high = points.length - 1;
    while (low < high) {
      int middle = (low + high) >>> 1;
      if (distances[middle] < distance) low = middle + 1;
      else high = middle;
    }
    double segment = distances[low] - distances[low - 1];
    float t = segment == 0 ? 1 : (float) ((distance - distances[low - 1]) / segment);
    out[0] = points[low - 1][0] + (points[low][0] - points[low - 1][0]) * t;
    out[1] = points[low - 1][1] + (points[low][1] - points[low - 1][1]) * t;
    return true;
  }
}
