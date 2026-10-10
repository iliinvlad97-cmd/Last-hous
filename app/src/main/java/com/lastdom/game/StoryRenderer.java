package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/** Read-only story overlays; drawing never rolls an outcome or grants an item. */
final class StoryRenderer {
  private final GameView view;
  private final List<List<String>> lines = new ArrayList<>();
  private int[] tops = new int[0], heights = new int[0];
  private int revision = -1, total;
  private float scale;

  StoryRenderer(GameView view) {
    this.view = view;
  }

  void prepare() {
    StoryPanelController p = view.storyPanel;
    if (revision == p.revision && scale == view.scale) return;
    revision = p.revision;
    scale = view.scale;
    lines.clear();
    tops = new int[p.rows.size()];
    heights = new int[p.rows.size()];
    total = 0;
    view.p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
    view.p.setTextSize(view.sy(12));
    for (int i = 0; i < p.rows.size(); i++) {
      List<String> out = new ArrayList<>();
      for (String paragraph : p.rows.get(i).text.split("\n", -1)) {
        String line = "";
        for (String word : paragraph.split(" ")) {
          String next = line.isEmpty() ? word : line + " " + word;
          if (!line.isEmpty() && view.p.measureText(next) > view.sy(332)) {
            out.add(line);
            line = word;
          } else line = next;
        }
        if (!line.isEmpty()) out.add(line);
      }
      lines.add(out);
      tops[i] = total;
      heights[i] = Math.max(p.rows.get(i).action ? 56 : 30, out.size() * 17 + 15);
      total += heights[i] + 6;
    }
  }

  StoryPanelLayout layout() {
    prepare();
    boolean compact =
        view.storyPanel.mode == StoryPanelController.Mode.MESSAGE
            && StoryInvestigationConfig.dialogue(view.storyPanel.messageId) != null;
    return new StoryPanelLayout(
        view.H / view.scale, view.storyPanel.choiceMode(), compact ? total : -1);
  }

  int maxScroll(StoryPanelLayout l) {
    prepare();
    return Math.max(0, total - (int) (l.contentBottom - l.contentTop));
  }

  int rowTop(int index) {
    prepare();
    return tops[index];
  }

  int rowAt(float y) {
    prepare();
    for (int i = 0; i < tops.length; i++) if (y >= tops[i] && y <= tops[i] + heights[i]) return i;
    return -1;
  }

  void draw(Canvas c) {
    StoryPanelController p = view.storyPanel;
    if (!p.open) return;
    prepare();
    StoryPanelLayout l = layout();
    p.scroll = Math.min(p.scroll, maxScroll(l));
    view.box(c, 0, 0, 420, view.H / view.scale - 80, Color.argb(220, 5, 10, 15), 0);
    view.box(c, 18, l.top, 402, l.bottom, view.panel, 16);
    view.bold(c, p.title(), 32, l.top + 32, 14, view.accent);
    view.txt(c, "STORY 1.2 · локальная кампания", 32, l.top + 50, 10, view.muted);
    c.save();
    c.clipRect(view.sy(28), view.sy(l.contentTop), view.sy(392), view.sy(l.contentBottom));
    for (int i = 0; i < lines.size(); i++) {
      float top = l.contentTop + tops[i] - p.scroll;
      if (top + heights[i] < l.contentTop || top > l.contentBottom) continue;
      StoryPanelController.Row row = p.rows.get(i);
      if (row.action) view.box(c, 30, top, 390, top + heights[i], view.panel2, 8);
      for (int j = 0; j < lines.get(i).size(); j++)
        view.txt(
            c, lines.get(i).get(j), 38, top + 18 + j * 17, 12, row.action ? view.good : view.text);
    }
    c.restore();
    if (maxScroll(l) > 0) view.txt(c, "↕ Прокрутите", 278, l.contentBottom + 5, 9, view.muted);
    button(c, l.primaryTop, p.primary(), p.primaryEnabled());
    button(c, l.closeTop, "ЗАКРЫТЬ", false);
  }

  private void button(Canvas c, float y, String label, boolean accent) {
    view.box(c, 30, y, 390, y + 56, accent ? view.accent : view.panel2, 9);
    view.bold(c, label, 42, y + 33, 12, accent ? view.bg : view.text);
  }

  boolean noticeVisible() {
    return !view.storyPanel.open
        && !view.game.story.pendingMessage.isEmpty()
        && view.game.screen != GameView.ONLINE_WORLD
        && view.game.overlay == 0
        && !view.game.event
        && !view.game.gameOver
        && !view.defensePanel.open
        && !view.cityMap.eventPanel
        && !view.cityMap.expeditionPanel
        && !view.game.jobMenu
        && !view.defenseRenderer.noticeVisible();
  }

  void notice(Canvas c) {
    if (!noticeVisible()) return;
    float y = view.H / view.scale - 144;
    view.box(c, 18, y, 402, y + 56, view.panel2, 10);
    view.bold(
        c,
        view.game.story.investigation != StoryInvestigationController.Phase.DORMANT
            ? "СЛЕДЫ ПРОШЛОГО · сообщение"
            : view.game.story.voicesStage > 0
                ? "ГОЛОСА В ЭФИРЕ · сообщение"
                : "ПОСЛЕДНИЙ СИГНАЛ · сообщение",
        30,
        y + 22,
        12,
        view.accent);
    view.txt(c, "Нажмите, чтобы открыть. Также: Журнал → Сюжет", 30, y + 43, 10, view.text);
  }
}
