package com.lastdom.game;

import static com.lastdom.game.ExpeditionEventConfig.*;
import static com.lastdom.game.ExpeditionLoot.Resource.*;

/** Resolves a player's choice without mutating residents, loot, phase or resources. */
final class ExpeditionEventResolver {
  static ExpeditionEventOutcome resolve(GameController game, Expedition expedition, int action) {
    ExpeditionController controller = game.expeditionController;
    boolean guard = has(controller, expedition, "Охрана"),
        doctor = has(controller, expedition, "Врач"),
        mechanic = has(controller, expedition, "Механик"),
        engineer = has(controller, expedition, "Инженер"),
        gatherer =
            has(controller, expedition, "Сборщик") || has(controller, expedition, "Собиратель");
    ExpeditionEvent.Type type = expedition.explorationEvent.type;
    ExpeditionEventOutcome out = new ExpeditionEventOutcome();
    int fatigue = 0, injury = 0;
    double success =
        successChance(
            type,
            action,
            guard,
            mechanic,
            engineer,
            gatherer,
            controller.conditionFactor(expedition));
    switch (type) {
      case MARAUDERS:
        if (action == 0) {
          lose(out, expedition, SURRENDER_PERCENT);
          out.message = "Отряд отдал часть припасов без столкновения";
        } else if (action == 1) {
          boolean ok = game.rnd.nextDouble() < success;
          if (!ok) lose(out, expedition, FAILED_TALK_PERCENT);
          fatigue = SEARCH_FATIGUE;
          out.message =
              ok
                  ? "Удалось договориться, припасы сохранены"
                  : "Договориться не удалось, пришлось отдать больше припасов";
        } else {
          boolean ok = game.rnd.nextDouble() < success;
          fatigue = ESCAPE_FATIGUE;
          if (!ok) {
            injury = NORMAL_INJURY;
            lose(out, expedition, ESCAPE_LOSS_PERCENT);
          }
          out.message =
              ok ? "Отряд успешно скрылся" : "При отходе отряд потерял припасы, участник ранен";
        }
        break;
      case INFECTED:
        if (action == 2) {
          out.retreat = true;
          fatigue = SMALL_FATIGUE;
          out.message = "Отряд отступает с уже найденным грузом";
        } else {
          boolean ok = game.rnd.nextDouble() < success;
          fatigue = SEARCH_FATIGUE;
          if (!ok) {
            injury = action == 0 ? NORMAL_INJURY : HEAVY_INJURY;
            out.delayMinutes = SEARCH_DELAY;
            if (action == 1) lose(out, expedition, ESCAPE_LOSS_PERCENT);
          }
          out.message =
              ok ? "Заражённых удалось обойти" : "Столкновение задержало отряд, участник ранен";
        }
        break;
      case COLLAPSE:
        if (action == 2) {
          out.retreat = true;
          out.message = "Исследование прекращено, отряд уходит с найденным грузом";
        } else if (action == 0) {
          out.delayMinutes = engineer ? SHORT_DELAY : LONG_DELAY;
          fatigue = SMALL_FATIGUE;
          if (!engineer
              && game.rnd.nextDouble()
                  < hazardChance(type, action, guard, gatherer, expedition.cityRiskReduction))
            injury = MINOR_INJURY;
          out.message = engineer ? "Инженер нашёл короткий безопасный обход" : "Отряд обошёл завал";
        } else {
          boolean ok = game.rnd.nextDouble() < success;
          out.delayMinutes = ok ? SEARCH_DELAY : LONG_DELAY;
          fatigue = HEAVY_FATIGUE;
          if (ok) out.added.add(MATERIALS, CLEARED_DETAILS);
          else injury = NORMAL_INJURY;
          out.message =
              ok
                  ? "Завал расчищен, найдены пригодные детали"
                  : "Расчистка не удалась, участник ранен";
        }
        break;
      case CACHE:
        if (action == 0) {
          out.added.add(FOOD, CACHE_FOOD + (gatherer ? CACHE_GATHERER_BONUS : 0));
          out.added.add(WATER, CACHE_WATER + (gatherer ? CACHE_GATHERER_BONUS : 0));
          out.delayMinutes = SEARCH_DELAY;
          fatigue = SMALL_FATIGUE;
          out.message =
              gatherer
                  ? "Сборщик обнаружил дополнительные запасы тайника"
                  : "Тайник проверен, найдены припасы";
        } else if (action == 1) {
          out.added.add(FOOD, VISIBLE_SUPPLIES);
          out.added.add(WATER, VISIBLE_SUPPLIES);
          out.message = "Видимые припасы взяты без риска";
        } else {
          out.riskReduction = ROUTE_RISK_REDUCTION;
          out.added.add(FOOD, CHECKED_SUPPLIES);
          out.added.add(WATER, CHECKED_SUPPLIES);
          if (game.rnd.nextDouble()
              < hazardChance(
                  type, action, guard, gatherer, expedition.cityRiskReduction + out.riskReduction))
            injury = MINOR_INJURY;
          out.message = "Проверен безопасный подход; риск при осмотре снижен";
        }
        break;
      case WOUNDED_SURVIVOR:
        if (action == 0) {
          out.delayMinutes = doctor ? SHORT_DELAY : LONG_DELAY;
          fatigue = doctor ? SMALL_FATIGUE : ESCAPE_FATIGUE;
          out.added.add(WATER, CACHE_WATER);
          out.added.add(FOOD, CACHE_FOOD);
          out.message =
              doctor
                  ? "Врач помог выжившему; за помощь отряд получил припасы"
                  : "Выжившему помогли; за помощь отряд получил припасы";
        } else if (action == 1) {
          out.lost.set(FOOD, Math.min(SHARED_FOOD, expedition.found.get(FOOD)));
          out.added.add(MATERIALS, SHARED_DETAILS);
          out.delayMinutes = SHORT_DELAY;
          out.message = "Отряд поделился найденной едой; выживший передал полезные детали";
        } else out.message = "Отряд прошёл мимо без дополнительных затрат";
        break;
      case LOCKED_ROOM:
        if (action == 0) {
          boolean ok = game.rnd.nextDouble() < success;
          out.delayMinutes = ok ? SEARCH_DELAY : LONG_DELAY;
          fatigue = ok ? SEARCH_FATIGUE : HEAVY_FATIGUE;
          if (ok) {
            out.added.add(MATERIALS, DOOR_MATERIALS);
            out.added.add(MEDICINE, DOOR_MEDICINE);
            out.added.add(EQUIPMENT, DOOR_EQUIPMENT);
          } else injury = MINOR_INJURY;
          out.message = ok ? "Дверь вскрыта, припасы найдены" : "Замок не поддался, участник ранен";
        } else if (action == 1) {
          out.delayMinutes = engineer ? SHORT_DELAY : LONG_DELAY;
          fatigue = engineer ? SMALL_FATIGUE : ESCAPE_FATIGUE;
          out.added.add(MATERIALS, engineer ? TECHNICAL_DETAILS : FALLBACK_DETAILS);
          out.message =
              engineer
                  ? "Инженер разобрал механизм и открыл технический доступ"
                  : "Отряд долго разбирал механизм и нашёл несколько деталей";
        } else out.message = "Отряд оставил запертое помещение";
        break;
      case DANGEROUS_AREA:
        if (action == 2) {
          out.retreat = true;
          fatigue = SMALL_FATIGUE;
          out.message = "Отряд отступает с уже найденным грузом";
        } else {
          if (action == 1) out.riskReduction = ROUTE_RISK_REDUCTION;
          out.delayMinutes = action == 1 ? LONG_DELAY : SEARCH_DELAY;
          fatigue = action == 1 ? SEARCH_FATIGUE : HEAVY_FATIGUE;
          MapLocation target = controller.location(expedition.locationId);
          for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
            if (target.lootTable.max(resource) > 0)
              out.added.add(resource, action == 1 ? CAUTIOUS_EXTRA : DANGEROUS_EXTRA);
          if (game.rnd.nextDouble()
              < hazardChance(
                  type,
                  action,
                  guard,
                  gatherer,
                  expedition.cityRiskReduction + out.riskReduction)) {
            injury = NORMAL_INJURY;
            lose(out, expedition, ESCAPE_LOSS_PERCENT);
          }
          out.message =
              action == 1
                  ? "Выбран осторожный маршрут; риск столкновения снижен"
                  : "Отряд продолжил исследование опасной территории";
        }
        break;
      case WAREHOUSE:
        if (action == 0) {
          out.added.add(MATERIALS, WAREHOUSE_MATERIALS + (mechanic ? WAREHOUSE_MECHANIC_BONUS : 0));
          out.delayMinutes = CLEAR_DELAY;
          fatigue = SEARCH_FATIGUE;
          if (game.rnd.nextDouble()
              < hazardChance(type, action, guard, gatherer, expedition.cityRiskReduction))
            injury = MINOR_INJURY;
          out.message =
              mechanic
                  ? "Механик нашёл дополнительные материалы склада"
                  : "Склад обследован, найдены материалы";
        } else if (action == 1) {
          out.added.add(MATERIALS, VISIBLE_MATERIALS);
          fatigue = SMALL_FATIGUE;
          out.message = "Доступные материалы взяты безопасно";
        } else {
          out.added.add(
              MATERIALS,
              SALVAGE_BASE
                  + (engineer ? ENGINEER_SALVAGE_BONUS : 0)
                  + (mechanic ? MECHANIC_SALVAGE_BONUS : 0));
          out.delayMinutes = SEARCH_DELAY;
          fatigue = SMALL_FATIGUE;
          out.message = "Оборудование разобрано на полезные материалы";
        }
        break;
      default:
        throw new IllegalArgumentException("Нет интерактивного события");
    }
    if (injury > 0) {
      Resident victim =
          controller.resident(
              expedition.participantIds.get(game.rnd.nextInt(expedition.participantIds.size())));
      if (victim != null) {
        int actual =
            Math.min(
                (int) Math.ceil(injury * (doctor ? DOCTOR_DAMAGE_FACTOR : 1)),
                Math.max(0, victim.health - 1));
        out.healthLoss.put(victim.id, actual);
      }
    }
    for (String id : expedition.participantIds) {
      Resident resident = controller.resident(id);
      if (resident != null)
        out.fatigueAdded.put(
            id, Math.max(0, Math.min(100, resident.fatigue + fatigue) - resident.fatigue));
    }
    return out;
  }

  static boolean has(ExpeditionController controller, Expedition expedition, String role) {
    for (String id : expedition.participantIds) {
      Resident resident = controller.resident(id);
      if (resident != null && resident.role.equals(role)) return true;
    }
    return false;
  }

  private static void lose(ExpeditionEventOutcome out, Expedition expedition, int percent) {
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
      out.lost.set(resource, (int) Math.ceil(expedition.found.get(resource) * percent / 100.0));
  }
}
