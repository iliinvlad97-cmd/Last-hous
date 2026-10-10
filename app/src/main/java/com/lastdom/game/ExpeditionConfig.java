package com.lastdom.game;

/** Balance and normalized road waypoints shared by travel, drawing and touch routing. */
final class ExpeditionConfig {
  static final int MAX_PARTICIPANTS = 3;
  static final int MAX_ACTIVE = 1;

  static int maxActive(Expedition.Type type) {
    return type == Expedition.Type.RECON ? ExplorationConfig.MAX_ACTIVE_RECON : MAX_ACTIVE;
  }

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

  private static final CityRouteCache ROUTES = new CityRouteCache();

  private static final class DefaultScene {
    static final java.util.List<MapLocation> LOCATIONS =
        CityMapController.defaultLocations().subList(0, 6);
    static final CityMapLayout LAYOUT = new CityMapLayout(840);
  }

  static float[][] route(MapLocation target) {
    return ROUTES.route(target, DefaultScene.LOCATIONS, DefaultScene.LAYOUT);
  }

  static float[] point(MapLocation target, float progress) {
    float[] point =
        CityRoutePlanner.point(
            route(target), progress, DefaultScene.LAYOUT.bottom - DefaultScene.LAYOUT.top);
    return point;
  }
}
