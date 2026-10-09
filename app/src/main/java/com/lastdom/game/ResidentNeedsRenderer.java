package com.lastdom.game;

import android.graphics.Canvas;
import java.util.ArrayList;
import java.util.List;

/** Read-only resident list/detail and threshold notifications, in the game's Canvas style. */
final class ResidentNeedsRenderer {
  private final GameView view;

  ResidentNeedsRenderer(GameView view) {
    this.view = view;
  }

  private int color(int value, boolean highIsGood) {
    int severity = highIsGood ? 100 - value : value;
    return severity >= 70 ? view.danger : severity >= 40 ? view.accent : view.good;
  }

  private String activity(Resident r, int index) {
    if (view.game.isOnExpedition(r))
      return "В экспедиции — " + view.game.expeditionController.memberLocation(r);
    if (view.game.isStoryBusy(r)) return "Расшифровывает координаты в мастерской";
    if (view.game.isDefending(r)) return "Защищает убежище";
    if (view.game.raidController.repairBuilder(r)) return "Ремонтирует баррикады";
    if (view.game.survivalController.treating(r)) return "Лечится в медпункте";
    if (view.game.survivalController.resting(r)) return "Отдыхает в спальне";
    return view.residentRenderer.residentState(r, index);
  }

  void draw(Canvas c, boolean list) {
    ResidentNeedsLayout l = new ResidentNeedsLayout(view.H / view.scale);
    view.overlayRenderer.dimForOverlay(c);
    view.box(c, 18, l.top, 402, l.bottom, view.panel, 18);
    view.bold(c, list ? "ЖИТЕЛИ" : "СОСТОЯНИЕ ЖИТЕЛЯ", 34, l.top + 35, 17, view.text);
    view.bold(c, "×", 372, l.top + 31, 22, view.muted);
    if (list) list(c, l);
    else detail(c, l);
  }

  private void list(Canvas c, ResidentNeedsLayout l) {
    ResidentNeedsPanelController ui = view.residentNeedsPanel;
    ui.page = Math.max(0, Math.min(ui.pages(l) - 1, ui.page));
    for (int row = 0; row < l.capacity; row++) {
      int index = ui.page * l.capacity + row;
      if (index >= view.game.people.size()) break;
      Resident r = view.game.people.get(index);
      float y = l.bodyTop + row * 98;
      view.box(c, 30, y, 390, y + 90, view.panel2, 10);
      view.bold(c, r.name + " • " + r.role, 40, y + 19, 12, view.text);
      view.txt(c, "Здоровье " + r.health, 40, y + 38, 10, color(r.health, true));
      view.txt(c, "Голод " + r.hunger, 160, y + 38, 10, color(r.hunger, false));
      view.txt(c, "Жажда " + r.thirst, 270, y + 38, 10, color(r.thirst, false));
      view.txt(c, "Усталость " + r.fatigue, 40, y + 55, 10, color(r.fatigue, false));
      view.txt(c, "Мораль " + r.morale, 160, y + 55, 10, color(r.morale, true));
      view.txt(
          c,
          "Работа " + view.game.survivalController.workPercent(r) + "%",
          270,
          y + 55,
          10,
          view.accent);
      String job = activity(r, index);
      // Keep the compact row bounded; full destination/state is readable in the detail card.
      view.p.setTextSize(view.sy(10));
      while (job.length() > 1 && view.p.measureText(job) > view.sy(330))
        job = job.substring(0, job.length() - 1);
      view.txt(c, job, 40, y + 77, 10, view.muted);
    }
    view.box(c, 30, l.footer, 206, l.footer + 44, view.panel2, 10);
    view.box(c, 214, l.footer, 390, l.footer + 44, view.panel2, 10);
    view.bold(c, "‹ НАЗАД", 74, l.footer + 28, 12, view.text);
    view.bold(c, "ДАЛЕЕ ›", 264, l.footer + 28, 12, view.text);
    view.box(c, 30, l.footer + 54, 390, l.footer + 98, view.panel2, 10);
    view.bold(
        c, "ЗАКРЫТЬ • " + (ui.page + 1) + " / " + ui.pages(l), 142, l.footer + 82, 12, view.text);
  }

  private void add(List<String> lines, String text) {
    view.p.setTextSize(view.sy(12));
    String line = "";
    for (String word : text.split(" ")) {
      if (!line.isEmpty() && view.p.measureText(line + " " + word) > view.sy(342)) {
        lines.add(line);
        line = "";
      }
      line += (line.isEmpty() ? "" : " ") + word;
    }
    if (!line.isEmpty()) lines.add(line);
  }

