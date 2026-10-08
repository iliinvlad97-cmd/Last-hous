package com.lastdom.game;

/** Daily rates and condition multipliers. All clocks use simulation minutes. */
final class SurvivalConfig {
  static final int MINUTES_PER_DAY = 1440;
  static final int HUNGER_PER_DAY = 25, THIRST_PER_DAY = 35, WORK_FATIGUE_PER_DAY = 30;
  // Bedroom recovery is measured in points per GAME hour, independently of production bonuses.
  static final int[] BEDROOM_RECOVERY_PER_HOUR = {5, 7, 10};
  static final int TREATMENT_REST_PER_DAY = 22, REST_HEALTH_PER_DAY = 2;

  static int bedroomRecoveryPerHour(int level) {
    int normalized = Math.max(1, level);
    if (normalized <= BEDROOM_RECOVERY_PER_HOUR.length)
      return BEDROOM_RECOVERY_PER_HOUR[normalized - 1];
    // Future levels continue the last +3 points/hour step; current room cap remains 3.
    return (int) Math.min(Integer.MAX_VALUE / 2400L, 10L + (normalized - 3L) * 3);
  }

  static final int GOOD_MORALE_PER_DAY = 2, REST_MORALE_PER_DAY = 4;
  static final int BAD_MORALE_PER_DAY = 8, CRITICAL_DAMAGE_PER_DAY = 6;
  static final int REST_START = 80, REST_FINISH = 0, TREAT_START = 35, TREAT_FINISH = 70;
  static final int NEED_WARNING = 40, NEED_HEAVY = 70;
  static final int INJURY_MORALE_PERCENT = 50;
  static final int CRITICAL = 90, CRITICAL_HEALTH = 20, WARNING_COOLDOWN = 360;
  static final int[] NEED_LIMITS = {NEED_WARNING, NEED_HEAVY, CRITICAL};
  static final double[] NEED_FACTORS = {1, .9, .65, .4};
  static final double[] MORALE_FACTORS = {.65, .8, .9, 1};

  static int clamp(int value) {
    return Math.max(0, Math.min(100, value));
  }

  static double needFactor(int value) {
    int band = 0;
    for (int limit : NEED_LIMITS) if (value >= limit) band++;
    return NEED_FACTORS[band];
  }

  static double moraleFactor(int morale) {
    return MORALE_FACTORS[morale >= 70 ? 3 : morale >= 40 ? 2 : morale >= 20 ? 1 : 0];
  }

  static double efficiency(Resident r) {
    if (!r.alive || r.health <= 0) return 0;
    double health = r.health / 100.0;
    double morale = moraleFactor(r.morale);
    return health * needFactor(r.hunger) * needFactor(r.thirst) * needFactor(r.fatigue) * morale;
  }

  private SurvivalConfig() {}
}
