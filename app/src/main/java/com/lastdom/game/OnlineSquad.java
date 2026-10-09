package com.lastdom.game;

/** A cosmetic patrol, with no participant IDs, cargo, damage or resource transactions. */
final class OnlineSquad {
  final String id, name;
  final OnlineWorldGeometry.Shape route;
  final double lapSeconds;
  private final double[] distances;
  private final double length;

  OnlineSquad(String id, String name, double lapSeconds, float... points) {
    if (id == null
        || id.isEmpty()
        || name == null
        || !Double.isFinite(lapSeconds)
        || lapSeconds <= 0) throw new IllegalArgumentException("Invalid patrol");
    this.id = id;
    this.name = name;
    this.lapSeconds = lapSeconds;
    route = new OnlineWorldGeometry.Shape(points);
    distances = new double[route.size()];
    for (int i = 1; i < route.size(); i++)
      distances[i] =
          distances[i - 1] + Math.hypot(route.x(i) - route.x(i - 1), route.y(i) - route.y(i - 1));
    length = distances[distances.length - 1];
    if (length <= 0
        || route.x(0) != route.x(route.size() - 1)
        || route.y(0) != route.y(route.size() - 1))
      throw new IllegalArgumentException("Patrol needs a nonempty closed route");
  }

  void position(double elapsedSeconds, float[] destination, int offset) {
    double distance = (elapsedSeconds % lapSeconds) / lapSeconds * length;
    for (int i = 1; i < route.size(); i++) {
      if (distance <= distances[i] || i == route.size() - 1) {
        double segment = distances[i] - distances[i - 1];
        double t = segment == 0 ? 0 : (distance - distances[i - 1]) / segment;
        destination[offset] = (float) (route.x(i - 1) + (route.x(i) - route.x(i - 1)) * t);
        destination[offset + 1] = (float) (route.y(i - 1) + (route.y(i) - route.y(i - 1)) * t);
        return;
      }
    }
  }
}
