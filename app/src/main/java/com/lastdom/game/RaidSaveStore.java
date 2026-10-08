package com.lastdom.game;

import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/** Adds Stage 7 keys to the same atomic save_v02 editor; never applies battle/repair effects. */
final class RaidSaveStore {
  static void save(GameController game, SharedPreferences.Editor e) {
    RaidController controller = game.raidController;
    e.putInt("raid7_schema", 1)
        .putInt("raid7_durability", controller.durability)
        .putInt("raid7_checkedDay", controller.checkedDay)
        .putString("raid7_lastAttack", Long.toString(controller.lastAttackMinute))
        .putInt("raid7_count", controller.raids.size());
    for (int i = 0; i < controller.raids.size(); i++) {
      RaidState raid = controller.raids.get(i);
      String k = "raid7_" + i + "_";
      e.putString(k + "id", raid.id)
          .putString(k + "enemy", raid.enemy.name())
          .putString(k + "phase", raid.phase.name())
          .putString(k + "seed", Long.toString(raid.seed))
          .putString(k + "warning", Long.toString(raid.warningMinute))
          .putString(k + "attackMinute", Long.toString(raid.attackMinute))
          .putInt(k + "attackPower", raid.attackPower)
          .putString(k + "defense", Double.toString(raid.defenseAtStart))
          .putInt(k + "durabilityStart", raid.durabilityAtStart)
          .putInt(k + "elapsed", raid.elapsed)
          .putInt(k + "attackElapsed", raid.attackElapsed)
          .putInt(k + "damageApplied", raid.damageApplied)
          .putInt(k + "damage", raid.plannedDamage)
          .putInt(k + "food", raid.foodLost)
          .putInt(k + "water", raid.waterLost)
          .putInt(k + "materials", raid.materialsLost)
          .putInt(k + "morale", raid.moraleChange)
          .putBoolean(k + "generated", raid.resultGenerated)
          .putBoolean(k + "applied", raid.effectsApplied)
          .putString(k + "outcome", raid.outcome == null ? "" : raid.outcome.name())
          .putString(k + "defenders", android.text.TextUtils.join(",", raid.defenders.keySet()))
          .putString(k + "victims", android.text.TextUtils.join(",", raid.victimNames.keySet()));
      for (String id : raid.defenders.keySet())
        saveAssignment(e, k + "member_" + id + "_", raid.defenders.get(id));
      for (String id : raid.victimNames.keySet()) {
        e.putString(k + "name_" + id, raid.victimNames.get(id));
        e.putInt(k + "injury_" + id, raid.injuries.getOrDefault(id, -1));
      }
    }
    BarricadeRepair repair = controller.repair;
    e.putBoolean("raid7_hasRepair", repair != null);
    if (repair != null) {
      e.putString("raid7_repair_id", repair.id)
          .putString("raid7_repair_builder", repair.builderId)
          .putString("raid7_repair_start", Long.toString(repair.startMinute))
          .putInt("raid7_repair_elapsed", repair.elapsed)
          .putBoolean("raid7_repair_paid", repair.paid)
          .putBoolean("raid7_repair_completed", repair.completed);
      saveAssignment(e, "raid7_repair_previous_", repair.previous);
    }
  }

  private static void saveAssignment(
      SharedPreferences.Editor e, String k, RaidState.Assignment previous) {
    e.putString(k + "job", previous.job)
        .putString(k + "resume", previous.resumeJob)
        .putBoolean(k + "auto", previous.autoRecovery);
  }

  private static RaidState.Assignment assignment(SharedPreferences p, String k) {
    String job = p.getString(k + "job", "Отдых"), resume = p.getString(k + "resume", "");
    if (job.equals("Экспедиция") || job.equals("Строительство") || job.equals("Оборона"))
      job = "Отдых";
    return new RaidState.Assignment(job, resume, p.getBoolean(k + "auto", false));
  }

  private static Set<String> ids(String text) {
    Set<String> ids = new java.util.LinkedHashSet<>();
    if (!text.isEmpty())
      for (String id : text.split(","))
        if (id.isEmpty() || !ids.add(id)) throw new IllegalArgumentException();
    return ids;
  }

