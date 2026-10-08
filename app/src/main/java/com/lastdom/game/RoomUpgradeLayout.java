package com.lastdom.game;

/** One logical coordinate system for the room overlay, scrolling and builder choice hit areas. */
final class RoomUpgradeLayout {
  static final int ASSIGNMENT_ROW = 104;
  final float top, bottom, actionTop, secondaryTop, rowTop, pageY;
  final int capacity, visibleLines;

  RoomUpgradeLayout(float height) {
    top = Math.max(70, height - 650);
    bottom = height - 86;
    actionTop = bottom - 126;
    secondaryTop = bottom - 69;
    rowTop = top + 90;
    pageY = actionTop - 26;
    capacity = Math.max(1, (int) ((pageY - 16 - rowTop) / ASSIGNMENT_ROW));
    visibleLines = Math.max(1, (int) ((actionTop - 30 - (top + 93)) / RoomEfficiencyRenderer.LINE));
  }

  boolean action(float x, float y) {
    return x >= 30 && x <= 390 && y >= actionTop && y <= actionTop + 48;
  }

  boolean secondary(float y) {
    return y >= secondaryTop && y <= secondaryTop + 43;
  }

  float workerRowTop(boolean kitchen) {
    return rowTop + (kitchen ? 48 : 0);
  }

  int workerCapacity(boolean kitchen) {
    return Math.max(1, (int) ((pageY - 16 - workerRowTop(kitchen)) / ASSIGNMENT_ROW));
  }
}
