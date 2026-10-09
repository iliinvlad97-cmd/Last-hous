package com.lastdom.game;

import java.util.*;

/**
 * Immutable precomputed actions. Replaying these data never invokes the engine or grants rewards.
 */
final class OnlineCombatReport {
  enum Outcome {
    VICTORY,
    DEFEAT,
    DRAW
  }

  static final class Action {
    final int round, actor, target, attack, blocked, before, after;
    final boolean playerAttack;

    Action(
        int round,
        boolean playerAttack,
        int actor,
        int target,
        int attack,
        int blocked,
        int before,
        int after) {
      if (round < 1
          || round > OnlineCombatRules.MAX_ROUNDS
          || actor < 0
          || actor > 2
          || target < 0
          || target > 2
          || attack < 1
          || blocked < 0
          || before < 1
          || before > 100
          || after < 0
          || after >= before
          || after != Math.max(0, before - Math.max(1, attack - blocked)))
        throw new IllegalArgumentException("Invalid combat action");
      this.round = round;
      this.playerAttack = playerAttack;
      this.actor = actor;
      this.target = target;
      this.attack = attack;
      this.blocked = blocked;
      this.before = before;
      this.after = after;
    }
  }

  final int rulesVersion;
  final long seed;
  final OnlineCombatSquad own, enemy;
  final OnlineCombatRules.Tactic tactic;
  final Outcome outcome;
  final List<Action> actions;

  OnlineCombatReport(
      int rulesVersion,
      long seed,
      OnlineCombatSquad own,
      OnlineCombatSquad enemy,
      OnlineCombatRules.Tactic tactic,
      Outcome outcome,
      List<Action> actions) {
    if (rulesVersion != OnlineCombatRules.VERSION
        || actions.isEmpty()
        || actions.size() > OnlineCombatRules.MAX_ROUNDS * 6)
      throw new IllegalArgumentException("Invalid combat report");
    this.rulesVersion = rulesVersion;
    this.seed = seed;
    this.own = own;
    this.enemy = enemy;
    this.tactic = tactic;
    this.outcome = outcome;
    this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
    int[] hp = health(actions.size());
    int a = 0, b = 0;
    for (int i = 0; i < own.fighters.size(); i++) a += hp[i];
    for (int i = 0; i < enemy.fighters.size(); i++) b += hp[3 + i];
    if (outcome != (a > b ? Outcome.VICTORY : a < b ? Outcome.DEFEAT : Outcome.DRAW))
      throw new IllegalArgumentException("Inconsistent combat outcome");
  }

  int[] health(int actionCount) {
    int[] hp = new int[6];
    fillHealth(actionCount, hp);
    return hp;
  }

  void fillHealth(int actionCount, int[] hp) {
    Arrays.fill(hp, 0);
    for (int i = 0; i < own.fighters.size(); i++) hp[i] = own.fighters.get(i).health;
    for (int i = 0; i < enemy.fighters.size(); i++) hp[3 + i] = enemy.fighters.get(i).health;
    int previousRound = 0;
    for (int i = 0; i < Math.min(actionCount, actions.size()); i++) {
      Action action = actions.get(i);
      int index = (action.playerAttack ? 3 : 0) + action.target,
          actor = (action.playerAttack ? 0 : 3) + action.actor;
      if (action.actor >= (action.playerAttack ? own : enemy).fighters.size()
          || action.target >= (action.playerAttack ? enemy : own).fighters.size()
          || action.round < previousRound
          || hp[actor] <= 0
          || hp[index] != action.before)
        throw new IllegalArgumentException("Broken combat timeline");
      hp[index] = action.after;
      previousRound = action.round;
    }
  }

  int durationSeconds() {
    return (int) Math.ceil(actions.size() * OnlineCombatRules.ACTION_SECONDS) + 2;
  }

  String resultLabel() {
    return outcome == Outcome.VICTORY
        ? "ПОБЕДА"
        : outcome == Outcome.DEFEAT ? "ПОРАЖЕНИЕ" : "НИЧЬЯ";
  }
}
