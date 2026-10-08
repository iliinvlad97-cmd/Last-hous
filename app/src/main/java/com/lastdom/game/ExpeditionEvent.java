package com.lastdom.game;

/** Exactly one persisted exploration event, including any already applied injury. */
final class ExpeditionEvent {
  enum Type {
    QUIET,
    CACHE,
    DAMAGED,
    INJURY,
    THREAT,
    MARAUDERS,
    INFECTED,
    COLLAPSE,
    WOUNDED_SURVIVOR,
    LOCKED_ROOM,
    DANGEROUS_AREA,
    WAREHOUSE
  }

  final Type type;
  final String message, injuredResidentId;
  final int damage;
  String instanceId = "";
  int chosenAction = -1;
  boolean effectsApplied, continued;
  ExpeditionEventOutcome outcome = new ExpeditionEventOutcome();

  boolean interactive() {
    return !instanceId.isEmpty();
  }

  static ExpeditionEvent city(Type type) {
    ExpeditionEvent event =
        new ExpeditionEvent(type, ExpeditionEventConfig.description(type), "", 0);
    event.instanceId = java.util.UUID.randomUUID().toString();
    return event;
  }

  String resultMessage() {
    return interactive() && effectsApplied ? outcome.message : message;
  }

  ExpeditionEvent(Type type, String message, String injuredResidentId, int damage) {
    this.type = type;
    this.message = message;
    this.injuredResidentId = injuredResidentId;
    this.damage = Math.max(0, damage);
  }
}
