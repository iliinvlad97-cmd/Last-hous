package com.lastdom.game;

import android.graphics.Canvas;
import android.graphics.Typeface;

/** Shared compact resident cards for ordinary work and room-building assignments. */
final class ResidentAssignmentRenderer {
  private final GameView view;

  ResidentAssignmentRenderer(GameView view) {
    this.view = view;
  }

  void row(
      Canvas c, Resident r, float top, int room, String job, boolean building, boolean assigned) {
    RoomAssignmentController controller = view.game.roomAssignmentController;
    view.box(c, 30, top, 390, top + RoomUpgradeLayout.ASSIGNMENT_ROW - 8, view.panel2, 9);
    view.residentRenderer.drawMiniPortrait(c, r, 50, top + 24, view.game.people.indexOf(r));
    line(c, r.name + " • " + r.role, top + 18, 12, view.text, true);
    line(c, "Сейчас: " + r.job, top + 34, 11, view.muted, false);
    line(
        c,
        "Здоровье " + r.health + "% • усталость " + r.fatigue + "%",
        top + 50,
        11,
        r.health < SurvivalConfig.TREAT_START || r.fatigue >= SurvivalConfig.REST_START
            ? view.danger
            : view.text,
        false);
    line(c, controller.profession(r, room, job, building, false), top + 66, 10, view.good, false);
    String action;
    int color;
    if (assigned) {
      action = "УЖЕ НАЗНАЧЕН" + (controller.removable(r) ? " • снять на отдых" : " • просмотр");
      color = view.accent;
    } else if (controller.availableCategory(r) == RoomAssignmentController.Category.FREE) {
      action = "СВОБОДЕН • " + (building ? "назначить строителем" : "назначить");
      color = view.good;
    } else {
      int from = view.game.homeRoomFor(r);
      action = "ДОСТУПЕН ДЛЯ ПЕРЕВОДА • " + (from < 0 ? r.job : view.game.rooms[from]);
      color = view.accent;
    }
    line(c, action, top + 84, 10, color, true);
  }

  private void line(Canvas c, String text, float y, int size, int color, boolean bold) {
    view.p.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
    view.p.setTextSize(view.sy(size));
    int end = text.length();
    while (end > 0
        && view.p.measureText(text.substring(0, end) + (end < text.length() ? "…" : ""))
            > view.sy(304)) end--;
    String fitted = text.substring(0, end) + (end < text.length() ? "…" : "");
    if (bold) view.bold(c, fitted, 74, y, size, color);
    else view.txt(c, fitted, 74, y, size, color);
  }
}
