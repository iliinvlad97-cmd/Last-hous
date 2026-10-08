package com.lastdom.game;

/** All Stage 7 balance; times use the existing simulation's game minutes. */
final class RaidConfig {
  static final int PREPARATION_MINUTES = 60, ATTACK_MINUTES = 30, MIN_INTERVAL = 1440;
  static final int REPAIR_COST = 5, REPAIR_MINUTES = 60, REPAIR_AMOUNT = 25;
  static final int MIN_HEALTH = 30, BASE_BARRICADE_POWER = 24;
  static final int BASE_RESIDENT_POWER = 8, SKILL_POWER = 2, GUARD_BONUS_PERCENT = 50;
  static final int EARLY_CHANCE = 20, MIDDLE_CHANCE = 35, LATE_CHANCE = 50;
  static final int EARLY_ATTACK = 30, MIDDLE_ATTACK = 38, LATE_ATTACK = 46;
  static final int MARAUDER_ATTACK_PERCENT = 100, INFECTED_ATTACK_PERCENT = 90;
  static final int ATTACK_VARIATION = 9, INFECTED_DAMAGE_PERCENT = 125;
  static final int SUCCESS_DAMAGE = 8, PARTIAL_DAMAGE = 24, DEFEAT_DAMAGE = 42;
  static final double PARTIAL_RATIO = .65, MIN_SUCCESS_RATIO = .85, MAX_SUCCESS_RATIO = 1.05;
  static final double DAMAGE_GAP_FACTOR = .3;
  static final int DAMAGE_VARIATION = 5;
  static final int[] INJURY_BASE = {2, 5, 10}, INJURY_VARIATION = {4, 10, 10};
  static final int MAX_DAMAGE = 70, SUCCESS_INJURY_CHANCE = 12;
  static final int PARTIAL_INJURY_CHANCE = 45, DEFEAT_INJURY_CHANCE = 70;
  static final int SUCCESS_MORALE = 3, PARTIAL_MORALE = -5, DEFEAT_MORALE = -10;
  static final int PARTIAL_THEFT_PERCENT = 8, DEFEAT_THEFT_PERCENT = 18;

  static int chance(int day) {
    return day <= 1 ? 0 : day <= 3 ? EARLY_CHANCE : day <= 6 ? MIDDLE_CHANCE : LATE_CHANCE;
  }

  static int attackPercent(RaidState.Enemy enemy) {
    return enemy == RaidState.Enemy.MARAUDERS ? MARAUDER_ATTACK_PERCENT : INFECTED_ATTACK_PERCENT;
  }

  static int attackBase(int day) {
    return day <= 3 ? EARLY_ATTACK : day <= 6 ? MIDDLE_ATTACK : LATE_ATTACK;
  }
}
