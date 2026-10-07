package com.lastdom.game;

import android.graphics.*;
import java.util.ArrayList;

/** Resident drawing and visual caches, preserving positions, slots and animation calculations. */
final class ResidentRenderer {

  private final GameView view;

  ResidentRenderer(GameView view) {
    this.view = view;
  }

  void drawFullSceneResidents(Canvas c, float top, float bottom) {
    int[] slots = {0, 0, 0, 0, 0, 0};
    for (int i = 0; i < view.game.people.size() && i < 32; i++) {
      Resident s = view.game.people.get(i);
      if (!s.alive || s.job.equals("Экспедиция")) continue;
      int ri = view.game.homeRoomFor(s);
      if (ri < 0) continue;
      int slot = Math.min(2, slots[ri]++);
      float[] q = ShelterGeometry.fullSceneResidentPos(ri, slot, top, bottom);
      view.game.residentX[i] = q[0];
      view.game.residentY[i] = q[1];
      view.game.residentVisualRoom[i] = ri;
      drawDynamicResident(c, s, q[0], q[1], i);
    }
    view.game.residentVisualReady = true;
  }

  void ensureResidentVisuals() {
    if (view.game.residentVisualReady) return;
    int[] slots = {0, 0, 0, 0, 0, 0};
    for (int i = 0; i < Math.min(view.game.people.size(), 32); i++) {
      int ri = view.game.homeRoomFor(view.game.people.get(i));
      if (ri < 0) ri = 5;
      float[] q = ShelterGeometry.shelterResidentPos(ri, Math.min(2, slots[ri]++));
      view.game.residentX[i] = q[0];
      view.game.residentY[i] = q[1];
      view.game.residentVisualRoom[i] = ri;
    }
    view.game.residentVisualReady = true;
  }

  String residentState(Resident s, int i) {
    if (!s.alive) return "Погиб";
    if (s.job.equals("Экспедиция")) return "В городе";
    int ri = view.game.homeRoomFor(s);
    if (i < 32
        && view.game.residentVisualReady
        && (Math.abs(view.game.residentX[i] - ShelterGeometry.shelterResidentPos(ri, 0)[0]) > 35
            || view.game.residentVisualRoom[i] != ri)) return "Идёт: " + view.game.rooms[ri];
    if (s.health < 45) return "Ранен • " + s.job;
    if (s.fatigue > 82) return "Измотан • " + s.job;
    if (s.job.equals("Отдых")) return "Отдыхает";
    if (s.job.equals("Лечение")) return "Лечит";
    if (s.job.equals("Охрана")) return "На посту";
    if (s.job.equals("Ремонт")) return "Ремонтирует";
    return "Работает: " + s.job;
  }

  void drawLivingResidentsOverShelter(Canvas c) {
    ensureResidentVisuals();
    int[] slots = {0, 0, 0, 0, 0, 0};
    boolean moving = false;
    for (int i = 0; i < view.game.people.size() && i < 32; i++) {
      Resident s = view.game.people.get(i);
      int ri = view.game.homeRoomFor(s);
      if (ri < 0) continue;
      int slot = Math.min(2, slots[ri]++);
      float[] target = ShelterGeometry.shelterResidentPos(ri, slot);
      float dx = target[0] - view.game.residentX[i],
          dy = target[1] - view.game.residentY[i],
          dist = (float) Math.sqrt(dx * dx + dy * dy);
      if (dist > 2) {
        float step = Math.min(3.8f, dist);
        view.game.residentX[i] += dx / dist * step;
        view.game.residentY[i] += dy / dist * step;
        moving = true;
      } else {
        view.game.residentX[i] = target[0];
        view.game.residentY[i] = target[1];
        view.game.residentVisualRoom[i] = ri;
      }
      float anim = (float) Math.sin(System.currentTimeMillis() / 190.0 + i * 1.7);
      float yy =
          view.game.residentY[i]
              + (dist > 2
                  ? Math.abs(anim) * 1.8f
                  : s.job.equals("Отдых") ? anim * .6f : Math.abs(anim) * .8f);
      drawDynamicResident(c, s, view.game.residentX[i], yy, i);
      if (dist > 2) {
        view.p.setColor(Color.argb(180, 20, 22, 25));
        c.drawRoundRect(
            view.sy(view.game.residentX[i] - 17),
            view.sy(yy - 34),
            view.sy(view.game.residentX[i] + 17),
            view.sy(yy - 25),
            view.sy(4),
            view.sy(4),
            view.p);
      }
    }
    if (moving) view.postInvalidateDelayed(45);
  }

