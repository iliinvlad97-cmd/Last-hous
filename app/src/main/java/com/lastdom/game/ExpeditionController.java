package com.lastdom.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/** All validation happens before any mutation; UI never assigns jobs or creates squads. */
final class ExpeditionController {
  private final GameController game;

  ExpeditionController(GameController game) {
    this.game = game;
  }

  Resident resident(String id) {
    for (Resident resident : game.people) if (resident.id.equals(id)) return resident;
    return null;
  }

  MapLocation location(String id) {
    for (MapLocation location : game.cityLocations) if (location.id.equals(id)) return location;
    return null;
  }

  Expedition active() {
    for (Expedition expedition : game.expeditions) if (expedition.active()) return expedition;
    return null;
  }

  boolean contains(Resident resident) {
    for (Expedition expedition : game.expeditions)
      if (expedition.active() && expedition.participantIds.contains(resident.id)) return true;
    return false;
  }

  String unavailableReason(Resident resident) {
    if (!resident.alive) return "Погиб";
    if (contains(resident) || resident.job.equals("Экспедиция")) return "В экспедиции";
    if (resident.health <= 0) return "Нет здоровья";
    return "";
  }

  String start(String locationId, Collection<String> ids) {
    MapLocation target = location(locationId);
    if (target == null || target.isLocked()) return "Район не исследован";
    if (target.depleted()) return "Локация истощена";
    long count = game.expeditions.stream().filter(Expedition::active).count();
    if (count >= ExpeditionConfig.MAX_ACTIVE || game.expeditionPerson >= 0)
      return "Сначала завершите текущую экспедицию";
    if (ids == null || ids.isEmpty()) return "Выберите от 1 до 3 жителей";
    if (ids.size() > ExpeditionConfig.MAX_PARTICIPANTS || new HashSet<>(ids).size() != ids.size())
      return "Выберите от 1 до 3 разных жителей";
    List<Resident> squad = new ArrayList<>();
    for (String id : ids) {
      Resident resident = resident(id);
      if (resident == null) return "Житель больше не существует";
      String reason = unavailableReason(resident);
      if (!reason.isEmpty()) return resident.name + ": " + reason;
      squad.add(resident);
    }
    if (game.gameOver) return "Игра завершена";
    Expedition expedition =
        new Expedition(
            target.id,
            new ArrayList<>(ids),
            (game.day - 1L) * 1440 + game.gameMinute,
            ExpeditionConfig.oneWayMinutes(target),
            0,
            Expedition.State.TRAVELING_TO_TARGET);
    game.expeditions.add(expedition);
    for (Resident resident : squad) {
      resident.job = "Экспедиция";
      resident.status = Resident.Status.ON_EXPEDITION;
    }
    game.addLog("Отряд отправлен: " + target.name + " (" + squad.size() + " чел.).");
    game.save();
    game.invalidate();
    return "";
  }

  long now() {
    return (game.day - 1L) * 1440 + game.gameMinute;
  }

  Expedition report() {
    Expedition active = active();
    if (active != null) return active;
    for (int i = game.expeditions.size() - 1; i >= 0; i--) {
      Expedition expedition = game.expeditions.get(i);
      if (expedition.state() == Expedition.State.COMPLETED && !expedition.reportAcknowledged)
        return expedition;
    }
    return null;
  }

  String memberLocation(Resident resident) {
    for (Expedition expedition : game.expeditions)
      if (expedition.active() && expedition.participantIds.contains(resident.id))
        return location(expedition.locationId).name;
    return "";
  }

  void beginExploration(Expedition expedition) {
    expedition.beginPhase(
        Expedition.State.EXPLORING,
        ExpeditionConfig.explorationMinutes(location(expedition.locationId)),
        now());
    game.addLog("Отряд прибыл: " + location(expedition.locationId).name + ". Начато исследование.");
  }

