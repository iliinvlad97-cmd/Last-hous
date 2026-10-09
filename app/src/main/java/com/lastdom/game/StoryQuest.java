package com.lastdom.game;

import java.util.*;

final class StoryQuest {
  final String id, chapterId, name;
  final List<StoryObjective> objectives;

  StoryQuest(String id, String chapter, String name, StoryObjective... objectives) {
    this.id = id;
    chapterId = chapter;
    this.name = name;
    this.objectives = Collections.unmodifiableList(Arrays.asList(objectives));
  }
}