  void drawDynamicResident(Canvas c, Resident s, float x, float y, int index) {
    // Stage 4: expressive resident sprites drawn in layers (shadow/body/head/gear/prop/status).
    long now = System.currentTimeMillis();
    float phase = (now / 150.0f) + index * 1.37f;
    int ri = view.game.homeRoomFor(s);
    float[] target = ri >= 0 ? ShelterGeometry.shelterResidentPos(ri, 0) : new float[] {x, y};
    float dist =
        (float) Math.sqrt((target[0] - x) * (target[0] - x) + (target[1] - y) * (target[1] - y));
    boolean walking = dist > 4;
    boolean resting = s.job.equals("Отдых");
    boolean hurt = s.health < 45;
    float walk = walking ? (float) Math.sin(phase) : 0f, breathe = (float) Math.sin(phase * .35f);
    float lean = resting ? 0 : (s.job.equals("Ремонт") || s.job.equals("Материалы") ? 2.2f : 1.0f);
    int skin = Color.rgb(199, 158, 126);
    int cloth =
        index == 0
            ? Color.rgb(52, 72, 82)
            : index == 1
                ? Color.rgb(188, 194, 187)
                : index == 2
                    ? Color.rgb(65, 78, 62)
                    : index == 3 ? Color.rgb(108, 73, 55) : Color.rgb(66, 78, 88);
    if (hurt) cloth = Color.rgb(92, 72, 70);
    // floor shadow
    view.p.setColor(Color.argb(105, 0, 0, 0));
    c.drawOval(view.sy(x - 12), view.sy(y + 17), view.sy(x + 12), view.sy(y + 22), view.p);
    // legs animate while walking; resting pose is wider and lower
    view.p.setStrokeCap(Paint.Cap.ROUND);
    view.p.setStrokeWidth(view.sy(3.2f));
    view.p.setColor(Color.rgb(42, 45, 47));
    float leg = walking ? walk * 5 : 0;
    float hipY = y + 12 + (resting ? 2 : 0);
    c.drawLine(view.sy(x - 4), view.sy(hipY), view.sy(x - 5 - leg), view.sy(y + 23), view.p);
    c.drawLine(view.sy(x + 4), view.sy(hipY), view.sy(x + 5 + leg), view.sy(y + 23), view.p);
    // boots
    view.p.setStrokeWidth(view.sy(3.8f));
    c.drawLine(
        view.sy(x - 7 - leg), view.sy(y + 23), view.sy(x - 3 - leg), view.sy(y + 23), view.p);
    c.drawLine(
        view.sy(x + 3 + leg), view.sy(y + 23), view.sy(x + 7 + leg), view.sy(y + 23), view.p);
    // torso + jacket seam
    view.p.setColor(cloth);
    c.drawRoundRect(
        view.sy(x - 9 + lean),
        view.sy(y - 5 + breathe * .3f),
        view.sy(x + 9 + lean),
        view.sy(y + 14),
        view.sy(4),
        view.sy(4),
        view.p);
    view.p.setColor(Color.argb(90, 255, 255, 255));
    view.p.setStrokeWidth(view.sy(.8f));
    c.drawLine(view.sy(x + lean), view.sy(y - 3), view.sy(x + lean), view.sy(y + 11), view.p);
    // head / hair
    view.p.setColor(skin);
    c.drawCircle(view.sy(x + lean * .45f), view.sy(y - 11 + breathe * .2f), view.sy(6.8f), view.p);
    view.p.setColor(
        index == 1
            ? Color.rgb(83, 55, 39)
            : index == 3 ? Color.rgb(55, 38, 30) : Color.rgb(43, 36, 32));
    c.drawArc(
        view.sy(x - 7 + lean * .45f),
        view.sy(y - 19),
        view.sy(x + 7 + lean * .45f),
        view.sy(y - 5),
        180,
        185,
        true,
        view.p);
    // arms: job-specific pose
    view.p.setStrokeWidth(view.sy(3));
    view.p.setColor(cloth);
    if (walking) {
      c.drawLine(
          view.sy(x - 7 + lean), view.sy(y), view.sy(x - 12 - walk * 3), view.sy(y + 9), view.p);
      c.drawLine(
          view.sy(x + 7 + lean), view.sy(y), view.sy(x + 12 + walk * 3), view.sy(y + 8), view.p);
    } else if (resting) {
      c.drawLine(view.sy(x - 6), view.sy(y), view.sy(x - 9), view.sy(y + 10), view.p);
      c.drawLine(view.sy(x + 6), view.sy(y), view.sy(x + 9), view.sy(y + 10), view.p);
    } else {
      float work = (float) Math.sin(phase * 1.5f) * 2;
      c.drawLine(
          view.sy(x - 7 + lean), view.sy(y), view.sy(x - 13 + lean), view.sy(y + 7 + work), view.p);
      c.drawLine(
          view.sy(x + 7 + lean), view.sy(y), view.sy(x + 13 + lean), view.sy(y + 6 - work), view.p);
    }
    // role/job props make residents readable without labels.
    if (s.role.equals("Врач") || s.job.equals("Лечение")) {
      view.p.setColor(Color.WHITE);
      view.p.setStrokeWidth(view.sy(1.8f));
      c.drawLine(
          view.sy(x + lean - 3), view.sy(y + 4), view.sy(x + lean + 3), view.sy(y + 4), view.p);
      c.drawLine(view.sy(x + lean), view.sy(y + 1), view.sy(x + lean), view.sy(y + 7), view.p);
    }
    if (s.job.equals("Ремонт") || s.role.equals("Механик")) {
      view.p.setColor(Color.rgb(180, 184, 185));
      view.p.setStrokeWidth(view.sy(2));
      c.drawLine(
          view.sy(x + 12 + lean), view.sy(y + 5), view.sy(x + 17 + lean), view.sy(y), view.p);
      c.drawCircle(view.sy(x + 17 + lean), view.sy(y), view.sy(2), view.p);
    }
    if (s.job.equals("Охрана") || s.role.equals("Охрана")) {
      view.p.setColor(Color.rgb(38, 42, 40));
      c.drawRoundRect(
          view.sy(x + 8),
          view.sy(y - 1),
          view.sy(x + 17),
          view.sy(y + 3),
          view.sy(1.5f),
          view.sy(1.5f),
          view.p);
    }
    // fatigue / injury feedback
    if (s.fatigue > 80) {
      view.p.setColor(Color.argb(210, 210, 220, 225));
      view.p.setTextSize(view.sy(6));
      view.p.setTypeface(Typeface.DEFAULT_BOLD);
      c.drawText("Z", view.sy(x + 10), view.sy(y - 22), view.p);
    }
    if (hurt) {
      view.p.setColor(Color.rgb(225, 225, 215));
      c.drawRoundRect(
          view.sy(x - 7),
          view.sy(y - 13),
          view.sy(x + 1),
          view.sy(y - 10),
          view.sy(1),
          view.sy(1),
          view.p);
      view.p.setColor(view.danger);
      c.drawCircle(view.sy(x + 10), view.sy(y - 18), view.sy(3.2f), view.p);
    } else {
      view.p.setColor(resting ? view.blue : view.good);
      c.drawCircle(view.sy(x + 10), view.sy(y - 18), view.sy(2.7f), view.p);
    }
    view.p.setStrokeCap(Paint.Cap.BUTT);
  }

