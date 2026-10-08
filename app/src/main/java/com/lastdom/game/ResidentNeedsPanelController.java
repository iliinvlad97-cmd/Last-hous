package com.lastdom.game;

import android.view.MotionEvent;

/** Transient UI selection/scrolling; assignments go through the game controller. */
final class ResidentNeedsPanelController {
  private final GameView view;
  int page, scroll, lineCount;
  String detailResidentId = "";
  private boolean dragging, moved;
  private float startY, lastY;

  ResidentNeedsPanelController(GameView view) {
    this.view = view;
  }

  boolean scrollTouch(int action, float y, ResidentNeedsLayout layout) {
    if (action == MotionEvent.ACTION_DOWN) {
      startY = lastY = y;
      dragging = y >= layout.bodyTop && y < layout.footer;
      moved = false;
      return true;
    }
    if (action == MotionEvent.ACTION_MOVE && dragging) {
      if (Math.abs(y - startY) > 8) moved = true;
      if (view.game.overlay == 3) {
        if (Math.abs(y - lastY) > 48) {
          page = Math.max(0, Math.min(pages(layout) - 1, page + (y < lastY ? 1 : -1)));
          lastY = y;
        }
      } else {
        int delta = (int) ((lastY - y) / 21);
        if (delta != 0) {
          scroll = Math.max(0, Math.min(Math.max(0, lineCount - layout.lines), scroll + delta));
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
      boolean result = moved;
      moved = false;
      return result;
    }
    return false;
  }

  int pages(ResidentNeedsLayout layout) {
    return Math.max(1, (view.game.people.size() + layout.capacity - 1) / layout.capacity);
  }

  void touch(float x, float y, ResidentNeedsLayout l) {
    GameController game = view.game;
    if (x < 18 || x > 402 || y < l.top || y > l.bottom || x > 350 && y < l.top + 50) {
      game.overlay = 0;
      if (game.screen == 1) game.screen = GameView.HOME;
      return;
    }
    if (game.overlay == 3) {
      if (y >= l.bodyTop && y < l.footer - 8 && x >= 30 && x <= 390) {
        int row = (int) ((y - l.bodyTop) / 98);
        int index = page * l.capacity + row;
        if (row < l.capacity && index < game.people.size() && (y - l.bodyTop) % 98 <= 90) {
          game.selected = index;
          game.overlay = 1;
          scroll = 0;
        }
      } else if (y >= l.footer && y <= l.footer + 44) {
        page = Math.max(0, Math.min(pages(l) - 1, page + (x < 210 ? -1 : 1)));
      } else if (y >= l.footer + 54) game.overlay = 0;
      return;
    }
    if (game.selected < 0 || game.selected >= game.people.size()) return;
    Resident r = game.people.get(game.selected);
    boolean busy = game.isOnExpedition(r) || game.isBuilding(r) || game.isDefending(r);
    if (y >= l.footer && y <= l.footer + 44 && !busy) game.jobMenu = true;
    else if (y >= l.footer + 54 && y <= l.footer + 98) {
      if (x < 210 && !busy) game.assignJob(game.selected, "Отдых");
      else if (x >= 210) {
        game.overlay = 0;
        if (game.screen == 1) game.screen = GameView.HOME;
      }
    }
  }
}
