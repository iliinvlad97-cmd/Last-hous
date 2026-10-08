package com.lastdom.game;

import android.content.SharedPreferences;
import java.util.Arrays;

/** Legacy save_v02 keys retained; durable snapshots cover expedition and upgrade transactions. */
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
          .putBoolean(k + "alive", s.alive)
          .putInt(k + "thirst", s.thirst)
          .putInt(k + "foodMinutes", s.foodMinutes)
          .putInt(k + "waterMinutes", s.waterMinutes)
          .putInt(k + "survivalWarnings", s.warningMask)
          .putString(k + "warningMinute", Long.toString(s.warningMinute))
          .putBoolean(k + "autoRecovery", s.autoRecovery)
          .putString(k + "resumeJob", s.resumeJob);
      for (int warning = 0; warning < s.warningAt.length; warning++)
        e.putString(k + "warningAt_" + warning, Long.toString(s.warningAt[warning]));
      for (String fraction : SurvivalController.FRACTIONS)
        e.putInt(k + "survivalFraction_" + fraction, s.survivalFractions.getOrDefault(fraction, 0));
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
          .putInt(key + "damage", expedition.explorationEvent.damage)
          .putBoolean(key + "cityChecked", expedition.cityEventChecked)
          .putBoolean(key + "lootRolled", expedition.lootRolled)
          .putInt(key + "explorationDelay", expedition.explorationDelay)
          .putInt(key + "riskReduction", expedition.cityRiskReduction)
          .putString(key + "cityEventId", expedition.explorationEvent.instanceId)
          .putInt(key + "cityAction", expedition.explorationEvent.chosenAction)
          .putBoolean(key + "cityApplied", expedition.explorationEvent.effectsApplied)
          .putBoolean(key + "cityContinued", expedition.explorationEvent.continued)
          .putString(
              key + "cityVictims",
              android.text.TextUtils.join(
                  ",", expedition.explorationEvent.outcome.healthLoss.keySet()))
          .putString(key + "cityResult", expedition.explorationEvent.outcome.message)
          .putInt(key + "cityDelay", expedition.explorationEvent.outcome.delayMinutes)
          .putInt(key + "cityReduction", expedition.explorationEvent.outcome.riskReduction)
          .putBoolean(key + "cityRetreat", expedition.explorationEvent.outcome.retreat);
      for (String id : expedition.participantIds) {
        e.putInt(
            key + "cityHealth_" + id,
            expedition.explorationEvent.outcome.healthLoss.getOrDefault(id, 0));
        e.putInt(
            key + "cityFatigue_" + id,
            expedition.explorationEvent.outcome.fatigueAdded.getOrDefault(id, 0));
      }
      for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values()) {
        e.putInt(key + "found_" + resource.name(), expedition.found.get(resource));
        e.putInt(key + "cargo_" + resource.name(), expedition.cargo.get(resource));
        e.putInt(key + "unsearched_" + resource.name(), expedition.unsearchedLoot.get(resource));
        e.putInt(
            key + "cityAdded_" + resource.name(),
            expedition.explorationEvent.outcome.added.get(resource));
        e.putInt(
            key + "cityLost_" + resource.name(),
            expedition.explorationEvent.outcome.lost.get(resource));
      }
    }
    e.putInt("exp3_schema", 1);
    for (MapLocation location : game.cityLocations) {
      e.putInt("map_" + location.id + "_depletion", location.depletion());
      e.putString("map_" + location.id + "_state", location.state().name());
    }
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
      e.putInt("exp_store_" + resource.name(), game.expeditionWarehouse.get(resource));
    saveUpgrades(game, e);
    e.putInt("survival6_schema", 1)
        .putInt("survival6_resourceWarnings", game.survivalController.resourceWarnings)
        .putString(
            "survival6_resourceWarningMinute",
            Long.toString(game.survivalController.resourceWarningMinute));
    for (String channel : SurvivalController.OUTPUTS)
      e.putInt(
          "survival6_output_" + channel,
          game.productionRemainders.values.getOrDefault("survival6_" + channel, 0));
    RaidSaveStore.save(game, e);
    if (!e.commit()) throw new IllegalStateException("Не удалось сохранить игру");
  }

  void load(GameController game) {
    game.expeditions.clear();
    game.roomUpgrades.clear();
    game.productionRemainders.values.clear();
    game.survivalController.reset();
    game.raidController.reset();
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
      game.roomLevels[i] =
          sp.contains("upgrade5_schema") ? Math.max(1, Math.min(3, sp.getInt("room" + i, 1))) : 1;
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
      s.health = SurvivalConfig.clamp(sp.getInt(k + "health", 100));
      s.hunger = SurvivalConfig.clamp(sp.getInt(k + "hunger", 10));
      s.fatigue = SurvivalConfig.clamp(sp.getInt(k + "fatigue", 10));
      s.morale = SurvivalConfig.clamp(sp.getInt(k + "morale", 75));
      s.alive = sp.getBoolean(k + "alive", true);
      s.thirst = SurvivalConfig.clamp(sp.getInt(k + "thirst", 10));
      s.foodMinutes = Math.max(0, Math.min(1440, sp.getInt(k + "foodMinutes", 0)));
      s.waterMinutes = Math.max(0, Math.min(1440, sp.getInt(k + "waterMinutes", 0)));
      s.warningMask = sp.getInt(k + "survivalWarnings", 0);
      s.warningMinute = safeLong(k + "warningMinute", -SurvivalConfig.WARNING_COOLDOWN);
      for (int warning = 0; warning < s.warningAt.length; warning++)
        s.warningAt[warning] =
            safeLong(k + "warningAt_" + warning, -SurvivalConfig.WARNING_COOLDOWN);
      s.autoRecovery = sp.getBoolean(k + "autoRecovery", false);
      s.resumeJob = sp.getString(k + "resumeJob", "");
      for (String fraction : SurvivalController.FRACTIONS)
        s.survivalFractions.put(
            fraction,
            Math.max(
                -SurvivalController.NEED_DENOMINATOR + 1,
                Math.min(
                    SurvivalController.NEED_DENOMINATOR - 1,
                    sp.getInt(k + "survivalFraction_" + fraction, 0))));
      game.people.add(s);
    }
    game.log.clear();
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
    loadUpgrades(game);
    game.survivalController.resourceWarnings = sp.getInt("survival6_resourceWarnings", 0);
    game.survivalController.resourceWarningMinute =
        safeLong("survival6_resourceWarningMinute", -SurvivalConfig.WARNING_COOLDOWN);
    for (String channel : SurvivalController.OUTPUTS) {
      int oldFraction = game.productionRemainders.values.getOrDefault(channel, 0) * 144000;
      game.productionRemainders.values.put(
          "survival6_" + channel,
          Math.max(
              0,
              Math.min(
                  SurvivalController.OUTPUT_DENOMINATOR - 1,
                  sp.getInt("survival6_output_" + channel, oldFraction))));
    }
    if (!sp.contains("survival6_schema"))
      for (Resident resident : game.people) {
        resident.survivalFractions.put(
            "health",
            game.productionRemainders.values.getOrDefault("heal_" + resident.id, 0) * 1440);
        resident.survivalFractions.put(
            "fatigue",
            -game.productionRemainders.values.getOrDefault("rest_" + resident.id, 0) * 1440);
      }
    RaidSaveStore.load(game, sp);
  }

  private long safeLong(String key, long fallback) {
    try {
      return Long.parseLong(sp.getString(key, Long.toString(fallback)));
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private void saveUpgrades(GameController game, SharedPreferences.Editor editor) {
    editor
        .putInt("upgrade5_schema", 1)
        .putInt("upgrade5_count", game.roomUpgrades.size())
        .putInt("upgrade5_defense", game.roomUpgradeController.percent(4));
    for (int i = 0; i < game.roomUpgrades.size(); i++) {
      RoomUpgradeTask task = game.roomUpgrades.get(i);
      String key = "upgrade5_" + i + "_";
      editor
          .putString(key + "id", task.id)
          .putInt(key + "room", task.room)
          .putInt(key + "target", task.targetLevel)
          .putString(key + "builder", task.builderId)
          .putString(key + "start", Long.toString(task.startMinute))
          .putInt(key + "duration", task.duration)
          .putInt(key + "elapsed", task.elapsed)
          .putInt(key + "cost", task.paidCost)
          .putBoolean(key + "paid", task.costPaid)
          .putBoolean(key + "completed", task.completed)
          .putBoolean(key + "legacy", task.legacy);
    }
    for (String channel : ProductionRemainders.GLOBAL)
      editor.putInt(
          "upgrade5_fraction_" + channel,
          game.productionRemainders.values.getOrDefault(channel, 0));
    for (Resident resident : game.people)
      for (String kind : new String[] {"heal_", "rest_"}) {
        String channel = kind + resident.id;
        editor.putInt(
            "upgrade5_fraction_" + channel,
            game.productionRemainders.values.getOrDefault(channel, 0));
      }
  }

  private void loadUpgrades(GameController game) {
    if (!sp.contains("upgrade5_schema")) {
      // Previously paid, builderless construction must not charge again or disappear on update.
      int room = sp.getInt("buildingRoom", -1), remaining = sp.getInt("buildRemaining", 0);
      if (RoomUpgradeConfig.valid(room) && remaining > 0) {
        String builder = "";
        for (Resident resident : game.people)
          if (game.roomUpgradeController.unavailableReason(resident).isEmpty()) {
            builder = resident.id;
            break;
          }
        int oldDuration = Math.max(remaining, 120 + Math.max(1, sp.getInt("room" + room, 1)) * 90);
        game.roomUpgrades.add(
            new RoomUpgradeTask(
                "legacy-build-" + room + "-" + game.day,
                room,
                2,
                builder,
                Math.max(0, game.expeditionController.now() - (oldDuration - remaining)),
                oldDuration,
                0,
                true,
                true,
                oldDuration - remaining,
                false));
      }
    } else {
      java.util.Set<String> ids = new java.util.HashSet<>();
      boolean active = false;
      int count = Math.max(0, Math.min(100, sp.getInt("upgrade5_count", 0)));
      for (int i = 0; i < count; i++) {
        String key = "upgrade5_" + i + "_";
        try {
          String id = sp.getString(key + "id", ""), builder = sp.getString(key + "builder", "");
          int room = sp.getInt(key + "room", -1),
              target = sp.getInt(key + "target", 0),
              duration = sp.getInt(key + "duration", 0),
              elapsed = sp.getInt(key + "elapsed", -1),
              cost = sp.getInt(key + "cost", -1);
          long start = Long.parseLong(sp.getString(key + "start", "-1"));
          boolean completed = sp.getBoolean(key + "completed", false),
              paid = sp.getBoolean(key + "paid", false),
              legacy = sp.getBoolean(key + "legacy", false);
          if (id.isEmpty()
              || ids.contains(id)
              || !RoomUpgradeConfig.valid(room)
              || target < 2
              || target > 3
              || duration < 1
              || elapsed < 0
              || elapsed > duration
              || cost < 0
              || start < 0
              || !paid) throw new IllegalArgumentException();
          if (!legacy
              && (cost != RoomUpgradeConfig.cost(room, target)
                  || duration != RoomUpgradeConfig.minutes(room, target)))
            throw new IllegalArgumentException();
          Resident resident = game.expeditionController.resident(builder);
          if (completed) {
            if (elapsed != duration || game.roomLevels[room] < target)
              throw new IllegalArgumentException();
          } else {
            if (active
                || game.roomLevels[room] != target - 1
                || (!legacy && resident == null)
                || (resident != null && game.isOnExpedition(resident)))
              throw new IllegalArgumentException();
          }
          game.roomUpgrades.add(
              new RoomUpgradeTask(
                  id, room, target, builder, start, duration, cost, paid, legacy, elapsed,
                  completed));
          ids.add(id);
          if (!completed) active = true;
        } catch (IllegalArgumentException exception) {
          // Invalid tasks cannot spend materials, grant a level or overwrite expedition membership.
        }
      }
      for (String channel : ProductionRemainders.GLOBAL) loadFraction(game, channel);
      for (Resident resident : game.people)
        for (String kind : new String[] {"heal_", "rest_"}) loadFraction(game, kind + resident.id);
    }
    game.roomUpgradeController.restoreMembership();
  }

  private void loadFraction(GameController game, String channel) {
    game.productionRemainders.values.put(
        channel, Math.max(0, Math.min(99, sp.getInt("upgrade5_fraction_" + channel, 0))));
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
        expedition.cityEventChecked =
            sp.getBoolean(key + "cityChecked", expedition.resultGenerated);
        expedition.lootRolled = sp.getBoolean(key + "lootRolled", expedition.resultGenerated);
        expedition.explorationDelay = Math.max(0, sp.getInt(key + "explorationDelay", 0));
        expedition.cityRiskReduction =
            Math.max(0, Math.min(80, sp.getInt(key + "riskReduction", 0)));
        ExpeditionEvent event = expedition.explorationEvent;
        event.instanceId = sp.getString(key + "cityEventId", "");
        event.chosenAction = sp.getInt(key + "cityAction", -1);
        event.effectsApplied = sp.getBoolean(key + "cityApplied", false);
        event.continued = sp.getBoolean(key + "cityContinued", false);
        event.outcome.message = sp.getString(key + "cityResult", "");
        event.outcome.delayMinutes = Math.max(0, sp.getInt(key + "cityDelay", 0));
        event.outcome.riskReduction =
            Math.max(0, Math.min(80, sp.getInt(key + "cityReduction", 0)));
        event.outcome.retreat = sp.getBoolean(key + "cityRetreat", false);
        for (String id : ids) {
          int health = Math.max(0, sp.getInt(key + "cityHealth_" + id, 0)),
              fatigue = Math.max(0, sp.getInt(key + "cityFatigue_" + id, 0));
          if (health > 0
              || java.util.Arrays.asList(sp.getString(key + "cityVictims", "").split(","))
                  .contains(id)) event.outcome.healthLoss.put(id, health);
          if (fatigue > 0) event.outcome.fatigueAdded.put(id, fatigue);
        }
        for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values()) {
          expedition.unsearchedLoot.set(
              resource, sp.getInt(key + "unsearched_" + resource.name(), 0));
          event.outcome.added.set(resource, sp.getInt(key + "cityAdded_" + resource.name(), 0));
          event.outcome.lost.set(resource, sp.getInt(key + "cityLost_" + resource.name(), 0));
        }
        if (event.interactive()) {
          int actions = ExpeditionEventConfig.actions(event.type).length;
          if (actions == 0
              || !expedition.cityEventChecked
              || !expedition.lootRolled
              || event.chosenAction < -1
              || event.chosenAction >= actions
              || (event.effectsApplied != (event.chosenAction >= 0))
              || (event.continued && !event.effectsApplied)) throw new IllegalArgumentException();
          if (state == Expedition.State.AWAITING_DECISION && event.continued)
            throw new IllegalArgumentException();
          if (state != Expedition.State.AWAITING_DECISION && !event.continued)
            throw new IllegalArgumentException();
        } else if (state == Expedition.State.AWAITING_DECISION)
          throw new IllegalArgumentException();
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
