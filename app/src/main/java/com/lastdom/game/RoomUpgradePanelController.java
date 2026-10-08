package com.lastdom.game;

/** Transient overlay selections only; building mutations belong to RoomUpgradeController. */
final class RoomUpgradePanelController {
  private final GameController game;
  Runnable defenseOpen = () -> {};
  boolean choosing;
  String builderId = "", message = "";
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
    builderId = "";
    message = "";
    page = scroll = lineCount = 0;
  }

  void close() {
    if (game.screen == 4) game.screen = GameView.HOME;
    game.overlay = 0;
    choosing = false;
  }

  boolean scrollTouch(int action, float y, RoomUpgradeLayout layout) {
    if (choosing) return false;
    if (action == android.view.MotionEvent.ACTION_DOWN) {
      dragging = y >= layout.top + 75 && y < layout.actionTop - 24;
      startY = dragY = y;
      moved = false;
      return true;
    }
    if (action == android.view.MotionEvent.ACTION_MOVE && dragging) {
      if (Math.abs(y - startY) > 8) moved = true;
      int delta = (int) ((dragY - y) / 18);
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
    if (choosing) {
      int index = page * layout.capacity + (int) ((y - layout.rowTop) / 64);
      float local = (y - layout.rowTop) % 64;
      if (x >= 30
          && x <= 390
          && y >= layout.rowTop
          && y < layout.pageY - 16
          && local <= 56
          && index < game.people.size()) {
        Resident resident = game.people.get(index);
        message = game.roomUpgradeController.unavailableReason(resident);
        if (message.isEmpty()) {
          builderId = resident.id;
          choosing = false;
          scroll = 0;
        }
      } else if (y >= layout.pageY - 16 && y <= layout.pageY + 12) {
        int pages = Math.max(1, (game.people.size() + layout.capacity - 1) / layout.capacity);
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
        int selected = -1;
        for (int i = 0; i < game.people.size(); i++)
          if (game.roomUpgradeController.unavailableReason(game.people.get(i)).isEmpty()) {
            selected = i;
            break;
          }
        if (selected >= 0) {
          game.selected = selected;
          game.jobMenu = true;
        } else {
          message = "Нет доступных жителей для назначения";
          scroll = 0;
        }
      }
    }
  }
}
