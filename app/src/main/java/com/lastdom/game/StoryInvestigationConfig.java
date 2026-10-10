package com.lastdom.game;

import java.util.*;

/** Chapter-two investigation definitions; no economy or independent expedition models. */
final class StoryInvestigationConfig {
  static final double LAB_RECOVERY_PENALTY = .20, ENTRANCE_RECOVERY_PENALTY = .10;
  static final String ARCHIVE = "story.city_archive",
      LAB = "story.research_lab",
      ENTRANCE = "story.sealed_entrance";
  static final String DOCUMENTS = "dawn.archive_documents",
      CATASTROPHE = "dawn.first_days",
      MENTION = "dawn.project_mention",
      JOURNAL = "dawn.damaged_journal",
      NOTES = "dawn.technical_notes",
      KEY = "dawn.access_fragment";
  static final String SHARE = "dawn.share_documents", KEEP = "dawn.keep_documents";
  static final StoryQuest QUEST =
      new StoryQuest(
          "quest.traces_of_past",
          "chapter.traces_of_past",
          "Следы прошлого",
          new StoryObjective("dawn.eva", "Получить сведения Евы о городском архиве"),
          new StoryObjective("dawn.archive", "Вернуть документы из городского архива"),
          new StoryObjective("dawn.review", "Изучить архивные документы с Евой"),
          new StoryObjective("dawn.lab", "Доставить журнал и ключ из исследовательского корпуса"),
          new StoryObjective("dawn.notes", "Обсудить технические записи с Иваном и Марией"),
          new StoryObjective("dawn.entrance", "Осмотреть закрытый подземный вход"),
          new StoryObjective("dawn.warning", "Обсудить угрозу комплекса с Сергеем"),
          new StoryObjective("dawn.choice", "Решить, кому передать найденные документы"));
  static final StoryDialogue INTRO =
      new StoryDialogue(
          "dawn.intro",
          "Ева · Городской архив",
          StoryDialogue.eva(
              "Я проверила записи станции 17. Копии документов отправляли в городской архив. Там"
                  + " может быть журнал первых дней катастрофы."),
          StoryDialogue.eva(
              "Отправьте отряд в архив и верните бумаги в убежище. Сравним даты и подписи; одного"
                  + " радиосигнала недостаточно."));
  static final StoryDialogue ARCHIVE_REVIEW =
      new StoryDialogue(
          "dawn.archive_review",
          "Архивные документы · Ева",
          new StoryDialogue.Line(
              "Архивные документы",
              "Документ · первые дни катастрофы",
              "Запись: после испытательного запуска городские узлы восстановления перестали"
                  + " отвечать. Среди служебных распоряжений упомянут проект «Рассвет».",
              ""),
          StoryDialogue.eva(
              "Это документы городской службы, а не рекламная передача. В накладной указан"
                  + " исследовательский корпус. Он дальше и опаснее архива; там могли остаться"
                  + " технические записи."));
  static final StoryDialogue LAB_REVIEW =
      new StoryDialogue(
          "dawn.lab_review",
          "Исследовательский журнал · анализ",
          new StoryDialogue.Line(
              "Повреждённый исследовательский журнал",
              "Документ · технические записи",
              "«Рассвет» — экспериментальная система восстановления городской инфраструктуры."
                  + " Испытания привели к непредвиденным последствиям. Раздел с окончательными"
                  + " выводами повреждён; причина катастрофы пока не доказана.",
              ""),
          StoryDialogue.resident(
              "Иван",
              "Инженер",
              "Система должна была связывать энергию, воду и транспорт. Записи показывают сбой"
                  + " согласования узлов. Данные неполные: делать вывод о всей катастрофе рано."),
          StoryDialogue.resident(
              "Мария",
              "Врач",
              "В журнале есть упоминания о последствиях для людей, но нет завершённого отчёта."
                  + " Нужно проверить, кого затронули испытания, прежде чем снова запускать"
                  + " систему."),
          StoryDialogue.eva(
              "Фрагмент ключа указывает на закрытый подземный вход. Можно осмотреть внешний"
                  + " терминал. Одного фрагмента для открытия гермодвери недостаточно."));
  static final StoryDialogue ENTRANCE_REVIEW =
      new StoryDialogue(
          "dawn.entrance_review",
          "Закрытый комплекс · предупреждение",
          new StoryDialogue.Line(
              "Отчёт внешнего осмотра",
              "Гермодверь · закрыта",
              "Дверь герметична, запоры работают. Терминал распознал фрагмент, но требует полный"
                  + " ключ и подтверждённый код допуска. Обход блокировки не найден. Внутренние"
                  + " уровни недоступны.",
              ""),
          StoryDialogue.resident(
              "Сергей",
              "Охрана",
              "Комплекс закрыт намеренно. Не ломайте дверь: мы не знаем, что осталось внутри."
                  + " Отступим с документами, пока не найдём безопасный способ доступа."),
          StoryDialogue.eva(
              "Теперь решите: поделиться найденными документами с выжившими или сохранить"
                  + " информацию для дальнейшего расследования. Оба пути оставляют возможность"
                  + " продолжить поиски."));
  private static final StoryDialogue[] DIALOGUES = {
    INTRO, ARCHIVE_REVIEW, LAB_REVIEW, ENTRANCE_REVIEW
  };

  static StoryDialogue dialogue(String id) {
    for (StoryDialogue d : DIALOGUES) if (d.event.id.equals(id)) return d;
    return null;
  }

  static boolean target(String id) {
    return ARCHIVE.equals(id) || LAB.equals(id) || ENTRANCE.equals(id);
  }

  static List<MapLocation> locations() {
    return Arrays.asList(
        point(
            ARCHIVE,
            "Городской архив",
            "ГОРОДСКОЙ\nАРХИВ",
            "Архивные документы / первые дни катастрофы",
            .37f,
            .54f,
            MapLocation.Distance.NEAR,
            MapLocation.Risk.LOW_MEDIUM),
        point(
            LAB,
            "Исследовательский корпус",
            "ИССЛЕДОВАТЕЛЬСКИЙ\nКОРПУС",
            "Исследовательский журнал / технические записи / фрагмент ключа",
            .86f,
            .48f,
            MapLocation.Distance.FAR,
            MapLocation.Risk.HIGH),
        point(
            ENTRANCE,
            "Закрытый подземный вход",
            "ПОДЗЕМНЫЙ\nВХОД",
            "Внешний осмотр гермодвери; проход внутрь закрыт",
            .49f,
            .13f,
            MapLocation.Distance.FAR,
            MapLocation.Risk.VERY_HIGH));
  }

  private static MapLocation point(
      String id,
      String name,
      String marker,
      String loot,
      float x,
      float y,
      MapLocation.Distance distance,
      MapLocation.Risk risk) {
    return new MapLocation(
        id,
        name,
        marker,
        loot,
        MapLocation.Kind.STORY,
        distance,
        risk,
        x,
        y,
        MapLocation.State.LOCKED,
        new LootTable());
  }

  static String itemLabel(String id) {
    switch (id) {
      case DOCUMENTS:
        return "Архивные документы";
      case CATASTROPHE:
        return "Запись о первых днях катастрофы";
      case MENTION:
        return "Упоминание проекта «Рассвет»";
      case JOURNAL:
        return "Повреждённый исследовательский журнал";
      case NOTES:
        return "Технические записи";
      case KEY:
        return "Фрагмент ключа доступа";
      default:
        return "";
    }
  }

  private StoryInvestigationConfig() {}
}
