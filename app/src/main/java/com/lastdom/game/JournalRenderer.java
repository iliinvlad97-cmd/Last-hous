package com.lastdom.game;

import android.graphics.Canvas;
import java.util.*;

/** Compact pages of existing log entries and durable expedition reports. */
final class JournalRenderer {
  private final GameView view;

  JournalRenderer(GameView view) {
    this.view = view;
  }

  void draw(Canvas c) {
    view.hudRenderer.drawHeader(c, "ЖУРНАЛ СОБЫТИЙ");
    JournalController journal = view.journal;
    JournalLayout layout = new JournalLayout(view.H / view.scale);
    int pages = journal.pages(layout);
    journal.page = Math.max(0, Math.min(pages - 1, journal.page));
    tab(c, 24, 138, "СОБЫТИЯ", !journal.reports);
    tab(c, 146, 304, "ОТЧЁТЫ ОТРЯДОВ", journal.reports);
    tab(c, 312, 396, "СЮЖЕТ", false);
    List<Expedition> reports = journal.completedReports();
    List<String> events = journal.eventEntries();
    int count = journal.reports ? reports.size() : events.size();
    if (count == 0)
      view.txt(
          c,
          journal.reports ? "Завершённых экспедиций пока нет" : "Журнал пока пуст",
          24,
          160,
          12,
          view.muted);
    for (int row = 0; row < layout.capacity; row++) {
      int index = journal.page * layout.capacity + row;
      if (index >= count) break;
      float y = layout.rowTop(row);
      view.box(c, 24, y, 396, y + 76, view.panel, 10);
      if (journal.reports) {
        Expedition e = reports.get(index);
        MapLocation target = view.game.expeditionController.location(e.locationId);
        view.wrap(c, target.name, 36, y + 19, 384, 12, view.text, 15);
        view.txt(
            c,
            e.type == Expedition.Type.RECON
                ? (e.recon.success ? "РАЙОН ИССЛЕДОВАН" : "РАЗВЕДКА НЕУДАЧНА")
                : "ОТРЯД ВЕРНУЛСЯ",
            36,
            y + 51,
            10,
            e.type == Expedition.Type.RECON && !e.recon.success ? view.accent : view.good);
        view.txt(
            c,
            "День " + (e.completedMinute / 1440 + 1) + " • Открыть отчёт ›",
            36,
            y + 68,
            10,
            view.muted);
      } else {
        String[] lines = events.get(index).split("\n");
        for (int line = 0; line < lines.length; line++)
          view.txt(c, lines[line], 36, y + 19 + line * 15, 11, view.text);
      }
    }
    view.txt(c, "‹  НАЗАД", 30, layout.pagerY + 4, 11, view.muted);
    view.txt(c, (journal.page + 1) + " / " + pages, 190, layout.pagerY + 4, 11, view.text);
    view.txt(c, "ДАЛЕЕ  ›", 326, layout.pagerY + 4, 11, view.muted);
    view.hudRenderer.bottomBack(c);
  }

  private void tab(Canvas c, float left, float right, String text, boolean active) {
    view.box(c, left, 86, right, 126, active ? view.accent : view.panel2, 9);
    view.bold(c, text, left + 12, 111, 11, active ? view.bg : view.text);
  }
}
