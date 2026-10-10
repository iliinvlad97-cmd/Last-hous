package com.lastdom.game;

import java.util.*;

/** Durable faction progress is part of the existing atomic story snapshot. */
final class StoryFactionState {
  boolean started;
  String side = "";
  final Map<String, Integer> reputation = new LinkedHashMap<>();
  final Set<String> contacts = new LinkedHashSet<>(),
      completed = new LinkedHashSet<>(),
      applied = new LinkedHashSet<>(),
      supplied = new LinkedHashSet<>();
  final List<String> history = new ArrayList<>();

  void reset() {
    started = false;
    side = "";
    reputation.clear();
    contacts.clear();
    completed.clear();
    applied.clear();
    supplied.clear();
    history.clear();
  }
}
