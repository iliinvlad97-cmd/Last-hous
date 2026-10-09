package com.lastdom.game;

import java.util.*;

/** Shared read-only command preflight for UI and repository transitions. Never rolls results. */
final class OnlineActionRules {
  static int medicine(OnlineCombatSquad.Fighter f) {
    return f.health < 100 ? OnlineCombatRules.RECOVER_MEDICINE : 0;
  }

  static int water(OnlineCombatSquad.Fighter f) {
    return f.stamina < 100 ? OnlineCombatRules.RECOVER_WATER : 0;
  }

  static String recovery(OnlineWorldGameplay.Data d, String id) {
    OnlineCombatSquad.Fighter f = d.combat.fighter(id);
    if (f == null) return "Выберите бойца для восстановления";
    if (d.combat.busy(id)) return "Отряд занят";
    if (f.health == 100 && f.stamina == 100) return "Восстановление не требуется";
    if (d.inventory.amount(OnlineInventory.Resource.MEDICINE) < medicine(f))
      return "Недостаточно ресурсов: нужен медикамент";
    if (d.inventory.amount(OnlineInventory.Resource.WATER) < water(f))
      return "Недостаточно ресурсов: нужна вода";
    return "";
  }

  static String mission(
      OnlineWorldGameplay.Data d,
      OnlineWorldRepository.Snapshot world,
      String id,
      String zone,
      List<String> own,
      String ally,
      boolean pvp) {
    if (!d.combat.requestId().equals(id)) return "Подготовка устарела: откройте её заново";
    if (d.combat.nextId > OnlineCombatRules.MAX_HISTORY) return "Демо-история заполнена";
    if (own == null || own.isEmpty() || own.size() > 3 || new HashSet<>(own).size() != own.size())
      return "Выберите 1–3 доступных бойцов";
    for (String fid : own) {
      String reason = d.combat.unavailable(fid);
      if (!reason.isEmpty()) return reason;
    }
    if (pvp) {
      if (!"pvp_frontier".equals(zone)) return "Выберите PvP-зону";
      for (OnlineBattleRepository.Battle b : d.combat.battles)
        if (b.active()) return "Сначала завершите текущий демо-бой";
    } else {
      if (OnlineCombatRules.coopMinutes(zone) == 0) return "Выберите PvE-зону";
      return ally(d, world, ally);
    }
    return "";
  }

  static String ally(OnlineWorldGameplay.Data d, OnlineWorldRepository.Snapshot world, String id) {
    for (OnlineShelter s : world.shelters)
      if (s.id.equals(id)) {
        for (OnlineCombatSquad.Fighter f : OnlineCombatSquad.ally(s).fighters)
          if (d.combat.busy(f.id)) return "Отряд союзника уже занят";
        return "";
      }
    return "Союзник не найден";
  }

  static String operation(
      OnlineWorldGameplay.Data d,
      OnlineWorldRepository.Snapshot world,
      String id,
      String eventId,
      List<String> own,
      List<String> allies) {
    OnlineCityEvent e = d.civic.event(eventId);
    if (e == null
        || e.state != OnlineCityEvent.State.AVAILABLE
        || d.civic.minute >= e.expiresMinute) return "Событие занято, завершено или истекло";
    if (d.civic.alliance == null) return "Сначала создайте союз";
    if (allies == null
        || allies.isEmpty()
        || allies.size() > 2
        || new HashSet<>(allies).size() != allies.size()) return "Выберите 1–2 принятых союзника";
    for (String aid : allies) {
      OnlineAllianceMember m = d.civic.alliance.member(aid);
      if (m == null || m.invitation != OnlineAllianceMember.Invitation.ACCEPTED)
        return "Союзник ещё не принял приглашение";
      String reason = ally(d, world, aid);
      if (!reason.isEmpty()) return reason;
    }
    return mission(d, world, id, e.type.zoneId, own, allies.get(0), false);
  }

  private OnlineActionRules() {}
}