  static void load(GameController game, SharedPreferences p) {
    RaidController controller = game.raidController;
    controller.durability = SurvivalConfig.clamp(p.getInt("raid7_durability", 100));
    controller.checkedDay = Math.max(0, p.getInt("raid7_checkedDay", 0));
    controller.lastAttackMinute = number(p, "raid7_lastAttack", -RaidConfig.MIN_INTERVAL);
    if (p.getBoolean("raid7_hasRepair", false)) {
      try {
        String id = p.getString("raid7_repair_id", ""),
            builder = p.getString("raid7_repair_builder", "");
        int elapsed = p.getInt("raid7_repair_elapsed", -1);
        long start = number(p, "raid7_repair_start", -1);
        boolean paid = p.getBoolean("raid7_repair_paid", false),
            completed = p.getBoolean("raid7_repair_completed", false);
        Resident resident = game.expeditionController.resident(builder);
        if (id.isEmpty()
            || start < 0
            || elapsed < 0
            || elapsed > RaidConfig.REPAIR_MINUTES
            || !paid
            || completed && elapsed != RaidConfig.REPAIR_MINUTES
            || !completed
                && (resident == null
                    || game.isOnExpedition(resident)
                    || game.roomUpgradeController.active() != null))
          throw new IllegalArgumentException();
        controller.repair =
            new BarricadeRepair(id, builder, start, assignment(p, "raid7_repair_previous_"));
        controller.repair.elapsed = elapsed;
        controller.repair.paid = paid;
        controller.repair.completed = completed;
      } catch (IllegalArgumentException ignored) {
        /* Invalid repairs cannot spend materials or change durability. */
      }
    }
    Set<String> seen = new HashSet<>();
    boolean active = false;
    int count = Math.max(0, Math.min(10000, p.getInt("raid7_count", 0)));
    for (int i = 0; i < count; i++) {
      String k = "raid7_" + i + "_";
      try {
        String id = p.getString(k + "id", "");
        int power = p.getInt(k + "attackPower", 0);
        long warning = number(p, k + "warning", -1);
        if (id.isEmpty() || seen.contains(id) || power < 1 || warning < 1440)
          throw new IllegalArgumentException();
        RaidState raid =
            new RaidState(
                id,
                RaidState.Enemy.valueOf(p.getString(k + "enemy", "")),
                number(p, k + "seed", 0),
                warning,
                power);
        raid.phase = RaidState.Phase.valueOf(p.getString(k + "phase", ""));
        raid.elapsed = p.getInt(k + "elapsed", -1);
        raid.attackElapsed = p.getInt(k + "attackElapsed", -1);
        raid.attackMinute = number(p, k + "attackMinute", -1);
        raid.defenseAtStart = Double.parseDouble(p.getString(k + "defense", "0"));
        raid.durabilityAtStart = p.getInt(k + "durabilityStart", 0);
        raid.damageApplied = p.getInt(k + "damageApplied", 0);
        raid.plannedDamage = p.getInt(k + "damage", 0);
        raid.foodLost = p.getInt(k + "food", 0);
        raid.waterLost = p.getInt(k + "water", 0);
        raid.materialsLost = p.getInt(k + "materials", 0);
        raid.moraleChange = p.getInt(k + "morale", 0);
        raid.resultGenerated = p.getBoolean(k + "generated", false);
        raid.effectsApplied = p.getBoolean(k + "applied", false);
        String outcome = p.getString(k + "outcome", "");
        if (!outcome.isEmpty()) raid.outcome = RaidState.Outcome.valueOf(outcome);
        boolean finished =
            raid.phase == RaidState.Phase.RESULT || raid.phase == RaidState.Phase.COMPLETED;
        if (raid.elapsed < 0
            || raid.elapsed > RaidConfig.PREPARATION_MINUTES
            || raid.attackElapsed < 0
            || raid.attackElapsed > RaidConfig.ATTACK_MINUTES
            || !Double.isFinite(raid.defenseAtStart)
            || raid.defenseAtStart < 0
            || raid.durabilityAtStart < 0
            || raid.durabilityAtStart > 100
            || !raid.resultGenerated
                && (raid.elapsed >= RaidConfig.PREPARATION_MINUTES
                    || raid.attackElapsed != 0
                    || raid.damageApplied != 0)
            || raid.phase == RaidState.Phase.ATTACK
                && (raid.attackElapsed >= RaidConfig.ATTACK_MINUTES
                    || raid.damageApplied
                        != raid.plannedDamage * raid.attackElapsed / RaidConfig.ATTACK_MINUTES)
            || raid.plannedDamage < 0
            || raid.plannedDamage > 100
            || raid.damageApplied < 0
            || raid.damageApplied > raid.plannedDamage
            || raid.foodLost < 0
            || raid.waterLost < 0
            || raid.materialsLost < 0
            || raid.resultGenerated != (raid.phase == RaidState.Phase.ATTACK || finished)
            || raid.effectsApplied != finished
            || raid.resultGenerated
                && (raid.outcome == null
                    || raid.attackMinute < warning + RaidConfig.PREPARATION_MINUTES
                    || raid.elapsed != RaidConfig.PREPARATION_MINUTES)
            || finished
                && (raid.attackElapsed != RaidConfig.ATTACK_MINUTES
                    || raid.damageApplied != raid.plannedDamage)
            || raid.active() && active) throw new IllegalArgumentException();
        for (String residentId : ids(p.getString(k + "defenders", ""))) {
          Resident resident = game.expeditionController.resident(residentId);
          if (raid.active()
              && (resident == null || game.isOnExpedition(resident) || game.isBuilding(resident)))
            throw new IllegalArgumentException();
          raid.defenders.put(residentId, assignment(p, k + "member_" + residentId + "_"));
        }
        for (String residentId : ids(p.getString(k + "victims", ""))) {
          raid.victimNames.put(residentId, p.getString(k + "name_" + residentId, "Житель"));
          int injury = p.getInt(k + "injury_" + residentId, -1);
          if (injury >= 0) raid.injuries.put(residentId, Math.min(100, injury));
        }
        controller.raids.add(raid);
        seen.add(id);
        if (raid.active()) active = true;
      } catch (IllegalArgumentException ignored) {
        /* Invalid records are not re-rolled or applied. */
      }
    }
    controller.restoreMembership();
    for (Resident resident : game.people) {
      if (resident.job.equals("Оборона") && !controller.defending(resident)) {
        resident.job = "Отдых";
        resident.status = Resident.Status.HOME;
      }
      if (resident.job.equals("Строительство")
          && !controller.repairBuilder(resident)
          && game.roomUpgradeController.taskFor(resident) == null) {
        resident.job = "Отдых";
        resident.status = Resident.Status.HOME;
      }
    }
  }

  private static long number(SharedPreferences p, String key, long fallback) {
    try {
      return Long.parseLong(p.getString(key, Long.toString(fallback)));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
