package com.lastdom.game;

/** Stage 8 service rates/caps. Existing room bonuses and survival multipliers are reused once. */
final class ProductionConfig {
  static final int DAY = 1440, BASIS = 10000;
  static final int ENERGY_CAPACITY = 60, WATER_CAPACITY = 60;
  static final int WATER_PER_WORKER_DAY = 4;
  static final int MAINTENANCE_BASE_PER_DAY = 3, GENERATOR_REPAIR_PER_DAY = 2;
  static final int GUARD_BASE_PER_DAY = 1;
  static final int GENERATOR_STAFF_BOOST = 10, GENERATOR_MAX_BOOST = 20;
  static final int KITCHEN_SAVING = 10, KITCHEN_MAX_SAVING = 2500;
  static final int WORKSHOP_SAVING = 5, WORKSHOP_MAX_SAVING = 1500;
  static final int MAX_STAFF_PERCENT = 300, MEDICINE_DOSE_MINUTES = DAY;
  static final int MAX_FOOD_MINUTES = DAY * BASIS / (BASIS - KITCHEN_MAX_SAVING);

  static int skillPercent(int skill) {
    return Math.max(50, Math.min(150, (Math.max(0, Math.min(5, skill)) + 1) * 25));
  }

  static int professionPercent(Resident r, int room) {
    if (room == 1 && r.role.equals("Сборщик")) return 120;
    if ((room == 0 || room == 3) && r.role.equals("Механик")) return 120;
    if ((room == 0 || room == 3) && r.role.equals("Инженер")) return 110;
    return 100;
  }

  private ProductionConfig() {}
}
