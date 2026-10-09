package com.lastdom.game;

import java.util.*;

/** Persisted precomputed PvE result; only the game-minute completion transaction applies it. */
final class OnlineCoopExpedition {
  final String id, zoneId, allyId;
  final long seed;
  final int rulesVersion, elapsedMinutes, durationMinutes, chance, allyContribution, staminaCost;
  final OnlineCombatSquad own, ally;
  final boolean success, rewardApplied;
  final OnlineInventory loot;
  final List<Integer> healthLoss, allyHealthLoss;

  OnlineCoopExpedition(
      String id,
      String zoneId,
      String allyId,
      long seed,
      int elapsed,
      OnlineCombatSquad own,
      OnlineCombatSquad ally,
      boolean success,
      int chance,
      int allyContribution,
      int staminaCost,
      OnlineInventory loot,
      List<Integer> healthLoss,
      List<Integer> allyHealthLoss,
      boolean rewardApplied) {
    durationMinutes = OnlineCombatRules.coopMinutes(zoneId);
    if (durationMinutes <= 0
        || elapsed < 0
        || elapsed > durationMinutes
        || rewardApplied != (elapsed == durationMinutes)
        || chance < 10
        || chance > 95
        || allyContribution < 0
        || allyContribution > 100
        || staminaCost < 0
        || staminaCost > 100
        || healthLoss.size() != own.fighters.size()
        || allyHealthLoss.size() != ally.fighters.size())
      throw new IllegalArgumentException("Invalid coop expedition");
    for (int loss : healthLoss)
      if (loss < 0 || loss > 100) throw new IllegalArgumentException("Invalid PvE damage");
    for (int loss : allyHealthLoss)
      if (loss < 0 || loss > 100) throw new IllegalArgumentException("Invalid ally damage");
    if (!success)
      for (OnlineInventory.Resource resource : OnlineInventory.Resource.values())
        if (loot.amount(resource) != 0)
          throw new IllegalArgumentException("Failed coop cannot award loot");
    this.id = id;
    this.zoneId = zoneId;
    this.allyId = allyId;
    this.seed = seed;
    this.elapsedMinutes = elapsed;
    this.own = own;
    this.ally = ally;
    this.success = success;
    this.chance = chance;
    this.allyContribution = allyContribution;
    this.staminaCost = staminaCost;
    this.loot = loot;
    this.healthLoss = Collections.unmodifiableList(new ArrayList<>(healthLoss));
    this.allyHealthLoss = Collections.unmodifiableList(new ArrayList<>(allyHealthLoss));
    this.rewardApplied = rewardApplied;
    rulesVersion = OnlineCombatRules.VERSION;
  }

  boolean active() {
    return !rewardApplied;
  }

  OnlineCoopExpedition advance() {
    int elapsed = Math.min(durationMinutes, elapsedMinutes + 1);
    return new OnlineCoopExpedition(
        id,
        zoneId,
        allyId,
        seed,
        elapsed,
        own,
        ally,
        success,
        chance,
        allyContribution,
        staminaCost,
        loot,
        healthLoss,
        allyHealthLoss,
        elapsed == durationMinutes);
  }

  static OnlineCoopExpedition calculate(
      String id,
      String zone,
      String allyId,
      OnlineCombatSquad own,
      OnlineCombatSquad ally,
      long seed) {
    int chance = OnlineCombatRules.coopChance(zone, own, ally);
    Random random = new Random(seed);
    boolean success = random.nextInt(100) < chance;
    boolean dangerous = "pve_infected".equals(zone);
    EnumMap<OnlineInventory.Resource, Integer> resources =
        new EnumMap<>(OnlineInventory.Resource.class);
    if (success) {
      double strength = own.strength() + ally.strength();
      int base = (dangerous ? 4 : 6) + random.nextInt(dangerous ? 5 : 7);
      int quantity = Math.max(1, (int) Math.round(base * (.75 + Math.min(150, strength) / 300)));
      if (!dangerous && own.has(OnlineCombatSquad.Specialization.MECHANIC))
        quantity = (int) Math.ceil(quantity * 1.15);
      resources.put(
          dangerous ? OnlineInventory.Resource.MEDICINE : OnlineInventory.Resource.MATERIALS,
          quantity);
      resources.put(OnlineInventory.Resource.EQUIPMENT, 1 + random.nextInt(3));
    }
    boolean doctor =
        own.has(OnlineCombatSquad.Specialization.MEDIC)
            || ally.has(OnlineCombatSquad.Specialization.MEDIC);
    List<Integer> losses = new ArrayList<>(), allyLosses = new ArrayList<>();
    for (OnlineCombatSquad squad : Arrays.asList(own, ally))
      for (OnlineCombatSquad.Fighter fighter : squad.fighters) {
        int loss = random.nextInt((dangerous ? 20 : 10) + (success ? 1 : 6));
        if (doctor) loss = loss * 75 / 100;
        (squad == own ? losses : allyLosses).add(Math.min(fighter.health, loss));
      }
    int contribution =
        (int) Math.round(ally.strength() * 100 / Math.max(1, own.strength() + ally.strength()));
    return new OnlineCoopExpedition(
        id,
        zone,
        allyId,
        seed,
        0,
        own,
        ally,
        success,
        chance,
        contribution,
        dangerous ? 30 : 20,
        new OnlineInventory(resources),
        losses,
        allyLosses,
        false);
  }
}
