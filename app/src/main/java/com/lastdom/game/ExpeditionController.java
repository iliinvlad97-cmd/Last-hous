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
    Expedition normal = active(Expedition.Type.LOOT);
    return normal != null ? normal : active(Expedition.Type.RECON);
  }

  Expedition active(Expedition.Type type) {
    for (Expedition e : game.expeditions) if (e.active() && e.type == type) return e;
    return null;
  }

  long activeCount(Expedition.Type type) {
    return game.expeditions.stream().filter(e -> e.active() && e.type == type).count();
  }

  Expedition find(String id) {
    for (Expedition e : game.expeditions) if (e.id.equals(id)) return e;
    return null;
  }

  boolean contains(Resident resident) {
    for (Expedition expedition : game.expeditions)
      if (expedition.active() && expedition.participantIds.contains(resident.id)) return true;
    return false;
  }

  String unavailableReason(Resident resident) {
    if (resident == null) return "Житель больше не существует";
    if (game.isStoryBusy(resident)) return "Расшифровывает координаты";
    if (game.isBuilding(resident)) return "Занят строительством";
    if (game.isDefending(resident)) return "Назначен на оборону";
    if (!resident.alive) return "Погиб";
    if (contains(resident) || resident.job.equals("Экспедиция")) return "В экспедиции";
    return game.survivalController.expeditionReason(resident);
  }

  String start(String locationId, Collection<String> ids) {
    return launch(locationId, ids, Expedition.Type.LOOT);
  }

  String startRecon(String locationId, Collection<String> ids) {
    return launch(locationId, ids, Expedition.Type.RECON);
  }

  private String launch(String locationId, Collection<String> ids, Expedition.Type type) {
    MapLocation target = location(locationId);
    if (type == Expedition.Type.RECON) {
      String reason = game.explorationController.unavailableReason(locationId);
      if (!reason.isEmpty()) return reason;
      if (target == null || target.kind != MapLocation.Kind.DISTRICT) return "Район не существует";
    } else {
      if (target == null || target.kind == MapLocation.Kind.DISTRICT || target.isLocked())
        return "Район не исследован";
      if (target.kind == MapLocation.Kind.STORY) {
        String reason = game.storyController.expeditionReason();
        if (!reason.isEmpty()) return reason;
      }
      if (target.depleted()) return "Локация истощена";
      if (activeCount(Expedition.Type.LOOT) >= ExpeditionConfig.maxActive(Expedition.Type.LOOT)
          || game.expeditionPerson >= 0) return "Сначала завершите текущую экспедицию";
    }
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
    expedition.type = type;
    if (type == Expedition.Type.RECON)
      expedition.recon = game.explorationController.capture(expedition);
    game.expeditions.add(expedition);
    for (Resident resident : squad) {
      resident.job = "Экспедиция";
      resident.status = Resident.Status.ON_EXPEDITION;
    }
    game.addLog(
        (type == Expedition.Type.RECON ? "Разведка отправлена: " : "Отряд отправлен: ")
            + target.name
            + " ("
            + squad.size()
            + " чел.).");
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
    if (expedition.type == Expedition.Type.RECON) {
      game.explorationController.beginResearch(expedition);
      return;
    }
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
      boolean phaseFinished = expedition.advanceMinute();
      if (expedition.type == Expedition.Type.LOOT
          && state == Expedition.State.EXPLORING
          && !expedition.cityEventChecked
          && expedition.elapsedMinutes() >= Math.max(1, expedition.phaseDurationMinutes / 2)) {
        checkCityEvent(expedition);
        if (expedition.state() == Expedition.State.AWAITING_DECISION) continue;
      }
      if (!phaseFinished) continue;
      if (state == Expedition.State.TRAVELING_TO_TARGET) beginExploration(expedition);
      else if (state == Expedition.State.EXPLORING) {
        if (expedition.type == Expedition.Type.RECON)
          game.explorationController.finishResearch(expedition);
        else finishExploration(expedition);
      } else complete(expedition);
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
      if (resident != null) total += SurvivalConfig.efficiency(resident);
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

  void rollLoot(Expedition expedition) {
    if (expedition.lootRolled) return;
    MapLocation target = location(expedition.locationId);
    double factor =
        (.6 + .2 * expedition.participantIds.size())
            * conditionFactor(expedition)
            * (1 - target.depletion() / 100.0);
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values()) {
      int min = target.lootTable.min(resource), max = target.lootTable.max(resource);
      int roll = max == 0 ? 0 : min + game.rnd.nextInt(max - min + 1);
      int quantity = (int) Math.round(roll * factor * (1 + professionBonus(expedition, resource)));
      expedition.found.set(resource, quantity / 2);
      expedition.unsearchedLoot.set(resource, quantity - quantity / 2);
    }
    expedition.lootRolled = true;
    expedition.cargo = expedition.found.cargo(expedition.capacity());
  }

  void checkCityEvent(Expedition expedition) {
    if (expedition.cityEventChecked || expedition.state() != Expedition.State.EXPLORING) return;
    expedition.cityEventChecked = true;
    MapLocation target = location(expedition.locationId);
    if (game.rnd.nextDouble() >= ExpeditionEventConfig.probability(target)) return;
    java.util.List<ExpeditionEvent.Type> eligible = ExpeditionEventConfig.eligible(target);
    rollLoot(expedition);
    expedition.explorationEvent =
        ExpeditionEvent.city(eligible.get(game.rnd.nextInt(eligible.size())));
    expedition.restorePhase(
        Expedition.State.AWAITING_DECISION,
        expedition.phaseDurationMinutes,
        expedition.elapsedMinutes(),
        expedition.phaseStartMinute);
    game.addLog(
        target.name
            + ": "
            + ExpeditionEventConfig.title(expedition.explorationEvent.type)
            + ". Отряд ждёт решения.");
  }

  void finishExploration(Expedition expedition) {
    if (expedition.resultGenerated
        || expedition.state() != Expedition.State.EXPLORING
        || expedition.remainingMinutes() != 0) return;
    finalizeResearch(expedition, false);
  }

  private void finalizeResearch(Expedition expedition, boolean early) {
    if (expedition.resultGenerated) return;
    rollLoot(expedition);
    if (!early)
      for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
        expedition.found.add(resource, expedition.unsearchedLoot.get(resource));
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
      expedition.unsearchedLoot.set(resource, 0);
    expedition.cargo = expedition.found.cargo(expedition.capacity());
    expedition.resultGenerated = true;
    MapLocation target = location(expedition.locationId);
    expedition.fatigueGain = 0; // Fatigue is already accumulated by the survival minute clock.
    if (target.kind == MapLocation.Kind.STORY) game.storyController.research(expedition, early);
    else target.setDepletion(target.depletion() + 10);
    target.setState(MapLocation.State.SEARCHED);
    expedition.restorePhase(Expedition.State.AWAITING_RETURN, 1, 1, now());
    game.addLog(
        "Исследование "
            + (early ? "прекращено" : "завершено")
            + ": "
            + target.name
            + ". "
            + expedition.explorationEvent.resultMessage());
  }

  String chooseEvent(String eventId, int action) {
    Expedition expedition = active(Expedition.Type.LOOT);
    if (expedition == null || expedition.state() != Expedition.State.AWAITING_DECISION)
      return "Отряд не ждёт решения";
    ExpeditionEvent event = expedition.explorationEvent;
    if (!event.interactive() || !event.instanceId.equals(eventId) || event.effectsApplied)
      return "Решение уже принято или событие изменилось";
    String[] actions = ExpeditionEventConfig.actions(event.type);
    if (action < 0 || action >= actions.length) return "Недоступное действие";
    ExpeditionEventOutcome out = ExpeditionEventResolver.resolve(game, expedition, action);
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values()) {
      expedition.found.set(resource, expedition.found.get(resource) - out.lost.get(resource));
      expedition.found.add(resource, out.added.get(resource));
    }
    for (java.util.Map.Entry<String, Integer> change : out.healthLoss.entrySet()) {
      Resident resident = resident(change.getKey());
      if (resident != null) {
        int before = resident.health;
        resident.health = SurvivalConfig.clamp(resident.health - change.getValue());
        game.survivalController.injury(resident, before - resident.health);
      }
    }
    for (java.util.Map.Entry<String, Integer> change : out.fatigueAdded.entrySet()) {
      Resident resident = resident(change.getKey());
      if (resident != null) resident.fatigue = Math.min(100, resident.fatigue + change.getValue());
    }
    expedition.explorationDelay += out.delayMinutes;
    expedition.cityRiskReduction = Math.min(80, expedition.cityRiskReduction + out.riskReduction);
    expedition.restorePhase(
        Expedition.State.AWAITING_DECISION,
        expedition.phaseDurationMinutes + out.delayMinutes,
        expedition.elapsedMinutes(),
        expedition.phaseStartMinute);
    expedition.cargo = expedition.found.cargo(expedition.capacity());
    event.chosenAction = action;
    event.outcome = out;
    event.effectsApplied = true;
    game.addLog(
        location(expedition.locationId).name
            + ": "
            + actions[action]
            + ". "
            + out.message
            + ". "
            + outcomeSummary(out));
    game.save();
    game.invalidate();
    return "";
  }

  String outcomeSummary(ExpeditionEventOutcome out) {
    String summary = "";
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values()) {
      if (out.added.get(resource) > 0)
        summary += resource.label + " +" + out.added.get(resource) + "; ";
      if (out.lost.get(resource) > 0)
        summary += resource.label + " -" + out.lost.get(resource) + "; ";
    }
    for (java.util.Map.Entry<String, Integer> change : out.healthLoss.entrySet()) {
      Resident resident = resident(change.getKey());
      summary +=
          (resident == null ? "Житель" : resident.name) + ": здоровье -" + change.getValue() + "; ";
    }
    for (java.util.Map.Entry<String, Integer> change : out.fatigueAdded.entrySet())
      if (change.getValue() > 0) {
        Resident resident = resident(change.getKey());
        summary +=
            (resident == null ? "Житель" : resident.name)
                + ": усталость +"
                + change.getValue()
                + "; ";
      }
    if (out.delayMinutes > 0) summary += "задержка +" + out.delayMinutes + " мин.; ";
    return summary;
  }

  String continueEvent(String eventId) {
    Expedition expedition = active(Expedition.Type.LOOT);
    if (expedition == null || expedition.state() != Expedition.State.AWAITING_DECISION)
      return "Отряд не ждёт решения";
    ExpeditionEvent event = expedition.explorationEvent;
    if (!event.instanceId.equals(eventId) || !event.effectsApplied || event.continued)
      return "Сначала выберите действие";
    event.continued = true;
    if (event.outcome.retreat) {
      finalizeResearch(expedition, true);
      expedition.beginPhase(Expedition.State.RETURNING, expedition.durationMinutes, now());
      game.addLog("Отряд досрочно возвращается из " + location(expedition.locationId).name + ".");
    } else
      expedition.restorePhase(
          Expedition.State.EXPLORING,
          expedition.phaseDurationMinutes,
          expedition.elapsedMinutes(),
          expedition.phaseStartMinute);
    game.save();
    game.invalidate();
    return "";
  }

  String returnHome(String id) {
    Expedition expedition = find(id);
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
    if (expedition.type == Expedition.Type.RECON) {
      if (expedition.recon == null || !expedition.recon.resolved || !expedition.recon.injuryApplied)
        return;
      game.explorationController.applyReturn(expedition);
    } else {
      game.food = safeAdd(game.food, expedition.cargo.get(ExpeditionLoot.Resource.FOOD));
      game.water = safeAdd(game.water, expedition.cargo.get(ExpeditionLoot.Resource.WATER));
      game.mats = safeAdd(game.mats, expedition.cargo.get(ExpeditionLoot.Resource.MATERIALS));
      game.expeditionWarehouse.add(
          ExpeditionLoot.Resource.MEDICINE, expedition.cargo.get(ExpeditionLoot.Resource.MEDICINE));
      game.expeditionWarehouse.add(
          ExpeditionLoot.Resource.EQUIPMENT,
          expedition.cargo.get(ExpeditionLoot.Resource.EQUIPMENT));
    }
    for (String id : expedition.participantIds) {
      Resident resident = resident(id);
      if (resident == null) continue;
      resident.job = "Отдых";
      resident.status = Resident.Status.HOME;
      resident.autoRecovery = false;
      resident.resumeJob = "";
    }
    expedition.rewardCredited = true;
    expedition.completedMinute = now();
    expedition.restorePhase(Expedition.State.COMPLETED, 1, 1, now());
    if (expedition.type == Expedition.Type.LOOT)
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
