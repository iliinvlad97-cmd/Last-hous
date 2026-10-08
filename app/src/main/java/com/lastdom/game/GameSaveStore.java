package com.lastdom.game;

import android.content.SharedPreferences;
import java.util.Arrays;

/** Legacy save_v02 serialization. Keys, defaults, ordering and apply semantics remain unchanged. */
final class GameSaveStore {

  private final SharedPreferences sp;

  GameSaveStore(SharedPreferences preferences) {
    sp = preferences;
  }

  void save(GameController game) {
    SharedPreferences.Editor e = sp.edit();
    e.putInt("day", game.day)
        .putInt("gameMinute", game.gameMinute)
        .putInt("speed", game.speed)
        .putBoolean("paused", game.paused)
        .putInt("buildingRoom", game.buildingRoom)
        .putInt("buildRemaining", game.buildRemaining)
        .putInt("food", game.food)
        .putInt("water", game.water)
        .putInt("power", game.power)
        .putInt("mats", game.mats)
        .putInt("threat", game.threat)
        .putInt("shelter", game.shelter)
        .putInt("count", game.people.size());
    for (int i = 0; i < game.people.size(); i++) {
      Resident s = game.people.get(i);
      String k = "p" + i + "_";
      e.putString(k + "name", s.name)
          .putString(k + "id", s.id)
          .putString(k + "role", s.role)
          .putString(k + "job", s.job)
          .putInt(k + "skill", s.skill)
          .putInt(k + "health", s.health)
          .putInt(k + "hunger", s.hunger)
          .putInt(k + "fatigue", s.fatigue)
          .putInt(k + "morale", s.morale)
          .putBoolean(k + "alive", s.alive);
    }
    for (int i = 0; i < 6; i++) {
      e.putInt("room" + i, game.roomLevels[i]);
      e.putInt("roomCond" + i, game.roomCondition[i]);
    }
    e.putInt("expPerson", game.expeditionPerson)
        .putInt("expLoc", game.expeditionLocation)
        .putInt("expRemain", game.expeditionRemaining);
    for (int i = 0; i < game.locations.size(); i++) {
      e.putInt("locStock" + i, game.locations.get(i).stock);
      e.putBoolean("locSeen" + i, game.locations.get(i).discovered);
    }
    e.putString("log", android.text.TextUtils.join("\n§\n", game.log));
    e.putInt("exp2_schema", 1).putInt("exp2_count", game.expeditions.size());
    for (int i = 0; i < game.expeditions.size(); i++) {
      Expedition expedition = game.expeditions.get(i);
      String key = "exp2_" + i + "_";
      e.putString(key + "location", expedition.locationId)
          .putString(
              key + "participants", android.text.TextUtils.join(",", expedition.participantIds))
          .putString(key + "state", expedition.state().name())
          .putString(key + "departure", Long.toString(expedition.departureMinute))
          .putInt(key + "duration", expedition.durationMinutes)
          .putInt(key + "elapsed", expedition.elapsedMinutes())
          .putString(key + "id", expedition.id)
          .putString(key + "phaseStart", Long.toString(expedition.phaseStartMinute))
          .putInt(key + "phaseDuration", expedition.phaseDurationMinutes)
          .putString(key + "completed", Long.toString(expedition.completedMinute))
          .putBoolean(key + "generated", expedition.resultGenerated)
          .putBoolean(key + "credited", expedition.rewardCredited)
          .putBoolean(key + "acknowledged", expedition.reportAcknowledged)
          .putInt(key + "fatigueGain", expedition.fatigueGain)
          .putString(key + "eventType", expedition.explorationEvent.type.name())
          .putString(key + "eventText", expedition.explorationEvent.message)
          .putString(key + "injured", expedition.explorationEvent.injuredResidentId)
          .putInt(key + "damage", expedition.explorationEvent.damage);
      for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values()) {
        e.putInt(key + "found_" + resource.name(), expedition.found.get(resource));
        e.putInt(key + "cargo_" + resource.name(), expedition.cargo.get(resource));
      }
    }
    e.putInt("exp3_schema", 1);
    for (MapLocation location : game.cityLocations) {
      e.putInt("map_" + location.id + "_depletion", location.depletion());
      e.putString("map_" + location.id + "_state", location.state().name());
    }
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
      e.putInt("exp_store_" + resource.name(), game.expeditionWarehouse.get(resource));
    if (game.expeditions.isEmpty()) e.apply();
    else if (!e.commit()) throw new IllegalStateException("Не удалось сохранить экспедицию");
  }

  void load(GameController game) {
    game.expeditions.clear();
    for (MapLocation location : game.cityLocations) {
      location.setDepletion(sp.getInt("map_" + location.id + "_depletion", 0));
      try {
        location.setState(
            MapLocation.State.valueOf(
                sp.getString(
                    "map_" + location.id + "_state",
                    location.kind == MapLocation.Kind.WATER
                            || location.kind == MapLocation.Kind.HOSPITAL
                        ? "LOCKED"
                        : "AVAILABLE")));
      } catch (IllegalArgumentException exception) {
        /* Keep the configuration's safe state. */
      }
    }
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
      game.expeditionWarehouse.set(resource, sp.getInt("exp_store_" + resource.name(), 0));
    game.residentVisualReady = false;
    if (!sp.contains("day")) {
      game.reset();
      return;
    }
    game.day = sp.getInt("day", 1);
    game.gameMinute = sp.getInt("gameMinute", 480);
    game.speed = sp.getInt("speed", 1);
    game.paused = sp.getBoolean("paused", false);
    game.buildingRoom = sp.getInt("buildingRoom", -1);
    game.buildRemaining = sp.getInt("buildRemaining", 0);
    game.food = sp.getInt("food", 28);
    game.water = sp.getInt("water", 34);
    game.power = sp.getInt("power", 24);
    game.mats = sp.getInt("mats", 18);
    game.threat = sp.getInt("threat", 12);
    game.shelter = sp.getInt("shelter", 100);
    game.expeditionPerson = sp.getInt("expPerson", -1);
    game.expeditionLocation = sp.getInt("expLoc", -1);
    game.expeditionRemaining = sp.getInt("expRemain", 0);
    for (int i = 0; i < game.locations.size(); i++) {
      game.locations.get(i).stock = sp.getInt("locStock" + i, 100);
      game.locations.get(i).discovered =
          sp.getBoolean("locSeen" + i, game.locations.get(i).discovered);
    }
    for (int i = 0; i < 6; i++) {
      game.roomLevels[i] = sp.getInt("room" + i, 1);
      game.roomCondition[i] = sp.getInt("roomCond" + i, 100);
    }
    java.util.Set<String> residentIds = new java.util.HashSet<>();
    int n = sp.getInt("count", 5);
    game.people.clear();
    for (int i = 0; i < n; i++) {
      String k = "p" + i + "_";
      Resident s =
          game.make(
              sp.getString(k + "name", "Выживший"),
              sp.getString(k + "role", "Житель"),
              sp.getInt(k + "skill", 2));
      s.job = sp.getString(k + "job", "Отдых");
      s.id = sp.getString(k + "id", "legacy-" + i);
      if (s.id == null || s.id.isEmpty() || residentIds.contains(s.id)) s.id = "legacy-" + i;
      while (residentIds.contains(s.id)) s.id += "-recovered";
      residentIds.add(s.id);
      s.health = sp.getInt(k + "health", 100);
      s.hunger = sp.getInt(k + "hunger", 10);
      s.fatigue = sp.getInt(k + "fatigue", 10);
      s.morale = sp.getInt(k + "morale", 75);
      s.alive = sp.getBoolean(k + "alive", true);
      game.people.add(s);
    }
    String l = sp.getString("log", "");
    if (!l.isEmpty()) game.log.addAll(Arrays.asList(l.split("\\n§\\n")));
    if (sp.contains("exp2_schema")) loadExpeditions(game);
    else migrateLegacyExpedition(game);
    for (Expedition expedition : game.expeditions)
      if (expedition.state() == Expedition.State.AT_LOCATION)
        expedition.beginPhase(
            Expedition.State.EXPLORING,
            ExpeditionConfig.explorationMinutes(
                game.expeditionController.location(expedition.locationId)),
            game.expeditionController.now());
    game.expeditionController.restoreMembership();
  }

  private void loadExpeditions(GameController game) {
    java.util.Set<String> used = new java.util.HashSet<>();
    int count = Math.max(0, Math.min(10000, sp.getInt("exp2_count", 0)));
    for (int i = 0; i < count; i++) {
      String key = "exp2_" + i + "_";
      try {
        String target = sp.getString(key + "location", "");
        java.util.List<String> ids =
            Arrays.asList(sp.getString(key + "participants", "").split(","));
        Expedition.State state = Expedition.State.valueOf(sp.getString(key + "state", ""));
        int duration = sp.getInt(key + "duration", 0), elapsed = sp.getInt(key + "elapsed", 0);
        int phaseDuration = sp.getInt(key + "phaseDuration", duration);
        long departure = Long.parseLong(sp.getString(key + "departure", "0"));
        if (game.expeditionController.location(target) == null
            || duration <= 0
            || elapsed < 0
            || phaseDuration <= 0
            || elapsed > phaseDuration
            || ids.isEmpty()
            || ids.size() > ExpeditionConfig.MAX_PARTICIPANTS
            || new java.util.HashSet<>(ids).size() != ids.size()
            || departure < 0) throw new IllegalArgumentException();
        for (String id : ids)
          if (game.expeditionController.resident(id) == null || used.contains(id))
            throw new IllegalArgumentException();
        if (state == Expedition.State.TRAVELING_TO_TARGET && elapsed == duration)
          state = Expedition.State.AT_LOCATION;
        if (state == Expedition.State.AT_LOCATION) elapsed = duration;
        Expedition expedition = new Expedition(target, ids, departure, duration, elapsed, state);
        expedition.id = sp.getString(key + "id", "legacy-exp-" + i + "-" + departure);
        expedition.restorePhase(
            state,
            phaseDuration,
            elapsed,
            Long.parseLong(sp.getString(key + "phaseStart", Long.toString(departure))));
        expedition.completedMinute = Long.parseLong(sp.getString(key + "completed", "0"));
        expedition.resultGenerated = sp.getBoolean(key + "generated", false);
        expedition.rewardCredited =
            sp.getBoolean(key + "credited", state == Expedition.State.COMPLETED);
        expedition.reportAcknowledged =
            sp.getBoolean(
                key + "acknowledged",
                state == Expedition.State.COMPLETED && !sp.contains("exp3_schema"));
        expedition.fatigueGain = Math.max(0, sp.getInt(key + "fatigueGain", 0));
        expedition.explorationEvent =
            new ExpeditionEvent(
                ExpeditionEvent.Type.valueOf(sp.getString(key + "eventType", "QUIET")),
                sp.getString(key + "eventText", "Исследование прошло спокойно"),
                sp.getString(key + "injured", ""),
                sp.getInt(key + "damage", 0));
        for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values()) {
          expedition.found.set(resource, sp.getInt(key + "found_" + resource.name(), 0));
          expedition.cargo.set(resource, sp.getInt(key + "cargo_" + resource.name(), 0));
        }
        if (expedition.cargo.total() > expedition.capacity()) throw new IllegalArgumentException();
        if (state == Expedition.State.RETURNING || state == Expedition.State.AWAITING_RETURN)
          if (!expedition.resultGenerated) throw new IllegalArgumentException();
        if (expedition.rewardCredited && state != Expedition.State.COMPLETED)
          throw new IllegalArgumentException();
        if (state == Expedition.State.COMPLETED && !expedition.rewardCredited)
          throw new IllegalArgumentException();
        if (state == Expedition.State.AT_LOCATION) {
          expedition.beginPhase(
              Expedition.State.EXPLORING,
              ExpeditionConfig.explorationMinutes(game.expeditionController.location(target)),
              game.expeditionController.now());
        }
        if (state == Expedition.State.EXPLORING && expedition.resultGenerated)
          expedition.beginPhase(
              Expedition.State.AWAITING_RETURN, 1, game.expeditionController.now());
        if (expedition.active()
            && game.expeditions.stream().filter(Expedition::active).count()
                >= ExpeditionConfig.MAX_ACTIVE) throw new IllegalArgumentException();
        if (game.expeditions.stream().anyMatch(existing -> existing.id.equals(expedition.id)))
          throw new IllegalArgumentException();
        game.expeditions.add(expedition);
        if (expedition.active()) used.addAll(ids);
      } catch (IllegalArgumentException exception) {
        game.addLog("Не удалось восстановить запись экспедиции. Назначения жителей сохранены.");
      }
    }
  }

  private void migrateLegacyExpedition(GameController game) {
    int person = game.expeditionPerson, location = game.expeditionLocation;
    if (person < 0
        || person >= game.people.size()
        || location < 0
        || location >= game.cityLocations.size()) return;
    MapLocation target = game.cityLocations.get(location);
    int remaining = Math.max(0, game.expeditionRemaining);
    int duration = Math.max(ExpeditionConfig.oneWayMinutes(target), remaining);
    game.expeditions.add(
        new Expedition(
            target.id,
            java.util.Collections.singletonList(game.people.get(person).id),
            Math.max(0, (game.day - 1L) * 1440 + game.gameMinute - (duration - remaining)),
            duration,
            duration - remaining,
            remaining == 0 ? Expedition.State.AT_LOCATION : Expedition.State.TRAVELING_TO_TARGET));
    game.expeditionPerson = -1;
    game.expeditionLocation = -1;
    game.expeditionRemaining = 0;
  }
}
