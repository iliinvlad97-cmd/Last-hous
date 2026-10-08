package com.lastdom.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One read-only room card presenter; all rates/quotes come from the real controllers/config. */
final class ProductionRenderer {
  private final GameView view;
  private List<RoomEfficiencyRenderer.Row> cached;
  private long cachedSignature;

  ProductionRenderer(GameView view) {
    this.view = view;
  }

  List<RoomEfficiencyRenderer.Row> card(int room) {
    long key = signature(room);
    if (cached != null && key == cachedSignature) return cached;
    cachedSignature = key;
    List<RoomEfficiencyRenderer.Row> rows = new ArrayList<>();
    GameController g = view.game;
    upgrade(rows, room);
    normal(rows, (room == 5 ? "Отдыхают: " : "Работают: ") + g.occupants(room));
    section(rows, "ПРОИЗВОДСТВО / ПОЛЕЗНЫЙ ЭФФЕКТ");
    if (g.gameOver) warning(rows, "Игра завершена: начисление и прогресс остановлены");
    else if (g.paused) warning(rows, "Пауза: начисление и прогресс остановлены");
    else muted(rows, "Скорости ниже — за игровые сутки/часы; сейчас ×" + g.speed);
    switch (room) {
      case 0:
        generator(rows);
        break;
      case 1:
        kitchen(rows);
        break;
      case 2:
        clinic(rows);
        break;
      case 3:
        workshop(rows);
        break;
      case 4:
        barricades(rows);
        break;
      default:
        bedroom(rows);
    }
    workers(rows, room);
    accounting(rows, room);
    if (!view.roomUpgradePanel.message.isEmpty()) warning(rows, view.roomUpgradePanel.message);
    cached = rows;
    return rows;
  }

  private void upgrade(List<RoomEfficiencyRenderer.Row> rows, int room) {
    GameController g = view.game;
    section(rows, "УЛУЧШЕНИЕ");
    RoomUpgradeTask active = g.roomUpgradeController.active();
    if (active != null && active.room == room) {
      Resident r = g.expeditionController.resident(active.builderId);
      good(rows, "УЛУЧШЕНИЕ ДО УРОВНЯ " + active.targetLevel);
      normal(rows, "Строитель: " + (r == null ? "ожидание жителя" : r.name));
      normal(
          rows, "Прогресс: " + active.progress() + "% • осталось " + active.remaining() + " мин.");
      normal(rows, "Оплачено: " + active.paidCost + " материалов");
      good(rows, "После улучшения: " + forecast(room, active.targetLevel));
    } else if (g.roomLevels[room] >= RoomUpgradeConfig.MAX_LEVEL)
      normal(rows, "Максимальный уровень");
    else {
      int level = g.roomLevels[room] + 1;
      ProductionController.UpgradeQuote quote =
          g.productionController.upgradeQuote(RoomUpgradeConfig.cost(room, level));
      normal(rows, "Стоимость: " + quote.cost + " материалов • доступно: " + g.mats);
      normal(
          rows,
          "Уровень "
              + g.roomLevels[room]
              + " → "
              + level
              + " • "
              + RoomUpgradeConfig.minutes(room, level)
              + " игровых минут");
      good(rows, "После улучшения: " + forecast(room, level));
      if (quote.saved > 0) good(rows, "Базовая цена " + quote.base + " • экономия " + quote.saved);
      if (g.mats < quote.cost) warning(rows, "Не хватает материалов: " + (quote.cost - g.mats));
      if (active != null) warning(rows, "Идёт строительство: " + g.rooms[active.room]);
      Resident r = g.expeditionController.resident(view.roomUpgradePanel.builderId);
      if (r != null) {
        normal(rows, "Строитель: " + r.name);
        String reason = g.roomUpgradeController.unavailableReason(r);
        if (!reason.isEmpty()) warning(rows, reason);
      }
    }
    muted(rows, "Прогноз: при нынешнем составе, запасах и состоянии");
  }

  private String forecast(int room, int level) {
    GameController g = view.game;
    ProductionController p = g.productionController;
    switch (room) {
      case 0:
        return number(p.energyPerDay(level)) + " энергии/день";
      case 1:
        return "экономия еды " + number(p.kitchenSavingBasis(level) / 100.0) + "%";
      case 2:
        return number(p.medicalPerDay(level)) + " здоровья/день на пациента";
      case 3:
        return "экономия материалов " + number(p.workshopSavingBasis(level) / 100.0) + "%";
      case 4:
        return "сила " + number(g.raidController.defensePower(level)) + " при той же прочности";
      default:
        return "−" + SurvivalConfig.bedroomRecoveryPerHour(level) + " усталости/час";
    }
  }

