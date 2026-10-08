package com.lastdom.game;

/** Persisted discovery state; destinations remain the existing MapLocation model. */
final class CityDistrict {
  enum State {
    UNEXPLORED("Неизведан"),
    DISCOVERED("Обнаружен"),
    EXPLORED("Исследован");
    final String label;

    State(String label) {
      this.label = label;
    }
  }

  final ExplorationConfig.District config;
  State state;

  CityDistrict(ExplorationConfig.District config) {
    this.config = config;
    state = config.prerequisite.isEmpty() ? State.DISCOVERED : State.UNEXPLORED;
  }
}
