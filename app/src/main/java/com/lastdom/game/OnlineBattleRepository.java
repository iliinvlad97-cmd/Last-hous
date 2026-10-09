package com.lastdom.game;

import java.util.*;

/** Repository command contract; the existing world adapter owns the single atomic save. */
interface OnlineBattleRepository {
  default OnlineWorldGameplay.Result startPvp(
      String id,
      String zoneId,
      List<String> fighters,
      OnlineCombatRules.Tactic tactic,
      long seed,
      boolean confirmed) {
    return new OnlineWorldGameplay.Result(false, "Бой недоступен");
  }

  default OnlineWorldGameplay.Result startCoop(
      String id,
      String zoneId,
      String allyId,
      List<String> fighters,
      long seed,
      boolean confirmed) {
    return new OnlineWorldGameplay.Result(false, "Экспедиция недоступна");
  }

  default OnlineWorldGameplay.Result recover(String fighterId) {
    return new OnlineWorldGameplay.Result(false, "Восстановление недоступно");
  }

  default boolean advanceMinute() {
    return false;
  }

  final class Battle {
    final String id, zoneId;
    final OnlineCombatReport report;
    final int elapsedSeconds;
    final boolean effectsApplied;

    Battle(
        String id, String zoneId, OnlineCombatReport report, int elapsed, boolean effectsApplied) {
      if (!"pvp_frontier".equals(zoneId)
          || elapsed < 0
          || elapsed > report.durationSeconds()
          || effectsApplied != (elapsed == report.durationSeconds()))
        throw new IllegalArgumentException("Invalid battle phase");
      this.id = id;
      this.zoneId = zoneId;
      this.report = report;
      this.elapsedSeconds = elapsed;
      this.effectsApplied = effectsApplied;
    }

    boolean active() {
      return !effectsApplied;
    }

    Battle advance() {
      int elapsed = Math.min(report.durationSeconds(), elapsedSeconds + 1);
      return new Battle(id, zoneId, report, elapsed, elapsed == report.durationSeconds());
    }
  }

  final class State {
    final List<OnlineCombatSquad.Fighter> fighters;
    final List<Battle> battles;
    final List<OnlineCoopExpedition> expeditions;
    final int nextId;
    final boolean used;

    State(
        List<OnlineCombatSquad.Fighter> fighters,
        List<Battle> battles,
        List<OnlineCoopExpedition> expeditions,
        int nextId,
        boolean used) {
      if (fighters.size() != 6
          || nextId < 1
          || nextId > 10000
          || battles.size() + expeditions.size() > OnlineCombatRules.MAX_HISTORY)
        throw new IllegalArgumentException("Invalid battle state");
      Set<String> ids = new HashSet<>(), jobs = new HashSet<>(), busy = new HashSet<>();
      for (OnlineCombatSquad.Fighter fighter : fighters)
        if (!ids.add(fighter.id)) throw new IllegalArgumentException("Duplicate online roster ID");
      for (Battle battle : battles) {
        if (!jobs.add(battle.id)) throw new IllegalArgumentException("Duplicate battle");
        if (battle.active())
          for (OnlineCombatSquad.Fighter fighter : battle.report.own.fighters)
            if (!ids.contains(fighter.id) || !busy.add(fighter.id))
              throw new IllegalArgumentException("Duplicate assignment");
      }
      for (OnlineCoopExpedition expedition : expeditions) {
        if (!jobs.add(expedition.id)) throw new IllegalArgumentException("Duplicate expedition");
        if (expedition.active()) {
          for (OnlineCombatSquad.Fighter fighter : expedition.own.fighters)
            if (!ids.contains(fighter.id) || !busy.add(fighter.id))
              throw new IllegalArgumentException("Duplicate assignment");
          for (OnlineCombatSquad.Fighter fighter : expedition.ally.fighters)
            if (!busy.add(fighter.id))
              throw new IllegalArgumentException("Duplicate ally assignment");
        }
      }
      if (nextId != jobs.size() + 1)
        throw new IllegalArgumentException("Inconsistent job sequence");
      for (int i = 1; i < nextId; i++)
        if (!jobs.contains("online_job_" + i)) throw new IllegalArgumentException("Missing job ID");
      for (OnlineCombatSquad.Fighter expected : OnlineCombatSquad.roster()) {
        if (!ids.contains(expected.id)) throw new IllegalArgumentException("Unknown roster ID");
        if (!used)
          for (OnlineCombatSquad.Fighter f : fighters)
            if (f.id.equals(expected.id) && (f.health != 100 || f.stamina != 100))
              throw new IllegalArgumentException("Unmarked fighter state");
      }
      if (!used && (!jobs.isEmpty() || nextId != 1))
        throw new IllegalArgumentException("Unmarked combat state");
      this.fighters = Collections.unmodifiableList(new ArrayList<>(fighters));
      this.battles = Collections.unmodifiableList(new ArrayList<>(battles));
      this.expeditions = Collections.unmodifiableList(new ArrayList<>(expeditions));
      this.nextId = nextId;
      this.used = used;
    }

    static State initial() {
      return new State(
          OnlineCombatSquad.roster(), Collections.emptyList(), Collections.emptyList(), 1, false);
    }

    String requestId() {
      return "online_job_" + nextId;
    }

    OnlineCombatSquad.Fighter fighter(String id) {
      for (OnlineCombatSquad.Fighter f : fighters) if (f.id.equals(id)) return f;
      return null;
    }

    Battle battle(String id) {
      for (Battle b : battles) if (b.id.equals(id)) return b;
      return null;
    }

    OnlineCoopExpedition expedition(String id) {
      for (OnlineCoopExpedition e : expeditions) if (e.id.equals(id)) return e;
      return null;
    }

    boolean busy(String id) {
      for (Battle b : battles) if (b.active() && b.report.own.contains(id)) return true;
      for (OnlineCoopExpedition e : expeditions)
        if (e.active() && (e.own.contains(id) || e.ally.contains(id))) return true;
      return false;
    }

    String unavailable(String id) {
      OnlineCombatSquad.Fighter f = fighter(id);
      if (f == null) return "Боец не найден";
      if (busy(id)) return "Боец занят заданием";
      if (f.health < 20) return "Здоровье ниже 20";
      if (f.stamina < 20) return "Выносливость ниже 20";
      return "";
    }
  }
}
