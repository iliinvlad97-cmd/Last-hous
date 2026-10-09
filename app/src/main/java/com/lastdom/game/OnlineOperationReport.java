package com.lastdom.game;

import java.util.*;

/** Frozen contribution shares; resources and damage remain in the existing PvE report. */
final class OnlineOperationReport {
  final Map<String, Integer> shares;
  final boolean applied;

  OnlineOperationReport(Map<String, Integer> shares, boolean applied) {
    int sum = 0;
    for (Map.Entry<String, Integer> e : shares.entrySet()) {
      if (!OnlineAllianceController.known(e.getKey()) || e.getValue() < 0 || e.getValue() > 100)
        throw new IllegalArgumentException("Invalid contribution");
      sum += e.getValue();
    }
    if (shares.size() < 2
        || shares.size() > 3
        || !shares.containsKey(OnlineAllianceMember.PLAYER)
        || sum != 100) throw new IllegalArgumentException("Incomplete contributions");
    this.shares = Collections.unmodifiableMap(new LinkedHashMap<>(shares));
    this.applied = applied;
  }
}
