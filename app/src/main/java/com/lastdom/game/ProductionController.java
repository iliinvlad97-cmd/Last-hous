package com.lastdom.game;

/** One production owner. No Canvas, random rolls, wall clock or parallel resource/room models. */
final class ProductionController {
  static final class UpgradeQuote {
    final int base, cost, saved, remainder;

    UpgradeQuote(int base, int saved, int remainder) {
      this.base = base;
      this.saved = saved;
      this.cost = base - saved;
      this.remainder = remainder;
    }
  }

  private final GameController game;

  ProductionController(GameController game) {
    this.game = game;
  }

  boolean worker(Resident r, String job) {
    return r.job.equals(job) && game.survivalController.working(r);
  }

  int staffPercent(int room, String job) {
    if (game.roomCondition[room] <= 0) return 0;
    int value = 0;
    for (Resident r : game.people)
      if (worker(r, job))
        value = Math.min(ProductionConfig.MAX_STAFF_PERCENT, value + workerQuality(r, room));
    return value;
  }

  private int workerQuality(Resident r, int room) {
    return (int)
        Math.round(
            game.survivalController.efficiencyPercent(r)
                * ProductionConfig.skillPercent(r.skill)
                * ProductionConfig.professionPercent(r, room)
                / 10000.0);
  }

  int workerPercent(Resident r) {
    if (!game.survivalController.working(r)) return 0;
    int room = game.homeRoomFor(r);
    if (room == 0 || room == 1 && r.job.equals("Еда") || room == 3)
      return workerQuality(r, room) * game.roomUpgradeController.percent(room) / 100;
    return (int)
        Math.round(
            SurvivalConfig.efficiency(r)
                * (r.job.equals("Вода") ? 100 : game.roomUpgradeController.percent(room)));
  }

  int kitchenSavingBasis() {
    return Math.min(
        ProductionConfig.KITCHEN_MAX_SAVING,
        staffPercent(1, "Еда")
            * ProductionConfig.KITCHEN_SAVING
            * game.roomUpgradeController.percent(1)
            / 100);
  }

  int foodRationMinutes() {
    return ProductionConfig.DAY
        * ProductionConfig.BASIS
        / (ProductionConfig.BASIS - kitchenSavingBasis());
  }

  boolean reserveFood(Resident r) {
    if (game.isOnExpedition(r) || !r.alive || r.foodMinutes > 0 || game.food <= 0) return false;
    int duration = foodRationMinutes();
    game.food--;
    r.foodMinutes = duration;
    game.production.foodRations = ProductionState.add(game.production.foodRations, 1);
    return true;
  }

  int workshopSavingBasis() {
    return Math.min(
        ProductionConfig.WORKSHOP_MAX_SAVING,
        staffPercent(3, "Материалы")
            * ProductionConfig.WORKSHOP_SAVING
            * game.roomUpgradeController.percent(3)
            / 100);
  }

  UpgradeQuote upgradeQuote(int base) {
    int percent = workshopSavingBasis();
    // An idle/unavailable workshop cannot cash in a previously accumulated fractional discount.
    if (percent == 0) return new UpgradeQuote(base, 0, game.production.materialSavingRemainder);
    long value = (long) Math.max(0, base) * percent + game.production.materialSavingRemainder;
    int saving = Math.min(Math.max(0, base - 1), (int) (value / ProductionConfig.BASIS));
    return new UpgradeQuote(base, saving, (int) (value % ProductionConfig.BASIS));
  }

  void applyUpgradeQuote(UpgradeQuote quote) {
    // Called once only after RoomUpgradeController validates room, funds and builder.
    game.production.materialSavingRemainder = quote.remainder;
    game.production.materialsSaved =
        ProductionState.add(game.production.materialsSaved, quote.saved);
  }

