package com.lastdom.game;

import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cached presentation data only; never resolves a battle, charges a repair or saves gameplay. */
final class DefensePanelContent {
  static final Typeface NORMAL = Typeface.create("sans", Typeface.NORMAL);
  static final Typeface BOLD = Typeface.create("sans", Typeface.BOLD);

  static final class Block {
    String title, leftLabel, rightLabel, leftValue, rightValue, percentLabel;
    List<String> lines, headings;
    int color, percent = -1, leftValueSize = 17, rightValueSize = 12;
    float height;
  }

  static final class ResidentRow {
    String id;
    List<String> title, detail;
    String condition;
    boolean selected, available;
  }

  final List<Block> blocks = new ArrayList<>();
  final List<ResidentRow> residents = new ArrayList<>();
  final GameView view;
  final String title, subtitle, primary, secondary, listSummary;
  final boolean primaryEnabled, secondaryEnabled, secondaryVisible;
  float height;
  int rowHeight = 76, listHeaderHeight = 42;

  DefensePanelContent(GameView view, double currentPower) {
    this.view = view;
    DefensePanelController ui = view.defensePanel;
    RaidController controller = view.game.raidController;
    RaidState raid = ui.report, active = controller.active();
    boolean repair = ui.mode == DefensePanelController.Mode.REPAIR;
    boolean battle = raid != null && raid.phase == RaidState.Phase.ATTACK;
    boolean result = raid != null && !raid.active();
    title =
        ui.mode == DefensePanelController.Mode.DEFENDERS
            ? "ПОДГОТОВКА ОБОРОНЫ"
            : ui.mode == DefensePanelController.Mode.BUILDERS
                ? "ВЫБОР РЕМОНТНИКА"
                : repair
                    ? "РЕМОНТ БАРРИКАД"
                    : result
                        ? "РЕЗУЛЬТАТ ОБОРОНЫ"
                        : battle
                            ? "НАПАДЕНИЕ НА УБЕЖИЩЕ"
                            : raid != null ? "ПРИБЛИЖАЕТСЯ НАПАДЕНИЕ" : "ОБОРОНА УБЕЖИЩА";
    subtitle = "Баррикады • уровень " + view.game.roomLevels[4];
    primary =
        ui.list()
            ? "НАЗАД К ПАНЕЛИ"
            : repair
                ? controller.repairing() ? "РЕМОНТ ИДЁТ" : "ВЫБРАТЬ РЕМОНТНИКА"
                : result
                    ? "ПОНЯТНО"
                    : active != null
                        ? active.phase == RaidState.Phase.ATTACK
                            ? "НАПАДЕНИЕ ИДЁТ"
                            : "ПОДГОТОВИТЬ ОБОРОНУ"
                        : "УГРОЗЫ НЕТ";
    primaryEnabled =
        ui.list()
            || (repair
                ? controller.repairReason().isEmpty()
                : result || active != null && active.phase != RaidState.Phase.ATTACK);
    secondary = repair ? "К ОБОРОНЕ" : "РЕМОНТ";
    secondaryEnabled = repair || ui.repairAccessible();
    secondaryVisible = !ui.list() && (!result || repair || secondaryEnabled);
    listSummary =
        ui.mode == DefensePanelController.Mode.DEFENDERS && active != null
            ? "Защита " + number(currentPower) + " • до атаки " + active.remaining() + " мин."
            : "Ремонт: "
                + RaidConfig.REPAIR_COST
                + " материалов • "
                + RaidConfig.REPAIR_MINUTES
                + " мин.";

    if (ui.list()) {
      ui.refreshResidents();
      for (String residentId : ui.residentIds) {
        Resident resident = view.game.expeditionController.resident(residentId);
        ResidentRow row = new ResidentRow();
        row.id = residentId;
        row.selected = controller.defending(resident);
        String reason =
            ui.mode == DefensePanelController.Mode.DEFENDERS
                ? controller.defenderReason(resident)
                : view.game.roomUpgradeController.unavailableReason(resident);
        row.available = row.selected || reason.isEmpty();
        row.title =
            wrap(resident.name + " • " + resident.role + (row.selected ? " ✓" : ""), 12, 336, true);
        row.condition =
            "Здоровье "
                + resident.health
                + " • усталость "
                + resident.fatigue
                + " • мораль "
                + resident.morale;
        RoomAssignmentController assignments = view.game.roomAssignmentController;
        String category =
            row.selected
                ? "УЖЕ НАЗНАЧЕН • нажмите, чтобы снять"
                : assignments.availableCategory(resident).label;
        row.detail =
            wrap(
                "Сейчас: "
                    + resident.job
                    + "\n"
                    + category
                    + "\n"
                    + assignments.profession(
                        resident,
                        4,
                        "Охрана",
                        ui.mode == DefensePanelController.Mode.BUILDERS,
                        ui.mode == DefensePanelController.Mode.DEFENDERS)
                    + (row.selected ? "" : "\n" + assignments.transfer(resident))
                    + (ui.mode == DefensePanelController.Mode.DEFENDERS
                        ? "\nСила " + number(RaidResolver.residentPower(resident))
                        : ""),
                11,
                336,
                false);
        rowHeight =
            Math.max(
                rowHeight,
                26 + 16 * (row.title.size() + row.detail.size()) + DefensePanelLayout.ROW_GAP);
        residents.add(row);
      }
      if (!ui.message.isEmpty()) card("НАЗНАЧЕНИЕ", ui.message, view.danger);
      listHeaderHeight += (int) height;
      height = listHeaderHeight + 32 + Math.max(1, Math.min(5, residents.size())) * rowHeight;
      return;
    }

    if (repair) {
      bar(
          "ПРОЧНОСТЬ БАРРИКАД",
          controller.durability,
          "Восстановление: +" + RaidConfig.REPAIR_AMOUNT + "% (не выше 100%)",
          durabilityColor(controller.durability));
      stats(
          "МАТЕРИАЛЫ",
          RaidConfig.REPAIR_COST + " / доступно " + view.game.mats,
          "ДЛИТЕЛЬНОСТЬ",
          RaidConfig.REPAIR_MINUTES + " мин.",
          view.accent);
      if (controller.repairing()) {
        BarricadeRepair task = controller.repair;
        Resident builder = view.game.expeditionController.resident(task.builderId);
        bar(
            "РЕМОНТ ИДЁТ",
            task.elapsed * 100 / RaidConfig.REPAIR_MINUTES,
            "Осталось: " + task.remaining() + " игровых минут",
            view.accent);
        card(
            "РЕМОНТНИК",
            (builder == null ? "—" : builder.name) + "\nСтоимость уже оплачена.",
            view.text);
      } else {
        String reason = controller.repairReason();
        card(
            reason.isEmpty() ? "НАЗНАЧЕНИЕ" : "РЕМОНТ НЕДОСТУПЕН",
            reason.isEmpty()
                ? "Выберите свободного жителя. На время ремонта он прекратит обычную работу."
                : reason,
            reason.isEmpty() ? view.text : view.danger);
      }
    } else if (raid == null) {
      bar(
          "ПРОЧНОСТЬ БАРРИКАД",
          controller.durability,
          "Текущая сила обороны: " + number(currentPower),
          durabilityColor(controller.durability));
      card(
          "УГРОЗ НЕТ",
          "При предупреждении назначьте защитников. Первые игровые сутки безопасны.",
          view.good);
    } else {
      if (result) {
        card(
            raid.outcome == RaidState.Outcome.DEFENDED
                ? "ОБОРОНА УСПЕШНА"
                : raid.outcome == RaidState.Outcome.PARTIAL_BREACH
                    ? "ЧАСТИЧНЫЙ ПРОРЫВ"
                    : "ОБОРОНА ПРОВАЛЕНА",
            raid.enemyName(),
            raid.outcome == RaidState.Outcome.DEFENDED
                ? view.good
                : raid.outcome == RaidState.Outcome.PARTIAL_BREACH ? view.accent : view.danger);
      }
      stats(
          battle || result ? "Сила защиты в бою" : "Сила текущей обороны",
          number(battle || result ? raid.defenseAtStart : currentPower),
          raid.enemyName(),
          "Сила врага: " + raid.attackPower,
          strengthColor(battle || result ? raid.defenseAtStart : currentPower, raid.attackPower));
      if (raid.active()) {
        bar(
            battle ? "ПРОГРЕСС НАПАДЕНИЯ" : "ДО НАПАДЕНИЯ: " + raid.remaining() + " МИН.",
            raid.progress(),
            battle
                ? "Осталось: " + raid.remaining() + " игровых минут"
                : "Убежище продолжает работать",
            battle ? view.danger : view.accent);
      }
      bar(
          "ПРОЧНОСТЬ БАРРИКАД",
          controller.durability,
          result || battle
              ? "Текущая сила обороны: " + number(currentPower)
              : "Ремонт: +"
                  + RaidConfig.REPAIR_AMOUNT
                  + "% за "
                  + RaidConfig.REPAIR_MINUTES
                  + " игровых минут",
          durabilityColor(controller.durability));
      card(
          battle
              ? "УЧАСТНИКИ ОБОРОНЫ • СОСТАВ ЗАФИКСИРОВАН"
              : result ? "УЧАСТНИКИ ОБОРОНЫ" : "НАЗНАЧЕННЫЕ ЗАЩИТНИКИ",
          names(raid),
          view.text);
      if (result) {
        card(
            "ПОСЛЕДСТВИЯ",
            "Повреждения баррикад: −"
                + raid.damageApplied
                + "%\n"
                + "Потеря еды: "
                + raid.foodLost
                + " • воды: "
                + raid.waterLost
                + " • материалов: "
                + raid.materialsLost
                + "\nИзменение морали: "
                + (raid.moraleChange > 0 ? "+" : "")
                + raid.moraleChange
                + " (штраф за травмы — отдельно)",
            view.accent);
        StringBuilder injured = new StringBuilder();
        for (String id : raid.injuries.keySet())
          if (raid.injuries.get(id) > 0) {
            if (injured.length() > 0) injured.append('\n');
            injured
                .append(raid.victimNames.getOrDefault(id, "Житель"))
                .append(": здоровье −")
                .append(raid.injuries.get(id));
          }
        card(
            "ПОСТРАДАВШИЕ",
            injured.length() == 0 ? "Пострадавших нет" : injured.toString(),
            injured.length() == 0 ? view.good : view.danger);
      }
    }
    if (!ui.message.isEmpty()) card("УВЕДОМЛЕНИЕ", ui.message, view.danger);
  }

