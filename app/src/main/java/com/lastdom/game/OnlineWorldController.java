package com.lastdom.game;

import java.util.function.LongSupplier;

/** Demo commands and frame-time presentation. No access to solo balances or residents. */
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
    OnlineWorldState replacement = new OnlineWorldState(repository.load(), repository.gameplay());
    if (state != null) replacement.animationSeconds = state.animationSeconds;
    for (int i = 0; i < replacement.squads.size(); i++)
      replacement
          .squads
          .get(i)
          .position(replacement.animationSeconds, replacement.squadPositions, i * 2);
    replacement.pvpEnabled = !replacement.gameplay.pvpZoneId.isEmpty();
    for (int i = 0; i < replacement.gameplay.operations.size(); i++) {
      OnlineWorldGameplay.Operation operation = replacement.gameplay.operations.get(i);
      replacement.deliverySeconds[i] = operation.elapsedSeconds;
      operation.route.position(
          operation.elapsedSeconds / (double) OnlineWorldGameplay.DELIVERY_SECONDS,
          replacement.deliveryPositions,
          i * 2);
    }
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
    syncGameplay();
    for (int i = 0; i < state.gameplay.operations.size(); i++)
      state.deliverySeconds[i] = state.gameplay.operations.get(i).elapsedSeconds;
  }

  /** Called once by the existing host timer, independently of the solo speed multiplier. */
  void advanceSecond() {
    if (repository.advanceSecond()) {
      syncGameplay();
      if (!visible)
        for (int i = 0; i < state.gameplay.operations.size(); i++)
          state.deliverySeconds[i] = state.gameplay.operations.get(i).elapsedSeconds;
      panelRevision++;
    }
  }

  private void syncGameplay() {
    int previousCount = state.gameplay.operations.size();
    state.gameplay = repository.gameplay();
    for (int i = previousCount; i < state.gameplay.operations.size(); i++) {
      OnlineWorldGameplay.Operation operation = state.gameplay.operations.get(i);
      state.deliverySeconds[i] = operation.elapsedSeconds;
      operation.route.position(
          operation.elapsedSeconds / (double) OnlineWorldGameplay.DELIVERY_SECONDS,
          state.deliveryPositions,
          i * 2);
    }
    state.pvpEnabled = !state.gameplay.pvpZoneId.isEmpty();
  }

  boolean demoActionsAvailable() {
    return state.connection == OnlineWorldState.Connection.DEMO && repository.writable();
  }

  OnlineWorldGameplay.Result execute(String offerId, OnlineWorldGameplay.Kind kind) {
    OnlineWorldGameplay.Result result =
        demoActionsAvailable()
            ? repository.execute(offerId, kind)
            : new OnlineWorldGameplay.Result(false, "Демо-действия недоступны");
    syncGameplay();
    state.result = result.message;
    state.resultSuccess = result.success;
    if (result.success) state.confirmationGlow = 1;
    changed();
    return result;
  }

  OnlineWorldGameplay.Result preparePvp(boolean consent) {
    OnlineWorldGameplay.Result result =
        pvpActionAvailable() && state.confirmingPvp && consent
            ? repository.preparePvp(state.zoneId, consent)
            : new OnlineWorldGameplay.Result(false, "Выберите добровольную PvP-зону");
    syncGameplay();
    state.result = result.message;
    state.resultSuccess = result.success;
    if (result.success) state.confirmationGlow = 1;
    changed();
    return result;
  }

  void openPanel(OnlineWorldState.Panel panel) {
    state.panel = panel;
    state.result = "";
    state.offerId = "";
    if (panel == OnlineWorldState.Panel.TRADE || panel == OnlineWorldState.Panel.HELP) {
      OnlineWorldGameplay.Kind kind =
          panel == OnlineWorldState.Panel.TRADE
              ? OnlineWorldGameplay.Kind.TRADE
              : OnlineWorldGameplay.Kind.HELP;
      for (OnlineWorldGameplay.Offer offer : state.gameplay.offers)
        if (offer.shelterId.equals(state.shelterId) && offer.kind == kind) {
          state.offerId = offer.id;
          break;
        }
    }
    changed();
  }

  private void nextOffer() {
    java.util.List<OnlineWorldGameplay.Offer> candidates = new java.util.ArrayList<>();
    for (OnlineWorldGameplay.Offer offer : state.gameplay.offers)
      if (offer.shelterId.equals(state.shelterId) && offer.kind == OnlineWorldGameplay.Kind.TRADE)
        candidates.add(offer);
    for (int i = 0; i < candidates.size(); i++)
      if (candidates.get(i).id.equals(state.offerId)) {
        state.offerId = candidates.get((i + 1) % candidates.size()).id;
        state.result = "";
        changed();
        return;
      }
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
    state.confirmationGlow *= (float) Math.exp(-seconds / .7);
    for (int i = 0; i < state.gameplay.operations.size(); i++) {
      OnlineWorldGameplay.Operation operation = state.gameplay.operations.get(i);
      if (!operation.active()) continue;
      state.deliverySeconds[i] =
          Math.min(
              operation.elapsedSeconds + 1,
              Math.max(operation.elapsedSeconds - 1, state.deliverySeconds[i]) + seconds);
      operation.route.position(
          state.deliverySeconds[i] / OnlineWorldGameplay.DELIVERY_SECONDS,
          state.deliveryPositions,
          i * 2);
    }
  }

  void closeCard() {
    state.shelterId = state.zoneId = state.squadId = "";
    state.confirmingPvp = false;
    state.panel = OnlineWorldState.Panel.OBJECT;
    state.offerId = state.deliveryId = state.result = "";
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
        } else if (state.panel == OnlineWorldState.Panel.TRADE
            || state.panel == OnlineWorldState.Panel.HELP
            || state.panel == OnlineWorldState.Panel.HISTORY) {
          openPanel(
              state.panel == OnlineWorldState.Panel.HISTORY
                  ? OnlineWorldState.Panel.INVENTORY
                  : OnlineWorldState.Panel.OBJECT);
        } else closeCard();
      } else if (g.secondary(x, y)
          && state.panel == OnlineWorldState.Panel.OBJECT
          && state.shelter() != null
          && demoActionsAvailable()) {
        if (g.secondaryLeft(x, y, false)) openPanel(OnlineWorldState.Panel.TRADE);
        else if (g.secondaryRight(x, y, false)) openPanel(OnlineWorldState.Panel.HELP);
      } else if (g.secondary(x, y)
          && (state.panel == OnlineWorldState.Panel.TRADE
              || state.panel == OnlineWorldState.Panel.HELP)) {
        boolean trade = state.panel == OnlineWorldState.Panel.TRADE;
        if (trade && g.secondaryRight(x, y, true)) nextOffer();
        else if (!trade || g.secondaryLeft(x, y, true)) {
          OnlineWorldGameplay.Offer offer = state.offer();
          if (offer != null) execute(offer.id, offer.kind);
        }
      } else if (g.secondary(x, y) && state.panel == OnlineWorldState.Panel.INVENTORY) {
        openPanel(OnlineWorldState.Panel.HISTORY);
      } else if (g.secondary(x, y)
          && state.panel == OnlineWorldState.Panel.OBJECT
          && pvpActionAvailable()) {
        if (state.confirmingPvp) {
          preparePvp(true);
          state.confirmingPvp = false;
        } else if (state.pvpEnabled) {
          OnlineWorldGameplay.Result result = repository.disablePvp();
          syncGameplay();
          state.result = result.message;
          state.resultSuccess = result.success;
        } else state.confirmingPvp = true;
        changed();
      } else if (y < g.panelTop || y > g.panelBottom || (x >= 350 && y <= g.panelTop + 52))
        closeCard();
      return TouchResult.CONSUMED;
    }
    if (x >= 20 && x <= 96 && y >= 12 && y <= 56) return TouchResult.BACK;
    if (x >= 300 && x <= 400 && y >= 12 && y <= 56) {
      openPanel(OnlineWorldState.Panel.INVENTORY);
      return TouchResult.CONSUMED;
    }
    if (!g.inMap(x, y)) return TouchResult.CONSUMED;
    for (OnlineShelter shelter : state.shelters)
      if (g.hits(x, y, shelter.position.x, shelter.position.y)) {
        select(shelter.id, "", "");
        return TouchResult.CONSUMED;
      }
    for (int i = state.gameplay.operations.size() - 1; i >= 0; i--) {
      OnlineWorldGameplay.Operation operation = state.gameplay.operations.get(i);
      if (operation.active()
          && g.hits(x, y, state.deliveryPositions[i * 2], state.deliveryPositions[i * 2 + 1])) {
        closeCard();
        state.deliveryId = operation.offer.id;
        openPanel(OnlineWorldState.Panel.DELIVERY);
        return TouchResult.CONSUMED;
      }
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
