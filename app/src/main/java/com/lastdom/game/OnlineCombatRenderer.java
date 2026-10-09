package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/** Reads persisted rounds; real-time presentation has no access to transactions or combat RNG. */
final class OnlineCombatRenderer {
  private final GameView view;
  private final int[] health = new int[6];
  private final List<List<String>> titles = new ArrayList<>(), details = new ArrayList<>();
  private int textRevision = -1;
  private float textScale;

  OnlineCombatRenderer(GameView view) {
    this.view = view;
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
      for (OnlineCombatPanelController.Row row : rows) {
        titles.add(wrap(row.title, 12, 2));
        details.add(wrap(row.detail, 10.5f, 2));
      }
    }
    controller.scroll =
        Math.min(
            controller.scroll,
            Math.max(0, controller.contentHeight - (int) (g.contentBottom - g.contentTop + 12)));
    view.box(c, 0, 0, 420, g.height - 80, opacity(Color.argb(180, 5, 11, 17)), 0);
    view.box(c, 18, g.panelTop, 402, g.panelBottom, opacity(view.panel), 16);
    view.bold(
        c,
        controller.title(),
        34,
        g.panelTop + 34,
        16,
        opacity(controller.confirming ? view.danger : view.text));
    view.bold(c, "×", 372, g.panelTop + 31, 22, opacity(view.muted));
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
      view.txt(c, label, 34, g.panelTop + 63, 12, opacity(view.accent));
      view.bar(
          c,
          34,
          g.panelTop + 71,
          386,
          3,
          count * 100 / battle.report.actions.size(),
          opacity(view.accent));
    } else {
      view.txt(c, controller.subtitle(), 34, g.panelTop + 63, 12, opacity(view.accent));
      if (state.panel == OnlineWorldState.Panel.COOP_REPORT) {
        OnlineCoopExpedition expedition = state.gameplay.combat.expedition(controller.reportId);
        if (expedition != null)
          view.bar(
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
      view.box(
          c,
          34,
          g.panelTop + 76,
          386,
          g.panelTop + 79,
          opacity(OnlineWorldRenderer.alpha(view.good, (int) (210 * state.confirmationGlow))),
          2);
    c.save();
    c.clipRect(view.sy(32), view.sy(g.contentTop - 12), view.sy(390), view.sy(g.contentBottom));
    for (int i = 0; i < rows.size(); i++) {
      float top =
          g.contentTop - 12 + i * OnlineCombatPanelController.ROW_HEIGHT - controller.scroll;
      if (top + OnlineCombatPanelController.ROW_HEIGHT < g.contentTop - 12 || top > g.contentBottom)
        continue;
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
          view.box(
              c,
              34,
              top + 2,
              386,
              top + OnlineCombatPanelController.ROW_HEIGHT - 3,
              opacity(selected ? Color.rgb(59, 49, 34) : view.panel2),
              8);
        int color =
            row.action == OnlineCombatPanelController.Action.FIGHTER
                    && !state.gameplay.combat.unavailable(row.id).isEmpty()
                ? view.muted
                : selected ? view.good : actionable ? view.accent : view.text;
        drawLines(c, titles.get(i), 42, top + 18, 12, color);
        drawLines(
            c, details.get(i), 42, top + (titles.get(i).size() > 1 ? 46 : 41), 10.5f, view.muted);
      }
    }
    c.restore();
    if (controller.contentHeight > g.contentBottom - g.contentTop + 12)
      view.txt(
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
    view.box(c, 34, bottom - 66, 386, bottom - 24, opacity(view.panel2), 10);
    view.bold(
        c,
        controller.confirming ? "ОТКАЗАТЬСЯ" : "ЗАКРЫТЬ",
        145,
        bottom - 39,
        12,
        opacity(view.text));
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
    view.box(c, x, top + 2, x + 170, top + 59, opacity(view.panel2), 6);
    if (attacked || attacking)
      view.box(
          c,
          x,
          top + 2,
          x + 170,
          top + 59,
          opacity(
              OnlineWorldRenderer.alpha(attacked ? view.danger : view.accent, (int) (pulse * 90))),
          6);
    if (index == 0)
      view.txt(c, player ? "ВАШ ОТРЯД" : "ПРОТИВНИК", x + 6, top + 8, 8, opacity(view.accent));
    view.txt(c, fighter.name, x + 6, top + 21, 12, opacity(view.text));
    view.txt(c, "Здоровье: " + hp, x + 6, top + 34, 11, opacity(hp < 30 ? view.danger : view.good));
    view.bar(c, x + 6, top + 42, x + 164, 6, hp, opacity(hp < 30 ? view.danger : view.good));
    if (attacked)
      view.txt(
          c,
          "−" + (hit.before - hit.after) + " · защита " + hit.blocked,
          x + 6,
          top + 58,
          9,
          opacity(view.accent));
  }

  private List<String> wrap(String text, float size, int maxLines) {
    List<String> result = new ArrayList<>();
    view.p.setTextSize(view.sy(size));
    view.p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
    String line = "";
    for (String word : text.split(" ")) {
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (!line.isEmpty() && view.p.measureText(candidate) > view.sy(336)) {
        result.add(line);
        line = word;
        if (result.size() >= maxLines) return result;
      } else line = candidate;
    }
    if (!line.isEmpty()) result.add(line);
    return result;
  }

  private void drawLines(Canvas c, List<String> lines, float x, float y, float size, int color) {
    for (int i = 0; i < lines.size(); i++)
      view.txt(c, lines.get(i), x, y + i * 14, size, opacity(color));
  }

  private void button(
      Canvas c, OnlineWorldGeometry g, float left, float right, String label, int color) {
    view.box(c, left, g.panelBottom - 114, right, g.panelBottom - 76, opacity(color), 10);
    view.p.setTextSize(view.sy(12));
    view.p.setTypeface(Typeface.create("sans", Typeface.BOLD));
    view.bold(
        c,
        label,
        (left + right - view.p.measureText(label) / view.scale) / 2,
        g.panelBottom - 89,
        12,
        opacity(view.bg));
  }

  private int opacity(int color) {
    return OnlineWorldRenderer.alpha(
        color, Math.round((color >>> 24) * view.onlineWorld.state.cardOpacity));
  }
}
