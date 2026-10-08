package com.lastdom.game;

import java.util.*;

/** Frozen launch conditions and exactly-once result, stored with the existing expedition. */
final class ReconData {
  long seed;
  int chanceBasis, researchMinutes, injuryChance, damagePercent = 100;
  boolean resolved, success, injuryApplied, unlocksApplied;
  String injuredId = "";
  int healthLoss;
  final Map<String, String> names = new LinkedHashMap<>();
  final Map<String, Integer> startFatigue = new LinkedHashMap<>(),
      fatigueGain = new LinkedHashMap<>();
  final List<String> openedPoints = new ArrayList<>();
}
