package com.lastdom.game;

/** Transient overlay selections only; building mutations belong to RoomUpgradeController. */
final class RoomUpgradePanelController {
  private final GameController game;
  Runnable defenseOpen = () -> {};
  boolean choosing, assigning;
  String builderId = "", message = "";
  String assignmentJob = "";
  // IDs of the visible candidates, so a stale tap can never select a different resident.
  final java.util.ArrayList<String> workerIds = new java.util.ArrayList<>();
  final java.util.HashSet<String> assignedIds = new java.util.HashSet<>();
  final java.util.ArrayList<String> builderIds = new java.util.ArrayList<>();
  int page, scroll, lineCount;
  private boolean dragging, moved;
  private float dragY, startY;

  RoomUpgradePanelController(GameController game) {
    this.game = game;
  }

  void open(int room) {
    game.selectedRoom = room;
    game.overlay = 2;
    choosing = false;
    assigning = false;
    workerIds.clear();
    assignedIds.clear();
    builderIds.clear();
    assignmentJob = RoomUpgradeConfig.valid(room) ? game.roomJobs[room] : "";
    builderId = "";
    message = "";
    page = scroll = lineCount = 0;
  }

  void close() {
    if (game.screen == 4) game.screen = GameView.HOME;
    game.overlay = 0;
    choosing = false;
    assigning = false;
    workerIds.clear();
    assignedIds.clear();
    builderIds.clear();
  }

  boolean scrollTouch(int action, float y, RoomUpgradeLayout layout) {
    if (choosing || assigning) return false;
    if (action == android.view.MotionEvent.ACTION_DOWN) {
      dragging = y >= layout.top + 75 && y < layout.actionTop - 24;
      startY = dragY = y;
      moved = false;
      return true;
    }
    if (action == android.view.MotionEvent.ACTION_MOVE && dragging) {
      if (Math.abs(y - startY) > 8) moved = true;
      int delta = (int) ((dragY - y) / RoomEfficiencyRenderer.LINE);
      if (delta != 0) {
        scroll =
            Math.max(0, Math.min(Math.max(0, lineCount - layout.visibleLines), scroll + delta));
        dragY = y;
      }
      return true;
    }
    if (action == android.view.MotionEvent.ACTION_CANCEL) {
      dragging = moved = false;
      return true;
    }
    if (action == android.view.MotionEvent.ACTION_UP) {
      dragging = false;
      boolean consumed = moved;
      moved = false;
      return consumed;
    }
    return false;
  }

  void touch(float x, float y, RoomUpgradeLayout layout) {
    if (y < layout.top || y > layout.bottom || (x > 350 && y < layout.top + 52)) {
      close();
      return;
    }
    if (assigning) {
      touchWorkers(x, y, layout);
      return;
    }
    if (choosing) {
      int index =
          page * layout.capacity + (int) ((y - layout.rowTop) / RoomUpgradeLayout.ASSIGNMENT_ROW);
      float local = (y - layout.rowTop) % RoomUpgradeLayout.ASSIGNMENT_ROW;
      if (x >= 30
          && x <= 390
          && y >= layout.rowTop
          && y < layout.pageY - 16
          && local <= RoomUpgradeLayout.ASSIGNMENT_ROW - 8
          && index < builderIds.size()) {
        Resident resident = game.expeditionController.resident(builderIds.get(index));
        message = game.roomUpgradeController.unavailableReason(resident);
        if (message.isEmpty()) {
          builderId = resident.id;
          choosing = false;
          scroll = 0;
        }
      } else if (y >= layout.pageY - 16 && y <= layout.pageY + 12) {
        int pages = Math.max(1, (builderIds.size() + layout.capacity - 1) / layout.capacity);
        if (x < 120) page = Math.max(0, page - 1);
        else if (x > 300) page = Math.min(pages - 1, page + 1);
      } else if (layout.action(x, y)) {
        choosing = false;
        message = "";
      } else if (layout.secondary(y)) close();
      return;
    }
    if (layout.action(x, y)) {
      message = game.roomUpgradeController.blockedReason(game.selectedRoom);
      if (message.isEmpty()) {
        if (builderId.isEmpty()) {
          choosing = true;
          page = 0;
          scroll = 0;
          refreshBuilders();
        } else {
          message = game.roomUpgradeController.start(game.selectedRoom, builderId);
          if (message.isEmpty()) builderId = "";
        }
      }
      scroll = 0;
    } else if (layout.secondary(y)) {
      if (x >= 214 && x <= 390) close();
      else if (x >= 30 && x <= 206) {
        if (game.selectedRoom == 4) {
          defenseOpen.run();
          return;
        }
        assigning = true;
        assignmentJob = game.roomJobs[game.selectedRoom];
        page = scroll = 0;
        message = "";
        refreshWorkers();
      }
    }
  }

