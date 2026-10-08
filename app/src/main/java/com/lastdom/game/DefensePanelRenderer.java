package com.lastdom.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only Canvas panels, adaptive scroll/paging and subtle in-room state indicators. */
final class DefensePanelRenderer {
  final GameView view;

  DefensePanelRenderer(GameView view) {
    this.view = view;
  }

  void draw(Canvas c) {
    DefensePanelController ui = view.defensePanel;
    GameController game = view.game;
    RaidController controller = game.raidController;
    RoomUpgradeLayout l = new RoomUpgradeLayout(view.H / view.scale);
    view.overlayRenderer.dimForOverlay(c);
    view.box(c, 18, l.top, 402, l.bottom, view.panel, 18);
    view.bold(c, "×", 372, l.top + 31, 22, view.muted);
    String title =
        ui.mode == DefensePanelController.Mode.THREAT
                && ui.report != null
                && ui.report.phase == RaidState.Phase.WARNING
            ? "ПРИБЛИЖАЕТСЯ НАПАДЕНИЕ"
            : ui.mode == DefensePanelController.Mode.DEFENDERS
                ? "ПОДГОТОВКА ОБОРОНЫ"
                : ui.mode == DefensePanelController.Mode.BUILDERS
                    ? "ВЫБОР РЕМОНТНИКА"
                    : ui.mode == DefensePanelController.Mode.REPAIR
                        ? "РЕМОНТ БАРРИКАД"
                        : ui.report != null && !ui.report.active()
                            ? "РЕЗУЛЬТАТ ОБОРОНЫ"
                            : "ОБОРОНА УБЕЖИЩА";
    view.bold(c, title, 30, l.top + 35, 15, view.text);
    view.txt(
        c,
        "Баррикады: ур. " + game.roomLevels[4] + " • прочность " + controller.durability + "%",
        30,
        l.top + 63,
        12,
        view.accent);
    if (ui.list()) {
      residents(c, l);
      return;
    }
    List<String> lines = new ArrayList<>();
    RaidState raid = ui.report;
    lines.add("Сила защиты сейчас: " + number(controller.defensePower()));
    if (ui.mode == DefensePanelController.Mode.REPAIR) {
      lines.add(
          RaidConfig.REPAIR_AMOUNT
              + "% прочности • "
              + RaidConfig.REPAIR_COST
              + " материалов • "
              + RaidConfig.REPAIR_MINUTES
              + " игровых минут.");
      lines.add("Доступно материалов: " + game.mats + ". Ремонт не отменяется.");
      if (controller.repairing()) {
        BarricadeRepair repair = controller.repair;
        Resident builder = game.expeditionController.resident(repair.builderId);
        lines.add("Ремонтник: " + (builder == null ? "—" : builder.name));
        lines.add(
            "Прогресс: "
                + repair.elapsed * 100 / RaidConfig.REPAIR_MINUTES
                + "% • осталось "
                + repair.remaining()
                + " игровых минут.");
        lines.add("Стоимость уже оплачена. Повторного списания не будет.");
      } else {
        String blocked = controller.repairReason();
        if (!blocked.isEmpty()) lines.add(blocked);
        lines.add("Житель не сможет работать, строить другую комнату или уйти в экспедицию.");
      }
    } else if (raid == null) {
      lines.add("Активной угрозы нет. Первые игровые сутки безопасны.");
      lines.add(
          "При предупреждении назначьте защитников. Обычная работа охранника сама по себе не"
              + " включает его в отряд обороны.");
    } else {
      lines.add("Враг: " + raid.enemyName() + " • сила " + raid.attackPower);
      if (raid.active()) {
        lines.add(
            raid.phase == RaidState.Phase.ATTACK ? "НАПАДЕНИЕ" : "УГРОЗА УБЕЖИЩУ — ПОДГОТОВКА");
        lines.add(
            raid.phase == RaidState.Phase.ATTACK
                ? "Защита в начале боя: " + number(raid.defenseAtStart)
                : "Враги приближаются. Подготовка не останавливает время убежища.");
        lines.add(
            "Осталось: " + raid.remaining() + " игровых минут • прогресс " + raid.progress() + "%");
        lines.add("Защитники: " + names(raid));
        if (raid.phase == RaidState.Phase.ATTACK) {
          lines.add("Состав зафиксирован. Вернувшиеся экспедиции не подключаются автоматически.");
          lines.add("Получено повреждений баррикад: " + raid.damageApplied + "%.");
        } else
          lines.add(
              "Назначение явно прерывает обычную работу или отдых. Состояние жителей влияет на"
                  + " защиту.");
      } else {
        lines.add(raid.outcomeName());
        lines.add("Защита: " + number(raid.defenseAtStart) + " • участники: " + names(raid));
        lines.add("Повреждения баррикад: " + raid.damageApplied + "%");
        lines.add(
            "Потеря еды: "
                + raid.foodLost
                + " • воды: "
                + raid.waterLost
                + " • материалов: "
                + raid.materialsLost);
        lines.add(
            "Изменение морали: "
                + (raid.moraleChange > 0 ? "+" : "")
                + raid.moraleChange
                + " (травмы дополнительно снижают мораль).");
        boolean injured = false;
        for (String id : raid.injuries.keySet())
          if (raid.injuries.get(id) > 0) {
            lines.add(
                "Пострадал " + raid.victimNames.get(id) + ": здоровье -" + raid.injuries.get(id));
            injured = true;
          }
        if (!injured) lines.add("Пострадавших нет.");
        lines.add("Последствия уже применены. Повторное открытие отчёта не списывает ресурсы.");
      }
    }
    if (!ui.message.isEmpty()) lines.add(ui.message);
    body(c, l, lines);
    RaidState active = controller.active();
    String primary =
        ui.mode == DefensePanelController.Mode.REPAIR
            ? controller.repairing() ? "РЕМОНТ ИДЁТ" : "ВЫБРАТЬ РЕМОНТНИКА"
            : active != null
                ? active.phase == RaidState.Phase.ATTACK ? "НАПАДЕНИЕ ИДЁТ" : "ПОДГОТОВИТЬ ОБОРОНУ"
                : raid != null ? "ПОНЯТНО" : "УГРОЗЫ НЕТ";
    button(
        c,
        30,
        l.actionTop,
        390,
        48,
        primary,
        ui.mode == DefensePanelController.Mode.REPAIR
            ? controller.repairReason().isEmpty()
            : active != null && active.phase != RaidState.Phase.ATTACK
                || raid != null && !raid.active());
    button(
        c,
        30,
        l.secondaryTop,
        206,
        43,
        ui.mode == DefensePanelController.Mode.REPAIR ? "К ОБОРОНЕ" : "РЕМОНТ",
        false);
    button(c, 214, l.secondaryTop, 390, 43, "ЗАКРЫТЬ", false);
  }

