package com.lastdom.game;

/** Fixed 56-unit actions remain above navigation; text and participant lists scroll. */
final class StoryPanelLayout {
  final float top, bottom, contentTop, contentBottom, primaryTop, closeTop;

  StoryPanelLayout(float height, boolean choice) {
    this(height, choice, -1);
  }

  StoryPanelLayout(float height, boolean choice, int contentHeight) {
    float defaultTop = Math.max(8, Math.min(70, height - 340));
    float maxBottom = height - 84;
    if (contentHeight < 0) {
      top = defaultTop;
      bottom = maxBottom;
    } else {
      float panelHeight = Math.min(maxBottom - defaultTop, Math.max(270, contentHeight + 194));
      top = Math.max(defaultTop, (maxBottom - panelHeight) / 2);
      bottom = top + panelHeight;
    }
    closeTop = bottom - 56;
    primaryTop = closeTop - 62;
    contentTop = top + 62;
    contentBottom = primaryTop - 8;
  }
}