  private int strengthColor(double strength, int attack) {
    double ratio = strength / attack;
    return ratio >= RaidConfig.MAX_SUCCESS_RATIO
        ? view.good
        : ratio >= RaidConfig.PARTIAL_RATIO ? view.accent : view.danger;
  }

  int durabilityColor(int value) {
    return value >= 70 ? view.good : value >= 35 ? view.accent : view.danger;
  }

  private String names(RaidState raid) {
    StringBuilder names = new StringBuilder();
    for (String id : raid.defenders.keySet()) {
      Resident resident = view.game.expeditionController.resident(id);
      if (names.length() > 0) names.append(" • ");
      names.append(raid.victimNames.getOrDefault(id, resident == null ? "Житель" : resident.name));
    }
    return names.length() == 0 ? "Не назначены" : names.toString();
  }

  private void card(String title, String detail, int color) {
    Block block = new Block();
    block.title = title;
    block.lines = wrap(detail, 13, 336, false);
    // Titles wrap too, so frozen-roster wording and long translated labels never clip.
    block.headings = wrap(title, 11, 336, true);
    block.color = color;
    block.height = 24 + block.headings.size() * 15 + block.lines.size() * 18;
    add(block);
  }

  private void stats(
      String leftLabel, String leftValue, String rightLabel, String rightValue, int color) {
    Block block = new Block();
    block.leftLabel = leftLabel;
    block.leftValue = leftValue;
    block.rightLabel = rightLabel;
    block.rightValue = rightValue;
    block.leftValueSize = fitValue(leftValue, 17);
    block.rightValueSize = fitValue(rightValue, 12);
    block.color = color;
    block.height = 72;
    add(block);
  }

