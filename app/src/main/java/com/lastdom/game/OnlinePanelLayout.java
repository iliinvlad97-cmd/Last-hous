package com.lastdom.game;

import java.util.Arrays;

/** Shared measured row geometry for drawing, scrolling and hit testing. */
final class OnlinePanelLayout {
  private int[] heights = new int[0], tops = new int[0];
  private int count, total;

  void reset(int count) {
    this.count = count;
    if (heights.length < count) {
      heights = new int[count];
      tops = new int[count];
    }
    Arrays.fill(heights, 0, count, OnlineUiStyle.ROW_HEIGHT);
    finish();
  }

  static int detailTop(int titleLines) {
    return 27 + 14 * titleLines;
  }

  void fit(int row, int titleLines, int detailLines) {
    heights[row] =
        Math.max(
            OnlineUiStyle.ROW_HEIGHT,
            detailTop(titleLines) + Math.max(0, detailLines - 1) * 14 + 5);
  }

  void finish() {
    total = 0;
    for (int i = 0; i < count; i++) {
      tops[i] = total;
      total += heights[i];
    }
  }

  int top(int row) {
    return tops[row];
  }

  int height(int row) {
    return heights[row];
  }

  int total() {
    return total;
  }

  int at(float y) {
    if (y < 0) return -1;
    for (int i = 0; i < count; i++) if (y < tops[i] + heights[i]) return i;
    return -1;
  }
}