  boolean medicineAvailable() {
    return game.roomCondition[2] > 0
        && (game.production.medicineMinutes > 0
            || game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE) > 0);
  }

  boolean patient(Resident r) {
    return r.alive
        && r.health < 100
        && !game.isOnExpedition(r)
        && !game.isBuilding(r)
        && !game.isDefending(r)
        && r.hunger < SurvivalConfig.CRITICAL
        && r.thirst < SurvivalConfig.CRITICAL;
  }

  boolean hasPatient() {
    for (Resident r : game.people) if (patient(r)) return true;
    return false;
  }

  boolean beginClinicMinute(boolean staffed) {
    if (!staffed || !hasPatient() || !medicineAvailable()) return false;
    if (game.production.medicineMinutes == 0) {
      int stock = game.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE);
      if (stock <= 0) return false;
      game.expeditionWarehouse.set(ExpeditionLoot.Resource.MEDICINE, stock - 1);
      game.production.medicineMinutes = ProductionConfig.MEDICINE_DOSE_MINUTES;
      game.production.medicineDoses = ProductionState.add(game.production.medicineDoses, 1);
    }
    game.production.medicineMinutes--;
    return true;
  }

  int generatorPercent() {
    return 100
        + Math.min(
            ProductionConfig.GENERATOR_MAX_BOOST,
            staffPercent(0, "Ремонт") * ProductionConfig.GENERATOR_STAFF_BOOST / 100);
  }

  double energyPerDay() {
    if (game.roomCondition[0] <= 0 || game.power >= ProductionConfig.ENERGY_CAPACITY) return 0;
    return RoomUpgradeConfig.BASE_ENERGY_PER_DAY
        * game.roomUpgradeController.percent(0)
        * generatorPercent()
        / 10000.0;
  }

  double waterPerDay() {
    if (game.roomCondition[1] <= 0 || game.water >= ProductionConfig.WATER_CAPACITY) return 0;
    double amount = 0;
    for (Resident r : game.people)
      if (worker(r, "Вода"))
        amount +=
            ProductionConfig.WATER_PER_WORKER_DAY
                * game.survivalController.efficiencyPercent(r)
                / 100.0;
    return amount;
  }

  double medicalPerDay() {
    if (!medicineAvailable() || !hasPatient()) return 0;
    int base = 0;
    double effective = 0;
    for (Resident r : game.people)
      if (worker(r, "Лечение")) {
        base += r.skill + 1;
        effective += (r.skill + 1) * SurvivalConfig.efficiency(r);
      }
    return base == 0
        ? 0
        : (base / 2) * effective / base * game.roomUpgradeController.percent(2) / 100.0;
  }

  private int output(int base, int room, int effectiveness, int minutes, String key) {
    String channel = "survival6_" + key;
    long value =
        game.productionRemainders.values.getOrDefault(channel, 0)
            + (long) base
                * (room < 0 ? 100 : game.roomUpgradeController.percent(room))
                * effectiveness
                * minutes;
    game.productionRemainders.values.put(
        channel, (int) (value % SurvivalController.OUTPUT_DENOMINATOR));
    return (int) Math.min(Integer.MAX_VALUE, value / SurvivalController.OUTPUT_DENOMINATOR);
  }

  private int stored(int value, int amount, int capacity, String channel) {
    if (value >= capacity) {
      game.productionRemainders.clear("survival6_" + channel);
      return value; // Old saves/expeditions above the soft capacity are never confiscated.
    }
    int next = Math.min(capacity, value + Math.min(amount, capacity));
    if (next == capacity) game.productionRemainders.clear("survival6_" + channel);
    return next;
  }

  void produce(int minutes) {
    // Each minute is a simulation step. Grouping steps cannot bypass storage or staff exclusions.
    for (int step = 0; step < Math.max(0, minutes); step++) produceMinute();
  }

  private void produceMinute() {
    if (game.roomCondition[0] > 0) {
      int old = game.power;
      game.power =
          stored(
              game.power,
              output(RoomUpgradeConfig.BASE_ENERGY_PER_DAY, 0, generatorPercent(), 1, "energy"),
              ProductionConfig.ENERGY_CAPACITY,
              "energy");
      game.production.energyProduced =
          ProductionState.add(game.production.energyProduced, game.power - old);
    }
    for (Resident r : game.people) {
      if (!game.survivalController.working(r)) continue;
      int efficiency = game.survivalController.efficiencyPercent(r);
      switch (r.job) {
        case "Вода":
          if (game.roomCondition[1] > 0) {
            int old = game.water;
            game.water =
                stored(
                    game.water,
                    output(ProductionConfig.WATER_PER_WORKER_DAY, -1, efficiency, 1, "water"),
                    ProductionConfig.WATER_CAPACITY,
                    "water");
            game.production.waterProduced =
                ProductionState.add(game.production.waterProduced, game.water - old);
          }
          break;
        case "Ремонт":
          game.shelter =
              Math.min(
                  100,
                  game.shelter
                      + output(
                          ProductionConfig.MAINTENANCE_BASE_PER_DAY + r.skill,
                          -1,
                          efficiency,
                          1,
                          "repair"));
          game.roomCondition[0] =
              Math.min(
                  100,
                  game.roomCondition[0]
                      + output(
                          ProductionConfig.GENERATOR_REPAIR_PER_DAY,
                          -1,
                          efficiency,
                          1,
                          "condition"));
          break;
        case "Охрана":
          game.threat =
              Math.max(
                  0,
                  game.threat
                      - output(
                          ProductionConfig.GUARD_BASE_PER_DAY + r.skill,
                          4,
                          efficiency,
                          1,
                          "guards"));
          break;
        default: // Cooking/workshop improve use of REAL inputs, never create food/materials.
          break;
      }
    }
  }
}
