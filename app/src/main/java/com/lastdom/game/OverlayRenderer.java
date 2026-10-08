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
    if (view.game.selected < 0 || view.game.selected >= view.game.people.size()) return;
    Resident s = view.game.people.get(view.game.selected);
    dimForOverlay(c);
    float hh = view.H / view.scale, top = Math.max(285, hh - 405);
    view.box(c, 18, top, 402, hh - 78, Color.rgb(25, 29, 33), 18);
    view.bold(c, "×", 372, top + 31, 22, view.muted);
    view.residentRenderer.drawMiniPortrait(c, s, 52, top + 45, view.game.selected);
    view.bold(c, s.name, 78, top + 39, 19, view.text);
    view.txt(c, s.role + " • навык " + s.skill, 78, top + 58, 10, view.accent);
    view.txt(
        c,
        "Сейчас: "
            + (view.game.isOnExpedition(s)
                ? "В экспедиции"
                : view.residentRenderer.residentState(s, view.game.selected)),
        34,
        top + 91,
        11,
        view.text);
    view.txt(c, "Здоровье " + s.health + "%", 34, top + 119, 10, view.muted);
    view.bar(c, 34, top + 128, 386, 8, s.health, view.good);
    view.txt(c, "Голод " + s.hunger + "%", 34, top + 158, 10, view.muted);
    view.txt(c, "Усталость " + s.fatigue + "%", 190, top + 158, 10, view.muted);
    view.txt(c, "Мораль " + s.morale + "%", 34, top + 184, 10, view.muted);
    boolean away = view.game.isOnExpedition(s);
    view.box(c, 34, top + 211, 386, top + 257, away ? view.panel2 : view.accent, 11);
    view.bold(
        c,
        away ? "В ЭКСПЕДИЦИИ" : "СМЕНИТЬ РАБОТУ",
        116,
        top + 240,
        11,
        away ? view.muted : Color.rgb(30, 27, 23));
    view.box(c, 34, top + 266, 206, top + 309, view.panel2, 10);
    view.bold(c, "ОТДЫХ", 91, top + 293, 10, view.text);
    view.box(c, 214, top + 266, 386, top + 309, view.panel2, 10);
    view.bold(c, "ЗАКРЫТЬ", 264, top + 293, 10, view.text);
  }

  void drawRoomOverlay(Canvas c) {
    if (view.game.selectedRoom < 0 || view.game.selectedRoom >= 6) return;
    int i = view.game.selectedRoom;
    dimForOverlay(c);
    float hh = view.H / view.scale, top = Math.max(300, hh - 390);
    view.box(c, 18, top, 402, hh - 78, Color.rgb(25, 29, 33), 18);
    view.bold(c, "×", 372, top + 31, 22, view.muted);
    view.bold(c, view.game.rooms[i], 34, top + 37, 18, view.text);
    view.txt(
        c,
        "Уровень " + view.game.roomLevels[i] + " • состояние " + view.game.roomCondition[i] + "%",
        34,
        top + 58,
        10,
        view.game.roomCondition[i] < 40 ? view.danger : view.muted);
    view.bar(
        c,
        34,
        top + 73,
        386,
        8,
        view.game.roomCondition[i],
        view.game.roomCondition[i] < 40 ? view.danger : view.good);
    view.txt(c, "Работают: " + view.game.occupants(i), 34, top + 111, 11, view.text);
    view.wrap(c, view.game.roomBonus(i), 34, top + 139, 386, 10, view.muted, 15);
    int cost = 6 + view.game.roomLevels[i] * 4;
    if (view.game.buildingRoom == i) {
      view.box(c, 34, top + 202, 386, top + 250, view.panel2, 11);
      view.bold(
          c,
          "СТРОИТСЯ • " + view.game.formatBuild(view.game.buildRemaining),
          75,
          top + 232,
          11,
          view.good);
    } else {
      view.box(c, 34, top + 202, 386, top + 250, view.accent, 11);
      view.bold(c, "УЛУЧШИТЬ • " + cost + " МАТ.", 94, top + 232, 11, Color.rgb(30, 27, 23));
    }
    view.box(c, 34, top + 259, 206, top + 302, view.panel2, 10);
    view.bold(c, "НАЗНАЧИТЬ", 70, top + 286, 9, view.text);
    view.box(c, 214, top + 259, 386, top + 302, view.panel2, 10);
    view.bold(c, "ЗАКРЫТЬ", 264, top + 286, 10, view.text);
  }

  void drawResidentsOverlay(Canvas c) {
    dimForOverlay(c);
    float hh = view.H / view.scale, top = Math.max(250, hh - 470);
    view.box(c, 18, top, 402, hh - 78, Color.rgb(25, 29, 33), 18);
    view.bold(c, "ЖИТЕЛИ", 34, top + 38, 18, view.text);
    view.bold(c, "×", 372, top + 31, 22, view.muted);
    float y = top + 65;
    for (int i = 0; i < Math.min(6, view.game.people.size()); i++) {
      Resident s = view.game.people.get(i);
      view.box(c, 30, y, 390, y + 52, view.panel2, 10);
      view.residentRenderer.drawMiniPortrait(c, s, 52, y + 25, i);
      view.bold(c, s.name, 76, y + 23, 11, view.text);
      view.txt(
          c,
          s.role
              + " • "
              + (view.game.isOnExpedition(s)
                  ? "В экспедиции"
                  : view.residentRenderer.residentState(s, i)),
          76,
          y + 41,
          9,
          s.job.equals("Отдых") ? view.blue : view.good);
      y += 59;
    }
  }

  void drawRooms(Canvas c) {
    view.hudRenderer.drawHeader(c, "интерактивный разрез");
    view.shelterRenderer.drawCutaway(c, 75, true);
    view.hudRenderer.bottomBack(c);
  }

  void drawRoomDetail(Canvas c) {
    int i = view.game.selectedRoom;
    view.hudRenderer.drawHeader(c, view.game.rooms[i] + " • уровень " + view.game.roomLevels[i]);
    view.box(c, 20, 72, 400, 310, view.panel, 16);
    if (view.shelterBitmap != null) {
      int sw = view.shelterBitmap.getWidth(), sh = view.shelterBitmap.getHeight();
      int col = i % 2, row = i / 2;
      int sx0 = col == 0 ? 10 : 425, sx1 = col == 0 ? 420 : 842;
      int sy0 = (i / 2 == 0 ? 85 : (i / 2 == 1 ? 350 : 610)), sy1 = Math.min(sh, sy0 + 255);
      Rect src = new Rect(sx0, sy0, sx1, sy1);
      RectF dst = new RectF(view.sy(28), view.sy(82), view.sy(392), view.sy(300));
      c.drawBitmap(view.shelterBitmap, src, dst, view.p);
    }
    view.bold(c, "СОСТОЯНИЕ", 24, 340, 12, view.muted);
    view.bar(
        c,
        24,
        350,
        396,
        10,
        view.game.roomCondition[i],
        view.game.roomCondition[i] < 40 ? view.danger : view.good);
    view.txt(c, "Работают: " + view.game.occupants(i), 24, 390, 12, view.text);
    view.txt(c, "Назначение: " + view.game.roomJobs[i], 24, 414, 11, view.muted);
    view.wrap(c, view.game.roomBonus(i), 24, 447, 396, 11, view.muted, 17);
    int cost = 6 + view.game.roomLevels[i] * 4, time = 120 + view.game.roomLevels[i] * 90;
    if (view.game.buildingRoom == i) {
      view.box(c, 24, 505, 396, 563, view.panel2, 12);
      view.bold(c, "УЛУЧШЕНИЕ ИДЁТ", 42, 529, 12, view.good);
      view.txt(
          c, "Осталось " + view.game.formatBuild(view.game.buildRemaining), 42, 550, 11, view.text);
    } else {
      view.box(c, 24, 505, 396, 563, view.accent, 12);
      view.bold(c, "УЛУЧШИТЬ • " + cost + " МАТ.", 42, 529, 12, Color.rgb(30, 27, 23));
      view.txt(c, "Время: " + view.game.formatBuild(time), 42, 550, 10, Color.rgb(50, 42, 34));
    }
    view.box(c, 24, 577, 396, 629, view.panel2, 12);
    view.bold(c, "НАЗНАЧИТЬ ЖИТЕЛЯ", 42, 609, 12, view.text);
    view.hudRenderer.bottomBack(c);
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
    Resident s = view.game.people.get(view.game.selected);
    view.hudRenderer.drawHeader(c, s.name + " • " + s.role);
    view.box(c, 20, 90, 400, 350, view.panel, 16);
    view.bold(c, s.name, 45, 135, 25, view.text);
    view.txt(c, s.role + " • навык " + s.skill, 45, 160, 12, view.accent);
    view.txt(c, "Здоровье " + s.health + "%", 45, 205, 12, view.text);
    view.bar(c, 45, 215, 370, 9, s.health, view.good);
    view.txt(c, "Голод " + s.hunger + "%   Усталость " + s.fatigue + "%", 45, 255, 11, view.muted);
    view.txt(c, "Мораль " + s.morale + "%", 45, 285, 11, view.muted);
    view.txt(c, "Сейчас: " + s.job, 45, 320, 12, view.accent);
    view.box(c, 35, 390, 385, 448, view.accent, 12);
    view.bold(c, "ИЗМЕНИТЬ РАБОТУ", 105, 425, 13, Color.rgb(30, 27, 23));
    view.hudRenderer.bottomBack(c);
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
