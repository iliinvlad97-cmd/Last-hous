package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/** Reads persisted rounds; real-time presentation has no access to transactions or combat RNG. */
final class OnlineCombatRenderer {
  private final GameView view;
  private final OnlineUiStyle ui;
  private final int[] health = new int[6];
  private final List<List<String>> titles = new ArrayList<>(), details = new ArrayList<>();
  private int textRevision = -1;
  private float textScale;
  private String primaryReason = "";

  OnlineCombatRenderer(GameView view, OnlineUiStyle ui) {
    this.view = view;
    this.ui = ui;
  }

  void draw(Canvas c, OnlineWorldGeometry g) {
    OnlineWorldState state = view.onlineWorld.state;
    OnlineCombatPanelController controller = view.onlineWorld.combatPanel;
    List<OnlineCombatPanelController.Row> rows = controller.rows();
    if (textRevision != view.onlineWorld.panelRevision || textScale != view.scale) {
      textRevision = view.onlineWorld.panelRevision;
      textScale = view.scale;
      titles.clear();
      details.clear();
      primaryReason = controller.primaryReason();
      for (int i = 0; i < rows.size(); i++) {
        OnlineCombatPanelController.Row row = rows.get(i);
        titles.add(wrap(row.title, 12, 2));
        details.add(wrap(row.detail, OnlineUiStyle.DETAIL, 2));
        controller.layout.fit(i, titles.get(i).size(), details.get(i).size());
      }
      controller.layout.finish();
    }
    controller.contentHeight = controller.layout.total();
    controller.scroll =
        Math.min(
            controller.scroll,
            Math.max(0, controller.contentHeight - (int) (g.contentBottom - g.contentTop + 12)));
    ui.surface(c, g.panelTop, g.panelBottom);
    ui.bold(
        c,
        controller.title(),
        34,
        g.panelTop + 34,
        16,
        opacity(controller.confirming ? view.danger : view.text));
    ui.bold(c, "×", 372, g.panelTop + 31, 22, opacity(view.muted));
    OnlineBattleRepository.Battle battle = state.gameplay.combat.battle(controller.reportId);
    int count = 0;
    OnlineCombatReport.Action hit = null;
    if (state.panel == OnlineWorldState.Panel.BATTLE && battle != null) {
      count =
          Math.min(
              battle.report.actions.size(),
              (int) (controller.replaySeconds / OnlineCombatRules.ACTION_SECONDS));
      battle.report.fillHealth(count, health);
      if (count > 0
          && controller.replaySeconds
              < (battle.report.actions.size() + 1) * OnlineCombatRules.ACTION_SECONDS)
        hit = battle.report.actions.get(count - 1);
      String label =
          count == battle.report.actions.size()
              ? battle.report.resultLabel()
              : "Раунд "
                  + (hit == null ? 1 : hit.round)
                  + " · удар "
                  + count
                  + " / "
                  + battle.report.actions.size();
      ui.txt(c, label, 34, g.panelTop + 63, 12, opacity(view.accent));
      ui.bar(
          c,
          34,
          g.panelTop + 71,
          386,
          3,
          count * 100 / battle.report.actions.size(),
          opacity(view.accent));
    } else {
      ui.txt(c, controller.subtitle(), 34, g.panelTop + 63, 12, opacity(view.accent));
      if (state.panel == OnlineWorldState.Panel.COOP_REPORT) {
        OnlineCoopExpedition expedition = state.gameplay.combat.expedition(controller.reportId);
        if (expedition != null)
          ui.bar(
              c,
              34,
              g.panelTop + 71,
              386,
              3,
              expedition.elapsedMinutes * 100 / expedition.durationMinutes,
              opacity(view.good));
      }
    }
    if (state.confirmationGlow > .01f)
      ui.box(
          c,
          34,
          g.panelTop + 76,
          386,
          g.panelTop + 79,
          opacity(OnlineWorldRenderer.alpha(view.good, (int) (210 * state.confirmationGlow))),
          2);
    ui.clip(c, 32, g.contentTop - 12, 390, g.contentBottom);
    for (int i = 0; i < rows.size(); i++) {
      float top = g.contentTop - 12 + controller.layout.top(i) - controller.scroll;
      if (top + controller.layout.height(i) < g.contentTop - 12 || top > g.contentBottom) continue;
      OnlineCombatPanelController.Row row = rows.get(i);
      if (row.action == OnlineCombatPanelController.Action.HEALTH && battle != null) {
        fighter(
            c,
            battle.report.own,
            row.index,
            34,
            top,
            health[row.index],
            hit,
            controller.replaySeconds,
            true);
        fighter(
            c,
            battle.report.enemy,
            row.index,
            214,
            top,
            health[3 + row.index],
            hit,
            controller.replaySeconds,
            false);
      } else {
        boolean actionable = row.action != OnlineCombatPanelController.Action.INFO;
        boolean selected =
            controller.selected.contains(row.id)
                || controller.recoveryId.equals(row.id) && !row.id.isEmpty();
        if (actionable)
          ui.box(
              c,
              34,
              top + 2,
              386,
              top + controller.layout.height(i) - 3,
              opacity(selected ? Color.rgb(59, 49, 34) : view.panel2),
              8);
        int color =
            row.action == OnlineCombatPanelController.Action.FIGHTER
                    && !state.gameplay.combat.unavailable(row.id).isEmpty()
                ? view.muted
                : selected ? view.good : actionable ? view.accent : view.text;
        drawLines(c, titles.get(i), 42, top + 18, 12, color);
        drawLines(
            c,
            details.get(i),
            42,
            top + OnlinePanelLayout.detailTop(titles.get(i).size()),
            OnlineUiStyle.DETAIL,
            view.muted);
      }
    }
    ui.restore(c);
    if (controller.contentHeight > g.contentBottom - g.contentTop + 12)
      ui.txt(
          c, "↑ Прокрутите для подробностей ↓", 34, g.contentBottom + 15, 10, opacity(view.muted));
    float bottom = g.panelBottom;
    switch (state.panel) {
      case PVP_PREP:
      case COOP_PREP:
        button(
            c,
            g,
            34,
            386,
            controller.confirming
                ? state.panel == OnlineWorldState.Panel.PVP_PREP ? "ПОДТВЕРДИТЬ БОЙ" : "ОТПРАВИТЬ"
                : "К ПОДТВЕРЖДЕНИЮ",
            view.accent);
        break;
      case ROSTER:
        button(c, g, 34, 204, "ВОССТАНОВИТЬ", view.good);
        button(c, g, 216, 386, "ОТЧЁТЫ", view.accent);
        break;
      case BATTLE:
        button(c, g, 34, 386, "ПОВТОР БОЯ", view.accent);
        break;
      case COOP_REPORT:
        button(c, g, 34, 386, "ВСЕ ОТЧЁТЫ", view.accent);
        break;
      default:
        break;
    }
    ui.button(
        c,
        34,
        bottom - 60,
        386,
        bottom - 4,
        controller.confirming ? "ОТКАЗАТЬСЯ" : "ЗАКРЫТЬ",
        view.panel2,
        true);
  }

