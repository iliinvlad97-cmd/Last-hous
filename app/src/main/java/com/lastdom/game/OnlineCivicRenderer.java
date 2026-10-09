package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/** Read-only radio sections with 56-unit actions, 64-unit rows and fixed navigation. */
final class OnlineCivicRenderer {
  private final GameView view;
  private final OnlineUiStyle ui;
  private final List<List<String>> detailLines = new ArrayList<>();
  private final List<List<String>> titleLines = new ArrayList<>();
  private int revision = -1;
  private float scale;
  private String primaryReason = "";

  OnlineCivicRenderer(GameView view, OnlineUiStyle ui) {
    this.view = view;
    this.ui = ui;
  }

  void draw(Canvas c, OnlineWorldGeometry g) {
    OnlineCivicPanelController panel = view.onlineWorld.civicPanel;
    List<OnlineCivicPanelController.Row> rows = panel.rows();
    if (revision != view.onlineWorld.panelRevision || scale != view.scale) {
      revision = view.onlineWorld.panelRevision;
      scale = view.scale;
      detailLines.clear();
      titleLines.clear();
      primaryReason = panel.primaryReason();
      for (int i = 0; i < rows.size(); i++) {
        OnlineCivicPanelController.Row row = rows.get(i);
        detailLines.add(wrap(row.detail, 11, false));
        titleLines.add(wrap(row.title, 12, true));
        panel.layout.fit(i, titleLines.get(i).size(), detailLines.get(i).size());
      }
      panel.layout.finish();
    }
    float bottom = g.height - 148;
    panel.scroll =
        Math.max(
            0, Math.min(panel.scroll, Math.max(0, panel.layout.total() - (int) (bottom - 112))));
    ui.surface(c, 112, g.height - 86);
    ui.clip(c, 32, 112, 390, bottom);
    for (int i = 0; i < rows.size(); i++) {
      float top = 112 + panel.layout.top(i) - panel.scroll;
      if (top + panel.layout.height(i) < 112 || top > bottom) continue;
      OnlineCivicPanelController.Row row = rows.get(i);
      if (row.action == OnlineCivicPanelController.Action.KEYS && !row.id.equals("space")) {
        for (int col = 0; col < row.title.length(); col++) {
          float left = 34 + col * 50.28f;
          ui.box(c, left, top + 4, left + 48, top + panel.layout.height(i) - 4, view.panel2, 6);
          ui.bold(c, String.valueOf(row.title.charAt(col)), left + 16, top + 39, 14, view.accent);
        }
        continue;
      }
      boolean clickable = row.action != OnlineCivicPanelController.Action.INFO;
      boolean selected = panel.fighters.contains(row.id) || panel.allies.contains(row.id);
      if (clickable)
        ui.box(
            c,
            34,
            top + 4,
            386,
            top + panel.layout.height(i) - 4,
            selected ? Color.rgb(57, 51, 34) : view.panel2,
            8);
      boolean available = true;
      if (row.action == OnlineCivicPanelController.Action.INVITE
          && view.onlineWorld.state.gameplay.civic.alliance != null) {
        OnlineAllianceMember m = view.onlineWorld.state.gameplay.civic.alliance.member(row.id);
        available =
            m == null
                || m.invitation == OnlineAllianceMember.Invitation.REJECTED
                    && view.onlineWorld.state.gameplay.civic.alliance.reputation()
                        >= OnlineAllianceController.required(row.id);
      }
      int color =
          !available ? view.muted : selected ? view.good : clickable ? view.accent : view.text;
      if (row.action == OnlineCivicPanelController.Action.FIGHTER
          && !view.onlineWorld.state.gameplay.combat.unavailable(row.id).isEmpty())
        color = view.muted;
      List<String> titles = titleLines.get(i);
      for (int j = 0; j < titles.size(); j++)
        ui.bold(c, titles.get(j), 42, top + 18 + j * 14, 12, color);
      List<String> lines = detailLines.get(i);
      for (int j = 0; j < lines.size(); j++)
        ui.txt(
            c,
            lines.get(j),
            42,
            top + OnlinePanelLayout.detailTop(titles.size()) + j * 14,
            11,
            view.muted);
    }
    ui.restore(c);
    ui.button(
        c,
        34,
        g.height - 142,
        386,
        g.height - 86,
        panel.primary(),
        view.accent,
        primaryReason.isEmpty());
    if (panel.layout.total() > bottom - 112) ui.txt(c, "↕ ПРОКРУТКА", 290, 119, 8, view.muted);
  }

  private List<String> wrap(String text, float size, boolean bold) {
    List<String> result = new ArrayList<>();
    ui.wrap(result, text, size, 336, bold);
    return result;
  }
}
