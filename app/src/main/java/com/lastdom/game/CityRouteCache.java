package com.lastdom.game;

import java.util.*;

/** Immutable map objects allow allocation-free scene comparison between frames. */
final class CityRouteCache {
  private float height = -1;
  private List<MapLocation> scene = Collections.emptyList();
  private final Map<String, float[][]> routes = new HashMap<>();
  private final Map<String, CityRouteGeometry> geometries = new HashMap<>();

  float[][] route(MapLocation target, List<MapLocation> locations, CityMapLayout layout) {
    float nextHeight = layout.bottom - layout.top;
    if (height != nextHeight || !locations.equals(scene)) {
      height = nextHeight;
      scene = new ArrayList<>(locations);
      routes.clear();
      geometries.clear();
    }
    float[][] points = routes.get(target.id);
    if (points == null) {
      points = new CityRoutePlanner(target, scene, height).build();
      routes.put(target.id, points);
    }
    return points;
  }

  CityRouteGeometry geometry(
      MapLocation target, List<MapLocation> locations, CityMapLayout layout) {
    float[][] points = route(target, locations, layout);
    CityRouteGeometry geometry = geometries.get(target.id);
    if (geometry == null) {
      geometry = new CityRouteGeometry(points, height);
      geometries.put(target.id, geometry);
    }
    return geometry;
  }
}
