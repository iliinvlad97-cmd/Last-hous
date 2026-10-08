package com.lastdom.game;

import android.graphics.Canvas;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.List;

/** Shared readable, colored and scrollable body for all six existing room overlays. */
final class RoomEfficiencyRenderer {
  static final int NORMAL = 0, GOOD = 1, WARNING = 2, SECTION = 3, MUTED = 4;
  static final int FONT = 14, LINE = 20;

  static final class Row {
    final String text;
    final int tone;

    Row(String text, int tone) {
      this.text = text;
      this.tone = tone;
    }
  }

  private final GameView view;
  private List<Row> source, wrapped;
  private float scale;

  RoomEfficiencyRenderer(GameView view) {
    this.view = view;
  }

  void draw(Canvas c, RoomUpgradeLayout layout, List<Row> rows) {
    if (source != rows || scale != view.scale) {
      source = rows;
      scale = view.scale;
      wrapped = new ArrayList<>();
      for (Row row : rows) {
        view.p.setTypeface(
            Typeface.create("sans", row.tone == SECTION ? Typeface.BOLD : Typeface.NORMAL));
        view.p.setTextSize(view.sy(row.tone == SECTION ? 11 : FONT));
        String rest = row.text;
        while (view.p.measureText(rest) > view.sy(350)) {
          int end = rest.length();
          while (end > 1 && view.p.measureText(rest.substring(0, end)) > view.sy(350)) end--;
          int space = rest.lastIndexOf(' ', end);
          if (space > 0) end = space;
          wrapped.add(new Row(rest.substring(0, end), row.tone));
          rest = rest.substring(end).trim();
        }
        wrapped.add(new Row(rest, row.tone));
      }
    }
    RoomUpgradePanelController ui = view.roomUpgradePanel;
    ui.lineCount = wrapped.size();
    ui.scroll = Math.max(0, Math.min(ui.scroll, Math.max(0, wrapped.size() - layout.visibleLines)));
    for (int i = 0; i < layout.visibleLines && ui.scroll + i < wrapped.size(); i++) {
      Row row = wrapped.get(ui.scroll + i);
      float y = layout.top + 93 + i * LINE;
      int color =
          row.tone == GOOD
              ? view.good
              : row.tone == WARNING ? view.danger : row.tone == MUTED ? view.muted : view.text;
      if (row.tone == SECTION) {
        view.box(c, 28, y - 15, 392, y + 3, view.panel2, 4);
        view.bold(c, row.text, 34, y, 11, view.accent);
      } else view.txt(c, row.text, 34, y, FONT, color);
    }
    if (wrapped.size() > layout.visibleLines)
      view.txt(
          c,
          "↑ ↓ Прокрутите панель • "
              + (ui.scroll + 1)
              + "–"
              + Math.min(ui.scroll + layout.visibleLines, wrapped.size())
              + " / "
              + wrapped.size(),
          34,
          layout.actionTop - 10,
          10,
          view.muted);
  }
}
