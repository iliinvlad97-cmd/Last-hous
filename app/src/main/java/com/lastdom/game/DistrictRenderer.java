package com.lastdom.game;

import android.graphics.Canvas;
import java.util.*;

/** District UI reads the same destinations, prerequisites and launch chance as the controller. */
final class DistrictRenderer {
  private final GameView view;

  DistrictRenderer(GameView view) {
    this.view = view;
  }

  void marker(Canvas c, MapLocation location, CityMapLayout layout) {
    CityDistrict d = view.game.explorationController.district(location.id);
    Expedition active = view.game.expeditionController.active(Expedition.Type.RECON);
    boolean scouting = active != null && active.locationId.equals(d.config.id);
    int color =
        scouting
            ? view.accent
            : d.state == CityDistrict.State.EXPLORED
                ? view.good
                : d.state == CityDistrict.State.DISCOVERED ? view.accent : view.muted;
    float x = layout.x(location.mapX), y = layout.y(location.mapY);
    view.box(c, x - 22, y - 22, x + 22, y + 22, view.panel2, 12);
    view.bold(
        c,
        scouting ? "Р" : d.state == CityDistrict.State.EXPLORED ? "✓" : "?",
        x - 6,
        y + 6,
        20,
        color);
    String[] title = location.markerName.split("\n");
    for (int i = 0; i < title.length; i++)
      center(c, title[i], x, y - 32 - (title.length - 1 - i) * 12, 9, view.text);
    center(
        c,
        scouting ? "РАЗВЕДКА" : d.state.label.toUpperCase(java.util.Locale.ROOT),
        x,
        y + 37,
        9,
        color);
    center(c, location.risk.label, x, y + 51, 9, view.muted);
  }

  void panel(Canvas c, MapLocation location, CityMapLayout layout) {
    CityDistrict d = view.game.explorationController.district(location.id);
    view.box(c, 0, 0, 420, view.H / view.scale, android.graphics.Color.argb(180, 8, 13, 18), 0);
    view.box(c, 18, layout.panelTop, 402, layout.panelBottom, view.panel, 16);
    view.bold(c, "РАЙОН ГОРОДА", 34, layout.panelTop + 35, 17, view.text);
    view.bold(c, "×", 372, layout.panelTop + 31, 22, view.muted);
    view.bold(c, d.config.name, 34, layout.panelTop + 65, 14, view.accent);
    List<String> lines = new ArrayList<>();
    if (!view.cityMap.message.isEmpty()) lines.add(view.cityMap.message);
    lines.add("Статус: " + d.state.label);
    lines.add("Опасность: " + d.config.risk.label);
    lines.add("Потенциальные ресурсы: " + d.config.loot);
    int travel = ExpeditionConfig.oneWayMinutes(location);
    lines.add("Путь: " + travel + " мин. туда / " + travel + " мин. обратно");
    lines.add(
        "Разведка: "
            + d.config.researchMinutes
            + " мин. • Всего: "
            + (2 * travel + d.config.researchMinutes)
            + " мин.");
    lines.add("Базовый шанс успеха: " + d.config.baseChance / 100 + "%");
    lines.add(
        "Сборщик, навык и состав повышают шанс; здоровье и усталость снижают его. Предел: 10–95%.");
    String reason = view.game.explorationController.unavailableReason(d.config.id);
    if (d.state != CityDistrict.State.EXPLORED)
      lines.add(reason.isEmpty() ? "Разведка доступна. Выберите 1–3 жителей." : reason);
    if (d.config.prerequisite.isEmpty())
      lines.add("Ранний район — предварительная разведка не требуется.");
    else
      lines.add(
          "Условие: исследовать "
              + view.game.explorationController.district(d.config.prerequisite).config.name);
    if (d.state == CityDistrict.State.EXPLORED) {
      lines.add("ОТКРЫТЫЕ МЕСТА");
      for (String id : d.config.points) lines.add(view.game.expeditionController.location(id).name);
    }
    lines.add("Разведка не приносит добычу. Запасы находят обычные экспедиции.");
    lines.add(
        "В пути растут потребности и усталость; возможна травма. После разведки отряд возвращается"
            + " автоматически.");
    MapPanelContent.draw(view, c, layout, lines, 132);
    Expedition report = view.game.explorationController.latestReport(d.config.id);
    button(
        c,
        layout.panelBottom - 114,
        report == null ? "ОТЧЁТОВ ПОКА НЕТ" : "ОТРЯД / ПОСЛЕДНИЙ ОТЧЁТ",
        report != null,
        false);
    button(
        c,
        layout.panelBottom - 66,
        d.state == CityDistrict.State.EXPLORED ? "ПОКАЗАТЬ ОТКРЫТЫЕ ТОЧКИ" : "ОТПРАВИТЬ РАЗВЕДКУ",
        d.state == CityDistrict.State.EXPLORED || reason.isEmpty(),
        true);
  }

  private void button(Canvas c, float y, String label, boolean enabled, boolean primary) {
    view.box(c, 34, y, 386, y + 38, enabled && primary ? view.accent : view.panel2, 10);
    center(
        c, label, 210, y + 25, 11, enabled && primary ? view.bg : enabled ? view.text : view.muted);
  }

  private void center(Canvas c, String text, float x, float y, int size, int color) {
    view.p.setTextSize(view.sy(size));
    view.txt(c, text, x - view.p.measureText(text) / view.scale / 2, y, size, color);
  }
}
