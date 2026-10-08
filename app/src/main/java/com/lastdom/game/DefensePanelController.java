package com.lastdom.game;

import android.view.MotionEvent;

/** Overlay selection/scrolling only. All mutations are delegated to RaidController. */
final class DefensePanelController {
  enum Mode {
    THREAT,
    STATUS,
    DEFENDERS,
    REPAIR,
    BUILDERS
  }

  final GameView view;
  boolean open;
  Mode mode = Mode.STATUS;
  RaidState report;
  String message = "", shownWarning = "", shownResult = "";
  int scroll, page;
  final java.util.ArrayList<String> residentIds = new java.util.ArrayList<>();
  float contentHeight;
  private boolean dragging, moved;
  private float startY, lastY;

  DefensePanelController(GameView view) {
    this.view = view;
  }

  void openStatus() {
    open = true;
    mode = Mode.STATUS;
    report = view.game.raidController.active();
    if (report == null) report = view.game.raidController.latest();
    if (report != null && report.phase == RaidState.Phase.WARNING) shownWarning = report.id;
    if (report != null && report.phase == RaidState.Phase.RESULT) shownResult = report.id;
    reset();
  }

  void reset() {
    scroll = page = 0;
    contentHeight = 0;
    message = "";
    refreshResidents();
  }

  void refreshResidents() {
    residentIds.clear();
    if (!list()) return;
    for (RoomAssignmentController.Category category : RoomAssignmentController.Category.values())
      for (Resident resident : view.game.people) {
        boolean selected = mode == Mode.DEFENDERS && view.game.raidController.defending(resident);
        String reason =
            mode == Mode.DEFENDERS
                ? view.game.raidController.defenderReason(resident)
                : view.game.roomUpgradeController.unavailableReason(resident);
        RoomAssignmentController.Category actual =
            selected
                ? RoomAssignmentController.Category.ASSIGNED
                : view.game.roomAssignmentController.availableCategory(resident);
        if (actual == category && (selected || reason.isEmpty())) residentIds.add(resident.id);
      }
  }

  void pending() {
    if (open && report != null && report.phase == RaidState.Phase.RESULT) shownResult = report.id;
    if (open
        && report != null
        && (mode == Mode.DEFENDERS && report.phase == RaidState.Phase.ATTACK
            || (mode == Mode.THREAT || mode == Mode.DEFENDERS)
                && report.phase == RaidState.Phase.RESULT)) {
      mode = Mode.STATUS;
      reset();
    }
    if (open || view.game.event || view.game.gameOver) return;
    // A threat must be visible even above an open non-pausing resident/map panel.
    // Underlying selections and unresolved city events remain intact when this panel closes.
    RaidState raid = view.game.raidController.active();
    if (raid != null && raid.phase == RaidState.Phase.WARNING && !shownWarning.equals(raid.id)) {
      openStatus();
      mode = Mode.THREAT;
      shownWarning = raid.id;
      return;
    }
    if (view.game.jobMenu
        || view.game.overlay != 0
        || view.cityMap.eventPanel
        || view.cityMap.expeditionPanel) return;
    RaidState latest = view.game.raidController.latest();
    if (latest != null
        && latest.phase == RaidState.Phase.RESULT
        && !shownResult.equals(latest.id)) {
      openStatus();
      report = latest;
      shownResult = latest.id;
    }
  }

  boolean list() {
    return mode == Mode.DEFENDERS || mode == Mode.BUILDERS;
  }

  boolean repairAccessible() {
    RaidController controller = view.game.raidController;
    return controller.repairing() || controller.repairReason().isEmpty();
  }

  int pages(DefensePanelLayout l) {
    return Math.max(1, (residentIds.size() + l.capacity - 1) / l.capacity);
  }

  boolean scrollTouch(int action, float y, DefensePanelLayout l) {
    if (!open) return false;
    if (action == MotionEvent.ACTION_DOWN) {
      startY = lastY = y;
      dragging = y >= l.bodyTop && y < l.bodyBottom;
      moved = false;
      return true;
    }
    if (action == MotionEvent.ACTION_MOVE && dragging) {
      if (Math.abs(y - startY) > 8) moved = true;
      if (list()) {
        if (Math.abs(y - lastY) > 48) {
          page = Math.max(0, Math.min(pages(l) - 1, page + (y < lastY ? 1 : -1)));
          lastY = y;
        }
      } else {
        int delta = (int) (lastY - y);
        if (delta != 0) {
          scroll = Math.max(0, Math.min(l.maxScroll(contentHeight), scroll + delta));
          lastY = y;
        }
      }
      return true;
    }
    if (action == MotionEvent.ACTION_CANCEL) {
      dragging = moved = false;
      return true;
    }
    if (action == MotionEvent.ACTION_UP) {
      dragging = false;
      boolean consumed = moved;
      moved = false;
      return consumed;
    }
    return false;
  }

  void touch(float x, float y, DefensePanelLayout l) {
    if (x < 18 || x > 402 || y < l.top || y > l.bottom || x > 350 && y < l.top + 48) {
      open = false;
      return;
    }
    if (list()) {
      int row = (int) ((y - l.rowTop) / l.rowHeight), index = page * l.capacity + row;
      if (x >= 30
          && x <= 390
          && y >= l.rowTop
          && y < l.pageY - 16
          && row < l.capacity
          && index < residentIds.size()
          && (y - l.rowTop) % l.rowHeight <= l.rowHeight - DefensePanelLayout.ROW_GAP) {
        String residentId = residentIds.get(index);
        message =
            mode == Mode.DEFENDERS
                ? view.game.raidController.toggleDefender(residentId)
                : view.game.raidController.startRepair(residentId);
        if (mode == Mode.BUILDERS && message.isEmpty()) {
          mode = Mode.REPAIR;
          reset();
        }
      } else if (y >= l.pageY - 16 && y <= l.pageY + 12) {
        if (x < 120) page = Math.max(0, page - 1);
        else if (x > 300) page = Math.min(pages(l) - 1, page + 1);
      } else if (l.action(x, y)) {
        mode = mode == Mode.DEFENDERS ? Mode.STATUS : Mode.REPAIR;
        reset();
      } else if (l.secondary(y)) open = false;
      return;
    }
    if (l.action(x, y)) {
      RaidState active = view.game.raidController.active();
      if (mode == Mode.REPAIR) {
        message = view.game.raidController.repairReason();
        if (message.isEmpty()) {
          mode = Mode.BUILDERS;
          reset();
        }
      } else if (report != null && !report.active()) {
        view.game.raidController.acknowledge(report);
        open = false;
      } else if (active != null && active.phase != RaidState.Phase.ATTACK) {
        view.game.raidController.prepare();
        mode = Mode.DEFENDERS;
        report = active;
        reset();
      }
    } else if (l.secondary(y)) {
      if (x >= 214
          || report != null && !report.active() && mode != Mode.REPAIR && !repairAccessible())
        open = false;
      else if (x >= 30 && (mode == Mode.REPAIR || repairAccessible())) {
        mode = mode == Mode.REPAIR ? Mode.STATUS : Mode.REPAIR;
        reset();
      }
    }
  }
}
