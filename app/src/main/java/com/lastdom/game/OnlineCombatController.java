package com.lastdom.game;

import java.util.*;

/** Pure transitions. The world repository serializes validation + commit + publication. */
final class OnlineCombatController {
  static final class Change {
    final OnlineWorldGameplay.Data data;
    final OnlineWorldGameplay.Result result;

    Change(OnlineWorldGameplay.Data data, boolean success, String message) {
      this.data = data;
      result = new OnlineWorldGameplay.Result(success, message);
    }
  }

  private Change fail(OnlineWorldGameplay.Data data, String message) {
    return new Change(data, false, message);
  }

  private OnlineWorldGameplay.Data replace(
      OnlineWorldGameplay.Data data,
      OnlineInventory inventory,
      OnlineBattleRepository.State state) {
    return new OnlineWorldGameplay.Data(
        inventory,
        data.offers,
        data.operations,
        data.reputation,
        data.pvpZoneId,
        state,
        data.civic);
  }

  private String request(OnlineWorldGameplay.Data data, String id, boolean consent) {
    if (!consent) return "Требуется отдельное подтверждение";
    if (!data.combat.requestId().equals(id)) return "Задание уже принято или подготовка устарела";
    if (data.combat.battles.size() + data.combat.expeditions.size()
        >= OnlineCombatRules.MAX_HISTORY) return "Демо-история заполнена (200 заданий)";
    return "";
  }

  private OnlineCombatSquad select(OnlineBattleRepository.State state, List<String> ids) {
    if (ids == null || ids.isEmpty() || ids.size() > 3)
      throw new IllegalArgumentException("Выберите от 1 до 3 бойцов");
    Set<String> unique = new HashSet<>();
    List<OnlineCombatSquad.Fighter> fighters = new ArrayList<>();
    for (String id : ids) {
      String reason = state.unavailable(id);
      if (!reason.isEmpty()) throw new IllegalArgumentException(reason);
      if (!unique.add(id)) throw new IllegalArgumentException("Один боец выбран дважды");
      fighters.add(state.fighter(id));
    }
    return new OnlineCombatSquad(fighters);
  }

  Change startPvp(
      OnlineWorldGameplay.Data data,
      String id,
      String zone,
      List<String> ids,
      OnlineCombatRules.Tactic tactic,
      long seed,
      boolean confirmed) {
    String reason = request(data, id, confirmed);
    if (!reason.isEmpty()) return fail(data, reason);
    if (!"pvp_frontier".equals(zone) || tactic == null)
      return fail(data, "Выберите PvP-зону и тактику");
    for (OnlineBattleRepository.Battle battle : data.combat.battles)
      if (battle.active()) return fail(data, "Сначала завершите текущий демо-бой");
    String preflight = OnlineActionRules.mission(data, null, id, zone, ids, "", true);
    if (!preflight.isEmpty()) return fail(data, preflight);
    OnlineCombatSquad squad;
    try {
      squad = select(data.combat, ids);
    } catch (IllegalArgumentException invalid) {
      return fail(data, invalid.getMessage());
    }
    OnlineCombatReport report =
        new OnlineCombatEngine().calculate(squad, OnlineCombatSquad.enemy(id), tactic, zone, seed);
    List<OnlineBattleRepository.Battle> battles = new ArrayList<>(data.combat.battles);
    battles.add(new OnlineBattleRepository.Battle(id, zone, report, 0, false));
    OnlineBattleRepository.State state =
        new OnlineBattleRepository.State(
            data.combat.fighters, battles, data.combat.expeditions, data.combat.nextId + 1, true);
    return new Change(
        new OnlineWorldGameplay.Data(
            data.inventory, data.offers, data.operations, data.reputation, zone, state, data.civic),
        true,
        "Демо-бой начался. Результат зафиксирован.");
  }

  Change startCoop(
      OnlineWorldGameplay.Data data,
      OnlineWorldRepository.Snapshot world,
      String id,
      String zone,
      String allyId,
      List<String> ids,
      long seed,
      boolean confirmed) {
    String reason = request(data, id, confirmed);
    if (!reason.isEmpty()) return fail(data, reason);
    if (OnlineCombatRules.coopMinutes(zone) == 0)
      return fail(data, "Совместные задания доступны только в PvE-зонах");
    OnlineShelter shelter = null;
    for (OnlineShelter candidate : world.shelters)
      if (candidate.id.equals(allyId)) shelter = candidate;
    if (shelter == null) return fail(data, "Союзник не найден");
    String preflight = OnlineActionRules.mission(data, world, id, zone, ids, allyId, false);
    if (!preflight.isEmpty()) return fail(data, preflight);
    OnlineCombatSquad squad;
    try {
      squad = select(data.combat, ids);
    } catch (IllegalArgumentException invalid) {
      return fail(data, invalid.getMessage());
    }
    OnlineCombatSquad ally = OnlineCombatSquad.ally(shelter);
    for (OnlineCombatSquad.Fighter fighter : ally.fighters)
      if (data.combat.busy(fighter.id)) return fail(data, "Отряд этого союзника уже занят");
    OnlineCoopExpedition expedition =
        OnlineCoopExpedition.calculate(id, zone, allyId, squad, ally, seed);
    List<OnlineCoopExpedition> expeditions = new ArrayList<>(data.combat.expeditions);
    expeditions.add(expedition);
    OnlineBattleRepository.State state =
        new OnlineBattleRepository.State(
            data.combat.fighters, data.combat.battles, expeditions, data.combat.nextId + 1, true);
    return new Change(replace(data, data.inventory, state), true, "Совместный отряд отправлен");
  }

