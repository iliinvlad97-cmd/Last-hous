package com.lastdom.game;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

/** Owns threat frequency, defensive assignments and exactly-once repair/combat transactions. */
final class RaidController {
  private final GameController game;
  final ArrayList<RaidState> raids = new ArrayList<>();
  int durability = 100, checkedDay;
  long lastAttackMinute = -RaidConfig.MIN_INTERVAL;
  BarricadeRepair repair;

  RaidController(GameController game) {
    this.game = game;
  }

  void reset() {
    raids.clear();
    durability = 100;
    checkedDay = 0;
    lastAttackMinute = -RaidConfig.MIN_INTERVAL;
    repair = null;
  }

  RaidState active() {
    for (int i = raids.size() - 1; i >= 0; i--) if (raids.get(i).active()) return raids.get(i);
    return null;
  }

  RaidState latest() {
    return raids.isEmpty() ? null : raids.get(raids.size() - 1);
  }

  boolean repairing() {
    return repair != null && !repair.completed;
  }

  boolean repairBuilder(Resident r) {
    return repairing() && repair.builderId.equals(r.id);
  }

  boolean defending(Resident r) {
    RaidState raid = active();
    return raid != null && raid.defenders.containsKey(r.id);
  }

  double defensePower() {
    return defensePower(game.roomLevels[4]);
  }

  double defensePower(int level) {
    double power = RaidResolver.barricadePower(level, durability);
    RaidState raid = active();
    if (raid != null)
      for (String id : raid.defenders.keySet()) {
        Resident resident = game.expeditionController.resident(id);
        if (resident != null) power += defenderPower(resident);
      }
    return power;
  }

  double defenderPower(Resident resident) {
    return defending(resident) && eligible(resident) ? RaidResolver.residentPower(resident) : 0;
  }

  private boolean eligible(Resident r) {
    return r.alive
        && !game.isOnExpedition(r)
        && !game.isBuilding(r)
        && r.health >= RaidConfig.MIN_HEALTH
        && r.hunger < SurvivalConfig.CRITICAL
        && r.thirst < SurvivalConfig.CRITICAL;
  }

  String defenderReason(Resident r) {
    if (r == null) return "Житель больше не существует";
    if (!r.alive || r.health < RaidConfig.MIN_HEALTH)
      return "Недостаточно здоровья (нужно " + RaidConfig.MIN_HEALTH + "%)";
    if (game.isOnExpedition(r)) return "В экспедиции";
    if (game.isBuilding(r)) return "Занят строительством";
    if (r.hunger >= SurvivalConfig.CRITICAL) return "Критический голод";
    if (r.thirst >= SurvivalConfig.CRITICAL) return "Критическая жажда";
    if (game.survivalController.treating(r)) return "На лечении";
    return "";
  }

  String toggleDefender(String id) {
    RaidState raid = active();
    if (raid == null) return "Нет активной угрозы";
    if (raid.phase == RaidState.Phase.ATTACK) return "Состав обороны уже зафиксирован";
    Resident resident = game.expeditionController.resident(id);
    if (raid.defenders.containsKey(id)) {
      release(resident, raid.defenders.remove(id));
    } else {
      String reason = defenderReason(resident);
      if (!reason.isEmpty()) return reason;
      raid.defenders.put(id, new RaidState.Assignment(resident));
      resident.job = "Оборона";
      resident.status = Resident.Status.DEFENDING;
      resident.autoRecovery = false;
      resident.resumeJob = "";
    }
    raid.phase = RaidState.Phase.PREPARING;
    game.save();
    game.invalidate();
    return "";
  }

  void prepare() {
    RaidState raid = active();
    if (raid != null && raid.phase == RaidState.Phase.WARNING) {
      raid.phase = RaidState.Phase.PREPARING;
      game.save();
    }
  }

  void checkDailyThreat() {
    if (game.day <= 1 || checkedDay >= game.day) return;
    checkedDay = game.day; // A missed/blocked daily roll is also saved; restarting cannot reroll.
    if (active() != null
        || game.expeditionController.now() - lastAttackMinute < RaidConfig.MIN_INTERVAL) return;
    if (game.rnd.nextInt(100) >= RaidConfig.chance(game.day)) return;
    long seed = game.rnd.nextLong();
    Random random = new Random(seed);
    RaidState.Enemy enemy =
        random.nextBoolean() ? RaidState.Enemy.MARAUDERS : RaidState.Enemy.INFECTED;
    int power =
        Math.max(
            1,
            (int)
                Math.round(
                    (RaidConfig.attackBase(game.day) + random.nextInt(RaidConfig.ATTACK_VARIATION))
                        * RaidConfig.attackPercent(enemy)
                        / 100.0));
    discover(enemy, power, seed);
  }

