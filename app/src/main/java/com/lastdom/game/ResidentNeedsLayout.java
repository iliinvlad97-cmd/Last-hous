package com.lastdom.game;

/** Shared logical coordinates for survival panels, fixed actions and scrolling. */
final class ResidentNeedsLayout {
  final float top, bottom, bodyTop, footer;
  final int capacity, lines;

  ResidentNeedsLayout(float height) {
    top = Math.max(70, height - 690);
    bottom = height - 86;
    bodyTop = top + 66;
    footer = bottom - 108;
    capacity = Math.max(1, (int) ((footer - bodyTop - 8) / 98));
    lines = Math.max(1, (int) ((footer - bodyTop - 12) / 21));
  }
}
