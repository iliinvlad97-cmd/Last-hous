package com.lastdom.game;

import java.util.LinkedHashMap;
import java.util.Map;

/** Durable threat/battle/report. Randomness is fixed at discovery, never in rendering or reload. */
final class RaidState {
  enum Enemy {
    MARAUDERS,
    INFECTED
  }

  enum Phase {
    WARNING,
    PREPARING,
    ATTACK,
    RESULT,
    COMPLETED
  }

  enum Outcome {
    DEFENDED,
    PARTIAL_BREACH,
    DEFEAT
  }

  static final class Assignment {
    final String job, resumeJob;
    final boolean autoRecovery;

    Assignment(Resident r) {
      this(r.job, r.resumeJob, r.autoRecovery);
    }

    Assignment(String job, String resumeJob, boolean autoRecovery) {
      this.job = job;
      this.resumeJob = resumeJob;
      this.autoRecovery = autoRecovery;
    }
  }

  final String id;
  final Enemy enemy;
  final long seed, warningMinute;
  final int attackPower;
  Phase phase = Phase.WARNING;
  int elapsed, attackElapsed, damageApplied;
  long attackMinute = -1;
  double defenseAtStart;
  int durabilityAtStart;
  boolean resultGenerated, effectsApplied;
  Outcome outcome;
  int plannedDamage, foodLost, waterLost, materialsLost, moraleChange;
  final Map<String, Assignment> defenders = new LinkedHashMap<>();
  final Map<String, Integer> injuries = new LinkedHashMap<>();
  final Map<String, String> victimNames = new LinkedHashMap<>();

  RaidState(String id, Enemy enemy, long seed, long warningMinute, int attackPower) {
    this.id = id;
    this.enemy = enemy;
    this.seed = seed;
    this.warningMinute = warningMinute;
    this.attackPower = attackPower;
  }

  boolean active() {
    return phase == Phase.WARNING || phase == Phase.PREPARING || phase == Phase.ATTACK;
  }

  int remaining() {
    return phase == Phase.ATTACK
        ? RaidConfig.ATTACK_MINUTES - attackElapsed
        : active() ? RaidConfig.PREPARATION_MINUTES - elapsed : 0;
  }

  int progress() {
    return phase == Phase.ATTACK
        ? attackElapsed * 100 / RaidConfig.ATTACK_MINUTES
        : active() ? elapsed * 100 / RaidConfig.PREPARATION_MINUTES : 100;
  }

  String enemyName() {
    return enemy == Enemy.MARAUDERS ? "Мародёры" : "Заражённые";
  }

  String outcomeName() {
    return outcome == Outcome.DEFENDED
        ? "Оборона успешна"
        : outcome == Outcome.PARTIAL_BREACH ? "Частичный прорыв" : "Оборона не удержала врагов";
  }
}
