package com.lastdom.game;

import java.util.*;

/** One named demo alliance; totals derive from the same credited member records. */
final class OnlineAlliance {
  final String id = "demo_alliance_1", name;
  final List<OnlineAllianceMember> members;

  OnlineAlliance(String name, List<OnlineAllianceMember> members) {
    if (name == null
        || !name.equals(name.trim())
        || name.length() < 2
        || name.length() > 28
        || !name.matches("[\\p{L}\\p{N} ._-]+")
        || members.isEmpty()
        || members.size() > 5)
      throw new IllegalArgumentException("Название: 2–28 букв, цифр, пробелов или ._- ");
    Set<String> ids = new HashSet<>();
    for (OnlineAllianceMember member : members)
      if (!ids.add(member.shelterId) || !OnlineAllianceController.known(member.shelterId))
        throw new IllegalArgumentException("Duplicate/unknown alliance member");
    OnlineAllianceMember player =
        members.stream()
            .filter(m -> m.shelterId.equals(OnlineAllianceMember.PLAYER))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Missing player membership"));
    if (player.invitation != OnlineAllianceMember.Invitation.ACCEPTED)
      throw new IllegalArgumentException("Invalid owner");
    this.name = name;
    this.members = Collections.unmodifiableList(new ArrayList<>(members));
  }

  OnlineAllianceMember member(String id) {
    for (OnlineAllianceMember m : members) if (m.shelterId.equals(id)) return m;
    return null;
  }

  int reputation() {
    int n = 0;
    for (OnlineAllianceMember m : members) n = Math.addExact(n, m.reputation);
    return n;
  }

  int successes() {
    return member(OnlineAllianceMember.PLAYER).successes;
  }

  int rating() {
    int n = reputation();
    for (OnlineAllianceMember m : members) n = Math.addExact(n, m.contribution);
    return Math.addExact(n, successes() * 20);
  }
}