  // Package-private deterministic entry point for tests; still enforces safety and intervals.
  boolean discover(RaidState.Enemy enemy, int attackPower, long seed) {
    long now = game.expeditionController.now();
    if (game.day <= 1
        || active() != null
        || now - lastAttackMinute < RaidConfig.MIN_INTERVAL
        || attackPower < 1) return false;
    for (RaidState previous : raids) if (previous.warningMinute / 1440 == now / 1440) return false;
    RaidState raid = new RaidState(UUID.randomUUID().toString(), enemy, seed, now, attackPower);
    raids.add(raid);
    checkedDay = game.day;
    game.addLog(
        "Обнаружена угроза: "
            + raid.enemyName()
            + ". Подготовка — "
            + RaidConfig.PREPARATION_MINUTES
            + " игровых минут.");
    game.save();
    game.invalidate();
    return true;
  }

  void advanceMinute() {
    if (repairing()) {
      repair.elapsed = Math.min(RaidConfig.REPAIR_MINUTES, repair.elapsed + 1);
      if (repair.remaining() == 0) finishRepair();
    }
    RaidState raid = active();
    if (raid != null) {
      if (raid.phase != RaidState.Phase.ATTACK) {
        raid.elapsed = Math.min(RaidConfig.PREPARATION_MINUTES, raid.elapsed + 1);
        if (raid.remaining() == 0) beginAttack(raid);
      } else {
        raid.attackElapsed = Math.min(RaidConfig.ATTACK_MINUTES, raid.attackElapsed + 1);
        int damage = raid.plannedDamage * raid.attackElapsed / RaidConfig.ATTACK_MINUTES;
        durability = Math.max(0, durability - Math.max(0, damage - raid.damageApplied));
        raid.damageApplied = damage;
        if (raid.attackElapsed == RaidConfig.ATTACK_MINUTES) applyResult(raid);
      }
    }
    checkDailyThreat();
  }

  private void beginAttack(RaidState raid) {
    // Residents can worsen during preparation; invalid defenders are freed, never replaced by AI.
    for (String id : new ArrayList<>(raid.defenders.keySet())) {
      Resident resident = game.expeditionController.resident(id);
      if (resident == null || !eligible(resident)) release(resident, raid.defenders.remove(id));
    }
    raid.defenseAtStart = defensePower();
    raid.durabilityAtStart = durability;
    RaidResolver.Result result = RaidResolver.resolve(game, raid, raid.defenseAtStart);
    raid.outcome = result.outcome;
    raid.plannedDamage = result.damage;
    raid.foodLost = result.food;
    raid.waterLost = result.water;
    raid.materialsLost = result.materials;
    raid.moraleChange = result.morale;
    raid.injuries.putAll(result.injuries);
    raid.victimNames.putAll(result.names);
    raid.resultGenerated = true;
    raid.phase = RaidState.Phase.ATTACK;
    raid.attackMinute = game.expeditionController.now();
    lastAttackMinute = raid.attackMinute;
    game.addLog(
        "Началась атака: "
            + raid.enemyName()
            + ". Защита "
            + Math.round(raid.defenseAtStart)
            + ", сила врагов "
            + raid.attackPower
            + ".");
  }

