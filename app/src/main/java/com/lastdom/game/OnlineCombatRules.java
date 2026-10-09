package com.lastdom.game;

/** Centralized local rules v1: integers in combat, bounded seeded randomness. */
final class OnlineCombatRules {
  static final int MAX_HISTORY = 200;
  static final int VERSION = 1,
      MAX_ROUNDS = 12,
      JITTER_MIN = 90,
      JITTER_MAX = 110,
      PVP_STAMINA_COST = 12;
  static final int RECOVER_HEALTH = 35,
      RECOVER_STAMINA = 50,
      RECOVER_MEDICINE = 1,
      RECOVER_WATER = 1;
  static final double ACTION_SECONDS = .24;

  enum Tactic {
    CAUTIOUS("Осторожная", 85, 120),
    BALANCED("Сбалансированная", 100, 100),
    AGGRESSIVE("Агрессивная", 120, 85);
    final String label;
    final int attackPercent, defensePercent;

    Tactic(String label, int attackPercent, int defensePercent) {
      this.label = label;
      this.attackPercent = attackPercent;
      this.defensePercent = defensePercent;
    }
  }

  static int cover(String zoneId) {
    if (!"pvp_frontier".equals(zoneId))
      throw new IllegalArgumentException("PvP requires a PvP zone");
    return 110;
  }

  static int attack(
      OnlineCombatSquad.Fighter fighter, int currentHealth, Tactic tactic, int jitter) {
    double specialization =
        fighter.specialization == OnlineCombatSquad.Specialization.SCOUT ? 1.05 : 1;
    return Math.max(
        1,
        (int)
            Math.round(
                fighter.attack
                    * tactic.attackPercent
                    / 100.0
                    * (50 + fighter.stamina / 2.0)
                    / 100.0
                    * (75 + currentHealth / 4.0)
                    / 100.0
                    * jitter
                    / 100.0
                    * specialization));
  }

  static int defense(OnlineCombatSquad.Fighter fighter, Tactic tactic, String zoneId) {
    double specialization =
        fighter.specialization == OnlineCombatSquad.Specialization.GUARD
                || fighter.specialization == OnlineCombatSquad.Specialization.ENGINEER
            ? 1.1
            : 1;
    return Math.max(
        0,
        (int)
            Math.round(
                fighter.defense
                    * tactic.defensePercent
                    / 100.0
                    * cover(zoneId)
                    / 100.0
                    * specialization));
  }

  static int coopMinutes(String zoneId) {
    return "pve_industry".equals(zoneId) ? 90 : "pve_infected".equals(zoneId) ? 150 : 0;
  }

  static int coopChance(String zoneId, OnlineCombatSquad own, OnlineCombatSquad ally) {
    if (coopMinutes(zoneId) == 0) throw new IllegalArgumentException("PvE requires a PvE zone");
    int base = "pve_industry".equals(zoneId) ? 25 : 15;
    return Math.max(
        10,
        Math.min(
            95,
            base
                + (int) Math.round(own.strength() / 3 + ally.strength() / 4)
                + (own.has(OnlineCombatSquad.Specialization.SCOUT) ? 5 : 0)));
  }

  private OnlineCombatRules() {}
}
