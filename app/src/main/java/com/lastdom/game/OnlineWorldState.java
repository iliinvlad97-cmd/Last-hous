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
  String shelterId = "", zoneId = "", squadId = "";
  boolean pvpEnabled, confirmingPvp;
  double animationSeconds;
  float cardOpacity, selectionStrength;
  int panelScroll;

  OnlineWorldState(OnlineWorldRepository.Snapshot snapshot) {
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

  boolean selected() {
    return !shelterId.isEmpty() || !zoneId.isEmpty() || !squadId.isEmpty();
  }
}
