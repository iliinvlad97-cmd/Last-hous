package com.lastdom.game;

import android.graphics.*;

/** Original in-scene overlays and secondary panels. */
final class OverlayRenderer {

  private final GameView view;

  OverlayRenderer(GameView view) {
    this.view = view;
  }

  void dimForOverlay(Canvas c) {
    view.p.setColor(Color.argb(175, 0, 0, 0));
    c.drawRect(0, 0, view.W, view.H, view.p);
  }

  void drawResidentOverlay(Canvas c) {
    view.residentNeedsRenderer.draw(c, false);
  }

  void drawRoomOverlay(Canvas c) {
    view.roomUpgradeRenderer.draw(c);
  }

  void drawResidentsOverlay(Canvas c) {
    view.residentNeedsRenderer.draw(c, true);
  }

  void drawRooms(Canvas c) {
    view.hudRenderer.drawHeader(c, "интерактивный разрез");
    view.shelterRenderer.drawCutaway(c, 75, true);
    view.hudRenderer.bottomBack(c);
  }

  void drawRoomDetail(Canvas c) {
    view.roomUpgradeRenderer.draw(c);
  }

  void drawMap(Canvas c) {
    view.cityMapRenderer.draw(c);
  }

  void drawMapNode(Canvas c, float x, float y, String name, boolean seen, int risk) {
    view.p.setColor(
        seen
            ? (risk > 40 ? Color.rgb(106, 62, 56) : Color.rgb(78, 84, 75))
            : Color.rgb(45, 47, 48));
    c.drawCircle(view.sy(x), view.sy(y), view.sy(22), view.p);
    view.p.setStyle(Paint.Style.STROKE);
    view.p.setStrokeWidth(view.sy(2));
    view.p.setColor(seen ? view.accent : view.muted);
    c.drawCircle(view.sy(x), view.sy(y), view.sy(22), view.p);
    view.p.setStyle(Paint.Style.FILL);
    if (name.equals("ДОМ")) {
      view.p.setColor(Color.rgb(135, 111, 79));
      c.drawRect(view.sy(x - 10), view.sy(y - 7), view.sy(x + 10), view.sy(y + 10), view.p);
      Path q = new Path();
      q.moveTo(view.sy(x - 14), view.sy(y - 7));
      q.lineTo(view.sy(x), view.sy(y - 18));
      q.lineTo(view.sy(x + 14), view.sy(y - 7));
      q.close();
      c.drawPath(q, view.p);
    }
    view.bold(
        c, name, x - Math.min(35, name.length() * 3.2f), y - 28, 8, seen ? view.text : view.muted);
  }

  void drawLocationDialog(Canvas c, int li) {}

  void drawSurvivor(Canvas c) {
    view.residentNeedsRenderer.draw(c, false);
  }

  void drawJournal(Canvas c) {
    view.hudRenderer.drawHeader(c, "журнал событий");
    float y = 90;
    for (int i = 0; i < Math.min(view.game.log.size(), 10); i++) {
      view.wrap(c, view.game.log.get(i), 24, y, 396, 10, i == 0 ? view.text : view.muted, 15);
      y += 48;
    }
    view.hudRenderer.bottomBack(c);
  }

  void drawEvent(Canvas c) {
    float hh = view.H / view.scale, t = Math.max(455, hh - 310), b = hh - 84;
    view.p.setColor(Color.argb(75, 0, 0, 0));
    c.drawRect(0, view.sy(t - 18), view.W, view.H, view.p);
    view.box(c, 18, t, 402, b, Color.rgb(24, 27, 32), 18);
    view.txt(c, "СОБЫТИЕ В УБЕЖИЩЕ", 38, t + 28, 9, view.accent);
    view.bold(c, view.game.eventTitle, 38, t + 55, 17, view.text);
    view.wrap(c, view.game.eventText, 38, t + 82, 382, 11, view.muted, 16);
    float by = b - 98;
    view.box(c, 36, by, 384, by + 39, view.accent, 10);
    view.bold(c, view.game.eventChoices[0], 52, by + 25, 11, Color.rgb(30, 27, 23));
    view.box(c, 36, by + 48, 384, by + 87, view.panel2, 10);
    view.bold(c, view.game.eventChoices[1], 52, by + 73, 11, view.text);
  }

  void drawJobMenu(Canvas c) {
    view.p.setColor(Color.argb(205, 0, 0, 0));
    c.drawRect(0, 0, view.W, view.H, view.p);
    float t = 110;
    view.box(c, 30, t, 390, t + 410, view.panel, 16);
    view.bold(c, "НАЗНАЧИТЬ РАБОТУ", 50, t + 35, 17, view.text);
    for (int i = 0; i < view.game.jobs.length; i++) {
      float y = t + 55 + i * 45;
      view.box(c, 48, y, 372, y + 36, view.panel2, 8);
      view.bold(c, view.game.jobs[i], 64, y + 24, 11, view.text);
    }
  }

  void drawGameOver(Canvas c) {
    view.p.setColor(Color.argb(230, 0, 0, 0));
    c.drawRect(0, 0, view.W, view.H, view.p);
    float hh = view.H / view.scale;
    view.bold(c, "ПОСЛЕДНИЙ ДОМ ПАЛ", 54, hh / 2 - 60, 22, view.text);
    view.txt(c, "Вы продержались " + view.game.day + " дней", 110, hh / 2 - 25, 13, view.muted);
    view.box(c, 55, hh / 2 + 25, 365, hh / 2 + 82, view.accent, 14);
    view.bold(c, "НАЧАТЬ ЗАНОВО", 132, hh / 2 + 60, 13, Color.rgb(30, 27, 23));
  }
}