  private String names(RaidState raid) {
    List<String> names = new ArrayList<>();
    for (String id : raid.defenders.keySet()) {
      Resident resident = view.game.expeditionController.resident(id);
      names.add(raid.victimNames.getOrDefault(id, resident == null ? "Житель" : resident.name));
    }
    return names.isEmpty() ? "—" : android.text.TextUtils.join(", ", names);
  }

  private String number(double n) {
    return String.format(Locale.ROOT, "%.1f", n);
  }

  private void residents(Canvas c, RoomUpgradeLayout l) {
    RaidState threat = view.game.raidController.active();
    if (view.defensePanel.mode == DefensePanelController.Mode.DEFENDERS && threat != null)
      view.txt(
          c,
          "Защита "
              + number(view.game.raidController.defensePower())
              + " • "
              + (threat.phase == RaidState.Phase.ATTACK
                  ? "бой идёт"
                  : "до атаки " + threat.remaining() + " мин."),
          34,
          l.top + 83,
          10,
          view.accent);
    DefensePanelController ui = view.defensePanel;
    RaidController controller = view.game.raidController;
    ui.page = Math.max(0, Math.min(ui.pages(l) - 1, ui.page));
    for (int row = 0; row < l.capacity; row++) {
      int index = ui.page * l.capacity + row;
      if (index >= view.game.people.size()) break;
      Resident resident = view.game.people.get(index);
      float top = l.rowTop + row * 64;
      boolean selected = controller.defending(resident);
      String reason =
          ui.mode == DefensePanelController.Mode.DEFENDERS
              ? controller.defenderReason(resident)
              : view.game.roomUpgradeController.unavailableReason(resident);
      view.box(c, 30, top, 390, top + 56, selected ? Color.rgb(74, 57, 36) : view.panel2, 9);
      view.bold(
          c,
          resident.name + " • " + resident.role + (selected ? " ✓" : ""),
          40,
          top + 19,
          12,
          reason.isEmpty() ? view.text : view.muted);
      view.txt(
          c,
          "Здоровье "
              + resident.health
              + " • усталость "
              + resident.fatigue
              + " • мораль "
              + resident.morale,
          40,
          top + 35,
          9,
          view.muted);
      String description =
          reason.isEmpty()
              ? selected
                  ? "На обороне • нажмите, чтобы снять"
                  : ui.mode == DefensePanelController.Mode.DEFENDERS
                      ? "Сила "
                          + number(RaidResolver.residentPower(resident))
                          + " • "
                          + resident.job
                      : "Доступен • " + resident.job
              : reason;
      view.txt(c, description, 40, top + 49, 9, reason.isEmpty() ? view.good : view.danger);
    }
    view.txt(c, "‹ Назад", 34, l.pageY, 11, view.muted);
    view.txt(c, (ui.page + 1) + " / " + ui.pages(l), 190, l.pageY, 11, view.text);
    view.txt(c, "Далее ›", 326, l.pageY, 11, view.muted);
    if (!ui.message.isEmpty()) view.txt(c, ui.message, 34, l.actionTop - 9, 10, view.danger);
    button(c, 30, l.actionTop, 390, 48, "НАЗАД К ПАНЕЛИ", false);
    button(c, 30, l.secondaryTop, 390, 43, "ЗАКРЫТЬ", false);
  }

