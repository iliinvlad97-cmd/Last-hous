package com.lastdom.game;

import java.util.*;

/** Immutable map objects allow allocation-free scene comparison between frames. */
final class CityRouteCache {
  private float height = -1;
  private List<MapLocation> scene = Collections.emptyList();
  private final Map<String, float[][]> routes = new HashMap<>();

  float[][] route(MapLocation target, List<MapLocation> locations, CityMapLayout layout) {
    float nextHeight = layout.bottom - layout.top;
    if (height != nextHeight || !locations.equals(scene)) {
      height = nextHeight;
      scene = new ArrayList<>(locations);
      routes.clear();
    }
    return routes.computeIfAbsent(
        target.id, id -> new CityRoutePlanner(target, scene, height).build());
  }
}
