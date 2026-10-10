package com.lastdom.game;

import java.util.*;

/** Centralized relationship thresholds, costs and immutable quest definitions. */
final class StoryFactionConfig {
  static final int FOOD = 2,
      WATER = 2,
      QUEST_REPUTATION = 15,
      INITIAL_SIGNAL = 10,
      INITIAL_DOCUMENTS = 10,
      INITIAL_HEIRS = 5,
      SIDE_BONUS = 20,
      OTHER_SIDE_PENALTY = -5;
  static final String UNION = "settlements",
      GARRISON = "garrison",
      HEIRS = "dawn_heirs",
      NONE = "none",
      FLAG = "faction.side.";
  static final StoryFaction[] ALL = {
    new StoryFaction(
        UNION,
        "Союз свободных поселений",
        "Объединить выживших, восстановить связь и снабжение.",
        "Координатор Нина",
        "story.crossroads",
        "Гуманитарный маршрут",
        "Доставить 2 еды и 2 воды в поселение «Перекрёсток». Припасы передаются после успешного"
            + " возвращения; при нехватке можно передать их позже из журнала.",
        "Мы слышали о вашем убежище. Поселения нуждаются друг в друге. Приходите на Перекрёсток:"
            + " небольшой запас поможет восстановить маршрут снабжения."),
    new StoryFaction(
        GARRISON,
        "Гарнизон «Рубеж»",
        "Ограничить доступ к опасным технологиям и охранять стратегические объекты.",
        "Капитан Орлов",
        "story.military_checkpoint",
        "Проверка периметра",
        "Осмотреть опасный участок у военного блокпоста и передать сведения о дороге.",
        "Говорит капитан Орлов, гарнизон «Рубеж». Мы контролируем опасные"
            + " объекты. Прежде чем распространять координаты, проверьте дорогу возле нашего"
            + " блокпоста."),
    new StoryFaction(
        HEIRS,
        "Наследники «Рассвета»",
        "Восстановить экспериментальную систему и продолжить исследования.",
        "Исследователь Воронцов",
        "story.communication_lab",
        "Утерянный протокол",
        "Найти техническую запись в заброшенной лаборатории связи.",
        "Ваши документы могут объяснить сбой узлов «Рассвета». Мы ищем утерянный протокол в"
            + " лаборатории связи. Фрагмент ключа сохраните: он ещё понадобится.")
  };

  static StoryFaction faction(String id) {
    for (StoryFaction f : ALL) if (f.id.equals(id)) return f;
    return null;
  }

  static StoryFaction target(String id) {
    for (StoryFaction f : ALL) if (f.locationId.equals(id)) return f;
    return null;
  }

  static StoryDialogue dialogue(String id) {
    for (StoryFaction f : ALL) if (f.contact.event.id.equals(id)) return f.contact;
    return null;
  }

  static String tier(int value) {
    return value <= -60
        ? "Враждебность"
        : value <= -20
            ? "Недоверие"
            : value < 20 ? "Нейтралитет" : value < 60 ? "Доверие" : "Союзничество";
  }

  static int clamp(long value) {
    return (int) Math.max(-100, Math.min(100, value));
  }

  static String sideLabel(String id) {
    StoryFaction f = faction(id);
    return f == null ? "Пока никому" : f.name;
  }

  static List<MapLocation> locations() {
    List<MapLocation> out = new ArrayList<>();
    float[][] xy = {{.11f, .75f}, {.91f, .24f}, {.94f, .07f}};
    String[] names = {
      "Поселение «Перекрёсток»", "Военный блокпост", "Заброшенная лаборатория связи"
    };
    String[] markers = {"ПЕРЕКРЁСТОК", "ВОЕННЫЙ\nБЛОКПОСТ", "ЛАБОРАТОРИЯ\nСВЯЗИ"};
    for (int i = 0; i < ALL.length; i++)
      out.add(
          new MapLocation(
              ALL[i].locationId,
              names[i],
              markers[i],
              ALL[i].quest.name,
              MapLocation.Kind.STORY,
              i == 0 ? MapLocation.Distance.NEAR : MapLocation.Distance.FAR,
              i == 0 ? MapLocation.Risk.LOW_MEDIUM : MapLocation.Risk.HIGH,
              xy[i][0],
              xy[i][1],
              MapLocation.State.LOCKED,
              new LootTable()));
    return out;
  }

  private StoryFactionConfig() {}
}
