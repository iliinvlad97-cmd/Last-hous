package com.lastdom.game;

import android.graphics.*;

/** Original header, resource strip and bottom navigation. */
final class HudRenderer {

  private final GameView view;

  HudRenderer(GameView view) {
    this.view = view;
  }

  void drawHeader(Canvas c, String sub) {
    view.bold(c, "ПОСЛЕДНИЙ ДОМ", 20, 30, 20, view.text);
    view.txt(c, sub, 20, 49, 11, view.muted);
    view.box(c, 250, 12, 400, 52, view.panel2, 9);
    view.bold(c, "Д" + view.game.day + "  " + view.game.clock(), 264, 31, 12, view.text);
    view.txt(
        c,
        view.game.phase(),
        264,
        46,
        9,
        view.game.phase().equals("НОЧЬ") ? view.blue : view.accent);
  }

  void drawResources(Canvas c) {
    String[] n = {"Еда", "Вода", "Энергия", "Материалы"};
    int[] v = {view.game.food, view.game.water, view.game.power, view.game.mats};
    String[] ic = {"F", "W", "E", "M"};
    for (int i = 0; i < 4; i++) {
      float x = 14 + i * 101;
      view.box(c, x, 66, x + 94, 110, Color.rgb(24, 29, 34), 9);
      view.p.setColor(
          i == 0
              ? Color.rgb(199, 157, 101)
              : i == 1
                  ? Color.rgb(78, 145, 184)
                  : i == 2 ? Color.rgb(225, 159, 59) : Color.rgb(142, 149, 151));
      c.drawCircle(view.sy(x + 16), view.sy(88), view.sy(10), view.p);
      view.bold(c, ic[i], x + 12, 92, 8, Color.WHITE);
      view.txt(c, n[i], x + 30, 81, 7, view.muted);
      view.bold(c, "" + v[i], x + 30, 101, 12, view.text);
    }
  }

  void drawNav(Canvas c) {
    float y = view.H / view.scale - 66;
    view.box(c, 16, y, 404, y + 50, view.panel, 12);
    String[] n = {
      "ДОМ", "ЖУРНАЛ", "КАРТА", "ЖИТЕЛИ", view.game.paused ? "▶" : "×" + view.game.speed
    };
    for (int i = 0; i < 5; i++) {
      float x = 18 + i * 77;
      if (i == 4) view.box(c, x + 2, y + 4, x + 73, y + 46, view.accent, 9);
      view.bold(c, n[i], x + 7, y + 30, 8, i == 4 ? Color.rgb(30, 27, 23) : view.text);
    }
  }

  void bottomBack(Canvas c) {
    float y = view.H / view.scale - 65;
    view.box(c, 20, y, 400, y + 48, view.panel2, 12);
    view.bold(c, "← НАЗАД", 35, y + 30, 11, view.text);
  }
}
