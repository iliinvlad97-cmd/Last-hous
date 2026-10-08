package com.lastdom.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Pure combat calculation. The controller freezes its result before applying damage or losses. */
final class RaidResolver {
  static final class Result {
    RaidState.Outcome outcome;
    int damage, food, water, materials, morale;
    final Map<String, Integer> injuries = new LinkedHashMap<>();
    final Map<String, String> names = new LinkedHashMap<>();
  }

  static double residentPower(Resident resident) {
    double base =
        RaidConfig.BASE_RESIDENT_POWER
            + Math.max(0, resident.skill) * (double) RaidConfig.SKILL_POWER;
    if (resident.role.equals("Охрана")) base *= 1 + RaidConfig.GUARD_BONUS_PERCENT / 100.0;
    return base * SurvivalConfig.efficiency(resident);
  }

  static double barricadePower(int level, int durability) {
    return RaidConfig.BASE_BARRICADE_POWER
        * RoomUpgradeConfig.percent(4, level)
        / 100.0
        * SurvivalConfig.clamp(durability)
        / 100.0;
  }

  static Result resolve(GameController game, RaidState raid, double defense) {
    Random random = new Random(raid.seed);
    double ratio = defense / raid.attackPower;
    double successThreshold =
        RaidConfig.MIN_SUCCESS_RATIO
            + random.nextDouble() * (RaidConfig.MAX_SUCCESS_RATIO - RaidConfig.MIN_SUCCESS_RATIO);
    Result result = new Result();
    result.outcome =
        ratio >= successThreshold
            ? RaidState.Outcome.DEFENDED
            : ratio >= RaidConfig.PARTIAL_RATIO
                ? RaidState.Outcome.PARTIAL_BREACH
                : RaidState.Outcome.DEFEAT;
    boolean success = result.outcome == RaidState.Outcome.DEFENDED,
        partial = result.outcome == RaidState.Outcome.PARTIAL_BREACH;
    int base =
        success
            ? RaidConfig.SUCCESS_DAMAGE
            : partial ? RaidConfig.PARTIAL_DAMAGE : RaidConfig.DEFEAT_DAMAGE;
    double damage =
        base
            + Math.max(0, raid.attackPower - defense) * RaidConfig.DAMAGE_GAP_FACTOR
            + random.nextInt(RaidConfig.DAMAGE_VARIATION);
    if (raid.enemy == RaidState.Enemy.INFECTED)
      damage *= RaidConfig.INFECTED_DAMAGE_PERCENT / 100.0;
    result.damage =
        Math.min(
            game.raidController.durability,
            Math.min(RaidConfig.MAX_DAMAGE, (int) Math.round(damage)));
    result.morale =
        success
            ? RaidConfig.SUCCESS_MORALE
            : partial ? RaidConfig.PARTIAL_MORALE : RaidConfig.DEFEAT_MORALE;
    int percent =
        success ? 0 : partial ? RaidConfig.PARTIAL_THEFT_PERCENT : RaidConfig.DEFEAT_THEFT_PERCENT;
    if (raid.enemy == RaidState.Enemy.MARAUDERS) {
      result.food = loss(game.food, percent);
      result.water = loss(game.water, percent);
      result.materials = loss(game.mats, percent);
    }
    int injuryChance =
        success
            ? RaidConfig.SUCCESS_INJURY_CHANCE
            : partial ? RaidConfig.PARTIAL_INJURY_CHANCE : RaidConfig.DEFEAT_INJURY_CHANCE;
    for (Resident resident : game.people) {
      if (!resident.alive || game.isOnExpedition(resident)) continue;
      result.names.put(resident.id, resident.name);
      if ((!success || raid.defenders.containsKey(resident.id))
          && random.nextInt(100) < injuryChance) {
        int rank = result.outcome.ordinal();
        int injury =
            RaidConfig.INJURY_BASE[rank] + random.nextInt(RaidConfig.INJURY_VARIATION[rank]);
        result.injuries.put(resident.id, Math.min(Math.max(0, resident.health - 1), injury));
      }
    }
    return result;
  }

  private static int loss(int stock, int percent) {
    return Math.min(Math.max(0, stock), (int) ((long) Math.max(0, stock) * percent / 100));
  }
}