  private void generator(List<RoomEfficiencyRenderer.Row> rows) {
    GameController g = view.game;
    ProductionController p = g.productionController;
    normal(rows, "Базовая выработка: " + RoomUpgradeConfig.BASE_ENERGY_PER_DAY + " энергии/день");
    bonus(rows, 0);
    good(
        rows,
        "Бонус работников: +"
            + (p.generatorPercent() - 100)
            + "% (макс. "
            + ProductionConfig.GENERATOR_MAX_BOOST
            + "%)");
    condition(rows, 0);
    normal(rows, "Итоговая выработка сейчас: +" + number(p.energyPerDay()) + " энергии/день");
    normal(rows, "Потребность убежища: 1 энергии/день; списание при смене суток");
    balance(rows, p.energyPerDay(), 1, "энергии/день — оценка при достаточном запасе");
    muted(rows, "Материалы не требуются; выработка до запаса " + ProductionConfig.ENERGY_CAPACITY);
    if (g.power >= ProductionConfig.ENERGY_CAPACITY)
      warning(rows, "Остановлено: накоплен запас. После расхода выработка возобновится");
    normal(
        rows,
        "Обслуживание: +" + number(p.maintenancePerDay(false)) + " прочности дома/день (до 100%)");
    normal(
        rows, "Оборудование: +" + number(p.maintenancePerDay(true)) + " состояния/день (до 100%)");
  }

  private void kitchen(List<RoomEfficiencyRenderer.Row> rows) {
    GameController g = view.game;
    ProductionController p = g.productionController;
    normal(rows, "Производство еды: 0. Функция: приготовление запасов");
    bonus(rows, 1);
    condition(rows, 1);
    good(
        rows, "Экономия новых пайков: " + number(p.kitchenSavingBasis() / 100.0) + "% (макс. 25%)");
    normal(rows, "1 еда → " + p.foodRationMinutes() + " минут питания дома");
    normal(
        rows,
        "Жителей дома: "
            + p.homeResidents()
            + " • базовая потребность: "
            + p.homeResidents()
            + " еды/день");
    normal(rows, "Потребность новых пайков: ≈" + number(p.foodDemandPerDay()) + " еды/день");
    balance(rows, 0, p.foodDemandPerDay(), "еды/день — оценка, не гарантированное списание");
    muted(
        rows,
        "Оценка: неизменный состав и достаточные запасы. Уже оплаченные пайки не пересчитываются");
    if (g.food <= 0) warning(rows, "Нет еды для новых пайков; оплаченные сохраняются");
    if (p.staffPercent(1, "Еда") == 0)
      warning(rows, "Нет доступных поваров; обычный паёк 1440 минут");
    normal(
        rows,
        "Вода: база "
            + ProductionConfig.WATER_PER_WORKER_DAY
            + "/день на работника × состояние жителя");
    normal(rows, "Итог воды: +" + number(p.waterPerDay()) + "/день; бонус кухни не применяется");
    balance(rows, p.waterPerDay(), p.homeResidents(), "воды/день — оценка новых пайков");
    if (g.water >= ProductionConfig.WATER_CAPACITY)
      warning(rows, "Получение воды остановлено: накоплен запас");
    else if (!hasWorker("Вода")) warning(rows, "Нет работников «Вода»");
    int fed = 0, watered = 0;
    for (Resident r : g.people)
      if (r.alive && !g.isOnExpedition(r)) {
        if (r.foodMinutes > 0) fed++;
        if (r.waterMinutes > 0) watered++;
      }
    normal(rows, "Оплаченные пайки: еда " + fed + " • вода " + watered);
  }

