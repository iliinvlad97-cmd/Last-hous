package com.lastdom.game;

import android.graphics.*;

/** The approved scene, lighting, room effects and retained legacy cutaway rendering. */
final class ShelterRenderer {

  private final GameView view;

  ShelterRenderer(GameView view) {
    this.view = view;
  }

  void drawMain(Canvas c) {
    view.hudRenderer.drawHeader(c, GameView.VERSION_LABEL);
    view.hudRenderer.drawResources(c);

    // v0.9.5.1: one continuous world image. No old shelter layer and no separate city strip.
    float hh = view.H / view.scale;
    float sceneTop = 116f;
    float sceneBottom = Math.max(610f, hh - 72f);
    if (view.fullSceneBitmap != null) {
      Rect src = new Rect(0, 0, view.fullSceneBitmap.getWidth(), view.fullSceneBitmap.getHeight());
      RectF dst = new RectF(view.sy(8), view.sy(sceneTop), view.sy(412), view.sy(sceneBottom));
      c.drawBitmap(view.fullSceneBitmap, src, dst, view.p);
    }
    // The exit is part of the world. Only an invisible tap target is added here.
    drawFullSceneState(c, sceneTop, sceneBottom);
    view.residentRenderer.drawFullSceneResidents(c, sceneTop, sceneBottom);
    view.hudRenderer.drawNav(c);
    if (view.game.overlay == 1) view.overlayRenderer.drawResidentOverlay(c);
    else if (view.game.overlay == 2) view.overlayRenderer.drawRoomOverlay(c);
    else if (view.game.overlay == 3) view.overlayRenderer.drawResidentsOverlay(c);
  }

  void drawFullSceneState(Canvas c, float top, float bottom) {
    float h = bottom - top;
    int hour = view.game.gameMinute / 60;
    if (hour >= 22 || hour < 6) {
      view.p.setColor(Color.argb(52, 5, 12, 28));
      c.drawRect(view.sy(8), view.sy(top), view.sy(412), view.sy(bottom), view.p);
    }
    if (view.game.power <= 0) {
      view.p.setColor(Color.argb(90, 0, 3, 10));
      c.drawRect(view.sy(8), view.sy(top + h * .23f), view.sy(412), view.sy(bottom), view.p);
      view.bold(c, "НЕТ ЭЛЕКТРИЧЕСТВА", 142, top + h * .25f, 8, view.danger);
    }
    if (view.game.incidentRoom >= 0) {
      float[] q = ShelterGeometry.fullSceneRoomRect(view.game.incidentRoom, top, bottom);
      float pulse = (float) (.5 + .5 * Math.sin(System.currentTimeMillis() / 220.0));
      view.p.setStyle(Paint.Style.STROKE);
      view.p.setStrokeWidth(view.sy(2));
      view.p.setColor(Color.argb((int) (120 + 100 * pulse), 235, 85, 60));
      c.drawRoundRect(
          view.sy(q[0]),
          view.sy(q[1]),
          view.sy(q[2]),
          view.sy(q[3]),
          view.sy(7),
          view.sy(7),
          view.p);
      view.p.setStyle(Paint.Style.FILL);
    }
  }

