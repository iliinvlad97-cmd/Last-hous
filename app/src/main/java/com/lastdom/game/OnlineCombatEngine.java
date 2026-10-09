package com.lastdom.game;

import java.util.*;

/** Pure seeded turn engine. No clock, repository, UI, resource mutation or global RNG. */
final class OnlineCombatEngine {
  OnlineCombatReport calculate(
      OnlineCombatSquad own,
      OnlineCombatSquad enemy,
      OnlineCombatRules.Tactic tactic,
      String zoneId,
      long seed) {
    Objects.requireNonNull(tactic);
    OnlineCombatRules.cover(zoneId);
    for (OnlineCombatSquad squad : Arrays.asList(own, enemy))
      for (OnlineCombatSquad.Fighter fighter : squad.fighters)
        if (fighter.health <= 0) throw new IllegalArgumentException("Incapacitated fighter");
    Set<String> ids = new HashSet<>();
    for (OnlineCombatSquad squad : Arrays.asList(own, enemy))
      for (OnlineCombatSquad.Fighter f : squad.fighters)
        if (!ids.add(f.id)) throw new IllegalArgumentException("Shared combat fighter");
    Random random = new Random(seed);
    int[][] hp = {new int[own.fighters.size()], new int[enemy.fighters.size()]};
    for (int i = 0; i < hp[0].length; i++) hp[0][i] = own.fighters.get(i).health;
    for (int i = 0; i < hp[1].length; i++) hp[1][i] = enemy.fighters.get(i).health;
    List<OnlineCombatReport.Action> actions = new ArrayList<>();
    for (int round = 1;
        round <= OnlineCombatRules.MAX_ROUNDS && alive(hp[0]) && alive(hp[1]);
        round++) {
      int initiative = random.nextBoolean() ? 0 : 1;
      for (int turn = 0; turn < 2 && alive(hp[0]) && alive(hp[1]); turn++) {
        int side = (initiative + turn) % 2, other = 1 - side;
        OnlineCombatSquad attacking = side == 0 ? own : enemy, defending = side == 0 ? enemy : own;
        for (int actor = 0; actor < attacking.fighters.size() && alive(hp[other]); actor++)
          if (hp[side][actor] > 0) {
            List<Integer> targets = new ArrayList<>();
            for (int i = 0; i < hp[other].length; i++) if (hp[other][i] > 0) targets.add(i);
            int target = targets.get(random.nextInt(targets.size()));
            int jitter =
                OnlineCombatRules.JITTER_MIN
                    + random.nextInt(
                        OnlineCombatRules.JITTER_MAX - OnlineCombatRules.JITTER_MIN + 1);
            int attack =
                OnlineCombatRules.attack(
                    attacking.fighters.get(actor),
                    hp[side][actor],
                    side == 0 ? tactic : OnlineCombatRules.Tactic.BALANCED,
                    jitter);
            int blocked =
                OnlineCombatRules.defense(
                        defending.fighters.get(target),
                        other == 0 ? tactic : OnlineCombatRules.Tactic.BALANCED,
                        zoneId)
                    / 2;
            int before = hp[other][target],
                after = Math.max(0, before - Math.max(1, attack - blocked));
            hp[other][target] = after;
            actions.add(
                new OnlineCombatReport.Action(
                    round, side == 0, actor, target, attack, blocked, before, after));
          }
      }
    }
    int a = Arrays.stream(hp[0]).sum(), b = Arrays.stream(hp[1]).sum();
    return new OnlineCombatReport(
        OnlineCombatRules.VERSION,
        seed,
        own,
        enemy,
        tactic,
        a > b
            ? OnlineCombatReport.Outcome.VICTORY
            : a < b ? OnlineCombatReport.Outcome.DEFEAT : OnlineCombatReport.Outcome.DRAW,
        actions);
  }

  private boolean alive(int[] hp) {
    for (int value : hp) if (value > 0) return true;
    return false;
  }
}
