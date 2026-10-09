package com.lastdom.game;

/** Fixed 56-unit actions remain above navigation; text and participant lists scroll. */
final class StoryPanelLayout {
  final float top, bottom, contentTop, contentBottom, primaryTop, closeTop;

  StoryPanelLayout(float height, boolean choice) {
    top = Math.max(8, Math.min(70, height - 340));
    bottom = height - 84;
    closeTop = bottom - 56;
    primaryTop = closeTop - 62;
    contentTop = top + 62;
    contentBottom = primaryTop - 8;
  }
}