  void drawDynamicRoomState(Canvas c) {
    // Dynamic overlays make the shelter react to room state instead of behaving like one flat JPEG.
    float[][] r = {
      {12, 150, 210, 280},
      {210, 150, 408, 280},
      {12, 280, 210, 410},
      {210, 280, 408, 410},
      {12, 410, 210, 535},
      {210, 410, 408, 535}
    };
    for (int i = 0; i < 6; i++) {
      float[] q = r[i];
      int cond = view.game.roomCondition[i];
      if (cond < 70) {
        view.p.setColor(Color.argb(Math.min(125, (70 - cond) * 3), 90, 18, 12));
        c.drawRect(view.sy(q[0]), view.sy(q[1]), view.sy(q[2]), view.sy(q[3]), view.p);
      }
      if (view.game.buildingRoom == i) {
        view.p.setColor(Color.argb(105, 220, 150, 55));
        c.drawRect(
            view.sy(q[0]),
            view.sy(q[3] - 10),
            view.sy(
                q[0]
                    + (q[2] - q[0])
                        * (1f
                            - Math.min(
                                1f,
                                view.game.buildRemaining
                                    / (float) (120 + view.game.roomLevels[i] * 90)))),
            view.sy(q[3]),
            view.p);
      }
      // Small status plate; no duplicate room title/HUD.
      if (cond < 85) {
        view.box(c, q[2] - 45, q[1] + 7, q[2] - 7, q[1] + 23, Color.argb(190, 20, 22, 25), 6);
        view.bold(c, cond + "%", q[2] - 38, q[1] + 19, 7, cond < 45 ? view.danger : view.accent);
      }
    }
    if (view.game.power <= 0) {
      view.p.setColor(Color.argb(115, 0, 4, 12));
      c.drawRect(view.sy(8), view.sy(118), view.sy(412), view.sy(568), view.p);
      view.txt(c, "НЕТ ЭЛЕКТРИЧЕСТВА", 142, 139, 8, view.danger);
    }
  }

  void drawTimeOfDayLighting(Canvas c) {
    int h = view.game.gameMinute / 60;
    int overlay;
    if (h >= 6 && h < 10) overlay = Color.argb(34, 245, 181, 103); // warm morning
    else if (h >= 10 && h < 18) overlay = Color.argb(10, 230, 238, 245); // daylight
    else if (h >= 18 && h < 22) overlay = Color.argb(48, 221, 116, 53); // sunset
    else overlay = Color.argb(112, 8, 18, 38); // night
    view.p.setColor(overlay);
    c.drawRect(view.sy(8), view.sy(118), view.sy(412), view.sy(568), view.p);
    // At night, powered rooms retain a warm pool of light.
    if ((h >= 18 || h < 7) && view.game.power > 0) {
      float[][] centers = {{111, 215}, {309, 215}, {111, 345}, {309, 345}, {111, 472}, {309, 472}};
      for (int i = 0; i < 6; i++) {
        float cx = centers[i][0], cy = centers[i][1];
        RadialGradient g =
            new RadialGradient(
                view.sy(cx),
                view.sy(cy),
                view.sy(78),
                Color.argb(65, 255, 188, 82),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP);
        view.p.setShader(g);
        c.drawCircle(view.sy(cx), view.sy(cy), view.sy(78), view.p);
        view.p.setShader(null);
      }
    }
  }

