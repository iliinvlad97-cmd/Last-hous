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
    HOME,
    RADIO
  }

  static List<MapLocation> defaultLocations() {
    java.util.ArrayList<MapLocation> points =
        new java.util.ArrayList<>(
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
    points.addAll(ExplorationConfig.destinations());
    points.add(StoryConfig.location());
    points.addAll(StoryInvestigationConfig.locations());
    points.addAll(StoryFactionConfig.locations());
    return Collections.unmodifiableList(points);
  }

  final List<MapLocation> locations;
  private final GameController game;
  final CityRouteCache routes = new CityRouteCache();

  CityMapController(GameController game) {
    this.game = game;
    locations =
        game.cityLocations.subList(
            0, 6); // Existing map projection keeps its six original identities.
  }

  private MapLocation selected;
  private MapLocation preparation;
  final java.util.LinkedHashSet<String> selectedIds = new java.util.LinkedHashSet<>();
  String message = "";
  int page;
  boolean expeditionPanel, eventPanel;
  float displayProgress, reconDisplayProgress;
  private final float[] routeTouchPoint = new float[2];
  boolean districtsLayer;
  String districtFilterId = "", expeditionId = "", focusedLocationId = "";
  private final java.util.Set<String> shownReports = new java.util.HashSet<>();
  int panelScroll, panelLineCount;
  private float dragY, dragStartY;
  private boolean dragging, moved;

  Expedition panelExpedition() {
    Expedition selected = game.expeditionController.find(expeditionId);
    return selected != null ? selected : game.expeditionController.report();
  }

  void openExpedition(Expedition e) {
    closeSelection();
    expeditionId = e.id;
    if (e.state() == Expedition.State.COMPLETED) shownReports.add(e.id + e.state());
    panelScroll = 0;
    eventPanel = e.state() == Expedition.State.AWAITING_DECISION;
    expeditionPanel = !eventPanel;
  }

  boolean hasOpenedPoints(Expedition e) {
    if (e == null
        || e.type != Expedition.Type.RECON
        || e.recon == null
        || e.state() != Expedition.State.COMPLETED
        || !e.recon.success) return false;
    for (String id : e.recon.openedPoints) {
      MapLocation point = game.expeditionController.location(id);
      if (point != null && !point.isLocked()) return true;
    }
    return false;
  }

  void showOpenedPoints(Expedition e) {
    if (!hasOpenedPoints(e)) return;
    String first = "";
    // Prefer an actually new point, with a safe fallback for legacy reports.
    for (String id : e.recon.newlyOpenedPoints)
      if (!game.expeditionController.location(id).isLocked()) {
        first = id;
        break;
      }
    if (first.isEmpty())
      for (String id : e.recon.openedPoints)
        if (!game.expeditionController.location(id).isLocked()) {
          first = id;
          break;
        }
    game.expeditionController.acknowledge(e.id);
    showDistrictPoints(game.explorationController.district(e.locationId), first);
  }

  private void showDistrictPoints(CityDistrict district, String preferred) {
    closeSelection();
    game.screen = GameView.CITY_MAP;
    game.overlay = 0;
    districtFilterId = district.config.id;
    districtsLayer = false;
    focusedLocationId = preferred;
    if (focusedLocationId.isEmpty())
      for (String id : district.config.points)
        if (!game.expeditionController.location(id).isLocked()) {
          focusedLocationId = id;
          break;
        }
  }

  void openPendingReport() {
    if (eventPanel || expeditionPanel) return;
    for (int pass = 0; pass < 2; pass++)
      for (int i = game.expeditions.size() - 1; i >= 0; i--) {
        Expedition e = game.expeditions.get(i);
        boolean event =
            e.state() == Expedition.State.AWAITING_DECISION && e.explorationEvent.interactive();
        if (pass == 0 && !event) continue;
        if (pass == 1
            && (event
                || (e.state() != Expedition.State.AWAITING_RETURN
                    && (e.state() != Expedition.State.COMPLETED || e.reportAcknowledged))))
          continue;
        String key =
            event
                ? e.explorationEvent.instanceId + e.explorationEvent.effectsApplied
                : e.id + e.state();
        if (shownReports.contains(key)) continue;
        shownReports.add(key);
        openExpedition(e);
        return;
      }
  }

  java.util.List<MapLocation> visibleLocations() {
    java.util.List<MapLocation> visible = new java.util.ArrayList<>();
    if (districtsLayer) {
      for (CityDistrict d : game.cityDistricts)
        visible.add(game.expeditionController.location(d.config.id));
    } else if (districtFilterId.isEmpty()) {
      visible.addAll(locations);
      MapLocation story = game.expeditionController.location(StoryConfig.RADIO);
      if (!story.isLocked()) visible.add(story);
      for (MapLocation point : game.cityLocations)
        if (StoryInvestigationConfig.target(point.id)
            && game.storyController.investigation.visible(point.id)) visible.add(point);
      for (MapLocation point : game.cityLocations)
        if (game.storyController.factions.visible(point.id)) visible.add(point);
    } else {
      CityDistrict d = game.explorationController.district(districtFilterId);
      if (d != null && d.state == CityDistrict.State.EXPLORED)
        for (String id : d.config.points) visible.add(game.expeditionController.location(id));
    }
    return visible;
  }

  String layerName() {
    CityDistrict d = game.explorationController.district(districtFilterId);
    return districtsLayer ? "Районы города" : d == null ? "Начальные точки" : d.config.name;
  }

  boolean scrollTouch(int action, float y, CityMapLayout layout) {
    boolean panel = eventPanel || expeditionPanel || selected != null;
    Expedition current = panelExpedition();
    float footer =
        eventPanel && current != null
            ? new ExpeditionEventLayout(layout, current.explorationEvent).footer
            : (selected != null && selected.kind == MapLocation.Kind.DISTRICT)
                    || (expeditionPanel && hasOpenedPoints(current))
                ? 132
                : 84;
    if (!panel) return false;
    if (action == android.view.MotionEvent.ACTION_DOWN) {
      dragging = y >= layout.panelTop + 75 && y <= layout.panelBottom - footer;
      dragY = dragStartY = y;
      moved = false;
      return true;
    }
    if (action == android.view.MotionEvent.ACTION_MOVE && dragging) {
      if (Math.abs(y - dragStartY) > 8) moved = true;
      int delta = (int) ((dragY - y) / MapPanelContent.LINE_HEIGHT);
      if (delta != 0) {
        panelScroll =
            Math.max(
                0,
                Math.min(
                    Math.max(0, panelLineCount - MapPanelContent.visibleLines(layout, footer)),
                    panelScroll + delta));
        dragY = y;
      }
      return true;
    }
    if (action == android.view.MotionEvent.ACTION_CANCEL) {
      dragging = false;
      moved = false;
      return true;
    }
    if (action == android.view.MotionEvent.ACTION_UP) {
      dragging = false;
      boolean consumed = moved;
      moved = false;
      return consumed;
    }
    return false;
  }

  MapLocation selected() {
    return selected;
  }

  MapLocation preparation() {
    return preparation;
  }

  void closeSelection() {
    selected = null;
    preparation = null;
    expeditionId = "";
    selectedIds.clear();
    message = "";
    page = 0;
    expeditionPanel = false;
    eventPanel = false;
    panelScroll = 0;
    panelLineCount = 0;
  }

  private void prepare() {
    if (selected == null) return;
    if (selected.kind == MapLocation.Kind.DISTRICT) {
      message = game.explorationController.unavailableReason(selected.id);
      if (!message.isEmpty()) {
        panelScroll = 0;
        return;
      }
    } else if (selected.isLocked()) return;
    if (selected.depleted()) {
      message = "Локация истощена";
      panelScroll = 0;
      return;
    }
    if (selected.kind == MapLocation.Kind.STORY) {
      message = game.storyController.expeditionReason(selected.id);
      if (!message.isEmpty()) return;
    }
    if (selected.kind != MapLocation.Kind.DISTRICT
        && (game.expeditionController.active(Expedition.Type.LOOT) != null
            || game.expeditionPerson >= 0)) {
      message = "Сначала завершите текущую экспедицию";
      panelScroll = 0;
      return;
    }
    preparation = selected;
    selected = null;
    selectedIds.clear();
    message = "";
    page = 0;
  }

  TouchResult onTouch(float x, float y, CityMapLayout layout) {
    if (eventPanel) {
      Expedition expedition = panelExpedition();
      if (expedition == null || expedition.state() != Expedition.State.AWAITING_DECISION) {
        eventPanel = false;
        expeditionId = "";
        return TouchResult.CONSUMED;
      }
      ExpeditionEvent event = expedition.explorationEvent;
      ExpeditionEventLayout eventLayout = new ExpeditionEventLayout(layout, event);
      int action = eventLayout.hit(x, y);
      if (action >= 0) {
        if (event.effectsApplied) {
          message = game.expeditionController.continueEvent(event.instanceId);
          if (message.isEmpty()) {
            eventPanel = false;
            expeditionId = "";
            panelScroll = 0;
          }
        } else {
          message = game.expeditionController.chooseEvent(event.instanceId, action);
          panelScroll = 0;
          shownReports.add(event.instanceId + event.effectsApplied);
        }
      } else if (y < layout.panelTop
          || y > layout.panelBottom
          || (x > 350 && y < layout.panelTop + 52)) {
        eventPanel = false;
        expeditionId = "";
        panelScroll = 0;
      }
      return TouchResult.CONSUMED;
    }
    if (expeditionPanel) {
      Expedition report = panelExpedition();
      if (hasOpenedPoints(report)
          && x >= 34
          && x <= 386
          && y >= layout.panelBottom - 114
          && y <= layout.panelBottom - 76) {
        showOpenedPoints(report);
      } else if (layout.hitsPreparation(x, y)) {
        if (report != null && report.state() == Expedition.State.AWAITING_RETURN) {
          message = game.expeditionController.returnHome(report.id);
          if (message.isEmpty()) {
            expeditionPanel = false;
            expeditionId = "";
            panelScroll = 0;
          }
        } else if (report != null && report.state() == Expedition.State.COMPLETED) {
          game.expeditionController.acknowledge(report.id);
          expeditionPanel = false;
          expeditionId = "";
          panelScroll = 0;
        } else {
          expeditionPanel = false;
          expeditionId = "";
        }
      } else if (y < layout.panelTop
          || y > layout.panelBottom
          || (x > 350 && y < layout.panelTop + 52)) {
        expeditionPanel = false;
        expeditionId = "";
        panelScroll = 0;
      }
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
        boolean recon = preparation.kind == MapLocation.Kind.DISTRICT;
        message =
            recon
                ? game.explorationController.start(preparation.id, selectedIds)
                : game.expeditionController.start(preparation.id, selectedIds);
        if (message.isEmpty()) {
          closeSelection();
          if (recon) reconDisplayProgress = 0;
          else displayProgress = 0;
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
      } else if (selected.kind == MapLocation.Kind.DISTRICT
          && x >= 34
          && x <= 386
          && y >= layout.panelBottom - 114
          && y <= layout.panelBottom - 76) {
        Expedition report = game.explorationController.latestReport(selected.id);
        if (report != null) openExpedition(report);
      } else if (layout.hitsPreparation(x, y)) {
        if (selected.kind == MapLocation.Kind.DISTRICT) {
          CityDistrict d = game.explorationController.district(selected.id);
          if (d.state == CityDistrict.State.EXPLORED) {
            showDistrictPoints(d, "");
          } else prepare();
        } else if (selected.isLocked()) closeSelection();
        else prepare();
      }
      return TouchResult.CONSUMED;
    }
    if (y >= 39 && y <= 69) {
      if (x >= 284 && x <= 400) return TouchResult.RADIO;
      if (x >= 20 && x <= 144) {
        districtsLayer = false;
        districtFilterId = "";
        focusedLocationId = "";
        return TouchResult.CONSUMED;
      }
      if (x >= 152 && x <= 276) {
        districtsLayer = true;
        districtFilterId = "";
        focusedLocationId = "";
        return TouchResult.CONSUMED;
      }
    }
    if (y < layout.top || y > layout.bottom) return TouchResult.NONE;
    boolean parallel =
        game.expeditionController.active(Expedition.Type.LOOT) != null
            && game.expeditionController.active(Expedition.Type.RECON) != null;
    // Indicators overlay the map; their touch targets take precedence over underlying markers.
    for (Expedition.Type type : Expedition.Type.values()) {
      Expedition route = game.expeditionController.active(type);
      if (route == null) continue;
      float row = layout.expeditionRow(type);
      if (x >= layout.expeditionLeft(type, parallel)
          && x <= layout.expeditionRight(type, parallel)
          && y >= row
          && y <= row + 28) {
        openExpedition(route);
        return TouchResult.CONSUMED;
      }
    }
    for (Expedition.Type type : Expedition.Type.values()) {
      Expedition route = game.expeditionController.active(type);
      if (route == null) continue;
      MapLocation target = game.expeditionController.location(route.locationId);
      float progress = type == Expedition.Type.RECON ? reconDisplayProgress : displayProgress;
      CityRouteGeometry geometry = routes.geometry(target, visibleLocations(), layout);
      if (!geometry.point(progress, routeTouchPoint)) continue;
      if (layout.hits(x, y, routeTouchPoint[0], routeTouchPoint[1])) {
        openExpedition(route);
        return TouchResult.CONSUMED;
      }
    }
    for (MapLocation location : visibleLocations()) {
      if (layout.hits(x, y, location.mapX, location.mapY)) {
        selected = location;
        focusedLocationId = location.id;
        panelScroll = 0;
        message = "";
        return TouchResult.CONSUMED;
      }
    }
    if (layout.hits(x, y, CityMapLayout.SHELTER_X, CityMapLayout.SHELTER_Y))
      return TouchResult.HOME;
    return TouchResult.CONSUMED;
  }
}
