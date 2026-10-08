package com.lastdom.game;

/** Paid one-shot construction transaction; shares the Stage 5 builder exclusivity. */
final class BarricadeRepair {
  final String id, builderId;
  final long startMinute;
  final RaidState.Assignment previous;
  int elapsed;
  boolean paid, completed;

  BarricadeRepair(String id, String builderId, long startMinute, RaidState.Assignment previous) {
    this.id = id;
    this.builderId = builderId;
    this.startMinute = startMinute;
    this.previous = previous;
  }

  int remaining() {
    return Math.max(0, RaidConfig.REPAIR_MINUTES - elapsed);
  }
}
