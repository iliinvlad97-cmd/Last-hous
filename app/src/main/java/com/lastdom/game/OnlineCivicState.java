package com.lastdom.game;

import java.util.*;

/** Versioned alliance/event metadata. Fighter assignments and loot are owned by combat state. */
final class OnlineCivicState {
  final boolean used;
  final long minute, nextSpawn, lastDebug;
  final int nextEvent;
  final OnlineAlliance alliance;
  final List<OnlineCityEvent> events;
  final List<OnlineCoopOperation> operations;

  OnlineCivicState(
      boolean used,
      long minute,
      long nextSpawn,
      long lastDebug,
      int nextEvent,
      OnlineAlliance alliance,
      List<OnlineCityEvent> events,
      List<OnlineCoopOperation> operations) {
    if (minute < 0
        || minute > 10_000_000_000L
        || nextSpawn < 0
        || lastDebug > minute
        || lastDebug < -OnlineCityEvent.INTERVAL
        || events.size() > OnlineCityEvent.MAX_EVENTS
        || operations.size() > OnlineCombatRules.MAX_HISTORY
        || nextEvent != events.size() + 1
        || (!used
            && (minute != 0 || alliance != null || !events.isEmpty() || !operations.isEmpty())))
      throw new IllegalArgumentException("Invalid civic state");
    Set<String> ids = new HashSet<>(), jobs = new HashSet<>(), handled = new HashSet<>();
    for (OnlineCityEvent event : events)
      if (!ids.add(event.id) || event.appearedMinute > minute)
        throw new IllegalArgumentException("Invalid event history");
    for (int i = 1; i < nextEvent; i++)
      if (!ids.contains("city_event_" + i)) throw new IllegalArgumentException("Missing event ID");
    for (OnlineCoopOperation op : operations)
      if (!jobs.add(op.id)
          || !handled.add(op.eventId)
          || !ids.contains(op.eventId)
          || alliance == null) throw new IllegalArgumentException("Invalid operation history");
    this.used = used;
    this.minute = minute;
    this.nextSpawn = nextSpawn;
    this.lastDebug = lastDebug;
    this.nextEvent = nextEvent;
    this.alliance = alliance;
    this.events = Collections.unmodifiableList(new ArrayList<>(events));
    this.operations = Collections.unmodifiableList(new ArrayList<>(operations));
  }

  static OnlineCivicState initial() {
    return new OnlineCivicState(
        false,
        0,
        OnlineCityEvent.FIRST_SPAWN,
        -OnlineCityEvent.INTERVAL,
        1,
        null,
        Collections.emptyList(),
        Collections.emptyList());
  }

  OnlineCityEvent event(String id) {
    for (OnlineCityEvent e : events) if (e.id.equals(id)) return e;
    return null;
  }

  OnlineCoopOperation operation(String id) {
    for (OnlineCoopOperation o : operations) if (o.id.equals(id)) return o;
    return null;
  }

  void validate(OnlineBattleRepository.State combat) {
    Map<String, int[]> expected = new HashMap<>();
    if (alliance != null)
      for (OnlineAllianceMember member : alliance.members)
        expected.put(member.shelterId, new int[3]);
    for (OnlineCoopOperation op : operations) {
      OnlineCityEvent e = event(op.eventId);
      OnlineCoopExpedition trip = combat.expedition(op.id);
      if (trip == null
          || !e.operationId.equals(op.id)
          || !e.type.zoneId.equals(trip.zoneId)
          || (op.state == OnlineCoopOperation.State.COMPLETED) != trip.rewardApplied
          || e.state
              != (trip.rewardApplied
                  ? OnlineCityEvent.State.COMPLETED
                  : OnlineCityEvent.State.ACTIVE))
        throw new IllegalArgumentException("Unmatched operation phase");
      for (String ally : op.allies)
        if (alliance.member(ally) == null
            || alliance.member(ally).invitation != OnlineAllianceMember.Invitation.ACCEPTED)
          throw new IllegalArgumentException("Unaccepted ally");
      if (trip.rewardApplied && trip.success)
        for (Map.Entry<String, Integer> share : op.report.shares.entrySet()) {
          int[] totals = expected.get(share.getKey());
          if (totals == null) throw new IllegalArgumentException("Missing credited member");
          totals[0] =
              Math.addExact(totals[0], share.getKey().equals(OnlineAllianceMember.PLAYER) ? 10 : 5);
          totals[1] = Math.addExact(totals[1], share.getValue());
          totals[2] = Math.addExact(totals[2], 1);
        }
    }
    if (alliance != null)
      for (OnlineAllianceMember member : alliance.members) {
        int[] totals = expected.get(member.shelterId);
        if (member.reputation != totals[0]
            || member.contribution != totals[1]
            || member.successes != totals[2])
          throw new IllegalArgumentException("Inconsistent alliance accounting");
      }
    for (OnlineCityEvent e : events)
      if ((!e.operationId.isEmpty() && operation(e.operationId) == null)
          || (e.state == OnlineCityEvent.State.AVAILABLE && minute >= e.expiresMinute)
          || (e.state == OnlineCityEvent.State.EXPIRED && minute < e.expiresMinute))
        throw new IllegalArgumentException("Orphan event job");
  }
}
