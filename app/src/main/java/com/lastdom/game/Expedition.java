package com.lastdom.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persisted squad identity and progress measured exclusively in simulation minutes. */
final class Expedition {
  enum State {
    PREPARING,
    TRAVELING_TO_TARGET,
    AT_LOCATION,
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
  }

  State state() {
    return state;
  }

  int elapsedMinutes() {
    return elapsedMinutes;
  }

  int remainingMinutes() {
    return Math.max(0, durationMinutes - elapsedMinutes);
  }

  float progress() {
    return Math.min(1, elapsedMinutes / (float) durationMinutes);
  }

  boolean active() {
    return state != State.COMPLETED && state != State.FAILED;
  }

  boolean advanceMinute() {
    if (state != State.TRAVELING_TO_TARGET) return false;
    elapsedMinutes = Math.min(durationMinutes, elapsedMinutes + 1);
    if (elapsedMinutes == durationMinutes) {
      state = State.AT_LOCATION;
      return true;
    }
    return false;
  }
}
