package com.lastdom.game;

/** Defense-only logical geometry, shared by drawing and touches. Footer stays above navigation. */
final class DefensePanelLayout {
  static final int ROW_GAP = 8;
  final float top, bottom, bodyTop, bodyBottom, actionTop, secondaryTop, rowTop, pageY;
  final int capacity, rowHeight;

  DefensePanelLayout(float height, float contentHeight, int rowHeight, int listHeaderHeight) {
    bottom = height - 86;
    top = Math.max(70, bottom - (78 + contentHeight + 132));
    bodyTop = top + 78;
    actionTop = bottom - 116;
    secondaryTop = bottom - 60;
    bodyBottom = actionTop - 16;
    rowTop = bodyTop + listHeaderHeight;
    pageY = bodyBottom - 12;
    this.rowHeight = rowHeight;
    capacity = Math.max(1, (int) ((pageY - 16 - rowTop + ROW_GAP) / rowHeight));
  }

  int maxScroll(float contentHeight) {
    return Math.max(0, (int) Math.ceil(contentHeight - (bodyBottom - bodyTop)));
  }

  boolean action(float x, float y) {
    return x >= 30 && x <= 390 && y >= actionTop && y <= actionTop + 48;
  }

  boolean secondary(float y) {
    return y >= secondaryTop && y <= secondaryTop + 44;
  }
}
