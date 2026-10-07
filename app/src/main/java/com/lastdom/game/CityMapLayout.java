package com.lastdom.game;

/** One transform for map artwork, markers, future route endpoints and their touch targets. */
final class CityMapLayout {
  static final float SHELTER_X = .50f, SHELTER_Y = .83f;
  final float left = 16f, right = 404f, top = 70f, bottom;
  final float panelTop, panelBottom;

  CityMapLayout(float logicalHeight) {
    bottom = Math.max(top + 180f, logicalHeight - 80f);
    panelTop = Math.max(top + 8f, logicalHeight - 400f);
    panelBottom = logicalHeight - 86f;
  }

  float x(float normalized) {
    return left + (right - left) * normalized;
  }

  float y(float normalized) {
    return top + (bottom - top) * normalized;
  }

  boolean hits(float touchX, float touchY, float mapX, float mapY) {
    float dx = touchX - x(mapX), dy = touchY - y(mapY);
    return dx * dx + dy * dy <= 34f * 34f;
  }

  boolean hitsPreparation(float x, float y) {
    return x >= 34f && x <= 386f && y >= panelBottom - 66f && y <= panelBottom - 24f;
  }
}
