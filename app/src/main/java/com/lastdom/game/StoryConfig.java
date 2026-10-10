package com.lastdom.game;

import java.util.*;

/**
 * Stable original campaign definitions; chapter-two investigation extends these via configuration.
 */
final class StoryConfig {
  static final int START_DAY = 3, DECODE_MINUTES = 60;
  static final String RADIO = "story.old_radio", CARRIER = "damaged_data_carrier";
  static final List<String> CHAPTERS =
      Collections.unmodifiableList(
          Arrays.asList(
              "Последний сигнал",
              "Следы прошлого",
              "Цена спасения",
              "Город на грани",
              "Рассвет или тьма"));
  static final StoryQuest FIRST =
      new StoryQuest(
          "quest.unknown_frequency",
          "chapter.last_signal",
          "Неизвестная частота",
          new StoryObjective("signal", "Прочитать необычную передачу"),
          new StoryObjective("carrier", "Доставить носитель со старой радиостанции"),
          new StoryObjective("decode", "Расшифровать координаты в мастерской"),
          new StoryObjective("choice", "Решить судьбу координат"));
  static final StoryQuest VOICES =
      new StoryQuest(
          "quest.voices_on_air",
          "chapter.last_signal",
          "Голоса в эфире",
          new StoryObjective("voices.message", "Получить новое сообщение"),
          new StoryObjective("voices.discussion", "Обсудить сигнал с жителями"),
          new StoryObjective("voices.eva", "Поговорить с Евой"),
          new StoryObjective("voices.consequence", "Узнать последствия первого решения"),
          new StoryObjective("voices.hook", "Получить зацепку следующего задания"));
  static final StoryEvent SIGNAL =
      new StoryEvent(
          "message.station17",
          "Станция 17",
          "...Если кто-нибудь меня слышит, не отключайте приёмник. Здесь станция 17. Мы нашли то,"
              + " что осталось от проекта „Рассвет“. Координаты повреждены. Повторяю: не доверяйте"
              + " автоматическим передачам...");
  static final StoryEvent FOUND =
      new StoryEvent(
          "message.carrier",
          "Отряд",
          "Повреждённый носитель доставлен со старой радиостанции. Инженер или механик сможет"
              + " расшифровать его в мастерской.");
  static final StoryEvent DECODED =
      new StoryEvent(
          "message.coordinates",
          "Мастерская",
          "Удалось восстановить часть координат объекта „Рассвет“. Данных пока недостаточно для"
              + " похода. Передать сигнал другим выжившим или сохранить координаты в тайне?");
  static final StoryChoice
      SHARE =
          new StoryChoice("choice.broadcast", "Передать сигнал другим выжившим", StoryFlags.SHARE),
      SECRET =
          new StoryChoice("choice.keep_secret", "Сохранить координаты в тайне", StoryFlags.SECRET);

  static StoryEvent event(String id) {
    for (StoryEvent e : new StoryEvent[] {SIGNAL, FOUND, DECODED}) if (e.id.equals(id)) return e;
    StoryDialogue d = StoryDialogue.find(id);
    return d == null ? null : d.event;
  }

  static MapLocation location() {
    return new MapLocation(
        RADIO,
        "Старая радиостанция",
        "СТАРАЯ\nРАДИОСТАНЦИЯ",
        "Повреждённый носитель данных",
        MapLocation.Kind.STORY,
        MapLocation.Distance.MEDIUM,
        MapLocation.Risk.MEDIUM,
        .47f,
        .34f,
        MapLocation.State.LOCKED,
        new LootTable());
  }

  static double recoveryChance(GameController g, Expedition e) {
    double quality = 0;
    boolean technical = false;
    for (String id : e.participantIds) {
      Resident r = g.expeditionController.resident(id);
      if (r != null) {
        quality += SurvivalConfig.efficiency(r);
        technical |= r.role.equals("Инженер") || r.role.equals("Механик");
      }
    }
    return Math.max(
        .1,
        Math.min(
            .95,
            .65
                + .1 * e.participantIds.size()
                + .1 * quality / e.participantIds.size()
                + (technical ? .05 : 0)));
  }

  private StoryConfig() {}
}
