package com.lastdom.game;

import java.util.ArrayList;
import java.util.List;

/** City event frequency, eligible locations, choices and initial balance. */
final class ExpeditionEventConfig {
  static final int SHORT_DELAY = 5, SEARCH_DELAY = 10, CLEAR_DELAY = 15, LONG_DELAY = 20;
  static final int SMALL_FATIGUE = 3, SEARCH_FATIGUE = 5, ESCAPE_FATIGUE = 8, HEAVY_FATIGUE = 10;
  static final int MINOR_INJURY = 12, NORMAL_INJURY = 16, HEAVY_INJURY = 20;
  static final double GUARD_BONUS = .25,
      TECH_BONUS = .35,
      GATHERER_BONUS = .20,
      DOCTOR_DAMAGE_FACTOR = .5;
  static final int SURRENDER_PERCENT = 25, FAILED_TALK_PERCENT = 40, ESCAPE_LOSS_PERCENT = 20;
  static final int ROUTE_RISK_REDUCTION = 50;
  static final int CACHE_FOOD = 4,
      CACHE_WATER = 3,
      WAREHOUSE_MATERIALS = 8,
      DOOR_MATERIALS = 5,
      DOOR_MEDICINE = 3,
      DOOR_EQUIPMENT = 2;
  static final int CACHE_GATHERER_BONUS = 2, VISIBLE_SUPPLIES = 2, CHECKED_SUPPLIES = 1;
  static final int SHARED_FOOD = 2, SHARED_DETAILS = 2, CLEARED_DETAILS = 2;
  static final int TECHNICAL_DETAILS = 4, FALLBACK_DETAILS = 2;
  static final int DANGEROUS_EXTRA = 4, CAUTIOUS_EXTRA = 2;
  static final int WAREHOUSE_MECHANIC_BONUS = 5, VISIBLE_MATERIALS = 4;
  static final int SALVAGE_BASE = 5, ENGINEER_SALVAGE_BONUS = 5, MECHANIC_SALVAGE_BONUS = 3;

  static double probability(MapLocation location) {
    switch (location.risk) {
      case LOW:
        return .15;
      case LOW_MEDIUM:
        return .25;
      case MEDIUM:
        return .35;
      default:
        return .50;
    }
  }

  static List<ExpeditionEvent.Type> eligible(MapLocation location) {
    List<ExpeditionEvent.Type> types = new ArrayList<>();
    if (location.kind != MapLocation.Kind.WATER) types.add(ExpeditionEvent.Type.MARAUDERS);
    if (location.kind == MapLocation.Kind.STORE
        || location.kind == MapLocation.Kind.PHARMACY
        || location.kind == MapLocation.Kind.HOSPITAL) types.add(ExpeditionEvent.Type.INFECTED);
    types.add(ExpeditionEvent.Type.COLLAPSE);
    types.add(ExpeditionEvent.Type.CACHE);
    types.add(ExpeditionEvent.Type.WOUNDED_SURVIVOR);
    if (location.kind != MapLocation.Kind.WATER) types.add(ExpeditionEvent.Type.LOCKED_ROOM);
    if (location.risk == MapLocation.Risk.MEDIUM || location.risk == MapLocation.Risk.HIGH)
      types.add(ExpeditionEvent.Type.DANGEROUS_AREA);
    if (location.kind == MapLocation.Kind.STORE
        || location.kind == MapLocation.Kind.GARAGE
        || location.kind == MapLocation.Kind.POLICE) types.add(ExpeditionEvent.Type.WAREHOUSE);
    return types;
  }

  static String title(ExpeditionEvent.Type type) {
    switch (type) {
      case MARAUDERS:
        return "ВСТРЕЧА С МАРОДЁРАМИ";
      case INFECTED:
        return "ЗАРАЖЁННЫЕ";
      case COLLAPSE:
        return "ОБРУШЕНИЕ";
      case CACHE:
        return "ТАЙНИК";
      case WOUNDED_SURVIVOR:
        return "РАНЕНЫЙ ВЫЖИВШИЙ";
      case LOCKED_ROOM:
        return "ЗАПЕРТОЕ ПОМЕЩЕНИЕ";
      case DANGEROUS_AREA:
        return "ОПАСНАЯ ТЕРРИТОРИЯ";
      case WAREHOUSE:
        return "ЗАБРОШЕННЫЙ СКЛАД";
      default:
        return "СОБЫТИЕ ЭКСПЕДИЦИИ";
    }
  }

  static String description(ExpeditionEvent.Type type) {
    switch (type) {
      case MARAUDERS:
        return "Отряд остановили вооружённые люди. Они требуют часть уже найденных припасов. Охрана"
            + " поможет договориться или уйти.";
      case INFECTED:
        return "Внутри здания слышно движение заражённых. Сборщик знает безопасные проходы, охрана"
            + " поможет прикрыть отход.";
      case COLLAPSE:
        return "Проход завален обломками. Инженер найдёт обход; механик поможет расчистить завал.";
      case CACHE:
        return "В нише обнаружен тайник. Сборщик заметит больше припасов и проверит безопасный"
            + " подход.";
      case WOUNDED_SURVIVOR:
        return "В укрытии лежит раненый человек. Врач сможет помочь быстрее и с меньшей усталостью."
            + " Нового жителя отряд сейчас не принимает.";
      case LOCKED_ROOM:
        return "За запертой дверью могут быть припасы. Механик поможет вскрыть замок, инженер —"
            + " разобраться с механизмом.";
      case DANGEROUS_AREA:
        return "Следы угрозы ведут в глубину района. Можно рискнуть, выбрать осторожный маршрут или"
            + " уйти с уже найденным грузом.";
      case WAREHOUSE:
        return "Отряд обнаружил склад материалов. Конструкции повреждены; механик и инженер помогут"
            + " безопасно разобрать оборудование.";
      default:
        return "Исследование завершено.";
    }
  }