  Change recover(OnlineWorldGameplay.Data data, String id) {
    String reason = OnlineActionRules.recovery(data, id);
    if (!reason.isEmpty()) return fail(data, reason);
    OnlineCombatSquad.Fighter fighter = data.combat.fighter(id);
    OnlineInventory inventory = data.inventory;
    int medicine = OnlineActionRules.medicine(fighter), water = OnlineActionRules.water(fighter);
    if (medicine > 0)
      inventory = inventory.exchange(OnlineInventory.Resource.MEDICINE, medicine, null, 0);
    if (water > 0) inventory = inventory.exchange(OnlineInventory.Resource.WATER, water, null, 0);
    List<OnlineCombatSquad.Fighter> fighters = new ArrayList<>(data.combat.fighters);
    for (int i = 0; i < fighters.size(); i++)
      if (fighters.get(i).id.equals(id))
        fighters.set(
            i,
            fighter.condition(
                fighter.health + OnlineCombatRules.RECOVER_HEALTH,
                fighter.stamina + OnlineCombatRules.RECOVER_STAMINA));
    OnlineBattleRepository.State state =
        new OnlineBattleRepository.State(
            fighters, data.combat.battles, data.combat.expeditions, data.combat.nextId, true);
    return new Change(
        replace(data, inventory, state),
        true,
        "Восстановление завершено: здоровье +"
            + Math.min(OnlineCombatRules.RECOVER_HEALTH, 100 - fighter.health)
            + " · силы +"
            + Math.min(OnlineCombatRules.RECOVER_STAMINA, 100 - fighter.stamina));
  }

  OnlineWorldGameplay.Data advanceSecond(OnlineWorldGameplay.Data data) {
    boolean changed = false;
    List<OnlineBattleRepository.Battle> battles = new ArrayList<>();
    List<OnlineCombatSquad.Fighter> fighters = new ArrayList<>(data.combat.fighters);
    for (OnlineBattleRepository.Battle battle : data.combat.battles) {
      if (battle.active()) {
        changed = true;
        battle = battle.advance();
        if (battle.effectsApplied) {
          int[] hp = battle.report.health(battle.report.actions.size());
          for (int i = 0; i < battle.report.own.fighters.size(); i++)
            condition(
                fighters,
                battle.report.own.fighters.get(i).id,
                hp[i],
                battle.report.own.fighters.get(i).stamina - OnlineCombatRules.PVP_STAMINA_COST);
        }
      }
      battles.add(battle);
    }
    if (!changed) return data;
    return replace(
        data,
        data.inventory,
        new OnlineBattleRepository.State(
            fighters, battles, data.combat.expeditions, data.combat.nextId, true));
  }

  OnlineWorldGameplay.Data advanceMinute(OnlineWorldGameplay.Data data) {
    boolean changed = false;
    List<OnlineCoopExpedition> expeditions = new ArrayList<>();
    List<OnlineCombatSquad.Fighter> fighters = new ArrayList<>(data.combat.fighters);
    OnlineInventory inventory = data.inventory;
    for (OnlineCoopExpedition expedition : data.combat.expeditions) {
      if (expedition.active()) {
        changed = true;
        expedition = expedition.advance();
        if (expedition.rewardApplied) {
          inventory = inventory.credit(expedition.loot);
          for (int i = 0; i < expedition.own.fighters.size(); i++) {
            OnlineCombatSquad.Fighter fighter = expedition.own.fighters.get(i);
            condition(
                fighters,
                fighter.id,
                fighter.health - expedition.healthLoss.get(i),
                fighter.stamina - expedition.staminaCost);
          }
        }
      }
      expeditions.add(expedition);
    }
    if (!changed) return data;
    return replace(
        data,
        inventory,
        new OnlineBattleRepository.State(
            fighters, data.combat.battles, expeditions, data.combat.nextId, true));
  }

  private void condition(
      List<OnlineCombatSquad.Fighter> fighters, String id, int health, int stamina) {
    for (int i = 0; i < fighters.size(); i++)
      if (fighters.get(i).id.equals(id)) {
        fighters.set(i, fighters.get(i).condition(health, stamina));
        return;
      }
    throw new IllegalArgumentException("Missing online fighter");
  }
}
