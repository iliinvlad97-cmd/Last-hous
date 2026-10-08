package com.lastdom.game;

/** Daily rates and condition multipliers. All clocks use simulation minutes. */
final class SurvivalConfig {
  static final int MINUTES_PER_DAY = 1440;
  static final int HUNGER_PER_DAY = 25, THIRST_PER_DAY = 35, WORK_FATIGUE_PER_DAY = 30;
  // Existing bedroom and incidental recovery rates, now spread over simulation minutes.
  static final int REST_PER_DAY = 22, REST_HEALTH_PER_DAY = 2;
  static final int GOOD_MORALE_PER_DAY = 2, REST_MORALE_PER_DAY = 4;
  static final int BAD_MORALE_PER_DAY = 8, CRITICAL_DAMAGE_PER_DAY = 6;
  static final int REST_START = 80, REST_FINISH = 30, TREAT_START = 35, TREAT_FINISH = 70;
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

  static double efficiency(Resident r) {
    if (!r.alive || r.health <= 0) return 0;
    double health = r.health / 100.0;
    double morale =
        MORALE_FACTORS[r.morale >= 70 ? 3 : r.morale >= 40 ? 2 : r.morale >= 20 ? 1 : 0];
    return health * needFactor(r.hunger) * needFactor(r.thirst) * needFactor(r.fatigue) * morale;
  }

  private SurvivalConfig() {}
}