  static String[] actions(ExpeditionEvent.Type type) {
    switch (type) {
      case MARAUDERS:
        return new String[] {"Отдать припасы", "Попытаться договориться", "Попытаться скрыться"};
      case INFECTED:
        return new String[] {"Пробраться незаметно", "Продвигаться под прикрытием", "Отступить"};
      case COLLAPSE:
        return new String[] {"Найти обход", "Расчистить проход", "Прекратить исследование"};
      case CACHE:
        return new String[] {
          "Проверить тайник", "Взять видимые припасы", "Проверить безопасный подход"
        };
      case WOUNDED_SURVIVOR:
        return new String[] {"Помочь выжившему", "Поделиться едой", "Пройти мимо"};
      case LOCKED_ROOM:
        return new String[] {"Вскрыть дверь", "Разобраться с механизмом", "Пройти мимо"};
      case DANGEROUS_AREA:
        return new String[] {"Продолжить исследование", "Выбрать безопасный маршрут", "Отступить"};
      case WAREHOUSE:
        return new String[] {
          "Обыскать склад", "Взять доступные материалы", "Разобрать оборудование"
        };
      default:
        return new String[0];
    }
  }

  static String[] warnings(ExpeditionEvent.Type type) {
    switch (type) {
      case MARAUDERS:
        return new String[] {
          "Потеря 25% найденного, без столкновения",
          "При неудаче потеря 40% найденного",
          "При неудаче травма и потеря 20% найденного"
        };
      case INFECTED:
        return new String[] {
          "Возможны травма и задержка",
          "Охрана снижает риск травмы и потери груза",
          "Вернуться с уже найденным грузом"
        };
      case COLLAPSE:
        return new String[] {
          "Задержка; без инженера возможна травма",
          "Материалы при успехе, травма при неудаче",
          "Вернуться с уже найденным грузом"
        };
      case CACHE:
        return new String[] {
          "Больше еды и воды, задержка 10 минут",
          "Небольшая добыча без риска",
          "Меньше добычи, возможна лёгкая травма"
        };
      case WOUNDED_SURVIVOR:
        return new String[] {
          "Задержка и усталость; благодарность припасами",
          "Отдать до 2 еды, получить материалы",
          "Без затрат и дополнительных припасов"
        };
      case LOCKED_ROOM:
        return new String[] {
          "Припасы при успехе, травма при неудаче",
          "Задержка; инженер поможет быстрее",
          "Без риска и дополнительной добычи"
        };
      case DANGEROUS_AREA:
        return new String[] {
          "Дополнительные припасы; риск травмы и потерь",
          "Дольше, но риск столкновения снижен",
          "Вернуться с уже найденным грузом"
        };
      case WAREHOUSE:
        return new String[] {
          "Больше материалов; возможны травма и задержка",
          "Меньше материалов, без травмы",
          "Инженер и механик найдут больше деталей"
        };
      default:
        return new String[0];
    }
  }

  static double successChance(
      ExpeditionEvent.Type type,
      int action,
      boolean guard,
      boolean mechanic,
      boolean engineer,
      boolean gatherer,
      double condition) {
    double chance = .6;
    switch (type) {
      case MARAUDERS:
        chance =
            (action == 1 ? .40 : .45)
                + (guard ? GUARD_BONUS : 0)
                + (action == 2 && gatherer ? GATHERER_BONUS : 0)
                + condition * .10;
        break;
      case INFECTED:
        chance =
            action == 0 ? .45 + (gatherer ? .30 : 0) + (guard ? .15 : 0) : .40 + (guard ? .35 : 0);
        break;
      case COLLAPSE:
        chance = .30 + (mechanic ? TECH_BONUS : 0) + (engineer ? .20 : 0);
        break;
      case LOCKED_ROOM:
        chance = .25 + (mechanic ? .50 : 0) + (engineer ? .15 : 0);
        break;
      default:
        break;
    }
    return Math.min(.95, Math.max(.05, chance));
  }

  static double hazardChance(
      ExpeditionEvent.Type type, int action, boolean guard, boolean gatherer, int reduction) {
    double base =
        type == ExpeditionEvent.Type.DANGEROUS_AREA
            ? .40
            : type == ExpeditionEvent.Type.COLLAPSE
                ? .30
                : type == ExpeditionEvent.Type.WAREHOUSE ? .20 : .15;
    return base
        * (guard ? .70 : 1)
        * (gatherer ? .75 : 1)
        * (1 - Math.min(80, Math.max(0, reduction)) / 100.0);
  }
}
