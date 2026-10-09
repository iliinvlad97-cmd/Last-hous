package com.lastdom.game;

import java.util.*;

/** Event schedule and operation commands, independent of Android and wall-clock time. */
final class OnlineCityEventController {
  private OnlineCombatController.Change result(
      OnlineWorldGameplay.Data d, boolean ok, String message) {
    return OnlineAllianceController.result(d, ok, message);
  }

  private OnlineCityEvent newEvent(int id, long minute) {
    OnlineCityEvent.Type[] types = OnlineCityEvent.Type.values();
    return new OnlineCityEvent(
        "city_event_" + id,
        types[(id - 1) % types.length],
        minute,
        minute + OnlineCityEvent.LIFETIME,
        OnlineCityEvent.State.AVAILABLE,
        "");
  }

  OnlineCombatController.Change spawnDemo(OnlineWorldGameplay.Data d) {
    OnlineCivicState c = d.civic;
    if (!c.used) return result(d, false, "Откройте раздел событий");
    if (c.events.size() >= OnlineCityEvent.MAX_EVENTS)
      return result(d, false, "Демо-история событий заполнена");
    if (c.minute - c.lastDebug < OnlineCityEvent.INTERVAL)
      return result(
          d,
          false,
          "Демо-событие: осталось "
              + (OnlineCityEvent.INTERVAL - c.minute + c.lastDebug)
              + " игровых минут");
    List<OnlineCityEvent> events = new ArrayList<>(c.events);
    events.add(newEvent(c.nextEvent, c.minute));
    return result(
        d.withCivic(
            new OnlineCivicState(
                true,
                c.minute,
                c.nextSpawn,
                c.minute,
                c.nextEvent + 1,
                c.alliance,
                events,
                c.operations)),
        true,
        "Создано одно демо-событие. Повтор через 120 игровых минут");
  }

  static OnlineCombatSquad allies(OnlineWorldRepository.Snapshot world, List<String> ids) {
    if (ids == null || ids.isEmpty() || ids.size() > 2 || new HashSet<>(ids).size() != ids.size())
      throw new IllegalArgumentException("Выберите 1–2 союзных убежища");
    List<OnlineCombatSquad.Fighter> fighters = new ArrayList<>();
    for (String id : ids) {
      OnlineShelter shelter =
          world.shelters.stream()
              .filter(s -> s.id.equals(id))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Союзник не найден"));
      fighters.add(OnlineCombatSquad.ally(shelter).fighters.get(0));
    }
    OnlineShelter first =
        world.shelters.stream().filter(s -> s.id.equals(ids.get(0))).findFirst().get();
    fighters.add(OnlineCombatSquad.ally(first).fighters.get(1));
    return new OnlineCombatSquad(fighters);
  }

