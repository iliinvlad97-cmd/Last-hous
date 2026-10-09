package com.lastdom.game;

import java.util.*;

/**
 * Recon transitions share squads, routes, minute clock and completion transaction with loot trips.
 */
final class ExplorationController {
  private final GameController game;

  ExplorationController(GameController game) {
    this.game = game;
  }

  CityDistrict district(String id) {
    for (CityDistrict d : game.cityDistricts) if (d.config.id.equals(id)) return d;
    return null;
  }

  void reset() {
    for (CityDistrict d : game.cityDistricts)
      d.state =
          d.config.prerequisite.isEmpty()
              ? CityDistrict.State.DISCOVERED
              : CityDistrict.State.UNEXPLORED;
  }

  String condition(CityDistrict d) {
    if (d == null) return "Район не существует";
    if (d.state == CityDistrict.State.EXPLORED) return "Район уже исследован";
    if (!d.config.prerequisite.isEmpty()
        && district(d.config.prerequisite).state != CityDistrict.State.EXPLORED)
      return "Сначала исследуйте: " + district(d.config.prerequisite).config.name;
    return "";
  }

  String unavailableReason(String id) {
    String reason = condition(district(id));
    if (!reason.isEmpty()) return reason;
    if (game.expeditionController.activeCount(Expedition.Type.RECON)
        >= ExpeditionConfig.maxActive(Expedition.Type.RECON))
      return "Сначала завершите текущую разведку";
    if (game.gameOver) return "Игра завершена";
    boolean available = false;
    for (Resident r : game.people)
      if (game.expeditionController.unavailableReason(r).isEmpty()) {
        available = true;
        break;
      }
    if (!available) return "Нет доступных жителей для разведки";
    return "";
  }

  String start(String districtId, Collection<String> ids) {
    return game.expeditionController.startRecon(districtId, ids);
  }

  int chanceBasis(String districtId, Collection<String> ids) {
    CityDistrict d = district(districtId);
    if (d == null) return ExplorationConfig.MIN_CHANCE;
    int health = 0, fatigue = 0, skill = 0, gatherers = 0, count = 0;
    if (ids != null)
      for (String id : new LinkedHashSet<>(ids)) {
        Resident r = game.expeditionController.resident(id);
        if (r == null) continue;
        health += SurvivalConfig.clamp(r.health);
        fatigue += SurvivalConfig.clamp(r.fatigue);
        skill += Math.max(0, Math.min(5, r.skill));
        count++;
        if (r.role.equals("Сборщик") || r.role.equals("Собиратель")) gatherers++;
      }
    if (count == 0)
      return Math.max(
          ExplorationConfig.MIN_CHANCE,
          Math.min(ExplorationConfig.MAX_CHANCE, d.config.baseChance));
    int value =
        d.config.baseChance
            + gatherers * ExplorationConfig.GATHERER_BONUS
            + (count - 1) * ExplorationConfig.EXTRA_MEMBER_BONUS
            + (skill * ExplorationConfig.SKILL_BONUS
                    - (100 * count - health) * ExplorationConfig.MISSING_HEALTH_PENALTY
                    - fatigue * ExplorationConfig.FATIGUE_PENALTY)
                / count;
    return Math.max(ExplorationConfig.MIN_CHANCE, Math.min(ExplorationConfig.MAX_CHANCE, value));
  }

  ReconData capture(Expedition e) {
    ReconData data = new ReconData();
    data.seed = game.rnd.nextLong();
    data.chanceBasis = chanceBasis(e.locationId, e.participantIds);
    data.researchMinutes = district(e.locationId).config.researchMinutes;
    data.injuryChance = district(e.locationId).config.injuryChance;
    if (game.expeditionController.guard(e))
      data.injuryChance = data.injuryChance * ExplorationConfig.GUARD_RISK_PERCENT / 100;
    for (String id : e.participantIds)
      if (game.expeditionController.resident(id).role.equals("Врач")) {
        data.damagePercent = ExplorationConfig.DOCTOR_DAMAGE_PERCENT;
        break;
      }
    for (String id : e.participantIds) {
      Resident r = game.expeditionController.resident(id);
      data.names.put(id, r.name);
      data.startFatigue.put(id, fatigueMilli(r));
    }
    return data;
  }

