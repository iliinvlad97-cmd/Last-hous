package com.lastdom.game;

import java.util.*;

/** A city operation refers to the single existing cooperative expedition and job ID. */
final class OnlineCoopOperation {
  enum State {
    ACTIVE,
    COMPLETED
  }

  final String id, eventId;
  final List<String> allies;
  final State state;
  final OnlineOperationReport report;

  OnlineCoopOperation(
      String id, String event, List<String> allies, State state, OnlineOperationReport report) {
    if (id == null
        || event == null
        || allies.isEmpty()
        || allies.size() > 2
        || new HashSet<>(allies).size() != allies.size()
        || report == null
        || state == null
        || report.applied != (state == State.COMPLETED))
      throw new IllegalArgumentException("Invalid alliance operation");
    Set<String> expected = new HashSet<>(allies);
    expected.add(OnlineAllianceMember.PLAYER);
    if (!expected.equals(report.shares.keySet()))
      throw new IllegalArgumentException("Invalid operation participants");
    this.id = id;
    eventId = event;
    this.allies = Collections.unmodifiableList(new ArrayList<>(allies));
    this.state = state;
    this.report = report;
  }

  OnlineRoute route(OnlineCityEvent event) {
    return event.type.zoneId.equals("pve_industry")
        ? new OnlineRoute(.22f, .77f, .50f, .77f, .50f, .52f, .27f, .52f)
        : new OnlineRoute(.22f, .77f, .50f, .77f, .50f, .585f, .75f, .585f);
  }
}
