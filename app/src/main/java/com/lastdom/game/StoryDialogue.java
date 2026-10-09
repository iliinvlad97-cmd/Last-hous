package com.lastdom.game;

/** Immutable dialogue definitions; progress and resolved speaker snapshots live in StoryState. */
final class StoryDialogue {
  static final int INITIAL_TRUST = 50, EVIDENCE_TRUST = 5, GUARDED_TRUST = -2, BRANCH_TRUST = 5;

  static final class Line {
    final String speaker, role, text, residentName;
    final String[] responses;

    Line(String speaker, String role, String text, String residentName, String... responses) {
      this.speaker = speaker;
      this.role = role;
      this.text = text;
      this.residentName = residentName;
      this.responses = responses;
    }
  }

  final StoryEvent event;
  final Line[] lines;

  StoryDialogue(String id, String title, Line... lines) {
    event = new StoryEvent(id, title, lines[0].text);
    this.lines = lines;
  }

  static Line eva(String text, String... responses) {
    return new Line("Ева", "Радистка станции 17 · радиосвязь", text, "", responses);
  }

  static Line resident(String name, String role, String text) {
    return new Line(name, role + " · разговор в убежище", text, name);
  }

  static final StoryDialogue INTRO =
      new StoryDialogue(
          "voices.intro",
          "Голоса в эфире · Ева",
          eva(
              "Вы вернулись с радиостанции. Я Ева, радистка станции 17. Носитель старый, но его код"
                  + " подлинный. Кто ещё видел данные?"),
          eva(
              "Прежде чем обсуждать «Рассвет», спросите своих людей, чего они опасаются."
                  + " Автоматическим передачам я больше не доверяю."));
  static final StoryDialogue RESIDENTS =
      new StoryDialogue(
          "voices.residents",
          "Обсуждение сигнала",
          resident(
              "Иван",
              "Инженер",
              "Нужно изучить технологию проекта. Данные нельзя уничтожать, пока мы не проверили,"
                  + " что они означают."),
          resident(
              "Мария",
              "Врач",
              "Меня беспокоят последствия для людей. Сначала найдём доказательства безопасности"
                  + " проекта."),
          resident(
              "Сергей",
              "Охрана",
              "Это может быть ловушка. Я бы ограничил распространение координат."));
  static final StoryDialogue EVA =
      new StoryDialogue(
          "voices.eva",
          "Ева · проект «Рассвет»",
          eva(
              "В старой сети сохранились следы проекта. Я знаю только часть истории и не стану"
                  + " выдавать догадки за факты.",
              "Сначала проверим доказательства",
              "Почему вы скрываете сведения?"),
          eva(
              "Хорошо. Проверяйте подпись носителя, а не обещания из эфира. Следующая передача"
                  + " зависит от вашего прежнего решения."));
  static final StoryDialogue PUBLIC =
      new StoryDialogue(
          "voices.public",
          "Сигнал услышан",
          new Line(
              "Убежище «Север»",
              "Ответ на открытой частоте",
              "Мы услышали вашу передачу. Координаты неполные, но сигнал уже повторяют другие"
                  + " приёмники.",
              ""),
          eva(
              "Вы передали сигнал другим выжившим. Теперь возможна утечка информации. Осторожнее: я"
                  + " не могу проверить всех, кто слушает."));
  static final StoryDialogue SECRET =
      new StoryDialogue(
          "voices.secret",
          "Тихая частота",
          eva(
              "Вы сохранили координаты в тайне. Передаю дополнительный зашифрованный фрагмент: ключ"
                  + " связан с архивом станции 17."),
          resident(
              "Сергей",
              "Охрана",
              "Одобряю осторожность. Сначала проверим источник, потом решим, кому доверять"
                  + " координаты."));
  static final StoryDialogue HOOK =
      new StoryDialogue(
          "voices.hook",
          "Зацепка · архив станции 17",
          eva(
              "В архиве станции 17 должна быть запись о первых испытаниях. Пока не выходите по"
                  + " неполным координатам. Когда проверю частоту, свяжусь снова."));

  private static final StoryDialogue[] ALL = {INTRO, RESIDENTS, EVA, PUBLIC, SECRET, HOOK};

  static StoryDialogue find(String id) {
    for (StoryDialogue d : ALL) if (d.event.id.equals(id)) return d;
    return null;
  }
}
