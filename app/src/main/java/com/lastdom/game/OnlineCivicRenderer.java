package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/** Read-only radio sections with 56-unit actions, 64-unit rows and fixed navigation. */
final class OnlineCivicRenderer {
  private final GameView view;
  private final List<List<String>> detailLines = new ArrayList<>();
  private final List<List<String>> titleLines = new ArrayList<>();
  private int revision = -1;
  private float scale;

  OnlineCivicRenderer(GameView view) {
    this.view = view;
  }

  void draw(Canvas c, OnlineWorldGeometry g) {
    OnlineCivicPanelController panel = view.onlineWorld.civicPanel;
    List<OnlineCivicPanelController.Row> rows = panel.rows();
    if (revision != view.onlineWorld.panelRevision || scale != view.scale) {
      revision = view.onlineWorld.panelRevision;
      scale = view.scale;
      detailLines.clear();
      titleLines.clear();
      for (OnlineCivicPanelController.Row row : rows) {
        detailLines.add(wrap(row.detail, 11, false));
        titleLines.add(wrap(row.title, 12, true));
      }
    }
    float bottom = g.height - 148;
    panel.scroll =
        Math.max(0, Math.min(panel.scroll, Math.max(0, rows.size() * 64 - (int) (bottom - 112))));
    view.box(c, 18, 112, 402, g.height - 86, view.panel, 12);
    c.save();
    c.clipRect(view.sy(32), view.sy(112), view.sy(390), view.sy(bottom));
    for (int i = 0; i < rows.size(); i++) {
      float top = 112 + i * 64 - panel.scroll;
      if (top + 64 < 112 || top > bottom) continue;
      OnlineCivicPanelController.Row row = rows.get(i);
      if (row.action == OnlineCivicPanelController.Action.KEYS && !row.id.equals("space")) {
        for (int col = 0; col < row.title.length(); col++) {
          float left = 34 + col * 50.28f;
          view.box(c, left, top + 4, left + 48, top + 60, view.panel2, 6);
          view.bold(c, String.valueOf(row.title.charAt(col)), left + 16, top + 39, 14, view.accent);
        }
        continue;
      }
      boolean clickable = row.action != OnlineCivicPanelController.Action.INFO;
      boolean selected = panel.fighters.contains(row.id) || panel.allies.contains(row.id);
      if (clickable)
        view.box(c, 34, top + 4, 386, top + 60, selected ? Color.rgb(57, 51, 34) : view.panel2, 8);
      int color = selected ? view.good : clickable ? view.accent : view.text;
      if (row.action == OnlineCivicPanelController.Action.FIGHTER
          && !view.onlineWorld.state.gameplay.combat.unavailable(row.id).isEmpty())
        color = view.muted;
      List<String> titles = titleLines.get(i);
      for (int j = 0; j < titles.size(); j++)
        view.bold(c, titles.get(j), 42, top + 18 + j * 14, 12, color);
      List<String> lines = detailLines.get(i);
      for (int j = 0; j < lines.size(); j++)
        view.txt(c, lines.get(j), 42, top + (titles.size() > 1 ? 46 : 41) + j * 14, 11, view.muted);
    }
    c.restore();
    view.box(c, 34, g.height - 142, 386, g.height - 86, view.accent, 10);
    view.p.setTextSize(view.sy(12));
    view.p.setTypeface(Typeface.create("sans", Typeface.BOLD));
    String label = panel.primary();
    view.bold(
        c, label, (420 - view.p.measureText(label) / view.scale) / 2, g.height - 108, 12, view.bg);
    if (rows.size() * 64 > bottom - 112) view.txt(c, "↕ ПРОКРУТКА", 290, 119, 8, view.muted);
  }

  private List<String> wrap(String text, float size, boolean bold) {
    List<String> result = new ArrayList<>();
    view.p.setTextSize(view.sy(size));
    view.p.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
    String line = "";
    for (String word : text.split(" ")) {
      String next = line.isEmpty() ? word : line + " " + word;
      if (!line.isEmpty() && view.p.measureText(next) > view.sy(336)) {
        result.add(line);
        line = word;
        if (result.size() == 2) return result;
      } else line = next;
    }
    if (!line.isEmpty()) result.add(line);
    return result;
  }
}
