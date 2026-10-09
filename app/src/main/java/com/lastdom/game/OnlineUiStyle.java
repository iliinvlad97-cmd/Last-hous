package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/**
 * Shared radio typography, surfaces and buttons. Reusable commands retain only the last card fade.
 */
final class OnlineUiStyle {
  static final int ACTION_HEIGHT = 56, ROW_HEIGHT = 64;
  static final float TITLE = 15, BODY = 12, DETAIL = 11;
  static final Typeface REGULAR = Typeface.create("sans", Typeface.NORMAL);
  static final Typeface BOLD = Typeface.create("sans", Typeface.BOLD);
  private final GameView view;
  private final Paint paint = new Paint(3);
  private final List<Command> ghost = new ArrayList<>();
  private int count;
  private boolean recording;
  private float alpha = 1;

  private static final class Command {
    int type, color;
    float a, b, c, d, size;
    String text;
    boolean bold;
  }

  OnlineUiStyle(GameView view) {
    this.view = view;
  }

  void beginCard() {
    recording = true;
    count = 0;
    alpha = view.onlineWorld.state.cardOpacity;
  }

  void endCard() {
    recording = false;
    alpha = 1;
  }

  void clearGhost() {
    if (count == 0) return;
    count = 0;
    for (Command cmd : ghost) cmd.text = null;
  }

  private Command remember(
      int type,
      float a,
      float b,
      float c,
      float d,
      float size,
      int color,
      String text,
      boolean bold) {
    if (!recording) return null;
    if (count == ghost.size()) ghost.add(new Command());
    Command cmd = ghost.get(count++);
    cmd.type = type;
    cmd.a = a;
    cmd.b = b;
    cmd.c = c;
    cmd.d = d;
    cmd.size = size;
    cmd.color = color;
    cmd.text = text;
    cmd.bold = bold;
    return cmd;
  }

  private int faded(int color) {
    return OnlineWorldRenderer.alpha(color, Math.round((color >>> 24) * alpha));
  }

  void txt(Canvas c, String text, float x, float y, float size, int color) {
    text(c, text, x, y, size, color, false);
  }

  void bold(Canvas c, String text, float x, float y, float size, int color) {
    text(c, text, x, y, size, color, true);
  }

  private void text(Canvas c, String text, float x, float y, float size, int color, boolean bold) {
    color = faded(color);
    remember(0, x, y, 0, 0, size, color, text, bold);
    paint.setStyle(Paint.Style.FILL);
    paint.setTypeface(bold ? BOLD : REGULAR);
    paint.setTextSize(view.sy(size));
    paint.setColor(color);
    c.drawText(text, view.sy(x), view.sy(y), paint);
  }

  void box(Canvas c, float left, float top, float right, float bottom, int color, float radius) {
    color = faded(color);
    remember(1, left, top, right, bottom, radius, color, null, false);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(color);
    c.drawRoundRect(
        view.sy(left),
        view.sy(top),
        view.sy(right),
        view.sy(bottom),
        view.sy(radius),
        view.sy(radius),
        paint);
  }

  void clip(Canvas c, float left, float top, float right, float bottom) {
    remember(2, left, top, right, bottom, 0, 0, null, false);
    c.save();
    c.clipRect(view.sy(left), view.sy(top), view.sy(right), view.sy(bottom));
  }

  void restore(Canvas c) {
    remember(3, 0, 0, 0, 0, 0, 0, null, false);
    c.restore();
  }

  void bar(Canvas c, float left, float top, float right, float height, int value, int color) {
    box(c, left, top, right, top + height, Color.rgb(48, 56, 62), 3);
    box(
        c,
        left,
        top,
        left + (right - left) * Math.max(0, Math.min(100, value)) / 100f,
        top + height,
        color,
        3);
  }

  void surface(Canvas c, float top, float bottom) {
    box(c, 0, 0, 420, view.H / view.scale - 80, Color.argb(170, 5, 11, 17), 0);
    box(c, 20, top + 3, 404, bottom + 3, Color.argb(90, 0, 0, 0), 16);
    box(c, 18, top, 402, bottom, view.panel, 16);
    box(c, 34, top, 386, top + 2, Color.argb(140, 205, 148, 79), 2);
  }

  void button(
      Canvas c,
      float left,
      float top,
      float right,
      float bottom,
      String label,
      int color,
      boolean enabled) {
    box(c, left, top, right, bottom, enabled ? color : view.panel2, 10);
    box(
        c,
        left + 10,
        top + 1,
        right - 10,
        top + 2,
        enabled ? Color.argb(55, 255, 255, 255) : Color.argb(30, 255, 255, 255),
        1);
    view.p.setTypeface(BOLD);
    view.p.setTextSize(view.sy(BODY));
    float x = (left + right - view.p.measureText(label) / view.scale) / 2;
    bold(
        c,
        label,
        x,
        (top + bottom) / 2 + 4,
        BODY,
        enabled && color != view.panel2 ? view.bg : view.muted);
  }

  void wrap(List<String> output, String text, float size, float width, boolean bold) {
    view.p.setTypeface(bold ? BOLD : REGULAR);
    view.p.setTextSize(view.sy(size));
    String line = "";
    for (String word : text.split(" ")) {
      if (view.p.measureText(word) > view.sy(width)) {
        if (!line.isEmpty()) output.add(line);
        line = "";
        for (int offset = 0; offset < word.length(); ) {
          int end = word.offsetByCodePoints(offset, 1);
          String next = line + word.substring(offset, end);
          if (!line.isEmpty() && view.p.measureText(next) > view.sy(width)) {
            output.add(line);
            line = word.substring(offset, end);
          } else line = next;
          offset = end;
        }
        continue;
      }
      String next = line.isEmpty() ? word : line + " " + word;
      if (!line.isEmpty() && view.p.measureText(next) > view.sy(width)) {
        output.add(line);
        line = word;
      } else line = next;
    }
    if (!line.isEmpty()) output.add(line);
  }

  void drawGhost(Canvas c, float opacity) {
    alpha = Math.max(0, Math.min(1, opacity));
    for (int i = 0; i < count; i++) {
      Command cmd = ghost.get(i);
      switch (cmd.type) {
        case 0:
          text(c, cmd.text, cmd.a, cmd.b, cmd.size, cmd.color, cmd.bold);
          break;
        case 1:
          box(c, cmd.a, cmd.b, cmd.c, cmd.d, cmd.color, cmd.size);
          break;
        case 2:
          clip(c, cmd.a, cmd.b, cmd.c, cmd.d);
          break;
        case 3:
          restore(c);
          break;
        default:
          break;
      }
    }
    alpha = 1;
  }
}
