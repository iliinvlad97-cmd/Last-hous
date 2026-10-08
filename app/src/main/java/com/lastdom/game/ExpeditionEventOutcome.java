package com.lastdom.game;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted consequence values, resolved once and applied in one save transaction. */
final class ExpeditionEventOutcome {
  final ExpeditionLoot added = new ExpeditionLoot(), lost = new ExpeditionLoot();
  final Map<String, Integer> healthLoss = new LinkedHashMap<>(),
      fatigueAdded = new LinkedHashMap<>();
  int delayMinutes, riskReduction;
  boolean retreat;
  String message = "";
}
