package com.lastdom.game;

/** Pagination keeps reports and controls above the persistent back button. */
final class JournalLayout {
  static final float TOP = 138, ROW_HEIGHT = 84;
  final float pagerY;
  final int capacity;

  JournalLayout(float height) {
    pagerY = height - 108;
    capacity = Math.max(1, (int) ((pagerY - 20 - TOP) / ROW_HEIGHT));
  }

  float rowTop(int row) {
    return TOP + row * ROW_HEIGHT;
  }
}