  private void fighter(
      Canvas c,
      OnlineCombatSquad squad,
      int index,
      float x,
      float top,
      int hp,
      OnlineCombatReport.Action hit,
      double seconds,
      boolean player) {
    if (index >= squad.fighters.size()) return;
    OnlineCombatSquad.Fighter fighter = squad.fighters.get(index);
    boolean attacked = hit != null && hit.playerAttack != player && hit.target == index;
    boolean attacking = hit != null && hit.playerAttack == player && hit.actor == index;
    float pulse = (float) Math.sin(Math.PI * ((seconds / OnlineCombatRules.ACTION_SECONDS) % 1));
    ui.box(c, x, top + 2, x + 170, top + 59, opacity(view.panel2), 6);
    if (attacked || attacking)
      ui.box(
          c,
          x,
          top + 2,
          x + 170,
          top + 59,
          opacity(
              OnlineWorldRenderer.alpha(attacked ? view.danger : view.accent, (int) (pulse * 90))),
          6);
    if (index == 0)
      ui.txt(c, player ? "ВАШ ОТРЯД" : "ПРОТИВНИК", x + 6, top + 8, 8, opacity(view.accent));
    ui.txt(c, fighter.name, x + 6, top + 21, 12, opacity(view.text));
    ui.txt(c, "Здоровье: " + hp, x + 6, top + 34, 11, opacity(hp < 30 ? view.danger : view.good));
    ui.bar(c, x + 6, top + 42, x + 164, 6, hp, opacity(hp < 30 ? view.danger : view.good));
    if (attacked)
      ui.txt(
          c,
          "−" + (hit.before - hit.after) + " · защита " + hit.blocked,
          x + 6,
          top + 58,
          9,
          opacity(view.accent));
  }

  private List<String> wrap(String text, float size, int maxLines) {
    List<String> result = new ArrayList<>();
    ui.wrap(result, text, size, 336, false);
    return result;
  }

  private void drawLines(Canvas c, List<String> lines, float x, float y, float size, int color) {
    for (int i = 0; i < lines.size(); i++)
      ui.txt(c, lines.get(i), x, y + i * 14, size, opacity(color));
  }

  private void button(
      Canvas c, OnlineWorldGeometry g, float left, float right, String label, int color) {
    boolean enabled = label.equals("ОТЧЁТЫ") || primaryReason.isEmpty();
    ui.button(c, left, g.panelBottom - 122, right, g.panelBottom - 66, label, color, enabled);
  }

  private int opacity(int color) {
    return color;
  }
}