  private void detail(Canvas c, ResidentNeedsLayout l) {
    if (view.game.selected < 0 || view.game.selected >= view.game.people.size()) return;
    Resident r = view.game.people.get(view.game.selected);
    if (!r.id.equals(view.residentNeedsPanel.detailResidentId)) {
      view.residentNeedsPanel.detailResidentId = r.id;
      view.residentNeedsPanel.scroll = 0;
    }
    List<String> lines = new ArrayList<>();
    add(lines, r.name + " • " + r.role + " • навык " + r.skill);
    add(lines, "Сейчас: " + activity(r, view.game.selected));
    int metricsStart = lines.size();
    String[] labels = {"Здоровье", "Голод", "Жажда", "Усталость", "Мораль"};
    int[] values = {r.health, r.hunger, r.thirst, r.fatigue, r.morale};
    for (int i = 0; i < labels.length; i++) {
      lines.add(labels[i] + ": " + values[i] + "%");
      lines.add("");
    }
    add(
        lines,
        "Состояние: "
            + view.game.survivalController.efficiencyPercent(r)
            + "% от базовой. Эффективность текущей работы: "
            + view.game.survivalController.workPercent(r)
            + "% с бонусом комнаты.");
    if (r.hunger >= 40)
      add(
          lines,
          r.hunger >= 90
              ? "Критический голод — здоровье ухудшается"
              : "Нужна еда — эффективность снижена");
    if (r.thirst >= 40)
      add(
          lines,
          r.thirst >= 90
              ? "Критическая жажда — здоровье ухудшается"
              : "Нужна вода — эффективность снижена");
    if (r.fatigue >= 70) add(lines, "Житель сильно устал — нужен отдых");
    if (r.health < 40) add(lines, "Житель нуждается в лечении");
    if (r.morale < 40) add(lines, "Низкая мораль — эффективность снижена");
    if (view.game.isOnExpedition(r))
      add(lines, "Припасы убежища в пути не расходуются. Потребности продолжают расти.");
    ResidentNeedsPanelController ui = view.residentNeedsPanel;
    ui.lineCount = lines.size();
    ui.scroll = Math.max(0, Math.min(ui.scroll, Math.max(0, lines.size() - l.lines)));
    for (int row = 0; row < l.lines && row + ui.scroll < lines.size(); row++) {
      int index = row + ui.scroll, metric = (index - metricsStart) / 2;
      float y = l.bodyTop + row * 21;
      boolean inMetrics = index >= metricsStart && index < metricsStart + 10;
      if (inMetrics && (index - metricsStart) % 2 == 1)
        view.bar(
            c, 34, y, 386, 6, values[metric], color(values[metric], metric == 0 || metric == 4));
      else
        view.txt(
            c,
            lines.get(index),
            34,
            y + 14,
            12,
            inMetrics ? color(values[metric], metric == 0 || metric == 4) : view.text);
    }
    boolean busy =
        view.game.isOnExpedition(r)
            || view.game.isBuilding(r)
            || view.game.isDefending(r)
            || view.game.isStoryBusy(r);
    view.box(c, 30, l.footer, 390, l.footer + 44, busy ? view.panel2 : view.accent, 10);
    view.bold(
        c,
        busy ? "ЖИТЕЛЬ ЗАНЯТ" : "СМЕНИТЬ РАБОТУ",
        118,
        l.footer + 28,
        12,
        busy ? view.muted : view.bg);
    view.box(c, 30, l.footer + 54, 206, l.footer + 98, view.panel2, 10);
    view.box(c, 214, l.footer + 54, 390, l.footer + 98, view.panel2, 10);
    view.bold(c, "ОТДЫХ", 88, l.footer + 82, 12, busy ? view.muted : view.text);
    view.bold(c, "ЗАКРЫТЬ", 264, l.footer + 82, 12, view.text);
  }

  void notice(Canvas c) {
    SurvivalController survival = view.game.survivalController;
    if (survival.notice.isEmpty()
        || view.game.expeditionController.now() - survival.noticeMinute > 20) return;
    float bottom = view.H / view.scale - 88;
    view.box(c, 18, bottom - 54, 402, bottom, view.panel, 10);
    view.wrap(c, survival.notice, 30, bottom - 33, 390, 12, view.accent, 17);
  }
}
