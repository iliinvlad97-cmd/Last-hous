package com.lastdom.game;

/** Immutable plot faction; deliberately unrelated to online shelters. */
final class StoryFaction {
  final String id, name, description, representative, locationId;
  final StoryQuest quest;
  final StoryDialogue contact;

  StoryFaction(
      String id,
      String name,
      String description,
      String representative,
      String locationId,
      String questName,
      String mission,
      String greeting) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.representative = representative;
    this.locationId = locationId;
    quest =
        new StoryQuest(
            "faction.quest." + id,
            "chapter.city_conflicts",
            questName,
            new StoryObjective("faction.done." + id, mission));
    contact =
        new StoryDialogue(
            "faction.contact." + id,
            representative + " · " + name,
            new StoryDialogue.Line(representative, name + " · радиосвязь", greeting, ""),
            new StoryDialogue.Line(representative, "Предложение встречи", mission, ""));
  }
}