  void applyResult(RaidState raid) {
    if (!raids.contains(raid)
        || raid.phase != RaidState.Phase.ATTACK
        || !raid.resultGenerated
        || raid.effectsApplied
        || raid.attackElapsed != RaidConfig.ATTACK_MINUTES) return;
    raid.foodLost = Math.min(Math.max(0, game.food), raid.foodLost);
    raid.waterLost = Math.min(Math.max(0, game.water), raid.waterLost);
    raid.materialsLost = Math.min(Math.max(0, game.mats), raid.materialsLost);
    game.food = Math.max(0, game.food - raid.foodLost);
    game.water = Math.max(0, game.water - raid.waterLost);
    game.mats = Math.max(0, game.mats - raid.materialsLost);
    for (String id : raid.victimNames.keySet()) {
      Resident resident = game.expeditionController.resident(id);
      if (resident == null || game.isOnExpedition(resident)) {
        if (raid.injuries.containsKey(id)) raid.injuries.put(id, 0);
        continue;
      }
      int damage = Math.min(Math.max(0, resident.health - 1), raid.injuries.getOrDefault(id, 0));
      if (raid.injuries.containsKey(id)) raid.injuries.put(id, damage);
      resident.health -= damage;
      game.survivalController.injury(resident, damage);
      resident.morale = SurvivalConfig.clamp(resident.morale + raid.moraleChange);
      if (damage > 0)
        game.addLog(resident.name + " пострадал при нападении: здоровье -" + damage + ".");
    }
    for (String id : raid.defenders.keySet())
      release(game.expeditionController.resident(id), raid.defenders.get(id));
    raid.effectsApplied = true;
    raid.phase = RaidState.Phase.RESULT;
    game.addLog(raid.outcomeName() + ". Баррикады повреждены на " + raid.damageApplied + "%.");
    if (raid.foodLost + raid.waterLost + raid.materialsLost > 0)
      game.addLog(
          "Ресурсы похищены: еда -"
              + raid.foodLost
              + ", вода -"
              + raid.waterLost
              + ", материалы -"
              + raid.materialsLost
              + ".");
  }

  void acknowledge(RaidState raid) {
    if (raid != null
        && raids.contains(raid)
        && raid.phase == RaidState.Phase.RESULT
        && raid.effectsApplied) {
      raid.phase = RaidState.Phase.COMPLETED;
      game.save();
    }
  }

  String repairReason() {
    if (durability >= 100) return "Баррикады полностью восстановлены";
    if (active() != null && active().phase == RaidState.Phase.ATTACK)
      return "Ремонт недоступен во время нападения";
    if (repairing() || game.roomUpgradeController.active() != null)
      return "Сначала завершите текущее строительство";
    if (game.mats < RaidConfig.REPAIR_COST)
      return "Не хватает материалов: " + (RaidConfig.REPAIR_COST - game.mats);
    return "";
  }

  String startRepair(String builderId) {
    String reason = repairReason();
    if (!reason.isEmpty()) return reason;
    Resident builder = game.expeditionController.resident(builderId);
    reason = game.roomUpgradeController.unavailableReason(builder);
    if (!reason.isEmpty()) return reason;
    repair =
        new BarricadeRepair(
            UUID.randomUUID().toString(),
            builder.id,
            game.expeditionController.now(),
            new RaidState.Assignment(builder));
    game.mats -= RaidConfig.REPAIR_COST;
    repair.paid = true;
    builder.status = Resident.Status.BUILDING;
    builder.job = "Строительство";
    game.addLog(
        "Начат ремонт баррикад. Строитель: "
            + builder.name
            + ". Материалы -"
            + RaidConfig.REPAIR_COST
            + ".");
    game.save();
    game.invalidate();
    return "";
  }

  private void finishRepair() {
    if (!repairing() || !repair.paid || repair.remaining() != 0) return;
    durability = Math.min(100, durability + RaidConfig.REPAIR_AMOUNT);
    repair.completed = true;
    release(game.expeditionController.resident(repair.builderId), repair.previous);
    game.addLog("Баррикады отремонтированы: прочность " + durability + "%.");
  }

  private void release(Resident resident, RaidState.Assignment previous) {
    if (resident == null || previous == null || game.isOnExpedition(resident)) return;
    resident.status = Resident.Status.HOME;
    resident.job = previous.job;
    resident.autoRecovery = previous.autoRecovery;
    resident.resumeJob = previous.resumeJob;
  }

  void restoreMembership() {
    if (repairing()) {
      Resident builder = game.expeditionController.resident(repair.builderId);
      if (builder != null) {
        builder.status = Resident.Status.BUILDING;
        builder.job = "Строительство";
      }
    }
    RaidState raid = active();
    if (raid != null)
      for (String id : raid.defenders.keySet()) {
        Resident resident = game.expeditionController.resident(id);
        if (resident != null) {
          resident.status = Resident.Status.DEFENDING;
          resident.job = "Оборона";
          resident.autoRecovery = false;
          resident.resumeJob = "";
        }
      }
  }
}