  private void body(Canvas c, RoomUpgradeLayout l, List<String> rows) {
    List<String> lines = new ArrayList<>();
    view.p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
    view.p.setTextSize(view.sy(12));
    for (String row : rows) {
      String line = "";
      for (String word : row.split(" ")) {
        if (!line.isEmpty() && view.p.measureText(line + " " + word) > view.sy(350)) {
          lines.add(line);
          line = "";
        }
        line += (line.isEmpty() ? "" : " ") + word;
      }
      lines.add(line);
    }
    DefensePanelController ui = view.defensePanel;
    ui.lineCount = lines.size();
    int scroll = Math.min(ui.scroll, Math.max(0, lines.size() - l.visibleLines));
    for (int i = 0; i < l.visibleLines && scroll + i < lines.size(); i++)
      view.txt(c, lines.get(scroll + i), 34, l.top + 93 + i * 18, 12, view.text);
    if (lines.size() > l.visibleLines)
      view.txt(c, "↑ ↓ Прокрутите панель", 34, l.actionTop - 10, 9, view.muted);
  }

  private void button(
      Canvas c, float left, float top, float right, float height, String text, boolean primary) {
    view.box(c, left, top, right, top + height, primary ? view.accent : view.panel2, 10);
    view.bold(c, text, left + 12, top + height / 2 + 5, 11, primary ? view.bg : view.text);
  }

  boolean noticeVisible() {
    return !view.defensePanel.open
        && view.game.overlay == 0
        && !view.game.event
        && !view.game.jobMenu
        && !view.game.gameOver
        && !view.cityMap.eventPanel
        && !view.cityMap.expeditionPanel
        && view.game.raidController.active() != null;
  }

  void notice(Canvas c) {
    if (!noticeVisible()) return;
    RaidState raid = view.game.raidController.active();
    float top = view.H / view.scale - 144;
    view.box(c, 18, top, 402, top + 52, view.panel, 10);
    view.bold(
        c,
        raid.phase == RaidState.Phase.ATTACK ? "НАПАДЕНИЕ НА УБЕЖИЩЕ" : "УГРОЗА УБЕЖИЩУ",
        30,
        top + 20,
        12,
        view.danger);
    view.txt(
        c,
        raid.enemyName() + " • " + raid.remaining() + " мин. • нажмите для обороны",
        30,
        top + 39,
        11,
        view.accent);
  }

  void indicator(Canvas c, float top, float bottom) {
    RaidController controller = view.game.raidController;
    if (controller.durability == 100 && controller.active() == null && !controller.repairing())
      return;
    float[] rect = ShelterGeometry.fullSceneRoomRect(4, top, bottom);
    float x = rect[0] + 4, y = rect[1] + 26;
    view.box(c, x, y, x + 94, y + 18, Color.argb(205, 14, 18, 22), 5);
    view.bold(
        c,
        "ЩИТ " + controller.durability + "%" + (controller.active() != null ? " !" : ""),
        x + 6,
        y + 12,
        9,
        controller.active() != null ? view.danger : view.accent);
    if (controller.repairing()) {
      view.box(c, x, y + 20, x + 94, y + 37, Color.argb(205, 14, 18, 22), 5);
      view.txt(
          c,
          "РЕМОНТ " + controller.repair.elapsed * 100 / RaidConfig.REPAIR_MINUTES + "%",
          x + 6,
          y + 32,
          9,
          view.accent);
    }
  }
}
