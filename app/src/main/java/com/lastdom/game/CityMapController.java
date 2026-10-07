package com.lastdom.game;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Stage 1 only: selection and informational panels, with no simulation/expedition side effects. */
final class CityMapController {
  enum TouchResult {
    NONE,
    CONSUMED,
    HOME
  }

  final List<MapLocation> locations =
      Collections.unmodifiableList(
          Arrays.asList(
              new MapLocation(
                  "shop",
                  "Заброшенный магазин",
                  "ЗАБРОШЕННЫЙ\nМАГАЗИН",
                  "Еда",
                  MapLocation.Kind.STORE,
                  MapLocation.Distance.NEAR,
                  MapLocation.Risk.LOW,
                  .22f,
                  .65f,
                  MapLocation.State.AVAILABLE),
              new MapLocation(
                  "pharmacy",
                  "Аптека",
                  "АПТЕКА",
                  "Медикаменты",
                  MapLocation.Kind.PHARMACY,
                  MapLocation.Distance.NEAR,
                  MapLocation.Risk.LOW_MEDIUM,
                  .69f,
                  .63f,
                  MapLocation.State.AVAILABLE),
              new MapLocation(
                  "garage",
                  "Автосервис",
                  "АВТОСЕРВИС",
                  "Материалы / детали",
                  MapLocation.Kind.GARAGE,
                  MapLocation.Distance.MEDIUM,
                  MapLocation.Risk.MEDIUM,
                  .18f,
                  .39f,
                  MapLocation.State.AVAILABLE),
              new MapLocation(
                  "police",
                  "Полицейский участок",
                  "ПОЛИЦЕЙСКИЙ\nУЧАСТОК",
                  "Снаряжение",
                  MapLocation.Kind.POLICE,
                  MapLocation.Distance.MEDIUM,
                  MapLocation.Risk.HIGH,
                  .73f,
                  .36f,
                  MapLocation.State.AVAILABLE),
              new MapLocation(
                  "water",
                  "Водонапорная станция",
                  "ВОДОНАПОРНАЯ\nСТАНЦИЯ",
                  "Вода",
                  MapLocation.Kind.WATER,
                  MapLocation.Distance.FAR,
                  MapLocation.Risk.MEDIUM,
                  .26f,
                  .14f,
                  MapLocation.State.LOCKED),
              new MapLocation(
                  "hospital",
                  "Больница",
                  "БОЛЬНИЦА",
                  "Медикаменты / редкие ресурсы",
                  MapLocation.Kind.HOSPITAL,
                  MapLocation.Distance.FAR,
                  MapLocation.Risk.HIGH,
                  .74f,
                  .13f,
                  MapLocation.State.LOCKED)));
  private MapLocation selected;
  private boolean preparationNotice;

  MapLocation selected() {
    return selected;
  }

  boolean preparationNotice() {
    return preparationNotice;
  }

  void closeSelection() {
    selected = null;
    preparationNotice = false;
  }

  TouchResult onTouch(float x, float y, CityMapLayout layout) {
    if (selected != null) {
      // All panel taps are consumed; dismissing never clicks a marker or navigation underneath.
      if (y < layout.panelTop
          || y > layout.panelBottom
          || (x >= 350f && y <= layout.panelTop + 52f)) {
        closeSelection();
      } else if (layout.hitsPreparation(x, y)) {
        if (selected.isLocked()) closeSelection();
        else preparationNotice = true;
      }
      return TouchResult.CONSUMED;
    }
    if (y < layout.top || y > layout.bottom) return TouchResult.NONE;
    for (MapLocation location : locations) {
      if (layout.hits(x, y, location.mapX, location.mapY)) {
        selected = location;
        preparationNotice = false;
        return TouchResult.CONSUMED;
      }
    }
    if (layout.hits(x, y, CityMapLayout.SHELTER_X, CityMapLayout.SHELTER_Y))
      return TouchResult.HOME;
    return TouchResult.CONSUMED;
  }
}
