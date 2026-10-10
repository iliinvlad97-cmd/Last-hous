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

  StoryInvestigationController.Phase investigation = StoryInvestigationController.Phase.DORMANT;
  String investigationDecision = "";
  boolean entranceInspected;

  int voicesStage, evaTrust = StoryDialogue.INITIAL_TRUST;
  final Map<String, Integer> dialogueSteps = new LinkedHashMap<>(),
      attitudes = new LinkedHashMap<>();
  final Map<String, String> answers = new LinkedHashMap<>(), residentIds = new LinkedHashMap<>();
  final Map<String, String> transcripts = new LinkedHashMap<>();
  final Set<String> completedDialogues = new LinkedHashSet<>();

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
    investigation = StoryInvestigationController.Phase.DORMANT;
    investigationDecision = "";
    entranceInspected = false;
    voicesStage = 0;
    evaTrust = StoryDialogue.INITIAL_TRUST;
    dialogueSteps.clear();
    attitudes.clear();
    answers.clear();
    residentIds.clear();
    transcripts.clear();
    completedDialogues.clear();
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
