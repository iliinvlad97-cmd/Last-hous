package com.lastdom.game;

/** Presentation only: one finite, real-time tween between the simulation's one-second samples. */
final class ExpeditionMarkerAnimation {
  static final long SAMPLE_NANOS = 1_000_000_000L;
  private float position, from, target;
  private long started;
  private boolean initialized, returning, stopped;

  void clear() {
    initialized = false;
  }

  float frame(float logical, boolean returnTrip, boolean blocked, long now) {
    logical = Math.max(0, Math.min(1, logical));
    if (!initialized || returning != returnTrip) {
      initialized = true;
      returning = returnTrip;
      position = from = target = logical;
      started = now;
      stopped = blocked;
      return position;
    }
    if (blocked) {
      stopped = true;
      return position;
    }
    if (stopped) {
      // Discard time spent paused; resume only the remaining visual distance.
      from = position;
      target = logical;
      started = now;
      stopped = false;
    } else {
      double fraction = Math.max(0, Math.min(1, (now - started) / (double) SAMPLE_NANOS));
      position = fraction == 1 ? target : from + (target - from) * (float) fraction;
      if (logical != target) {
        from = position;
        target = logical;
        started = now;
      }
    }
    // Never predict beyond an authoritative outbound/return sample or leave the route.
    position = returning ? Math.max(position, logical) : Math.min(position, logical);
    return Math.max(0, Math.min(1, position));
  }

  boolean moving() {
    return initialized && !stopped && position != target;
  }
}