  boolean advanceMinute() {
    boolean changed = false;
    for (Expedition expedition : game.expeditions) {
      Expedition.State state = expedition.state();
      if (state == Expedition.State.AT_LOCATION) {
        beginExploration(expedition);
        changed = true;
        continue;
      }
      if (state != Expedition.State.TRAVELING_TO_TARGET
          && state != Expedition.State.EXPLORING
          && state != Expedition.State.RETURNING) continue;
      changed = true;
      if (!expedition.advanceMinute()) continue;
      if (state == Expedition.State.TRAVELING_TO_TARGET) beginExploration(expedition);
      else if (state == Expedition.State.EXPLORING) finishExploration(expedition);
      else complete(expedition);
    }
    return changed;
  }

  double professionBonus(Expedition expedition, ExpeditionLoot.Resource resource) {
    double bonus = 0;
    for (String id : expedition.participantIds) {
      Resident resident = resident(id);
      if (resident == null) continue;
      String role = resident.role;
      if ((role.equals("Сборщик") || role.equals("Собиратель"))
          && (resource == ExpeditionLoot.Resource.FOOD
              || resource == ExpeditionLoot.Resource.WATER)) bonus += .2;
      if (role.equals("Механик") && resource == ExpeditionLoot.Resource.MATERIALS) bonus += .2;
      if (role.equals("Инженер") && resource == ExpeditionLoot.Resource.MATERIALS) bonus += .1;
      if (role.equals("Врач") && resource == ExpeditionLoot.Resource.MEDICINE) bonus += .2;
    }
    return bonus;
  }

  double conditionFactor(Expedition expedition) {
    double total = 0;
    for (String id : expedition.participantIds) {
      Resident resident = resident(id);
      if (resident != null)
        total +=
            Math.max(0, Math.min(100, resident.health))
                / 100.0
                * (1 - Math.max(0, Math.min(100, resident.fatigue)) * .005);
    }
    return total / expedition.participantIds.size();
  }

  boolean guard(Expedition expedition) {
    for (String id : expedition.participantIds) {
      Resident resident = resident(id);
      if (resident != null && resident.role.equals("Охрана")) return true;
    }
    return false;
  }

