package com.lastdom.game;

import java.util.*;

/** Browsing history never reapplies completion effects. */
final class JournalController {
  private final GameView view;
  boolean reports;
  int page;

  JournalController(GameView view) {
    this.view = view;
  }

  List<Expedition> completedReports() {
    List<Expedition> result = new ArrayList<>();
    for (int i = view.game.expeditions.size() - 1; i >= 0; i--) {
      Expedition e = view.game.expeditions.get(i);
      if (e.state() == Expedition.State.COMPLETED) result.add(e);
    }
    return result;
  }

  List<String> eventEntries() {
    List<String> entries = new ArrayList<>();
    view.p.setTextSize(view.sy(11));
    view.p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
    for (String entry : view.game.log) {
      List<String> lines = new ArrayList<>();
      String line = "";
      for (String word : entry.split("\\s+")) {
        String candidate = line.isEmpty() ? word : line + " " + word;
        if (!line.isEmpty() && view.p.measureText(candidate) > view.sy(348)) {
          lines.add(line);
          line = word;
        } else line = candidate;
      }
      if (!line.isEmpty()) lines.add(line);
      for (int i = 0; i < lines.size(); i += 4)
        entries.add(String.join("\n", lines.subList(i, Math.min(i + 4, lines.size()))));
    }
    return entries;
  }

  int pages(JournalLayout layout) {
    int count = reports ? completedReports().size() : eventEntries().size();
    return Math.max(1, (count + layout.capacity - 1) / layout.capacity);
  }

  void touch(float x, float y) {
    float height = view.H / view.scale;
    JournalLayout layout = new JournalLayout(height);
    if (y > height - 80) {
      view.game.screen = GameView.HOME;
      return;
    }
    if (y >= 86 && y <= 126 && x >= 24 && x <= 396) {
      reports = x >= 210;
      page = 0;
    } else if (y >= layout.pagerY - 20 && y <= layout.pagerY + 20) {
      page = Math.max(0, Math.min(pages(layout) - 1, page + (x < 210 ? -1 : 1)));
    } else if (reports && x >= 24 && x <= 396) {
      List<Expedition> entries = completedReports();
      int row = (int) ((y - JournalLayout.TOP) / JournalLayout.ROW_HEIGHT);
      int index = page * layout.capacity + row;
      if (y >= JournalLayout.TOP
          && row >= 0
          && row < layout.capacity
          && index < entries.size()
          && y <= layout.rowTop(row) + 76) view.cityMap.openExpedition(entries.get(index));
    }
  }
}
