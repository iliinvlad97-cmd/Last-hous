package com.lastdom.game;

/** Exactly one persisted exploration event, including any already applied injury. */
final class ExpeditionEvent {
  enum Type {
    QUIET,
    CACHE,
    DAMAGED,
    INJURY,
    THREAT
  }

  final Type type;
  final String message, injuredResidentId;
  final int damage;

  ExpeditionEvent(Type type, String message, String injuredResidentId, int damage) {
    this.type = type;
    this.message = message;
    this.injuredResidentId = injuredResidentId;
    this.damage = Math.max(0, damage);
  }
}
