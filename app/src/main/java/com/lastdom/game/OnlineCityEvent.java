package com.lastdom.game;

/** Configured event instances advance only from the persisted radio game-minute clock. */
final class OnlineCityEvent {
  enum State {
    AVAILABLE,
    ACTIVE,
    COMPLETED,
    EXPIRED
  }

  enum Type {
    INFECTED(
        "Нападение заражённых",
        "Заражённые приближаются к радиовышке.",
        "Оборона радиовышки",
        "pve_infected",
        "Высокая"),
    FIRE(
        "Пожар в промышленном районе",
        "Пожар угрожает техническим запасам.",
        "Ликвидация пожара",
        "pve_industry",
        "Средняя"),
    DISTRESS(
        "Сигнал бедствия",
        "Виртуальный пост просит доставить помощь.",
        "Доставка медикаментов",
        "pve_industry",
        "Средняя"),
    WAREHOUSE(
        "Обнаруженный склад",
        "Найден склад с демонстрационными припасами.",
        "Зачистка промышленной зоны",
        "pve_industry",
        "Средняя"),
    STORM(
        "Радиоактивная буря",
        "Караван нуждается в сопровождении через опасный район.",
        "Защита каравана",
        "pve_infected",
        "Высокая");
    final String name, description, operation, zoneId, danger;

    Type(String name, String description, String operation, String zone, String danger) {
      this.name = name;
      this.description = description;
      this.operation = operation;
      zoneId = zone;
      this.danger = danger;
    }
  }

  static final int INTERVAL = 120, LIFETIME = 240, FIRST_SPAWN = 15, MAX_EVENTS = 500;
  final String id, operationId;
  final Type type;
  final long appearedMinute, expiresMinute;
  final State state;

  OnlineCityEvent(
      String id, Type type, long appeared, long expires, State state, String operationId) {
    if (id == null
        || type == null
        || appeared < 0
        || expires != appeared + LIFETIME
        || state == null
        || operationId == null
        || ((state == State.ACTIVE || state == State.COMPLETED) != !operationId.isEmpty()))
      throw new IllegalArgumentException("Invalid city event");
    this.id = id;
    this.type = type;
    appearedMinute = appeared;
    expiresMinute = expires;
    this.state = state;
    this.operationId = operationId;
  }

  OnlineCityEvent phase(State phase, String job) {
    return new OnlineCityEvent(id, type, appearedMinute, expiresMinute, phase, job);
  }

  float x() {
    return type.zoneId.equals("pve_industry") ? .27f : .75f;
  }

  float y() {
    return type.zoneId.equals("pve_industry") ? .52f : .585f;
  }
}