  int fatigueMilli(Resident r) {
    return Math.max(
        0,
        Math.min(
            100000,
            r.fatigue * 1000
                + (int)
                    ((long) r.survivalFractions.getOrDefault("fatigue", 0)
                        * 1000
                        / SurvivalController.NEED_DENOMINATOR)));
  }

  void beginResearch(Expedition e) {
    if (e.type != Expedition.Type.RECON
        || (e.state() != Expedition.State.TRAVELING_TO_TARGET
            && e.state() != Expedition.State.AT_LOCATION)) return;
    e.beginPhase(
        Expedition.State.EXPLORING, e.recon.researchMinutes, game.expeditionController.now());
    game.addLog("Разведчики прибыли: " + district(e.locationId).config.name + ". Начата разведка.");
  }

  void finishResearch(Expedition e) {
    if (e.recon == null
        || e.recon.resolved
        || e.state() != Expedition.State.EXPLORING
        || e.remainingMinutes() != 0) return;
    ReconData data = e.recon;
    Random random = new Random(data.seed);
    data.success = random.nextInt(10000) < data.chanceBasis;
    int risk = data.injuryChance;
    if (random.nextInt(10000) < risk) {
      data.injuredId = e.participantIds.get(random.nextInt(e.participantIds.size()));
      int damage =
          ExplorationConfig.MIN_INJURY
              + random.nextInt(ExplorationConfig.MAX_INJURY - ExplorationConfig.MIN_INJURY + 1);
      damage = damage * data.damagePercent / 100;
      Resident victim = game.expeditionController.resident(data.injuredId);
      data.healthLoss = Math.min(victim.health, damage);
      victim.health = SurvivalConfig.clamp(victim.health - data.healthLoss);
      game.survivalController.injury(victim, data.healthLoss);
    }
    data.injuryApplied = true;
    data.resolved = true;
    e.resultGenerated = true;
    e.beginPhase(Expedition.State.RETURNING, e.durationMinutes, game.expeditionController.now());
    game.addLog(
        "Разведка "
            + district(e.locationId).config.name
            + ": "
            + (data.success ? "разведка успешна" : "разведка неудачна")
            + ". Отряд возвращается."
            + (data.healthLoss > 0
                ? " " + data.names.get(data.injuredId) + ": здоровье -" + data.healthLoss + "."
                : ""));
  }

  void applyReturn(Expedition e) {
    ReconData data = e.recon;
    if (data == null
        || data.unlocksApplied
        || !data.resolved
        || !data.injuryApplied
        || e.state() != Expedition.State.RETURNING
        || e.remainingMinutes() != 0) return;
    CityDistrict d = district(e.locationId);
    if (data.success) {
      d.state = CityDistrict.State.EXPLORED;
      for (String pointId : d.config.points) {
        MapLocation point = game.expeditionController.location(pointId);
        if (point.isLocked()) {
          point.setState(MapLocation.State.AVAILABLE);
          data.newlyOpenedPoints.add(pointId);
        }
        data.openedPoints.add(pointId);
      }
      for (CityDistrict next : game.cityDistricts)
        if (next.state == CityDistrict.State.UNEXPLORED
            && next.config.prerequisite.equals(d.config.id)) {
          next.state = CityDistrict.State.DISCOVERED;
          data.discoveredDistricts.add(next.config.id);
        }
    }
    data.discoveryRecorded = true;
    for (String id : e.participantIds)
      data.fatigueGain.put(
          id,
          Math.max(
              0, fatigueMilli(game.expeditionController.resident(id)) - data.startFatigue.get(id)));
    data.unlocksApplied = true;
    game.addLog(
        "Разведчики вернулись: "
            + d.config.name
            + ". "
            + (data.success
                ? "Район исследован. Новых точек: " + data.newlyOpenedPoints.size()
                : "Разведка неудачна, район не открыт")
            + ". Усталость накоплена за игровое время.");
  }

  Expedition latestReport(String districtId) {
    for (int i = game.expeditions.size() - 1; i >= 0; i--) {
      Expedition e = game.expeditions.get(i);
      if (e.type == Expedition.Type.RECON && e.locationId.equals(districtId)) return e;
    }
    return null;
  }
}