  private void clinic(List<RoomEfficiencyRenderer.Row> rows) {
    GameController g = view.game;
    ProductionController p = g.productionController;
    normal(
        rows, "Базовая скорость состава: " + p.medicalWeight() / 2 + " здоровья/день на пациента");
    bonus(rows, 2);
    condition(rows, 2);
    normal(
        rows,
        "Эффективность медиков: " + number(p.medicalEfficiency() * 100) + "% (взвешено по навыку)");
    normal(
        rows,
        "Итог лечения сейчас: +"
            + number(p.medicalPerDay())
            + " здоровья/день на пациента (до 100%)");
    int injured = 0;
    for (Resident r : g.people) if (r.alive && !g.isOnExpedition(r) && r.health < 100) injured++;
    normal(rows, "Нуждаются дома: " + injured + " • подходят для лечения: " + p.patients());
    normal(
        rows,
        "Расход: 1 медикамент / "
            + ProductionConfig.MEDICINE_DOSE_MINUTES
            + " активных минут клиники; общая доза");
    normal(
        rows,
        "Склад: "
            + g.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE)
            + " • оплачено ещё "
            + g.production.medicineMinutes
            + " активных минут");
    if (g.production.medicineMinutes > 0)
      normal(
          rows,
          "Доза использована: "
              + (ProductionConfig.MEDICINE_DOSE_MINUTES - g.production.medicineMinutes)
                  * 100
                  / ProductionConfig.MEDICINE_DOSE_MINUTES
              + "%");
    if (!hasWorker("Лечение")) warning(rows, "Лечение остановлено: нет доступного медика");
    else if (p.clinicalRateHundredths(g.roomLevels[2]) == 0)
      warning(rows, "Лечение остановлено: недостаточная эффективность состава");
    else if (!p.hasPatient()) muted(rows, "Лечение сейчас не требуется / нет подходящих пациентов");
    else if (!p.medicineAvailable())
      warning(rows, "Лечение остановлено: нет медикаментов или оборудование сломано");
    muted(
        rows,
        "Критические потребности ухудшают здоровье отдельно. Пассивный отдых не расходует"
            + " медикаменты");
  }

  private void workshop(List<RoomEfficiencyRenderer.Row> rows) {
    GameController g = view.game;
    ProductionController p = g.productionController;
    normal(rows, "Производство материалов: 0. Функция: экономная обработка");
    bonus(rows, 3);
    condition(rows, 3);
    good(rows, "Экономия улучшений: " + number(p.workshopSavingBasis() / 100.0) + "% (макс. 15%)");
    normal(rows, "Расход: только при подтверждении улучшения; скидка учтена в цене");
    normal(rows, "Сэкономлено за игру: " + g.production.materialsSaved + " материалов");
    normal(rows, "Запас материалов: " + g.mats);
    if (!hasWorker("Материалы")) warning(rows, "Нет доступных работников; скидка не применяется");
    if (g.mats <= 0) warning(rows, "Нет материалов; ожидает сырьё из экспедиций");
    muted(rows, "Дробная экономия сохраняется; ремонт баррикад без скидки");
  }

  private void bedroom(List<RoomEfficiencyRenderer.Row> rows) {
    GameController g = view.game;
    int speed = SurvivalConfig.bedroomRecoveryPerHour(g.roomLevels[5]);
    normal(
        rows,
        "Базовый отдых: −" + SurvivalConfig.bedroomRecoveryPerHour(1) + " усталости/игровой час");
    good(
        rows,
        "Бонус уровня к отдыху: +"
            + number((speed / (double) SurvivalConfig.bedroomRecoveryPerHour(1) - 1) * 100)
            + "%");
    normal(rows, "Итог восстановления: −" + speed + " пунктов/час на отдыхающего, до 0");
    int resting = 0, recovering = 0;
    for (Resident r : g.people)
      if (r.alive && g.survivalController.resting(r)) {
        resting++;
        if (r.fatigue > 0 || r.survivalFractions.getOrDefault("fatigue", 0) > 0) recovering++;
      }
    normal(rows, "Отдыхающих: " + resting + " • ещё восстанавливают усталость: " + recovering);
    normal(rows, "0% — полностью отдохнул; 100% — полностью устал");
    normal(rows, "Состояние комнаты и штрафы труда не замедляют отдых в текущих правилах");
    normal(
        rows,
        "Пассивное восстановление: +"
            + SurvivalConfig.REST_HEALTH_PER_DAY
            + " здоровья/день при допустимых потребностях");
    muted(
        rows,
        "Рабочая усталость не начисляется. Автоматический отдых заканчивается при точном нуле");
  }

  private void barricades(List<RoomEfficiencyRenderer.Row> rows) {
    GameController g = view.game;
    RaidController p = g.raidController;
    normal(rows, "Прочность баррикад: " + p.durability + "%");
    normal(rows, "Базовая сила при 100% прочности: " + RaidConfig.BASE_BARRICADE_POWER);
    bonus(rows, 4);
    double fixed = RaidResolver.barricadePower(g.roomLevels[4], p.durability);
    normal(rows, "Сила баррикад сейчас: " + number(fixed));
    good(rows, "Бонус назначенных защитников: +" + number(p.defensePower() - fixed));
    normal(rows, "Текущая сила обороны: " + number(p.defensePower()));
    RaidState raid = p.latest();
    if (raid != null && raid.resultGenerated)
      normal(
          rows,
          "Сила защиты в бою: " + number(raid.defenseAtStart) + " • зафиксирована при начале боя");
    else muted(rows, "Сила в бою будет зафиксирована при начале нападения");
    normal(rows, "Защитники: " + defenderNames(p.active()));
    if (p.active() != null && p.active().phase == RaidState.Phase.ATTACK)
      muted(rows, "Изменения нынешней обороны не пересчитывают зафиксированную силу этого боя");
    normal(rows, "Состояние помещения не влияет на бой: используется прочность баррикад");
    normal(
        rows, "Охрана: −" + number(g.productionController.guardsPerDay()) + " угрозы/день (до 0)");
    normal(
        rows,
        "Ремонт: "
            + RaidConfig.REPAIR_COST
            + " материалов, "
            + RaidConfig.REPAIR_MINUTES
            + " мин., +"
            + RaidConfig.REPAIR_AMOUNT
            + "% (до 100%)");
    String reason = p.repairReason();
    if (!reason.isEmpty()) warning(rows, "Ремонт: " + reason);
    if (p.repairing()) {
      Resident r = g.expeditionController.resident(p.repair.builderId);
      normal(
          rows,
          "Ремонтник: "
              + (r == null ? "—" : r.name)
              + " • осталось "
              + p.repair.remaining()
              + " мин.");
    }
  }

  private String defenderNames(RaidState raid) {
    if (raid == null || raid.defenders.isEmpty()) return "—";
    StringBuilder s = new StringBuilder();
    for (String id : raid.defenders.keySet()) {
      Resident r = view.game.expeditionController.resident(id);
      if (s.length() > 0) s.append(", ");
      s.append(r == null ? "Житель" : r.name);
    }
    return s.toString();
  }

  private void workers(List<RoomEfficiencyRenderer.Row> rows, int room) {
    GameController g = view.game;
    section(rows, "РАБОТНИКИ");
    normal(rows, (room == 5 ? "Отдыхают: " : "Работают: ") + g.occupants(room));
    RoomUpgradeTask task = g.roomUpgradeController.active();
    if (task != null && task.room == room) {
      Resident r = g.expeditionController.resident(task.builderId);
      normal(rows, "Строитель отдельно: " + (r == null ? "—" : r.name) + "; не участвует в работе");
    }
    for (Resident r : g.people)
      if (r.alive && !g.isOnExpedition(r) && g.homeRoomFor(r) == room && !g.isBuilding(r)) {
        normal(rows, r.name + " • " + r.role + " • навык " + r.skill + " • " + r.job);
        if (g.survivalController.working(r)) {
          double condition = SurvivalConfig.efficiency(r) * 100;
          normal(
              rows,
              "Состояние жителя: "
                  + number(condition)
                  + "% • здоровье "
                  + r.health
                  + ", усталость "
                  + r.fatigue
                  + ", мораль "
                  + r.morale);
          String penalties = penalties(r);
          if (!penalties.isEmpty()) warning(rows, "Штрафы: " + penalties);
          if (room == 0 || room == 3 || room == 1 && r.job.equals("Еда")) {
            good(
                rows,
                "Навык ×"
                    + number(ProductionConfig.skillPercent(r.skill) / 100.0)
                    + " • профессия ×"
                    + number(ProductionConfig.professionPercent(r, room) / 100.0));
            normal(
                rows,
                "Вклад до бонуса комнаты: " + g.productionController.workerQuality(r, room) + "%");
          } else if (r.job.equals("Лечение"))
            normal(
                rows,
                "Медицинский вклад: "
                    + number((r.skill + 1) * SurvivalConfig.efficiency(r))
                    + " из "
                    + (r.skill + 1));
        } else if (room == 5) {
          double fatigue =
              r.fatigue
                  + r.survivalFractions.getOrDefault("fatigue", 0)
                      / (double) SurvivalController.NEED_DENOMINATOR;
          normal(
              rows,
              "Усталость " + number(fatigue) + "% • здоровье " + r.health + ", мораль " + r.morale);
          normal(
              rows,
              "Отдых: −"
                  + (fatigue > 0 ? SurvivalConfig.bedroomRecoveryPerHour(g.roomLevels[5]) : 0)
                  + " пунктов/час сейчас, до 0");
        } else if (g.isDefending(r)) {
          normal(
              rows, "Эффективность состояния: " + number(SurvivalConfig.efficiency(r) * 100) + "%");
          String penalties = penalties(r);
          if (!penalties.isEmpty()) warning(rows, "Штрафы: " + penalties);
          normal(rows, "Вклад в текущую оборону: " + number(g.raidController.defenderPower(r)));
        } else muted(rows, "Не участвует в производстве: " + r.job);
      }
    muted(rows, "Экспедиции, строительство, оборона, отдых и лечение исключены из производства");
  }

  private static String penalties(Resident r) {
    StringBuilder s = new StringBuilder();
    penalty(s, "здоровье", r.health / 100.0);
    penalty(s, "голод", SurvivalConfig.needFactor(r.hunger));
    penalty(s, "жажда", SurvivalConfig.needFactor(r.thirst));
    penalty(s, "усталость", SurvivalConfig.needFactor(r.fatigue));
    penalty(s, "мораль", SurvivalConfig.moraleFactor(r.morale));
    return s.toString();
  }

  private static void penalty(StringBuilder s, String name, double factor) {
    if (factor < 1) {
      if (s.length() > 0) s.append("; ");
      s.append(name).append(" −").append(number((1 - factor) * 100)).append('%');
    }
  }

  private void accounting(List<RoomEfficiencyRenderer.Row> rows, int room) {
    ResourceAccounting a = view.game.resourceAccounting;
    section(rows, "ФАКТИЧЕСКИЙ УЧЁТ РЕСУРСОВ");
    normal(
        rows,
        "Всего по убежищу • день "
            + a.day
            + " • с "
            + String.format(Locale.ROOT, "%02d:%02d", a.startMinute / 60, a.startMinute % 60));
    int[] resources =
        room == 0
            ? new int[] {0}
            : room == 1 || room == 5 ? new int[] {1, 2} : room == 2 ? new int[] {4} : new int[] {3};
    for (int i : resources) {
      normal(
          rows,
          ResourceAccounting.NAMES[i]
              + ": производство +"
              + a.produced[i]
              + " • расход −"
              + a.used[i]);
      muted(
          rows,
          "Прочие изменения: " + signed(a.other[i]) + " • итог запаса: " + signed(a.change(i)));
      if (a.previousDay > 0)
        muted(
            rows,
            "За день "
                + a.previousDay
                + " (с "
                + String.format(
                    Locale.ROOT, "%02d:%02d", a.previousStart / 60, a.previousStart % 60)
                + "): +"
                + a.previousProduced[i]
                + " −"
                + a.previousUsed[i]
                + "; прочие "
                + signed(a.previousOther[i])
                + "; итог "
                + signed(a.previousProduced[i] - a.previousUsed[i] + a.previousOther[i]));
    }
    muted(
        rows,
        "Производство − расход + прочие = изменение запасов. Прочие: добыча, улучшения, ремонт и"
            + " потери");
  }

  private void bonus(List<RoomEfficiencyRenderer.Row> rows, int room) {
    good(
        rows,
        "Бонус уровня: +"
            + (view.game.roomUpgradeController.percent(room) - 100)
            + "% к базовому эффекту");
  }

  private void condition(List<RoomEfficiencyRenderer.Row> rows, int room) {
    if (view.game.roomCondition[room] <= 0)
      warning(rows, "Помещение сломано: основной эффект остановлен");
    else
      muted(rows, "Состояние помещения: коэффициент 1,00; остановка при 0%, без скрытого штрафа");
  }

  private boolean hasWorker(String job) {
    for (Resident r : view.game.people)
      if (view.game.productionController.worker(r, job)) return true;
    return false;
  }

  private static void balance(
      List<RoomEfficiencyRenderer.Row> rows, double input, double output, String unit) {
    rows.add(
        new RoomEfficiencyRenderer.Row(
            number(input) + " − " + number(output) + " = " + number(input - output) + " " + unit,
            input >= output ? RoomEfficiencyRenderer.GOOD : RoomEfficiencyRenderer.WARNING));
  }

  private static String number(double v) {
    return String.format(Locale.ROOT, "%.2f", v);
  }

  private static String signed(long v) {
    return (v >= 0 ? "+" : "") + v;
  }

  private static void section(List<RoomEfficiencyRenderer.Row> r, String s) {
    r.add(new RoomEfficiencyRenderer.Row(s, RoomEfficiencyRenderer.SECTION));
  }

  private static void normal(List<RoomEfficiencyRenderer.Row> r, String s) {
    r.add(new RoomEfficiencyRenderer.Row(s, RoomEfficiencyRenderer.NORMAL));
  }

  private static void muted(List<RoomEfficiencyRenderer.Row> r, String s) {
    r.add(new RoomEfficiencyRenderer.Row(s, RoomEfficiencyRenderer.MUTED));
  }

  private static void good(List<RoomEfficiencyRenderer.Row> r, String s) {
    r.add(new RoomEfficiencyRenderer.Row(s, RoomEfficiencyRenderer.GOOD));
  }

  private static void warning(List<RoomEfficiencyRenderer.Row> r, String s) {
    r.add(new RoomEfficiencyRenderer.Row(s, RoomEfficiencyRenderer.WARNING));
  }

  private long signature(int room) {
    GameController g = view.game;
    long h = room;
    h = h * 31 + g.day;
    h = h * 31 + g.gameMinute;
    h = h * 31 + g.speed;
    h = h * 31 + (g.paused ? 1 : 0);
    h = h * 31 + (g.gameOver ? 1 : 0);
    h = h * 31 + g.power;
    h = h * 31 + g.food;
    h = h * 31 + g.water;
    h = h * 31 + g.mats;
    h = h * 31 + g.shelter;
    h = h * 31 + g.threat;
    h = h * 31 + g.raidController.durability;
    h = h * 31 + g.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE);
    for (int i = 0; i < 6; i++) {
      h = h * 31 + g.roomLevels[i];
      h = h * 31 + g.roomCondition[i];
    }
    for (Resident r : g.people) {
      h = h * 31 + r.id.hashCode();
      h = h * 31 + r.name.hashCode();
      h = h * 31 + r.role.hashCode();
      h = h * 31 + r.job.hashCode();
      h = h * 31 + r.status.ordinal();
      h = h * 31 + r.skill;
      h = h * 31 + r.health;
      h = h * 31 + r.hunger;
      h = h * 31 + r.thirst;
      h = h * 31 + r.fatigue;
      h = h * 31 + r.morale;
      h = h * 31 + (r.alive ? 1 : 0);
      h = h * 31 + (g.isOnExpedition(r) ? 1 : 0);
      h = h * 31 + (g.isBuilding(r) ? 1 : 0);
      h = h * 31 + (g.isDefending(r) ? 1 : 0);
      h = h * 31 + r.foodMinutes;
      h = h * 31 + r.waterMinutes;
      h = h * 31 + r.survivalFractions.getOrDefault("fatigue", 0);
    }
    RoomUpgradeTask t = g.roomUpgradeController.active();
    if (t != null) {
      h = h * 31 + t.room;
      h = h * 31 + t.targetLevel;
      h = h * 31 + t.elapsed;
      h = h * 31 + t.paidCost;
      h = h * 31 + t.builderId.hashCode();
    }
    RaidState raid = g.raidController.latest();
    if (raid != null) {
      h = h * 31 + raid.id.hashCode();
      h = h * 31 + raid.phase.ordinal();
      h = h * 31 + Double.doubleToLongBits(raid.defenseAtStart);
      h = h * 31 + (raid.resultGenerated ? 1 : 0);
      h = h * 31 + raid.defenders.hashCode();
    }
    if (g.raidController.repairing()) {
      h = h * 31 + g.raidController.repair.elapsed;
      h = h * 31 + g.raidController.repair.builderId.hashCode();
    }
    h = h * 31 + g.production.medicineMinutes;
    h = h * 31 + g.production.materialSavingRemainder;
    h = h * 31 + g.production.materialsSaved;
    ResourceAccounting a = g.resourceAccounting;
    h = h * 31 + a.day;
    h = h * 31 + a.startMinute;
    h = h * 31 + a.previousDay;
    h = h * 31 + a.previousStart;
    for (int i = 0; i < 5; i++) {
      h = h * 31 + a.produced[i];
      h = h * 31 + a.used[i];
      h = h * 31 + a.other[i];
      h = h * 31 + a.previousProduced[i];
      h = h * 31 + a.previousUsed[i];
      h = h * 31 + a.previousOther[i];
    }
    h = h * 31 + view.roomUpgradePanel.builderId.hashCode();
    h = h * 31 + view.roomUpgradePanel.message.hashCode();
    return h;
  }
}
