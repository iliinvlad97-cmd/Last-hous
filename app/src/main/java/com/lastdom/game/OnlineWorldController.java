package com.lastdom.game;

import java.util.function.LongSupplier;

/**
 * Only demo selection/consent and cosmetic time. No GameController, saves, residents or rewards.
 */
final class OnlineWorldController {
  enum TouchResult {
    CONSUMED,
    BACK,
    NAVIGATION
  }

  OnlineWorldState state;
  private final OnlineWorldRepository repository;
  private final LongSupplier clock;
  private long previousNanos = Long.MIN_VALUE;
  private boolean visible, dragging, moved;
  private float dragStartX, dragStartY, dragY;
  private OnlineWorldGeometry geometry;
  int panelRevision, panelLineCount;

  OnlineWorldController(OnlineWorldRepository repository) {
    this(repository, System::nanoTime);
  }

  OnlineWorldController(OnlineWorldRepository repository, LongSupplier clock) {
    this.clock = clock;
    this.repository = repository;
    refresh();
  }

  /** Replace a validated repository snapshot atomically, outside the drawing methods. */
  void refresh() {
    OnlineWorldState replacement = new OnlineWorldState(repository.load());
    if (state != null) replacement.animationSeconds = state.animationSeconds;
    for (int i = 0; i < replacement.squads.size(); i++)
      replacement
          .squads
          .get(i)
          .position(replacement.animationSeconds, replacement.squadPositions, i * 2);
    state = replacement;
    previousNanos = Long.MIN_VALUE;
    changed();
  }

  OnlineWorldGeometry geometry(float height) {
    if (geometry == null || geometry.height != height) geometry = new OnlineWorldGeometry(height);
    return geometry;
  }

  void enter() {
    visible = true;
    previousNanos = Long.MIN_VALUE;
  }

  void leave() {
    visible = false;
    previousNanos = Long.MIN_VALUE;
    closeCard();
  }

  void frame() {
    if (!visible) return;
    long now = clock.getAsLong();
    double seconds = previousNanos == Long.MIN_VALUE ? 0 : (now - previousNanos) / 1_000_000_000.0;
    previousNanos = now;
    // Returning from background or a long render stall must not teleport the patrol.
    if (seconds < 0 || seconds > .25) seconds = 0;
    state.animationSeconds += seconds;
    float opacity = (float) (1 - Math.exp(-seconds / .12));
    float highlight = (float) (1 - Math.exp(-seconds / .18));
    float target = state.selected() ? 1 : 0;
    state.cardOpacity += (target - state.cardOpacity) * opacity;
    state.selectionStrength += (target - state.selectionStrength) * highlight;
    for (int i = 0; i < state.squads.size(); i++)
      state.squads.get(i).position(state.animationSeconds, state.squadPositions, i * 2);
  }

  void closeCard() {
    state.shelterId = state.zoneId = state.squadId = "";
    state.confirmingPvp = false;
    state.cardOpacity = state.selectionStrength = 0;
    changed();
  }

  private void changed() {
    state.panelScroll = 0;
    panelLineCount = 0;
    panelRevision++;
    dragging = moved = false;
  }

  private void select(String shelter, String zone, String squad) {
    closeCard();
    state.shelterId = shelter;
    state.zoneId = zone;
    state.squadId = squad;
    changed();
  }

  boolean pvpActionAvailable() {
    return state.connection == OnlineWorldState.Connection.DEMO
        && state.zone() != null
        && state.zone().type == OnlineZone.Type.PVP;
  }

  TouchResult touch(int action, float x, float y, OnlineWorldGeometry g) {
    boolean card = state.selected();
    if (action == android.view.MotionEvent.ACTION_CANCEL) {
      dragging = moved = false;
      return TouchResult.CONSUMED;
    }
    if (action == android.view.MotionEvent.ACTION_DOWN) {
      dragging = card && y >= g.contentTop && y <= g.contentBottom;
      dragStartX = x;
      dragStartY = dragY = y;
      moved = false;
      return TouchResult.CONSUMED;
    }
    if (action == android.view.MotionEvent.ACTION_MOVE) {
      if (Math.abs(y - dragStartY) > 8 || Math.abs(x - dragStartX) > 8) moved = true;
      if (dragging) {
        int delta = (int) ((dragY - y) / 19);
        if (delta != 0) {
          state.panelScroll =
              Math.max(
                  0,
                  Math.min(
                      Math.max(0, panelLineCount - g.visibleLines()), state.panelScroll + delta));
          dragY = y;
        }
      }
      return TouchResult.CONSUMED;
    }
    if (action != android.view.MotionEvent.ACTION_UP) return TouchResult.CONSUMED;
    dragging = false;
    if (moved) {
      moved = false;
      return TouchResult.CONSUMED;
    }
    if (y >= g.height - 78) return TouchResult.NAVIGATION;
    if (card) {
      if (g.closeButton(x, y)) {
        if (state.confirmingPvp) {
          state.confirmingPvp = false;
          changed();
        } else closeCard();
      } else if (g.secondary(x, y) && pvpActionAvailable()) {
        if (state.confirmingPvp) {
          state.pvpEnabled = true;
          state.confirmingPvp = false;
        } else if (state.pvpEnabled) state.pvpEnabled = false;
        else state.confirmingPvp = true;
        changed();
      } else if (y < g.panelTop || y > g.panelBottom || (x >= 350 && y <= g.panelTop + 52))
        closeCard();
      return TouchResult.CONSUMED;
    }
    if (x >= 20 && x <= 96 && y >= 12 && y <= 56) return TouchResult.BACK;
    if (!g.inMap(x, y)) return TouchResult.CONSUMED;
    for (OnlineShelter shelter : state.shelters)
      if (g.hits(x, y, shelter.position.x, shelter.position.y)) {
        select(shelter.id, "", "");
        return TouchResult.CONSUMED;
      }
    for (int i = 0; i < state.squads.size(); i++)
      if (g.hits(x, y, state.squadPositions[i * 2], state.squadPositions[i * 2 + 1])) {
        select("", "", state.squads.get(i).id);
        return TouchResult.CONSUMED;
      }
    OnlineZone zone = g.zoneAt(state, x, y);
    if (zone != null) select("", zone.id, "");
    return TouchResult.CONSUMED;
  }
}