  void finishExploration(Expedition expedition) {
    if (expedition.resultGenerated
        || expedition.state() != Expedition.State.EXPLORING
        || expedition.remainingMinutes() != 0) return;
    MapLocation target = location(expedition.locationId);
    double factor =
        (.6 + .2 * expedition.participantIds.size())
            * conditionFactor(expedition)
            * (1 - target.depletion() / 100.0);
    ExpeditionLoot found = new ExpeditionLoot();
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values()) {
      int min = target.lootTable.min(resource), max = target.lootTable.max(resource);
      int roll = max == 0 ? 0 : min + game.rnd.nextInt(max - min + 1);
      found.set(
          resource, (int) Math.round(roll * factor * (1 + professionBonus(expedition, resource))));
    }
    ExpeditionEvent event;
    if (game.rnd.nextDouble() < ExpeditionConfig.negativeProbability(target, guard(expedition))) {
      int negative = game.rnd.nextInt(3);
      if (negative == 1) {
        Resident victim =
            resident(
                expedition.participantIds.get(game.rnd.nextInt(expedition.participantIds.size())));
        int damage = 8 + game.rnd.nextInt(13);
        int actual = victim == null ? 0 : Math.min(damage, Math.max(0, victim.health - 1));
        if (victim != null) victim.health = Math.max(1, victim.health - actual);
        event =
            new ExpeditionEvent(
                ExpeditionEvent.Type.INJURY,
                (victim == null ? "Житель" : victim.name)
                    + " получил травму (-"
                    + actual
                    + " здоровья)",
                victim == null ? "" : victim.id,
                actual);
      } else {
        for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
          found.set(resource, (int) Math.floor(found.get(resource) * .75));
        event =
            new ExpeditionEvent(
                negative == 0 ? ExpeditionEvent.Type.DAMAGED : ExpeditionEvent.Type.THREAT,
                negative == 0
                    ? "Часть припасов повреждена"
                    : "Отряд столкнулся с угрозой и потерял часть добычи",
                "",
                0);
      }
    } else if (game.rnd.nextInt(100) < 15) {
      for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
        if (target.lootTable.max(resource) > 0)
          found.add(resource, Math.max(1, found.get(resource) / 4));
      event =
          new ExpeditionEvent(ExpeditionEvent.Type.CACHE, "Найден дополнительный тайник", "", 0);
    } else
      event =
          new ExpeditionEvent(ExpeditionEvent.Type.QUIET, "Исследование прошло спокойно", "", 0);
    expedition.found = found;
    expedition.cargo = found.cargo(expedition.capacity());
    expedition.explorationEvent = event;
    expedition.resultGenerated = true;
    expedition.fatigueGain =
        10 + (expedition.durationMinutes * 2 + ExpeditionConfig.explorationMinutes(target)) / 30;
    target.setDepletion(target.depletion() + 10);
    target.setState(MapLocation.State.SEARCHED);
    expedition.restorePhase(Expedition.State.AWAITING_RETURN, 1, 1, now());
    game.addLog("Исследование завершено: " + target.name + ". " + event.message);
  }

  String returnHome(String id) {
    Expedition expedition = active();
    if (expedition == null
        || !expedition.id.equals(id)
        || expedition.state() != Expedition.State.AWAITING_RETURN
        || !expedition.resultGenerated) return "Отряд ещё не готов к возвращению";
    expedition.beginPhase(Expedition.State.RETURNING, expedition.durationMinutes, now());
    game.addLog("Отряд возвращается из " + location(expedition.locationId).name + ".");
    game.save();
    game.invalidate();
    return "";
  }

  void complete(Expedition expedition) {
    if (expedition.rewardCredited) return;
    if (expedition.state() != Expedition.State.RETURNING
        || expedition.remainingMinutes() != 0
        || !expedition.resultGenerated) return;
    game.food = safeAdd(game.food, expedition.cargo.get(ExpeditionLoot.Resource.FOOD));
    game.water = safeAdd(game.water, expedition.cargo.get(ExpeditionLoot.Resource.WATER));
    game.mats = safeAdd(game.mats, expedition.cargo.get(ExpeditionLoot.Resource.MATERIALS));
    game.expeditionWarehouse.add(
        ExpeditionLoot.Resource.MEDICINE, expedition.cargo.get(ExpeditionLoot.Resource.MEDICINE));
    game.expeditionWarehouse.add(
        ExpeditionLoot.Resource.EQUIPMENT, expedition.cargo.get(ExpeditionLoot.Resource.EQUIPMENT));
    for (String id : expedition.participantIds) {
      Resident resident = resident(id);
      if (resident == null) continue;
      resident.job = "Отдых";
      resident.status = Resident.Status.HOME;
      resident.fatigue = Math.min(100, resident.fatigue + expedition.fatigueGain);
    }
    expedition.rewardCredited = true;
    expedition.completedMinute = now();
    expedition.restorePhase(Expedition.State.COMPLETED, 1, 1, now());
    game.addLog(
        "Отряд вернулся из "
            + location(expedition.locationId).name
            + ". Доставлено: "
            + expedition.cargo.total()
            + " ед.");
  }

  void acknowledge(String id) {
    for (Expedition expedition : game.expeditions)
      if (expedition.id.equals(id) && expedition.state() == Expedition.State.COMPLETED) {
        expedition.reportAcknowledged = true;
        game.save();
        game.invalidate();
        return;
      }
  }

  private int safeAdd(int existing, int amount) {
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0, (long) existing) + amount);
  }

  void restoreMembership() {
    for (Resident resident : game.people)
      if (contains(resident)) {
        resident.job = "Экспедиция";
        resident.status = Resident.Status.ON_EXPEDITION;
      }
  }
}
