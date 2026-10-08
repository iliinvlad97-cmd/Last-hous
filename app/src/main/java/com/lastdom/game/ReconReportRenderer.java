package com.lastdom.game;

import android.graphics.Canvas;
import java.util.*;

/** Historical result uses launch/return snapshots, never current participant condition. */
final class ReconReportRenderer {
  private final GameView view;

  ReconReportRenderer(GameView view) {
    this.view = view;
  }

  void draw(Canvas c, CityMapLayout layout, Expedition e) {
    CityDistrict d = view.game.explorationController.district(e.locationId);
    ReconData data = e.recon;
    boolean completed = e.state() == Expedition.State.COMPLETED;
    view.box(c, 0, 0, 420, view.H / view.scale, android.graphics.Color.argb(180, 8, 13, 18), 0);
    view.box(c, 18, layout.panelTop, 402, layout.panelBottom, view.panel, 16);
    view.bold(
        c,
        completed ? "РАЗВЕДКА — ОТЧЁТ" : "РАЗВЕДЫВАТЕЛЬНЫЙ ОТРЯД",
        34,
        layout.panelTop + 35,
        15,
        view.text);
    view.bold(c, "×", 372, layout.panelTop + 31, 22, view.muted);
    view.bold(c, d.config.name, 34, layout.panelTop + 65, 14, view.accent);
    List<String> lines = new ArrayList<>();
    lines.add("Отряд: " + String.join(", ", data.names.values()));
    lines.add("Статус: " + e.phaseLabel());
    lines.add(
        "Шанс при отправлении: "
            + String.format(Locale.ROOT, "%.2f", data.chanceBasis / 100.0)
            + "%");
    if (data.resolved) {
      lines.add(data.success ? "Разведка успешна" : "Разведка неудачна");
      if (!completed) lines.add("Результат сохранён. Открытие точек — после возвращения.");
      if (data.healthLoss > 0)
        lines.add("Травма: " + data.names.get(data.injuredId) + " • Здоровье −" + data.healthLoss);
      else lines.add("Травм нет");
    }
    if (completed) {
      lines.add(
          data.success
              ? "РАЙОН ИССЛЕДОВАН — НОВЫЕ ЭКСПЕДИЦИИ"
              : "Район не открыт. Можно повторить разведку.");
      for (String id : data.openedPoints)
        lines.add(view.game.expeditionController.location(id).name);
      for (String id : e.participantIds)
        lines.add(
            data.names.get(id)
                + ": усталость +"
                + String.format(Locale.ROOT, "%.2f", data.fatigueGain.getOrDefault(id, 0) / 1000.0)
                + " п.п.");
      long duration = e.completedMinute - e.departureMinute;
      lines.add("Продолжительность: " + duration / 60 + " ч " + duration % 60 + " мин.");
      lines.add("Последствия уже применены. Повторный просмотр ничего не начисляет.");
    } else {
      lines.add("Прогресс этапа: " + Math.round(e.progress() * 100) + "%");
      lines.add("Осталось: " + e.remainingMinutes() + " игровых мин.");
      lines.add("Разведка не расходует дополнительные припасы убежища.");
    }
    MapPanelContent.draw(view, c, layout, lines);
    view.box(
        c,
        34,
        layout.panelBottom - 66,
        386,
        layout.panelBottom - 24,
        completed ? view.accent : view.panel2,
        10);
    view.bold(
        c,
        completed ? "ПОНЯТНО" : "ЗАКРЫТЬ",
        176,
        layout.panelBottom - 40,
        11,
        completed ? view.bg : view.text);
  }
}
