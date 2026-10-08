package com.lastdom.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persisted squad identity and progress measured exclusively in simulation minutes. */
final class Expedition {
  enum Type {
    LOOT,
    RECON
  }

  Type type = Type.LOOT;
  ReconData recon;

  enum State {
    PREPARING,
    TRAVELING_TO_TARGET,
    AT_LOCATION, // Legacy Stage 2 save value; migrated to EXPLORING on load.
    EXPLORING,
    AWAITING_RETURN,
    AWAITING_DECISION,
    RETURNING,
    COMPLETED,
    FAILED
  }

  final String locationId;
  final List<String> participantIds;
  final long departureMinute;
  final int durationMinutes;
  private int elapsedMinutes;
  private State state;
  String id = java.util.UUID.randomUUID().toString();
  long phaseStartMinute;
  int phaseDurationMinutes;
  long completedMinute;
  boolean resultGenerated, rewardCredited, reportAcknowledged;
  ExpeditionLoot found = new ExpeditionLoot(), cargo = new ExpeditionLoot();
  ExpeditionEvent explorationEvent =
      new ExpeditionEvent(ExpeditionEvent.Type.QUIET, "Исследование прошло спокойно", "", 0);
  int fatigueGain;
  boolean cityEventChecked, lootRolled;
  ExpeditionLoot unsearchedLoot = new ExpeditionLoot();
  int explorationDelay, cityRiskReduction;

  Expedition(
      String locationId,
      List<String> ids,
      long departureMinute,
      int duration,
      int elapsed,
      State state) {
    this.locationId = locationId;
    participantIds = Collections.unmodifiableList(new ArrayList<>(ids));
    this.departureMinute = departureMinute;
    durationMinutes = duration;
    elapsedMinutes = Math.max(0, Math.min(duration, elapsed));
    this.state = state;
    phaseStartMinute = departureMinute;
    phaseDurationMinutes = duration;
  }

  State state() {
    return state;
  }

  int elapsedMinutes() {
    return elapsedMinutes;
  }

  int remainingMinutes() {
    return Math.max(0, phaseDurationMinutes - elapsedMinutes);
  }

  float progress() {
    return Math.min(1, elapsedMinutes / (float) phaseDurationMinutes);
  }

  boolean active() {
    return state != State.COMPLETED && state != State.FAILED;
  }

  int capacity() {
    return participantIds.size() * 10;
  }

  float routeProgress() {
    if (state == State.RETURNING) return 1 - progress();
    if (state == State.COMPLETED) return 0;
    return state == State.TRAVELING_TO_TARGET ? progress() : 1;
  }

  String phaseLabel() {
    switch (state) {
      case EXPLORING:
        return type == Type.RECON ? "Разведка" : "Исследование";
      case AWAITING_DECISION:
        return explorationEvent.effectsApplied ? "Решение принято" : "Ждёт решения";
      case AWAITING_RETURN:
        return "Ожидает возвращения";
      case RETURNING:
        return "Возвращается";
      case COMPLETED:
        return "Отряд вернулся";
      default:
        return "В пути";
    }
  }

  void beginPhase(State state, int duration, long now) {
    this.state = state;
    phaseDurationMinutes = Math.max(1, duration);
    elapsedMinutes = 0;
    phaseStartMinute = now;
  }

  void restorePhase(State state, int duration, int elapsed, long started) {
    this.state = state;
    phaseDurationMinutes = Math.max(1, duration);
    elapsedMinutes = Math.max(0, Math.min(phaseDurationMinutes, elapsed));
    phaseStartMinute = started;
  }

  boolean advanceMinute() {
    if (state != State.TRAVELING_TO_TARGET && state != State.EXPLORING && state != State.RETURNING)
      return false;
    elapsedMinutes = Math.min(phaseDurationMinutes, elapsedMinutes + 1);
    return elapsedMinutes == phaseDurationMinutes;
  }
}
