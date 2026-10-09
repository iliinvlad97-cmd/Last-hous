package com.lastdom.game;

import java.util.List;

/** Demo session data is deliberately separate from GameState and save_v02. */
final class OnlineWorldState {
  enum Connection {
    DEMO("ДЕМО", "ДЕМО-РЕЖИМ · ЛОКАЛЬНЫЕ ДАННЫЕ"),
    CONNECTING("СВЯЗЬ…", "Подключение к радиосети"),
    CONNECTED("СВЯЗЬ", "Связь с радиосетью установлена"),
    OFFLINE("НЕТ СВЯЗИ", "Радиосеть недоступна"),
    ERROR("ОШИБКА", "Ошибка источника данных");
    final String badge, description;

    Connection(String badge, String description) {
      this.badge = badge;
      this.description = description;
    }
  }

  final Connection connection;
  final List<OnlineShelter> shelters;
  final List<OnlineZone> zones;
  final List<OnlineSquad> squads;
  final OnlineWorldRepository.Snapshot snapshot;
  final float[] squadPositions;

  enum Panel {
    ALLIANCE,
    ALLIANCE_NAME,
    EVENTS,
    CITY_EVENT,
    OPERATION_PREP,
    OPERATION_REPORT,
    OBJECT,
    TRADE,
    HELP,
    INVENTORY,
    HISTORY,
    DELIVERY,
    ROSTER,
    COMBAT_HISTORY,
    PVP_PREP,
    COOP_PREP,
    BATTLE,
    COOP_REPORT
  }

  Panel panel = Panel.OBJECT;
  OnlineWorldGameplay.Data gameplay;
  String offerId = "", deliveryId = "", result = "";
  final double[] deliverySeconds;
  final float[] deliveryPositions;
  float confirmationGlow;
  String notice = "";
  double noticeSeconds;
  boolean resultSuccess;

  String shelterId = "", zoneId = "", squadId = "";
  boolean pvpEnabled, confirmingPvp;
  double animationSeconds;
  float cardOpacity, selectionStrength;
  int panelScroll;

  OnlineWorldState(OnlineWorldRepository.Snapshot snapshot) {
    this(snapshot, OnlineWorldGameplay.Data.initial());
  }

  OnlineWorldState(OnlineWorldRepository.Snapshot snapshot, OnlineWorldGameplay.Data gameplay) {
    this.gameplay = gameplay;
    deliverySeconds = new double[gameplay.offers.size()];
    deliveryPositions = new float[gameplay.offers.size() * 2];
    this.snapshot = snapshot;
    connection = snapshot.connection;
    shelters = snapshot.shelters;
    zones = snapshot.zones;
    squads = snapshot.squads;
    squadPositions = new float[squads.size() * 2];
    for (int i = 0; i < squads.size(); i++) squads.get(i).position(0, squadPositions, i * 2);
  }

  OnlineShelter shelter() {
    for (OnlineShelter shelter : shelters) if (shelter.id.equals(shelterId)) return shelter;
    return null;
  }

  OnlineZone zone() {
    for (OnlineZone zone : zones) if (zone.id.equals(zoneId)) return zone;
    return null;
  }

  OnlineSquad squad() {
    for (OnlineSquad squad : squads) if (squad.id.equals(squadId)) return squad;
    return null;
  }

  OnlineWorldGameplay.Offer offer() {
    return gameplay.offer(offerId);
  }

  OnlineWorldGameplay.Operation delivery() {
    return gameplay.operation(deliveryId);
  }

  /** Prefer an active mission; otherwise expose the latest saved report for this region. */
  OnlineCoopExpedition coop(String zoneId) {
    OnlineCoopExpedition latest = null;
    for (int i = gameplay.combat.expeditions.size() - 1; i >= 0; i--) {
      OnlineCoopExpedition expedition = gameplay.combat.expeditions.get(i);
      if (!expedition.zoneId.equals(zoneId)) continue;
      if (expedition.active()) return expedition;
      if (latest == null) latest = expedition;
    }
    return latest;
  }

  String shelterName(String id) {
    for (OnlineShelter shelter : shelters) if (shelter.id.equals(id)) return shelter.name;
    return id;
  }

  boolean selected() {
    return panel != Panel.OBJECT || !shelterId.isEmpty() || !zoneId.isEmpty() || !squadId.isEmpty();
  }
}
