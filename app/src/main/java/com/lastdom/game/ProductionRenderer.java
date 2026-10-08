package com.lastdom.game;

import java.util.List;
import java.util.Locale;

/** Read-only production section of the EXISTING room panel; no resource/clock mutations. */
final class ProductionRenderer {
  private final GameView view;

  ProductionRenderer(GameView view) {
    this.view = view;
  }

  void append(List<String> rows, int room) {
    GameController game = view.game;
    ProductionController production = game.productionController;
    if (room == 4) return; // Preserve the Stage 7 defense panel and its rules.
    rows.add("ПРОИЗВОДСТВО / ПОЛЕЗНЫЙ ЭФФЕКТ");
    rows.add((room == 5 ? "Отдыхают: " : "Работают: ") + game.occupants(room));
    for (Resident r : game.people)
      if (game.homeRoomFor(r) == room && game.survivalController.working(r))
        rows.add(r.name + ": эффективность " + production.workerPercent(r) + "% с бонусом комнаты");
    switch (room) {
      case 0:
        rows.add("Режим: генерация + обслуживание");
        rows.add(
            "Эффективность выработки: "
                + number(
                    game.roomUpgradeController.percent(0) * production.generatorPercent() / 100.0)
                + "% от базовой");
        rows.add("Производство: +" + number(production.energyPerDay()) + " энергии / день");
        rows.add("Расход убежища: −1 энергии / день; материалы не требуются");
        rows.add("Запас: " + game.power + " • выработка до " + ProductionConfig.ENERGY_CAPACITY);
        rows.add("Обслуживание: +" + number(maintenanceRate(false)) + " прочности дома / день");
        rows.add("Ремонт оборудования: +" + number(maintenanceRate(true)) + " состояния / день");
        rows.add(
            "Состояние: "
                + (game.roomCondition[0] <= 0
                    ? "оборудование не работает; назначьте ремонтника"
                    : game.power >= ProductionConfig.ENERGY_CAPACITY
                        ? "выработка остановлена: накоплен запас"
                        : "работает; базовая генерация без работника"));
        break;
      case 1:
        rows.add("Режим: приготовление запасов / получение воды");
        rows.add(
            "Еда не создаётся. Экономия новых пайков: "
                + number(production.kitchenSavingBasis() / 100.0)
                + "%");
        rows.add(
            "Расход еды: ≈"
                + number(
                    homeResidents()
                        * (double) ProductionConfig.DAY
                        / production.foodRationMinutes())
                + " / день дома (для новых пайков)");
        rows.add(
            "1 еда → "
                + production.foodRationMinutes()
                + " минут питания; паёк оплачивается один раз");
        rows.add(
            "Приготовление: "
                + (game.roomCondition[1] <= 0
                    ? "оборудование не работает"
                    : production.staffPercent(1, "Еда") == 0
                        ? "нет доступных поваров"
                        : game.food <= 0
                            ? "нет пищевых запасов; готовые пайки сохраняются"
                            : "работает"));
        rows.add(
            "Вода: +"
                + number(production.waterPerDay())
                + " / день • расход ≈"
                + homeResidents()
                + " / день дома");
        rows.add(
            "Получение воды: "
                + (game.roomCondition[1] <= 0
                    ? "оборудование не работает"
                    : game.water >= ProductionConfig.WATER_CAPACITY
                        ? "остановлено: запас от " + ProductionConfig.WATER_CAPACITY
                        : waterWorkers() == 0
                            ? "нет доступных работников «Вода»"
                            : "работает; сырьё не требуется"));
        int fed = 0;
        for (Resident r : game.people)
          if (r.alive && !game.isOnExpedition(r) && r.foodMinutes > 0) fed++;
        rows.add("Оплаченные пищевые пайки: " + fed + " • запас еды: " + game.food);
        break;
      case 2:
        rows.add("Режим: лечение существующими медикаментами");
        rows.add(
            "Лечение: +" + number(production.medicalPerDay()) + " здоровья / день на пациента");
        rows.add(
            "Расход: 1 медикамент / "
                + ProductionConfig.MEDICINE_DOSE_MINUTES
                + " минут активного лечения, общая доза клиники");
        rows.add(
            "Склад медикаментов: "
                + game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE));
        rows.add(
            "Состояние: "
                + (game.roomCondition[2] <= 0
                    ? "оборудование не работает"
                    : medicWorkers() == 0
                        ? "нет доступных медиков"
                        : !production.hasPatient()
                            ? "лечение сейчас не требуется"
                            : !production.medicineAvailable()
                                ? "нет медикаментов; доступен пассивный отдых"
                                : "лечит"));
        if (game.production.medicineMinutes > 0)
          rows.add(
              "Оплаченная доза: "
                  + (ProductionConfig.MEDICINE_DOSE_MINUTES - game.production.medicineMinutes)
                      * 100
                      / ProductionConfig.MEDICINE_DOSE_MINUTES
                  + "% • осталось "
                  + game.production.medicineMinutes
                  + " активных минут");
        for (Resident r : game.people)
          if (game.survivalController.treating(r)) rows.add("Лечится: " + r.name);
        break;
      case 3:
        rows.add("Режим: экономная обработка материалов");
        rows.add(
            "Материалы не создаются. Экономия улучшений: "
                + number(production.workshopSavingBasis() / 100.0)
                + "% (макс. 15%)");
        rows.add("Расход: материалы только при подтверждении улучшения");
        rows.add("Сэкономлено за игру: " + game.production.materialsSaved + " материалов");
        rows.add("Запас сырья: " + game.mats);
        rows.add(
            "Состояние: "
                + (game.roomCondition[3] <= 0
                    ? "оборудование не работает"
                    : production.staffPercent(3, "Материалы") == 0
                        ? "нет доступных работников"
                        : game.mats <= 0
                            ? "ожидает материалы из экспедиций"
                            : "готова обрабатывать при улучшении"));
        rows.add("Ремонт баррикад: прежняя стоимость, скидка не применяется");
        break;
      default:
        rows.add("Режим: отдых; рабочая усталость не начисляется");
        rows.add(
            "Восстановление: −"
                + SurvivalConfig.bedroomRecoveryPerHour(game.roomLevels[5])
                + " усталости / час на отдыхающего");
        rows.add(
            "Пассивное восстановление: +"
                + SurvivalConfig.REST_HEALTH_PER_DAY
                + " здоровья / день");
        rows.add("Расход: обычные оплаченные пайки еды и воды");
        int resting = 0;
        for (Resident r : game.people) if (r.alive && game.survivalController.resting(r)) resting++;
        rows.add(
            "Состояние: "
                + (resting == 0
                    ? "нет отдыхающих"
                    : "восстановление до 0 усталости; AI может возобновить прежнюю работу"));
    }
    rows.add("Расчёт по игровому времени: пауза / ×1 / ×2 / ×4");
  }

  private int homeResidents() {
    int count = 0;
    for (Resident r : view.game.people) if (r.alive && !view.game.isOnExpedition(r)) count++;
    return count;
  }

  private int waterWorkers() {
    return workers("Вода");
  }

  private int medicWorkers() {
    return workers("Лечение");
  }

  private int workers(String job) {
    int count = 0;
    for (Resident r : view.game.people) if (view.game.productionController.worker(r, job)) count++;
    return count;
  }

  private double maintenanceRate(boolean equipment) {
    double amount = 0;
    for (Resident r : view.game.people)
      if (view.game.productionController.worker(r, "Ремонт"))
        amount +=
            (equipment
                    ? ProductionConfig.GENERATOR_REPAIR_PER_DAY
                    : ProductionConfig.MAINTENANCE_BASE_PER_DAY + r.skill)
                * view.game.survivalController.efficiencyPercent(r)
                / 100.0;
    return amount;
  }

  private String number(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }
}