  OnlineCombatController.Change start(
      OnlineWorldGameplay.Data d,
      OnlineWorldRepository.Snapshot world,
      String id,
      String eventId,
      List<String> fighterIds,
      List<String> allies,
      long seed,
      boolean confirmed) {
    OnlineCivicState c = d.civic;
    OnlineCityEvent event = c.event(eventId);
    if (!confirmed) return result(d, false, "Подтвердите совместную операцию");
    if (!d.combat.requestId().equals(id) || d.combat.nextId > OnlineCombatRules.MAX_HISTORY)
      return result(d, false, "Задание уже принято или история заполнена");
    if (event == null
        || event.state != OnlineCityEvent.State.AVAILABLE
        || c.minute >= event.expiresMinute)
      return result(d, false, "Событие уже занято, завершено или истекло");
    if (c.alliance == null) return result(d, false, "Сначала создайте союз");
    String preflight = OnlineActionRules.operation(d, world, id, eventId, fighterIds, allies);
    if (!preflight.isEmpty()) return result(d, false, preflight);
    try {
      OnlineCombatSquad allySquad = allies(world, allies);
      for (String ally : allies) {
        OnlineAllianceMember member = c.alliance.member(ally);
        if (member == null || member.invitation != OnlineAllianceMember.Invitation.ACCEPTED)
          throw new IllegalArgumentException("Союзник должен принять приглашение");
        for (OnlineCombatSquad.Fighter fighter :
            OnlineCombatSquad.ally(
                    world.shelters.stream().filter(s -> s.id.equals(ally)).findFirst().get())
                .fighters)
          if (d.combat.busy(fighter.id))
            throw new IllegalArgumentException("Отряд союзника уже занят");
      }
      if (fighterIds == null
          || fighterIds.isEmpty()
          || fighterIds.size() > 3
          || new HashSet<>(fighterIds).size() != fighterIds.size())
        throw new IllegalArgumentException("Выберите 1–3 уникальных бойцов");
      List<OnlineCombatSquad.Fighter> fighters = new ArrayList<>();
      for (String fid : fighterIds) {
        String reason = d.combat.unavailable(fid);
        if (!reason.isEmpty()) throw new IllegalArgumentException(reason);
        fighters.add(d.combat.fighter(fid));
      }
      OnlineCombatSquad own = new OnlineCombatSquad(fighters);
      OnlineCoopExpedition expedition =
          OnlineCoopExpedition.calculate(
              id, event.type.zoneId, allies.get(0), own, allySquad, seed);
      List<OnlineCoopExpedition> expeditions = new ArrayList<>(d.combat.expeditions);
      expeditions.add(expedition);
      OnlineBattleRepository.State combat =
          new OnlineBattleRepository.State(
              d.combat.fighters, d.combat.battles, expeditions, d.combat.nextId + 1, true);
      Map<String, Double> weights = new LinkedHashMap<>();
      weights.put(OnlineAllianceMember.PLAYER, own.strength());
      for (String ally : allies) {
        List<OnlineCombatSquad.Fighter> matching = new ArrayList<>();
        for (OnlineCombatSquad.Fighter f : allySquad.fighters)
          if (f.id.startsWith(ally + "_ally_")) matching.add(f);
        weights.put(ally, new OnlineCombatSquad(matching).strength());
      }
      double total = own.strength() + allySquad.strength();
      Map<String, Integer> shares = new LinkedHashMap<>();
      int remain = 100;
      for (String ally : allies) {
        int share = (int) Math.floor(weights.get(ally) * 100 / Math.max(1, total));
        shares.put(ally, share);
        remain -= share;
      }
      shares.put(OnlineAllianceMember.PLAYER, remain);
      List<OnlineCoopOperation> operations = new ArrayList<>(c.operations);
      operations.add(
          new OnlineCoopOperation(
              id,
              event.id,
              allies,
              OnlineCoopOperation.State.ACTIVE,
              new OnlineOperationReport(shares, false)));
      List<OnlineCityEvent> events = new ArrayList<>();
      for (OnlineCityEvent e : c.events)
        events.add(e.id.equals(eventId) ? e.phase(OnlineCityEvent.State.ACTIVE, id) : e);
      OnlineCivicState civic =
          new OnlineCivicState(
              true,
              c.minute,
              c.nextSpawn,
              c.lastDebug,
              c.nextEvent,
              c.alliance,
              events,
              operations);
      return result(
          new OnlineWorldGameplay.Data(
              d.inventory, d.offers, d.operations, d.reputation, d.pvpZoneId, combat, civic),
          true,
          "Совместная операция отправлена");
    } catch (IllegalArgumentException invalid) {
      return result(d, false, invalid.getMessage());
    }
  }

  OnlineWorldGameplay.Data advance(OnlineWorldGameplay.Data d) {
    OnlineCivicState c = d.civic;
    if (!c.used) return d;
    long minute = Math.addExact(c.minute, 1), nextSpawn = c.nextSpawn;
    List<OnlineAllianceMember> members =
        c.alliance == null ? new ArrayList<>() : new ArrayList<>(c.alliance.members);
    for (int i = 0; i < members.size(); i++) members.set(i, members.get(i).resolve(minute));
    List<OnlineCoopOperation> operations = new ArrayList<>();
    Set<String> completed = new HashSet<>();
    for (OnlineCoopOperation op : c.operations) {
      OnlineCoopExpedition e = d.combat.expedition(op.id);
      if (op.state == OnlineCoopOperation.State.ACTIVE && e.rewardApplied) {
        completed.add(op.eventId);
        if (e.success)
          for (int i = 0; i < members.size(); i++) {
            OnlineAllianceMember member = members.get(i);
            Integer share = op.report.shares.get(member.shelterId);
            if (share != null) members.set(i, member.credit(share));
          }
        op =
            new OnlineCoopOperation(
                op.id,
                op.eventId,
                op.allies,
                OnlineCoopOperation.State.COMPLETED,
                new OnlineOperationReport(op.report.shares, true));
      }
      operations.add(op);
    }
    List<OnlineCityEvent> events = new ArrayList<>();
    for (OnlineCityEvent e : c.events) {
      if (completed.contains(e.id)) e = e.phase(OnlineCityEvent.State.COMPLETED, e.operationId);
      else if (e.state == OnlineCityEvent.State.AVAILABLE && minute >= e.expiresMinute)
        e = e.phase(OnlineCityEvent.State.EXPIRED, "");
      events.add(e);
    }
    int nextEvent = c.nextEvent;
    if (minute >= nextSpawn && events.size() < OnlineCityEvent.MAX_EVENTS) {
      events.add(newEvent(nextEvent++, minute));
      nextSpawn = minute + OnlineCityEvent.INTERVAL;
    }
    OnlineAlliance alliance =
        c.alliance == null ? null : new OnlineAlliance(c.alliance.name, members);
    return d.withCivic(
        new OnlineCivicState(
            true, minute, nextSpawn, c.lastDebug, nextEvent, alliance, events, operations));
  }
}
