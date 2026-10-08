package com.lastdom.game;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Map selections and preparation input; simulation changes are delegated to the game controller.
 */
final class CityMapController {
  enum TouchResult {
    NONE,
    CONSUMED,
    HOME
  }

  static List<MapLocation> defaultLocations() {
    return Collections.unmodifiableList(
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
  }

  final List<MapLocation> locations;
  private final GameController game;

  CityMapController(GameController game) {
    this.game = game;
    locations = game.cityLocations;
  }

  private MapLocation selected;
  private MapLocation preparation;
  final java.util.LinkedHashSet<String> selectedIds = new java.util.LinkedHashSet<>();
  String message = "";
  int page;
  boolean expeditionPanel;
  float displayProgress;

  MapLocation selected() {
    return selected;
  }

  MapLocation preparation() {
    return preparation;
  }

  void closeSelection() {
    selected = null;
    preparation = null;
    selectedIds.clear();
    message = "";
    page = 0;
    expeditionPanel = false;
  }

  private void prepare() {
    if (selected == null || selected.isLocked()) return;
    if (game.expeditionController.active() != null || game.expeditionPerson >= 0) {
      message = "Сначала завершите текущую экспедицию";
      return;
    }
    preparation = selected;
    selected = null;
    selectedIds.clear();
    message = "";
    page = 0;
  }

  TouchResult onTouch(float x, float y, CityMapLayout layout) {
    Expedition active = game.expeditionController.active();
    if (expeditionPanel) {
      if (y < layout.panelTop
          || y > layout.panelBottom
          || (x > 350 && y < layout.panelTop + 52)
          || layout.hitsPreparation(x, y)) expeditionPanel = false;
      return TouchResult.CONSUMED;
    }
    if (preparation != null) {
      ExpeditionPreparationLayout prep = new ExpeditionPreparationLayout(layout.panelBottom);
      if (y >= prep.backTop && y <= prep.backTop + 36) {
        preparation = null;
        selectedIds.clear();
        message = "";
        return TouchResult.CONSUMED;
      }
      if (y >= prep.sendTop && y <= prep.sendTop + 40) {
        message = game.expeditionController.start(preparation.id, selectedIds);
        if (message.isEmpty()) {
          closeSelection();
          displayProgress = 0;
        }
        return TouchResult.CONSUMED;
      }
      int pages = Math.max(1, (game.people.size() + prep.capacity - 1) / prep.capacity);
      if (y >= prep.pageY - 20 && y <= prep.pageY + 8) {
        page = Math.max(0, Math.min(pages - 1, page + (x < 210 ? -1 : 1)));
        return TouchResult.CONSUMED;
      }
      for (int row = 0; row < prep.capacity; row++) {
        int index = page * prep.capacity + row;
        if (index >= game.people.size()) break;
        float top = prep.rowTop(row);
        if (y >= top && y <= top + 50 && x >= 30 && x <= 390) {
          Resident resident = game.people.get(index);
          if (selectedIds.remove(resident.id)) {
            message = "";
            break;
          }
          message = game.expeditionController.unavailableReason(resident);
          if (!message.isEmpty()) break;
          if (selectedIds.size() >= ExpeditionConfig.MAX_PARTICIPANTS)
            message = "В отряде может быть не более 3 жителей";
          else selectedIds.add(resident.id);
          break;
        }
      }
      return TouchResult.CONSUMED;
    }
    if (selected != null) {
      if (y < layout.panelTop
          || y > layout.panelBottom
          || (x >= 350f && y <= layout.panelTop + 52f)) {
        closeSelection();
      } else if (layout.hitsPreparation(x, y)) {
        if (selected.isLocked()) closeSelection();
        else prepare();
      }
      return TouchResult.CONSUMED;
    }
    if (y < layout.top || y > layout.bottom) return TouchResult.NONE;
    if (active != null) {
      MapLocation target = game.expeditionController.location(active.locationId);
      float[] point = ExpeditionConfig.point(target, displayProgress);
      if (layout.hits(x, y, point[0], point[1])
          || (x >= 30 && x <= 390 && y >= layout.bottom - 53 && y <= layout.bottom - 25)) {
        expeditionPanel = true;
        return TouchResult.CONSUMED;
      }
    }
    for (MapLocation location : locations) {
      if (layout.hits(x, y, location.mapX, location.mapY)) {
        selected = location;
        message = "";
        return TouchResult.CONSUMED;
      }
    }
    if (layout.hits(x, y, CityMapLayout.SHELTER_X, CityMapLayout.SHELTER_Y))
      return TouchResult.HOME;
    return TouchResult.CONSUMED;
  }
}
