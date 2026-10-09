package com.lastdom.game;

/** Finite road delivery; progress is clamped, never wraps back to the origin. */
final class OnlineRoute {
  final OnlineWorldGeometry.Shape shape;
  private final double[] distances;
  private final double length;

  OnlineRoute(float... coordinates) {
    shape = new OnlineWorldGeometry.Shape(coordinates);
    distances = new double[shape.size()];
    for (int i = 1; i < shape.size(); i++)
      distances[i] =
          distances[i - 1] + Math.hypot(shape.x(i) - shape.x(i - 1), shape.y(i) - shape.y(i - 1));
    length = distances[distances.length - 1];
    if (length <= 0) throw new IllegalArgumentException("Empty delivery route");
  }

  void position(double progress, float[] destination) {
    position(progress, destination, 0);
  }

  void position(double progress, float[] destination, int offset) {
    double distance = Math.max(0, Math.min(1, progress)) * length;
    for (int i = 1; i < shape.size(); i++)
      if (distance <= distances[i] || i == shape.size() - 1) {
        double segment = distances[i] - distances[i - 1];
        double t = segment == 0 ? 0 : (distance - distances[i - 1]) / segment;
        destination[offset] = (float) (shape.x(i - 1) + (shape.x(i) - shape.x(i - 1)) * t);
        destination[offset + 1] = (float) (shape.y(i - 1) + (shape.y(i) - shape.y(i - 1)) * t);
        return;
      }
  }
}
