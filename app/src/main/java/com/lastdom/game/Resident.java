package com.lastdom.game;

/** Mutable resident data; legacy save fields are preserved. */
final class Resident {
  enum Status {
    HOME,
    ON_EXPEDITION,
    BUILDING
  }

  Status status = Status.HOME;
  String id = java.util.UUID.randomUUID().toString();
  String name, role, job = "Отдых";
  int skill, health = 100, hunger = 10, fatigue = 10, morale = 75;
  boolean alive = true;

  Resident(String name, String role, int skill) {
    this.name = name;
    this.role = role;
    this.skill = skill;
  }
}
