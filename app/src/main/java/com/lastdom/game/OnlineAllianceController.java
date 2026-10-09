package com.lastdom.game;

import java.util.*;

/** Pure alliance commands. Invitations never grant loot or reputation. */
final class OnlineAllianceController {
  static boolean known(String id) {
    return Arrays.asList(
            OnlineAllianceMember.PLAYER,
            "demo_ember",
            "demo_beacon",
            "demo_foundry",
            "demo_outpost")
        .contains(id);
  }

  static int required(String id) {
    return id.equals("demo_foundry") ? 10 : id.equals("demo_outpost") ? 20 : 0;
  }

  static String rule(String id) {
    int n = required(id);
    return n == 0
        ? "Принимает через 10 игровых минут"
        : "Нужно " + n + " репутации союза; ответ через 10 минут";
  }

  static OnlineCombatController.Change result(
      OnlineWorldGameplay.Data data, boolean ok, String message) {
    return new OnlineCombatController.Change(data, ok, message);
  }

  OnlineCombatController.Change activate(OnlineWorldGameplay.Data d) {
    OnlineCivicState c = d.civic;
    return result(
        c.used
            ? d
            : d.withCivic(
                new OnlineCivicState(
                    true,
                    c.minute,
                    c.nextSpawn,
                    c.lastDebug,
                    c.nextEvent,
                    c.alliance,
                    c.events,
                    c.operations)),
        true,
        "Демо-сеть активирована");
  }

  OnlineCombatController.Change create(OnlineWorldGameplay.Data d, String name) {
    if (d.civic.alliance != null) return result(d, false, "Союз уже создан");
    try {
      OnlineAlliance a =
          new OnlineAlliance(
              name == null ? null : name.trim(),
              Collections.singletonList(
                  new OnlineAllianceMember(
                      OnlineAllianceMember.PLAYER,
                      "owner",
                      OnlineAllianceMember.Invitation.ACCEPTED,
                      0,
                      true,
                      0,
                      0,
                      0)));
      OnlineCivicState c = d.civic;
      return result(
          d.withCivic(
              new OnlineCivicState(
                  true,
                  c.minute,
                  c.nextSpawn,
                  c.lastDebug,
                  c.nextEvent,
                  a,
                  c.events,
                  c.operations)),
          true,
          "Демонстрационный союз создан");
    } catch (IllegalArgumentException invalid) {
      return result(d, false, invalid.getMessage());
    }
  }

  OnlineCombatController.Change invite(OnlineWorldGameplay.Data d, String id) {
    OnlineCivicState c = d.civic;
    OnlineAlliance a = c.alliance;
    if (a == null || !known(id) || id.equals(OnlineAllianceMember.PLAYER))
      return result(d, false, "Создайте союз и выберите виртуальное убежище");
    OnlineAllianceMember old = a.member(id);
    if (old != null && old.invitation != OnlineAllianceMember.Invitation.REJECTED)
      return result(d, false, "Убежище уже приглашено или состоит в союзе");
    if (old != null && a.reputation() < required(id))
      return result(d, false, "Для нового приглашения нужно " + required(id) + " репутации союза");
    List<OnlineAllianceMember> members = new ArrayList<>(a.members);
    if (old != null) members.remove(old);
    members.add(
        new OnlineAllianceMember(
            id,
            "invite_" + id + "_" + c.minute,
            OnlineAllianceMember.Invitation.PENDING,
            c.minute + 10,
            a.reputation() >= required(id),
            0,
            0,
            0));
    return result(
        d.withCivic(
            new OnlineCivicState(
                true,
                c.minute,
                c.nextSpawn,
                c.lastDebug,
                c.nextEvent,
                new OnlineAlliance(a.name, members),
                c.events,
                c.operations)),
        true,
        "Приглашение отправлено. " + rule(id));
  }
}