  int shelterResidentAt(float x, float y) {
    ensureResidentVisuals();
    for (int i = 0; i < view.game.people.size() && i < 32; i++) {
      Resident s = view.game.people.get(i);
      if (!s.alive || s.job.equals("Экспедиция")) continue;
      if (x >= view.game.residentX[i] - 17
          && x <= view.game.residentX[i] + 17
          && y >= view.game.residentY[i] - 25
          && y <= view.game.residentY[i] + 27) return i;
    }
    return -1;
  }

  void drawResidentDock(Canvas c, float top) {
    float cardW = 72, gap = 6, left = 14;
    for (int i = 0; i < Math.min(5, view.game.people.size()); i++) {
      Resident s = view.game.people.get(i);
      float x = left + i * (cardW + gap);
      view.box(c, x, top, x + cardW, top + 62, view.panel, 10);
      drawMiniPortrait(c, s, x + 17, top + 19, i);
      view.bold(c, s.name, x + 7, top + 42, 8, view.text);
      view.txt(
          c, shortJob(s.job), x + 7, top + 55, 7, s.job.equals("Отдых") ? view.blue : view.good);
    }
  }

  void drawMiniPortrait(Canvas c, Resident s, float x, float y, int i) {
    view.p.setColor(Color.rgb(51, 55, 58));
    c.drawCircle(view.sy(x), view.sy(y), view.sy(12), view.p);
    view.p.setColor(Color.rgb(199, 158, 126));
    c.drawCircle(view.sy(x), view.sy(y - 3), view.sy(5), view.p);
    int cloth =
        i == 0
            ? Color.rgb(55, 70, 78)
            : i == 1
                ? Color.rgb(186, 190, 180)
                : i == 2
                    ? Color.rgb(65, 73, 62)
                    : i == 3 ? Color.rgb(94, 70, 57) : Color.rgb(65, 74, 82);
    view.p.setColor(cloth);
    c.drawRoundRect(
        view.sy(x - 7),
        view.sy(y + 2),
        view.sy(x + 7),
        view.sy(y + 10),
        view.sy(4),
        view.sy(4),
        view.p);
  }

