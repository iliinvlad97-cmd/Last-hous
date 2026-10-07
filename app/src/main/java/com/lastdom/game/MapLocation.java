package com.lastdom.game;

/** City-map identity and normalized route endpoints; separate from legacy expedition save slots. */
final class MapLocation {
  enum State {
    LOCKED,
    DISCOVERED,
    AVAILABLE,
    SEARCHED,
    DANGEROUS
  }

  enum Kind {
    STORE,
    PHARMACY,
    GARAGE,
    POLICE,
    WATER,
    HOSPITAL
  }

  enum Distance {
    NEAR("Близко"),
    MEDIUM("Среднее"),
    FAR("Далеко");
    final String label;

    Distance(String label) {
      this.label = label;
    }
  }

  enum Risk {
    LOW("Низкий", "НИЗКИЙ"),
    LOW_MEDIUM("Низкий / средний", "УМЕРЕННЫЙ"),
    MEDIUM("Средний", "СРЕДНИЙ"),
    HIGH("Высокий", "ВЫСОКИЙ");
    final String label, markerLabel;

    Risk(String label, String markerLabel) {
      this.label = label;
      this.markerLabel = markerLabel;
    }
  }

  final String id, name, markerName, loot;
  final Kind kind;
  final Distance distance;
  final Risk risk;
  final float mapX, mapY;
  private State state;

  MapLocation(
      String id,
      String name,
      String markerName,
      String loot,
      Kind kind,
      Distance distance,
      Risk risk,
      float mapX,
      float mapY,
      State state) {
    this.id = id;
    this.name = name;
    this.markerName = markerName;
    this.loot = loot;
    this.kind = kind;
    this.distance = distance;
    this.risk = risk;
    this.mapX = mapX;
    this.mapY = mapY;
    this.state = state;
  }

  State state() {
    return state;
  }

  void setState(State state) {
    this.state = java.util.Objects.requireNonNull(state);
  }

  boolean isLocked() {
    return state == State.LOCKED;
  }

  String statusLabel() {
    switch (state) {
      case LOCKED:
        return "Район не исследован";
      case DISCOVERED:
        return "Обнаружено";
      case SEARCHED:
        return "Исследовано";
      case DANGEROUS:
        return "Опасно";
      default:
        return "Не исследовано";
    }
  }
}
