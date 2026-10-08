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
          .putInt(key + "elapsed", expedition.elapsedMinutes());
    }
    e.apply();
  }

  void load(GameController game) {
    game.expeditions.clear();
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
    game.expeditionController.restoreMembership();
  }

  private void loadExpeditions(GameController game) {
    java.util.Set<String> used = new java.util.HashSet<>();
    int count = Math.max(0, Math.min(100, sp.getInt("exp2_count", 0)));
    for (int i = 0; i < count; i++) {
      String key = "exp2_" + i + "_";
      try {
        String target = sp.getString(key + "location", "");
        java.util.List<String> ids =
            Arrays.asList(sp.getString(key + "participants", "").split(","));
        Expedition.State state = Expedition.State.valueOf(sp.getString(key + "state", ""));
        int duration = sp.getInt(key + "duration", 0), elapsed = sp.getInt(key + "elapsed", 0);
        long departure = Long.parseLong(sp.getString(key + "departure", "0"));
        if (game.expeditionController.location(target) == null
            || duration <= 0
            || elapsed < 0
            || elapsed > duration
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
        if (expedition.active()
            && game.expeditions.stream().filter(Expedition::active).count()
                >= ExpeditionConfig.MAX_ACTIVE) throw new IllegalArgumentException();
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