  void refreshWorkers() {
    workerIds.clear();
    assignedIds.clear();
    for (RoomAssignmentController.Category category : RoomAssignmentController.Category.values())
      for (Resident resident : game.people)
        if (game.roomAssignmentController.category(game.selectedRoom, assignmentJob, resident)
            == category) {
          workerIds.add(resident.id);
          if (category == RoomAssignmentController.Category.ASSIGNED) assignedIds.add(resident.id);
        }
  }

  void refreshBuilders() {
    builderIds.clear();
    for (RoomAssignmentController.Category category :
        new RoomAssignmentController.Category[] {
          RoomAssignmentController.Category.FREE, RoomAssignmentController.Category.TRANSFER
        })
      for (Resident resident : game.people)
        if (game.roomUpgradeController.unavailableReason(resident).isEmpty()
            && game.roomAssignmentController.availableCategory(resident) == category)
          builderIds.add(resident.id);
  }

  private void touchWorkers(float x, float y, RoomUpgradeLayout layout) {
    boolean kitchen = game.selectedRoom == 1;
    if (kitchen && y >= layout.top + 78 && y <= layout.top + 118) {
      if (x >= 30 && x <= 206) assignmentJob = "Еда";
      else if (x >= 214 && x <= 390) assignmentJob = "Вода";
      else return;
      page = 0;
      message = "";
      refreshWorkers();
      return;
    }
    int capacity = layout.workerCapacity(kitchen);
    float rowTop = layout.workerRowTop(kitchen);
    int row = (int) ((y - rowTop) / RoomUpgradeLayout.ASSIGNMENT_ROW),
        index = page * capacity + row;
    if (x >= 30
        && x <= 390
        && y >= rowTop
        && y < layout.pageY - 16
        && row < capacity
        && (y - rowTop) % RoomUpgradeLayout.ASSIGNMENT_ROW <= RoomUpgradeLayout.ASSIGNMENT_ROW - 8
        && index < workerIds.size()) {
      String id = workerIds.get(index);
      boolean removing = assignedIds.contains(id);
      message =
          removing
              ? game.roomAssignmentController.unassign(game.selectedRoom, assignmentJob, id)
              : game.roomAssignmentController.assign(game.selectedRoom, assignmentJob, id);
      if (message.isEmpty()) {
        Resident resident = game.expeditionController.resident(id);
        message = resident.name + ": " + (removing ? "назначен отдых" : assignmentJob) + ".";
        assigning = false;
        scroll = 0;
        // A worker can no longer be the pending builder of this panel.
        if (builderId.equals(id)) builderId = "";
      } else refreshWorkers();
    } else if (y >= layout.pageY - 16 && y <= layout.pageY + 12) {
      int pages = Math.max(1, (workerIds.size() + capacity - 1) / capacity);
      if (x < 120) page = Math.max(0, page - 1);
      else if (x > 300) page = Math.min(pages - 1, page + 1);
    } else if (layout.action(x, y)) {
      assigning = false;
      message = "";
    } else if (layout.secondary(y)) close();
  }
}
