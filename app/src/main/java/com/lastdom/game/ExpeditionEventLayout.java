package com.lastdom.game;

/** Event body and fixed large choice hit areas use the same logical coordinates. */
final class ExpeditionEventLayout {
  static final float CHOICE_HEIGHT = 54, STEP = 62;
  final float footer, firstChoice;
  final int actions;

  ExpeditionEventLayout(CityMapLayout map, ExpeditionEvent event) {
    actions = event.effectsApplied ? 1 : ExpeditionEventConfig.actions(event.type).length;
    firstChoice =
        event.effectsApplied ? map.panelBottom - 66 : map.panelBottom - 24 - actions * STEP;
    // Reserve the actual button area plus room for the body's scroll hint.
    footer = map.panelBottom - firstChoice + MapPanelContent.LINE_HEIGHT;
  }

  float top(int index) {
    return firstChoice + index * STEP;
  }

  int hit(float x, float y) {
    if (x < 30 || x > 390) return -1;
    for (int i = 0; i < actions; i++) if (y >= top(i) && y <= top(i) + CHOICE_HEIGHT) return i;
    return -1;
  }
}
