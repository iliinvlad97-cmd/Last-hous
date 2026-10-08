package com.lastdom.game;

/**
 * Shared preparation row/button geometry, with paging for small screens and growing populations.
 */
final class ExpeditionPreparationLayout {
  final float bottom, backTop, sendTop, pageY, messageY;
  final int capacity;

  ExpeditionPreparationLayout(float bottom) {
    this.bottom = bottom;
    sendTop = bottom - 54;
    backTop = bottom - 98;
    pageY = bottom - 155;
    messageY = bottom - 126;
    capacity = Math.max(1, (int) ((bottom - 170 - 213) / 56));
  }

  float rowTop(int row) {
    return 213 + row * 56;
  }
}
