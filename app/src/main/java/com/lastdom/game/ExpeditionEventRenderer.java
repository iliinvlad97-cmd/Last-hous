package com.lastdom.game;

import android.graphics.Canvas;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

/** City event overlay reads state; random outcomes and mutations belong to the controller. */
final class ExpeditionEventRenderer {
  private final GameView view;

  ExpeditionEventRenderer(GameView view) {
    this.view = view;
  }

  void draw(Canvas canvas, CityMapLayout map) {
    Expedition expedition = view.game.expeditionController.active();
    if (expedition == null || expedition.state() != Expedition.State.AWAITING_DECISION) return;
    ExpeditionEvent event = expedition.explorationEvent;
    if (!event.interactive()) return;
    ExpeditionEventLayout layout = new ExpeditionEventLayout(map, event);
    view.box(canvas, 0, 0, 420, view.H / view.scale, Color.argb(200, 8, 13, 18), 0);
    view.box(canvas, 18, map.panelTop, 402, map.panelBottom, view.panel, 16);
    view.bold(
        canvas, ExpeditionEventConfig.title(event.type), 30, map.panelTop + 35, 15, view.text);
    view.bold(canvas, "×", 372, map.panelTop + 31, 22, view.muted);
    view.txt(
        canvas,
        view.game.expeditionController.location(expedition.locationId).name,
        30,
        map.panelTop + 65,
        13,
        view.accent);
    List<String> lines = new ArrayList<>();
    String names = "";
    for (String id : expedition.participantIds) {
      Resident resident = view.game.expeditionController.resident(id);
      if (resident != null) names += (names.isEmpty() ? "" : ", ") + resident.name;
    }
    lines.add("Отряд: " + names);
    if (event.effectsApplied) {
      lines.add("Решение: " + ExpeditionEventConfig.actions(event.type)[event.chosenAction]);
      lines.add("РЕЗУЛЬТАТ: " + event.outcome.message);
      lines.add(view.game.expeditionController.outcomeSummary(event.outcome));
      if (event.outcome.riskReduction > 0)
        lines.add("Риск при выбранном осмотре снижен на " + event.outcome.riskReduction + "%.");
      lines.add(
          event.outcome.retreat
              ? "После продолжения отряд отправится домой с найденным грузом."
              : "После продолжения исследование возобновится.");
    } else {
      lines.add(event.message);
      String[] actions = ExpeditionEventConfig.actions(event.type),
          warnings = ExpeditionEventConfig.warnings(event.type);
      for (int i = 0; i < actions.length; i++)
        lines.add((i + 1) + ". " + actions[i] + ": " + warnings[i]);
    }
    lines.add(
        "Уже найдено: "
            + expedition.found.total()
            + " • Груз: "
            + expedition.cargo.total()
            + " / "
            + expedition.capacity());
    lines.add("Остановлен только отряд. Убежище продолжает жить.");
    MapPanelContent.draw(view, canvas, map, lines, layout.footer);
    String[] actions =
        event.effectsApplied
            ? new String[] {"ПРОДОЛЖИТЬ"}
            : ExpeditionEventConfig.actions(event.type);
    for (int i = 0; i < actions.length; i++) {
      float top = layout.top(i);
      view.box(
          canvas,
          30,
          top,
          390,
          top + ExpeditionEventLayout.CHOICE_HEIGHT,
          i == 0 ? view.accent : view.panel2,
          10);
      view.bold(
          canvas,
          (event.effectsApplied ? "" : (i + 1) + ". ") + actions[i],
          42,
          top + 32,
          12,
          i == 0 ? view.bg : view.text);
    }
  }
}
