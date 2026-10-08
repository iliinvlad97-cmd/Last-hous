package com.lastdom.game;

/** Durable identity, paid cost and simulated progress; completion is never replayed on load. */
final class RoomUpgradeTask {
  final String id;
  String builderId;
  final int room, targetLevel, duration, paidCost;
  final long startMinute;
  final boolean costPaid, legacy;
  int elapsed;
  boolean completed;

  RoomUpgradeTask(
      String id,
      int room,
      int target,
      String builder,
      long start,
      int duration,
      int cost,
      boolean paid,
      boolean legacy,
      int elapsed,
      boolean completed) {
    this.id = id;
    this.room = room;
    targetLevel = target;
    builderId = builder;
    startMinute = start;
    this.duration = duration;
    paidCost = cost;
    costPaid = paid;
    this.legacy = legacy;
    this.elapsed = elapsed;
    this.completed = completed;
  }

  int remaining() {
    return Math.max(0, duration - elapsed);
  }

  int progress() {
    return Math.min(100, (int) (100L * elapsed / duration));
  }
}