  void drawRoomActivityEffects(Canvas c) {
    // Activity is tied to actual assignments, so rooms visibly "work".
    int pulse = (view.game.gameMinute % 4);
    for (int ri = 0; ri < 6; ri++) {
      int workers = 0;
      for (Resident s : view.game.people)
        if (s.alive && view.game.homeRoomFor(s) == ri && !s.job.equals("Отдых")) workers++;
      if (workers == 0) continue;
      float[][] centers = {{111, 215}, {309, 215}, {111, 345}, {309, 345}, {111, 472}, {309, 472}};
      float x = centers[ri][0], y = centers[ri][1];
      if (ri == 0) { // generator vibration / electric pulse
        view.p.setStyle(Paint.Style.STROKE);
        view.p.setStrokeWidth(view.sy(2));
        view.p.setColor(Color.argb(180, 235, 167, 62));
        c.drawCircle(view.sy(x - 42), view.sy(y + 17), view.sy(9 + pulse * 2), view.p);
        view.p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 3; k++) {
          view.p.setColor(Color.argb(180, 255, 205, 88));
          c.drawCircle(
              view.sy(x + 30 + k * 5), view.sy(y - 18 - k * 3 + pulse), view.sy(1.6f), view.p);
        }
      } else if (ri == 1) { // kitchen steam
        view.p.setStyle(Paint.Style.STROKE);
        view.p.setStrokeWidth(view.sy(2));
        view.p.setColor(Color.argb(115, 235, 235, 225));
        for (int k = 0; k < 3; k++) {
          float xx = x - 18 + k * 14;
          c.drawArc(
              view.sy(xx - 5),
              view.sy(y - 24 - pulse * 2),
              view.sy(xx + 5),
              view.sy(y - 6 - pulse * 2),
              180,
              180,
              false,
              view.p);
        }
        view.p.setStyle(Paint.Style.FILL);
      } else if (ri == 2) { // med monitor
        view.p.setColor(Color.argb(210, 86, 190, 113));
        c.drawCircle(view.sy(x + 58), view.sy(y - 27), view.sy(3 + pulse * .5f), view.p);
        view.p.setStrokeWidth(view.sy(1.5f));
        c.drawLine(view.sy(x + 38), view.sy(y - 12), view.sy(x + 45), view.sy(y - 12), view.p);
        c.drawLine(view.sy(x + 45), view.sy(y - 12), view.sy(x + 49), view.sy(y - 19), view.p);
        c.drawLine(view.sy(x + 49), view.sy(y - 19), view.sy(x + 54), view.sy(y - 7), view.p);
        c.drawLine(view.sy(x + 54), view.sy(y - 7), view.sy(x + 61), view.sy(y - 12), view.p);
      } else if (ri == 3) { // workshop sparks
        for (int k = 0; k < 5; k++) {
          float a = (k * 71 + pulse * 29) * 0.01745f;
          float rr = 8 + k * 2;
          view.p.setColor(Color.argb(220, 255, 165, 55));
          c.drawCircle(
              view.sy(x + 22 + (float) Math.cos(a) * rr),
              view.sy(y + 8 + (float) Math.sin(a) * rr),
              view.sy(1.5f),
              view.p);
        }
      } else if (ri == 4) { // guard alert sweep
        view.p.setStyle(Paint.Style.STROKE);
        view.p.setStrokeWidth(view.sy(2));
        view.p.setColor(Color.argb(150, 210, 93, 64));
        c.drawArc(
            view.sy(x - 50),
            view.sy(y - 35),
            view.sy(x + 50),
            view.sy(y + 35),
            200 + pulse * 10,
            45,
            false,
            view.p);
        view.p.setStyle(Paint.Style.FILL);
      }
    }
    if (view.game.buildingRoom >= 0) {
      float[][] centers = {{111, 215}, {309, 215}, {111, 345}, {309, 345}, {111, 472}, {309, 472}};
      float x = centers[view.game.buildingRoom][0], y = centers[view.game.buildingRoom][1];
      for (int k = 0; k < 4; k++) {
        view.p.setColor(Color.argb(210, 255, 177, 64));
        c.drawCircle(view.sy(x - 20 + k * 12), view.sy(y + 25 - (k % 2) * 8), view.sy(2), view.p);
      }
    }
  }

  void drawEmergencyEffects(Canvas c) {
    float t = (System.currentTimeMillis() % 4000L) / 1000f;
    // Short circuit: smoke, sparks and emergency flicker live inside the generator room.
    if (view.game.event && view.game.eventTitle.equals("КОРОТКОЕ ЗАМЫКАНИЕ")) {
      float flick = (float) ((Math.sin(t * 18) + 1) * .5);
      view.p.setColor(Color.argb((int) (45 + 75 * flick), 210, 48, 28));
      c.drawRect(view.sy(12), view.sy(150), view.sy(210), view.sy(280), view.p);
      for (int i = 0; i < 5; i++) {
        float rise = (t * 22 + i * 19) % 72;
        float x = 83 + i * 9 + (float) Math.sin(t * 2 + i) * 6;
        float y = 248 - rise;
        view.p.setColor(Color.argb(Math.max(20, 115 - (int) rise), 105, 105, 100));
        c.drawCircle(view.sy(x), view.sy(y), view.sy(6 + i * .8f), view.p);
      }
      for (int i = 0; i < 5; i++) {
        float a = t * 5 + i * 1.25f;
        view.p.setColor(Color.argb(220, 255, 184, 55));
        c.drawCircle(
            view.sy(128 + (float) Math.cos(a) * 18),
            view.sy(226 + (float) Math.sin(a) * 12),
            view.sy(1.8f),
            view.p);
      }
    }
    // Medical incident: pulsing monitor and a patient silhouette on the bed.
    if (view.game.event && view.game.eventTitle.equals("БОЛЕЗНЬ")) {
      float pulse = (float) ((Math.sin(t * 7) + 1) * .5);
      view.p.setColor(Color.argb(170, 70, 210, 115));
      c.drawCircle(view.sy(180), view.sy(319), view.sy(3 + 2 * pulse), view.p);
      view.p.setColor(Color.rgb(186, 145, 115));
      c.drawCircle(view.sy(80), view.sy(355), view.sy(5), view.p);
      view.p.setColor(Color.rgb(80, 86, 82));
      c.drawRoundRect(
          view.sy(85), view.sy(350), view.sy(119), view.sy(361), view.sy(4), view.sy(4), view.p);
    }
    // Threat at the entrance: moving red searchlight / silhouettes outside barricades.
    if (view.game.event
        && (view.game.eventTitle.equals("МАРОДЁРЫ")
            || view.game.eventTitle.equals("ЧУЖАК У ДВЕРИ"))) {
      float sweep = (float) Math.sin(t * 1.7f);
      view.p.setColor(Color.argb(55, 220, 55, 40));
      Path q = new Path();
      q.moveTo(view.sy(176), view.sy(445));
      q.lineTo(view.sy(48 + sweep * 22), view.sy(410));
      q.lineTo(view.sy(92 + sweep * 22), view.sy(520));
      q.close();
      c.drawPath(q, view.p);
      view.p.setColor(Color.argb(190, 22, 22, 22));
      for (int i = 0; i < (view.game.eventTitle.equals("МАРОДЁРЫ") ? 3 : 1); i++) {
        float xx = 42 + i * 22;
        c.drawCircle(view.sy(xx), view.sy(464), view.sy(5), view.p);
        c.drawRect(view.sy(xx - 4), view.sy(469), view.sy(xx + 4), view.sy(486), view.p);
      }
    }
    // Construction now looks active rather than only showing a progress bar.
    if (view.game.buildingRoom >= 0) {
      float[][] cc = {{111, 215}, {309, 215}, {111, 345}, {309, 345}, {111, 472}, {309, 472}};
      float x = cc[view.game.buildingRoom][0], y = cc[view.game.buildingRoom][1];
      for (int i = 0; i < 4; i++) {
        float phase = (t * 25 + i * 17) % 38;
        view.p.setColor(Color.argb(210, 255, 174, 55));
        c.drawLine(
            view.sy(x - 18 + i * 10),
            view.sy(y + 12),
            view.sy(x - 24 + i * 10 - phase * .15f),
            view.sy(y + 12 - phase * .45f),
            view.p);
      }
    }
    view.postInvalidateDelayed(80);
  }

  int sky() {
    int h = view.game.gameMinute / 60;
    if (h >= 6 && h < 12) return Color.rgb(78, 75, 70);
    if (h < 18) return Color.rgb(72, 82, 88);
    if (h < 22) return Color.rgb(66, 47, 51);
    return Color.rgb(18, 23, 34);
  }

  void drawAtmosphere(Canvas c, float top, float bottom) {
    int h = view.game.gameMinute / 60;
    if (h >= 22 || h < 6) {
      view.p.setColor(Color.rgb(214, 218, 207));
      c.drawCircle(view.sy(345), view.sy(top + 32), view.sy(15), view.p);
      view.p.setColor(sky());
      c.drawCircle(view.sy(351), view.sy(top + 27), view.sy(14), view.p);
    } else {
      view.p.setColor(Color.rgb(205, 164, 91));
      c.drawCircle(view.sy(345), view.sy(top + 32), view.sy(13), view.p);
    }
    view.p.setStrokeWidth(view.sy(1));
    view.p.setColor(Color.argb(65, 190, 205, 215));
    for (int i = 0; i < 18; i++) {
      float x = 18 + ((i * 67 + view.game.gameMinute * 3) % 385),
          y = top + ((i * 43 + view.game.gameMinute * 2) % Math.max(1, (int) (bottom - top)));
      c.drawLine(view.sy(x), view.sy(y), view.sy(x - 5), view.sy(y + 13), view.p);
    }
  }

  void drawCutaway(Canvas c, float top, boolean large) {
    float left = 20, right = 400, height = large ? 500 : 305;
    view.box(c, left, top, right, top + height, Color.rgb(20, 22, 25), 14);
    view.p.setColor(sky());
    c.drawRect(
        view.sy(left + 7), view.sy(top + 7), view.sy(right - 7), view.sy(top + height - 7), view.p);
    drawRuins(c, left + 7, top + 7, right - 7, top + height - 7);
    float bx = large ? 42 : 55,
        by = top + (large ? 38 : 28),
        bw = large ? 336 : 310,
        rh = large ? 136 : 82;
    view.p.setColor(Color.rgb(48, 43, 39));
    c.drawRect(
        view.sy(bx - 8), view.sy(by - 12), view.sy(bx + bw + 8), view.sy(by + rh * 3 + 6), view.p);
    for (int i = 0; i < 6; i++) {
      int row = i / 2, col = i % 2;
      float l = bx + col * bw / 2, t = by + row * rh, r = l + bw / 2, b = t + rh;
      drawRoom(c, i, l, t, r, b, large);
    }
    view.p.setColor(Color.rgb(28, 27, 25));
    c.drawRect(
        view.sy(bx + bw / 2 - 3),
        view.sy(by),
        view.sy(bx + bw / 2 + 3),
        view.sy(by + rh * 3),
        view.p);
    view.txt(
        c, "Нажмите на комнату • жители работают внутри", 34, top + height - 13, 10, view.muted);
  }

  void drawRuins(Canvas c, float l, float t, float r, float b) {
    view.p.setColor(Color.rgb(30, 32, 34));
    for (int i = 0; i < 7; i++) {
      float x = l + i * 62;
      float hh = 24 + (i % 3) * 17;
      c.drawRect(view.sy(x), view.sy(b - hh), view.sy(Math.min(r, x + 45)), view.sy(b), view.p);
      for (int w = 0; w < 2; w++) {
        view.p.setColor(Color.rgb(69, 61, 48));
        c.drawRect(
            view.sy(x + 8 + w * 18),
            view.sy(b - hh + 9),
            view.sy(x + 13 + w * 18),
            view.sy(b - hh + 15),
            view.p);
        view.p.setColor(Color.rgb(30, 32, 34));
      }
    }
  }

  void drawRoom(Canvas c, int i, float l, float t, float r, float b, boolean large) {
    int base = view.game.roomCondition[i] < 40 ? Color.rgb(70, 43, 40) : Color.rgb(62, 57, 50);
    view.p.setColor(base);
    c.drawRect(view.sy(l + 2), view.sy(t + 2), view.sy(r - 2), view.sy(b - 2), view.p);
    view.p.setColor(Color.rgb(38, 35, 32));
    c.drawRect(view.sy(l + 2), view.sy(t + 2), view.sy(r - 2), view.sy(t + 7), view.p);
    view.p.setColor(Color.rgb(103, 84, 63));
    c.drawRect(view.sy(l + 2), view.sy(b - 12), view.sy(r - 2), view.sy(b - 2), view.p);
    boolean lit =
        view.game.power > 0 && (view.game.gameMinute / 60 >= 18 || view.game.gameMinute / 60 < 7);
    if (lit) {
      view.p.setColor(Color.argb(75, 240, 185, 82));
      c.drawCircle(view.sy((l + r) / 2), view.sy(t + 18), view.sy((r - l) * .34f), view.p);
      view.p.setColor(Color.rgb(224, 174, 79));
      c.drawCircle(view.sy((l + r) / 2), view.sy(t + 11), view.sy(4), view.p);
    }
    drawRoomDetails(c, i, l, t, r, b, large);
    drawRoomIcon(c, i, (l + r) / 2, t + (b - t) * .49f, large ? 1.25f : .8f);
    view.residentRenderer.drawOccupantSprites(c, i, l, t, r, b, large);
    view.bold(c, view.game.rooms[i], l + 8, b - 27, large ? 11 : 9, view.text);
    view.txt(
        c,
        "УР." + view.game.roomLevels[i] + " • " + view.game.roomCondition[i] + "%",
        l + 8,
        b - 14,
        large ? 9 : 8,
        view.game.roomCondition[i] < 40 ? view.danger : view.muted);
    if (view.game.buildingRoom == i) {
      view.p.setColor(Color.argb(175, 20, 20, 20));
      c.drawRect(view.sy(l + 2), view.sy(t + 2), view.sy(r - 2), view.sy(b - 2), view.p);
      view.bold(c, "🔨 СТРОЙКА", l + 12, t + 25, 10, view.good);
      view.txt(c, view.game.formatBuild(view.game.buildRemaining), l + 12, t + 42, 9, view.text);
    }
  }

  void drawRoomIcon(Canvas c, int i, float x, float y, float z) {
    view.p.setStyle(Paint.Style.FILL);
    if (i == 0) {
      view.p.setColor(Color.rgb(70, 83, 84));
      c.drawRoundRect(
          view.sy(x - 31 * z),
          view.sy(y - 17 * z),
          view.sy(x + 31 * z),
          view.sy(y + 19 * z),
          view.sy(4 * z),
          view.sy(4 * z),
          view.p);
      view.p.setColor(Color.rgb(31, 35, 35));
      for (int q = -1; q <= 1; q++)
        c.drawCircle(view.sy(x + q * 17 * z), view.sy(y + 2 * z), view.sy(7 * z), view.p);
      view.p.setColor(view.accent);
      c.drawCircle(view.sy(x), view.sy(y + 2 * z), view.sy(4 * z), view.p);
      view.p.setColor(Color.rgb(113, 96, 65));
      c.drawRect(
          view.sy(x - 35 * z),
          view.sy(y + 19 * z),
          view.sy(x + 35 * z),
          view.sy(y + 23 * z),
          view.p);
    } else if (i == 1) {
      view.p.setColor(Color.rgb(110, 82, 59));
      c.drawRect(view.sy(x - 34 * z), view.sy(y), view.sy(x + 34 * z), view.sy(y + 18 * z), view.p);
      view.p.setColor(Color.rgb(72, 76, 73));
      c.drawRect(view.sy(x - 25 * z), view.sy(y - 24 * z), view.sy(x + 1 * z), view.sy(y), view.p);
      view.p.setColor(Color.rgb(137, 55, 42));
      c.drawCircle(view.sy(x + 18 * z), view.sy(y - 6 * z), view.sy(7 * z), view.p);
      view.p.setColor(Color.rgb(180, 166, 127));
      for (int q = 0; q < 3; q++)
        c.drawRect(
            view.sy(x - 30 * z + q * 12 * z),
            view.sy(y - 8 * z),
            view.sy(x - 24 * z + q * 12 * z),
            view.sy(y - 2 * z),
            view.p);
    } else if (i == 2) {
      view.p.setColor(Color.rgb(164, 162, 149));
      c.drawRect(view.sy(x - 32 * z), view.sy(y), view.sy(x + 32 * z), view.sy(y + 15 * z), view.p);
      view.p.setColor(Color.rgb(205, 201, 181));
      c.drawRect(
          view.sy(x - 27 * z), view.sy(y - 8 * z), view.sy(x + 12 * z), view.sy(y + 3 * z), view.p);
      view.p.setColor(view.danger);
      c.drawRect(
          view.sy(x + 18 * z),
          view.sy(y - 25 * z),
          view.sy(x + 24 * z),
          view.sy(y - 7 * z),
          view.p);
      c.drawRect(
          view.sy(x + 12 * z),
          view.sy(y - 19 * z),
          view.sy(x + 30 * z),
          view.sy(y - 13 * z),
          view.p);
    } else if (i == 3) {
      view.p.setColor(Color.rgb(108, 74, 51));
      c.drawRect(view.sy(x - 35 * z), view.sy(y), view.sy(x + 35 * z), view.sy(y + 12 * z), view.p);
      view.p.setColor(Color.rgb(124, 128, 126));
      c.drawRect(
          view.sy(x - 22 * z),
          view.sy(y - 15 * z),
          view.sy(x + 20 * z),
          view.sy(y - 7 * z),
          view.p);
      view.p.setColor(Color.rgb(68, 69, 67));
      c.drawCircle(view.sy(x + 23 * z), view.sy(y - 11 * z), view.sy(8 * z), view.p);
      view.p.setStrokeWidth(view.sy(2 * z));
      c.drawLine(
          view.sy(x - 27 * z),
          view.sy(y - 19 * z),
          view.sy(x - 12 * z),
          view.sy(y - 5 * z),
          view.p);
    } else if (i == 4) {
      view.p.setColor(Color.rgb(96, 78, 56));
      for (int q = -1; q <= 1; q++)
        c.drawRect(
            view.sy(x - 37 * z),
            view.sy(y + q * 12 * z),
            view.sy(x + 37 * z),
            view.sy(y + (q * 12 + 7) * z),
            view.p);
      view.p.setColor(Color.rgb(74, 74, 69));
      for (int q = -1; q <= 1; q++)
        c.drawCircle(view.sy(x + q * 25 * z), view.sy(y + 3 * z), view.sy(3 * z), view.p);
    } else {
      view.p.setColor(Color.rgb(100, 79, 66));
      c.drawRect(view.sy(x - 35 * z), view.sy(y), view.sy(x + 35 * z), view.sy(y + 16 * z), view.p);
      view.p.setColor(Color.rgb(174, 160, 136));
      c.drawRect(
          view.sy(x - 30 * z), view.sy(y - 9 * z), view.sy(x - 5 * z), view.sy(y + 1 * z), view.p);
      c.drawRect(
          view.sy(x + 4 * z), view.sy(y - 9 * z), view.sy(x + 29 * z), view.sy(y + 1 * z), view.p);
      view.p.setColor(Color.rgb(68, 58, 52));
      c.drawRect(
          view.sy(x - 36 * z),
          view.sy(y + 16 * z),
          view.sy(x + 36 * z),
          view.sy(y + 20 * z),
          view.p);
    }
  }

  void drawRoomDetails(Canvas c, int i, float l, float t, float r, float b, boolean large) {
    float z = large ? 1f : .65f;
    view.p.setStrokeWidth(view.sy(1));
    if (i == 0) {
      view.p.setColor(Color.rgb(36, 38, 39));
      for (int q = 0; q < 4; q++)
        c.drawLine(
            view.sy(l + 12),
            view.sy(t + 16 + q * 9),
            view.sy(r - 12),
            view.sy(t + 16 + q * 9),
            view.p);
      view.p.setColor(Color.rgb(194, 133, 50));
      for (int q = 0; q < 3; q++)
        c.drawCircle(view.sy(l + 18 + q * 13), view.sy(t + 15), view.sy(2.5f * z), view.p);
    } else if (i == 1) {
      view.p.setColor(Color.rgb(73, 92, 65));
      c.drawRect(view.sy(l + 8), view.sy(b - 27), view.sy(r - 8), view.sy(b - 15), view.p);
      view.p.setColor(Color.rgb(170, 147, 103));
      for (int q = 0; q < 5; q++)
        c.drawRect(
            view.sy(l + 12 + q * 13),
            view.sy(t + 15),
            view.sy(l + 18 + q * 13),
            view.sy(t + 24),
            view.p);
    } else if (i == 2) {
      view.p.setColor(Color.rgb(188, 196, 190));
      for (int q = 0; q < 3; q++)
        c.drawRect(
            view.sy(l + 10 + q * 19),
            view.sy(t + 14),
            view.sy(l + 23 + q * 19),
            view.sy(t + 25),
            view.p);
      view.p.setColor(Color.rgb(125, 150, 150));
      c.drawRect(view.sy(r - 34), view.sy(t + 13), view.sy(r - 12), view.sy(t + 35), view.p);
    } else if (i == 3) {
      view.p.setColor(Color.rgb(130, 112, 87));
      for (int q = 0; q < 6; q++) {
        float xx = l + 12 + q * 12;
        c.drawLine(view.sy(xx), view.sy(t + 13), view.sy(xx), view.sy(t + 31), view.p);
      }
      view.p.setColor(Color.rgb(54, 55, 53));
      c.drawRect(view.sy(l + 8), view.sy(b - 30), view.sy(r - 8), view.sy(b - 20), view.p);
    } else if (i == 4) {
      view.p.setColor(Color.rgb(89, 65, 46));
      for (int q = 0; q < 5; q++)
        c.drawLine(
            view.sy(l + 8),
            view.sy(t + 15 + q * 8),
            view.sy(r - 8),
            view.sy(t + 5 + q * 8),
            view.p);
      view.p.setColor(Color.rgb(132, 72, 52));
      c.drawCircle(view.sy(r - 18), view.sy(t + 18), view.sy(4), view.p);
    } else {
      view.p.setColor(Color.rgb(74, 79, 72));
      c.drawRect(
          view.sy((l + r) / 2 - 12),
          view.sy(t + 12),
          view.sy((l + r) / 2 + 12),
          view.sy(t + 39),
          view.p);
      view.p.setColor(Color.rgb(122, 102, 82));
      c.drawRect(view.sy(l + 9), view.sy(b - 28), view.sy(l + 29), view.sy(b - 16), view.p);
      c.drawRect(view.sy(r - 29), view.sy(b - 28), view.sy(r - 9), view.sy(b - 16), view.p);
    }
    if (view.game.roomCondition[i] < 55) {
      view.p.setColor(Color.argb(170, 40, 34, 31));
      view.p.setStrokeWidth(view.sy(2));
      c.drawLine(view.sy(l + 15), view.sy(t + 8), view.sy(l + 31), view.sy(t + 25), view.p);
      c.drawLine(view.sy(l + 31), view.sy(t + 25), view.sy(l + 23), view.sy(t + 40), view.p);
    }
    if (view.game.power <= 0) {
      view.p.setColor(Color.argb(125, 0, 0, 0));
      c.drawRect(view.sy(l + 2), view.sy(t + 2), view.sy(r - 2), view.sy(b - 2), view.p);
    }
  }

  void drawIncidentMarker(Canvas c) {
    if (!view.game.event || view.game.incidentRoom < 0) return;
    float[][] centers = {{109, 214}, {311, 214}, {109, 344}, {311, 344}, {109, 474}, {311, 474}};
    float x = centers[view.game.incidentRoom][0], y = centers[view.game.incidentRoom][1];
    float pulse = (float) ((Math.sin(System.currentTimeMillis() / 180.0) + 1) * .5);
    view.p.setStyle(Paint.Style.STROKE);
    view.p.setStrokeWidth(view.sy(2.5f));
    view.p.setColor(
        Color.argb(180, view.danger >> 16 & 255, view.danger >> 8 & 255, view.danger & 255));
    c.drawCircle(view.sy(x), view.sy(y), view.sy(24 + pulse * 8), view.p);
    view.p.setStyle(Paint.Style.FILL);
    view.box(c, x - 13, y - 13, x + 13, y + 13, view.danger, 13);
    view.bold(c, "!", x - 3.5f, y + 6, 16, Color.WHITE);
  }
}
