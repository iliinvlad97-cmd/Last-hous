package com.lastdom.game;

import java.util.*;

/** Durable campaign state, committed with the existing solo snapshot. */
final class StoryState {
  enum Phase {
    DORMANT,
    SEARCHING,
    DECODE_READY,
    DECODING,
    DECISION,
    CHAIN_COMPLETE
  }

  Phase phase = Phase.DORMANT;
  String chapterId = "chapter.last_signal",
      questId = "",
      specialistId = "",
      previousJob = "",
      pendingMessage = "",
      decisionId = "";
  long decodeStart;
  int decodeElapsed;
  StoryFlags.Ending ending = StoryFlags.Ending.NONE;
  final Set<String> flags = new LinkedHashSet<>(),
      objectives = new LinkedHashSet<>(),
      items = new LinkedHashSet<>(),
      processedReturns = new LinkedHashSet<>(),
      readMessages = new LinkedHashSet<>();
  final List<String> messages = new ArrayList<>();
  final Map<String, Boolean> attemptResults = new LinkedHashMap<>();

  void reset() {
    phase = Phase.DORMANT;
    chapterId = "chapter.last_signal";
    questId = specialistId = previousJob = pendingMessage = decisionId = "";
    decodeStart = 0;
    decodeElapsed = 0;
    ending = StoryFlags.Ending.NONE;
    flags.clear();
    objectives.clear();
    items.clear();
    processedReturns.clear();
    readMessages.clear();
    messages.clear();
    attemptResults.clear();
  }

  boolean survivalContinues() {
    return true;
  }
}