  String shortJob(String j) {
    return j.length() > 9 ? j.substring(0, 9) : j;
  }

  void drawOccupantSprites(Canvas c, int ri, float l, float t, float r, float b, boolean large) {
    ArrayList<Resident> here = new ArrayList<>();
    for (Resident s : view.game.people)
      if (s.alive && s.job.equals(view.game.roomJobs[ri]) && !s.job.equals("Экспедиция"))
        here.add(s);
    int n = Math.min(3, here.size());
    for (int j = 0; j < n; j++) {
      float x = r - 18 - j * (large ? 25 : 18), y = b - (large ? 48 : 38);
      drawPersonSprite(c, here.get(j), x, y, large ? 1f : .72f);
    }
  }

  void drawPersonSprite(Canvas c, Resident s, float x, float y, float z) {
    int cloth =
        s.role.equals("Врач")
            ? Color.rgb(178, 184, 177)
            : s.role.equals("Охрана")
                ? Color.rgb(74, 84, 72)
                : s.role.equals("Сборщик") ? Color.rgb(102, 82, 61) : Color.rgb(76, 91, 96);
    view.p.setColor(Color.rgb(196, 158, 125));
    c.drawCircle(view.sy(x), view.sy(y - 15 * z), view.sy(5 * z), view.p);
    view.p.setColor(cloth);
    c.drawRoundRect(
        view.sy(x - 6 * z),
        view.sy(y - 10 * z),
        view.sy(x + 6 * z),
        view.sy(y + 7 * z),
        view.sy(3 * z),
        view.sy(3 * z),
        view.p);
    view.p.setStrokeWidth(view.sy(2 * z));
    view.p.setColor(Color.rgb(35, 33, 31));
    c.drawLine(
        view.sy(x - 3 * z), view.sy(y + 7 * z), view.sy(x - 5 * z), view.sy(y + 16 * z), view.p);
    c.drawLine(
        view.sy(x + 3 * z), view.sy(y + 7 * z), view.sy(x + 5 * z), view.sy(y + 16 * z), view.p);
  }

  void drawPortrait(Canvas c, Resident s, float x, float y, float rad) {
    view.p.setColor(Color.rgb(43, 48, 51));
    c.drawCircle(view.sy(x), view.sy(y), view.sy(rad), view.p);
    int skin = Color.rgb(190, 148, 116);
    view.p.setColor(skin);
    c.drawCircle(view.sy(x), view.sy(y - rad * .15f), view.sy(rad * .43f), view.p);
    int hair =
        s.name.equals("Мария") || s.name.equals("Анна")
            ? Color.rgb(49, 34, 28)
            : Color.rgb(45, 39, 34);
    view.p.setColor(hair);
    c.drawArc(
        view.sy(x - rad * .46f),
        view.sy(y - rad * .65f),
        view.sy(x + rad * .46f),
        view.sy(y + rad * .12f),
        180,
        180,
        true,
        view.p);
    view.p.setColor(
        s.role.equals("Врач")
            ? Color.rgb(164, 174, 169)
            : s.role.equals("Охрана") ? Color.rgb(72, 83, 69) : Color.rgb(88, 76, 63));
    c.drawRoundRect(
        view.sy(x - rad * .55f),
        view.sy(y + rad * .25f),
        view.sy(x + rad * .55f),
        view.sy(y + rad * .9f),
        view.sy(4),
        view.sy(4),
        view.p);
    view.p.setColor(Color.rgb(28, 25, 23));
    c.drawCircle(view.sy(x - rad * .15f), view.sy(y - rad * .12f), view.sy(1.4f), view.p);
    c.drawCircle(view.sy(x + rad * .15f), view.sy(y - rad * .12f), view.sy(1.4f), view.p);
  }

  void drawPeople(Canvas c, float y) {
    view.bold(c, "ЖИТЕЛИ", 20, y, 16, view.text);
    int max = Math.min(view.game.people.size(), 5);
    for (int i = 0; i < max; i++) {
      Resident s = view.game.people.get(i);
      float t = y + 12 + i * 50;
      view.box(c, 20, t, 400, t + 44, s.alive ? view.panel : Color.rgb(48, 31, 32), 9);
      drawPortrait(c, s, 43, t + 22, 16);
      view.bold(c, s.name, 68, t + 18, 12, view.text);
      view.txt(
          c,
          s.alive ? s.role + " • " + s.job : "ПОГИБ",
          68,
          t + 34,
          9,
          s.alive ? view.muted : view.danger);
      view.bar(c, 292, t + 10, 382, 5, s.health, view.good);
      view.txt(c, "❤ " + s.health + "   ☺ " + s.morale, 292, t + 33, 8, view.muted);
    }
  }
}