  private int fitValue(String value, int size) {
    view.p.setTypeface(BOLD);
    while (size > 10) {
      view.p.setTextSize(view.sy(size));
      if (view.p.measureText(value) <= view.sy(152)) break;
      size--;
    }
    return size;
  }

  private void bar(String title, int percent, String detail, int color) {
    Block block = new Block();
    block.title = title;
    block.percent = Math.max(0, Math.min(100, percent));
    block.percentLabel = block.percent + "%";
    block.lines = wrap(detail, 12, 336, false);
    block.height = 58 + block.lines.size() * 16;
    block.color = color;
    add(block);
  }

  private void add(Block block) {
    blocks.add(block);
    height += block.height + 8;
  }

  List<String> wrap(String text, int size, float width, boolean bold) {
    List<String> lines = new ArrayList<>();
    view.p.setTypeface(bold ? BOLD : NORMAL);
    view.p.setTextSize(view.sy(size));
    for (String paragraph : text.split("\n", -1)) {
      String rest = paragraph;
      while (view.p.measureText(rest) > view.sy(width)) {
        int end = rest.length();
        while (end > 1 && view.p.measureText(rest.substring(0, end)) > view.sy(width)) end--;
        int space = rest.lastIndexOf(' ', end);
        if (space > 0) end = space;
        lines.add(rest.substring(0, end));
        rest = rest.substring(end).trim();
      }
      lines.add(rest);
    }
    return lines;
  }

  static String number(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }
}
