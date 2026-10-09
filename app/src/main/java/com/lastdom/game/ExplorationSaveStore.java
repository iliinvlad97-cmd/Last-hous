package com.lastdom.game;

import android.content.SharedPreferences;

/** Additive save_v02 fields, committed in the same transaction as residents and ordinary trips. */
final class ExplorationSaveStore {
  static void save(GameController game, SharedPreferences.Editor editor) {
    editor.putInt("exploration9_schema", 1);
    for (CityDistrict d : game.cityDistricts)
      editor.putString("district9_" + d.config.id, d.state.name());
  }

  static void load(GameController game, SharedPreferences preferences) {
    game.explorationController.reset();
    for (CityDistrict d : game.cityDistricts) {
      try {
        d.state =
            CityDistrict.State.valueOf(
                preferences.getString("district9_" + d.config.id, d.state.name()));
      } catch (IllegalArgumentException ignored) {
        /* Safe configuration default. */
      }
      if (d.state == CityDistrict.State.EXPLORED)
        for (String point : d.config.points) {
          MapLocation location = game.expeditionController.location(point);
          if (location.isLocked()) location.setState(MapLocation.State.AVAILABLE);
        }
    }
  }

  static void saveExpedition(Expedition e, String prefix, SharedPreferences.Editor editor) {
    editor.putString(prefix + "type9", e.type.name());
    if (e.type != Expedition.Type.RECON) return;
    ReconData d = e.recon;
    String key = prefix + "recon9_";
    editor
        .putString(key + "seed", Long.toString(d.seed))
        .putInt(key + "chance", d.chanceBasis)
        .putInt(key + "researchMinutes", d.researchMinutes)
        .putInt(key + "injuryChance", d.injuryChance)
        .putInt(key + "damagePercent", d.damagePercent)
        .putBoolean(key + "resolved", d.resolved)
        .putBoolean(key + "success", d.success)
        .putBoolean(key + "injuryApplied", d.injuryApplied)
        .putBoolean(key + "unlocksApplied", d.unlocksApplied)
        .putString(key + "injured", d.injuredId)
        .putInt(key + "healthLoss", d.healthLoss)
        .putString(key + "opened", String.join(",", d.openedPoints))
        .putBoolean(key + "discoveryRecorded", d.discoveryRecorded)
        .putString(key + "newPoints", String.join(",", d.newlyOpenedPoints))
        .putString(key + "newDistricts", String.join(",", d.discoveredDistricts));
    for (String id : e.participantIds)
      editor
          .putString(key + "name_" + id, d.names.get(id))
          .putInt(key + "startFatigue_" + id, d.startFatigue.get(id))
          .putInt(key + "fatigueGain_" + id, d.fatigueGain.getOrDefault(id, 0));
  }

  static void loadExpedition(
      GameController game, Expedition e, String prefix, SharedPreferences preferences) {
    e.type = Expedition.Type.valueOf(preferences.getString(prefix + "type9", "LOOT"));
    MapLocation target = game.expeditionController.location(e.locationId);
    if (e.type == Expedition.Type.LOOT) {
      if (target.kind == MapLocation.Kind.DISTRICT) throw new IllegalArgumentException();
      return;
    }
    CityDistrict district = game.explorationController.district(e.locationId);
    if (district == null || target.kind != MapLocation.Kind.DISTRICT || e.durationMinutes > 10000)
      throw new IllegalArgumentException();
    String key = prefix + "recon9_";
    ReconData d = new ReconData();
    d.seed = Long.parseLong(preferences.getString(key + "seed", ""));
    d.chanceBasis = preferences.getInt(key + "chance", 0);
    d.researchMinutes =
        preferences.getInt(key + "researchMinutes", district.config.researchMinutes);
    d.injuryChance = preferences.getInt(key + "injuryChance", district.config.injuryChance);
    d.damagePercent = preferences.getInt(key + "damagePercent", 100);
    d.resolved = preferences.getBoolean(key + "resolved", false);
    d.success = preferences.getBoolean(key + "success", false);
    d.injuryApplied = preferences.getBoolean(key + "injuryApplied", false);
    d.unlocksApplied = preferences.getBoolean(key + "unlocksApplied", false);
    d.injuredId = preferences.getString(key + "injured", "");
    d.healthLoss = preferences.getInt(key + "healthLoss", 0);
    String opened = preferences.getString(key + "opened", "");
    if (!opened.isEmpty()) d.openedPoints.addAll(java.util.Arrays.asList(opened.split(",")));
    for (String id : e.participantIds) {
      d.names.put(
          id,
          preferences.getString(key + "name_" + id, game.expeditionController.resident(id).name));
      int initial = preferences.getInt(key + "startFatigue_" + id, -1),
          gain = preferences.getInt(key + "fatigueGain_" + id, 0);
      if (initial < 0 || initial > 100000 || gain < 0 || gain > 100000)
        throw new IllegalArgumentException();
      d.startFatigue.put(id, initial);
      if (d.unlocksApplied) d.fatigueGain.put(id, gain);
    }
    d.discoveryRecorded = preferences.getBoolean(key + "discoveryRecorded", false);
    readIds(preferences.getString(key + "newPoints", ""), d.newlyOpenedPoints);
    readIds(preferences.getString(key + "newDistricts", ""), d.discoveredDistricts);
    if ((!d.discoveryRecorded
            && (!d.newlyOpenedPoints.isEmpty() || !d.discoveredDistricts.isEmpty()))
        || (d.discoveryRecorded && !d.unlocksApplied)
        || (!d.success && (!d.newlyOpenedPoints.isEmpty() || !d.discoveredDistricts.isEmpty()))
        || !district.config.points.containsAll(d.newlyOpenedPoints))
      throw new IllegalArgumentException();
    for (String id : d.discoveredDistricts) {
      ExplorationConfig.District child = ExplorationConfig.district(id);
      if (child == null || !child.prerequisite.equals(district.config.id))
        throw new IllegalArgumentException();
    }
    Expedition.State state = e.state();
    if (d.chanceBasis < ExplorationConfig.MIN_CHANCE
        || d.chanceBasis > ExplorationConfig.MAX_CHANCE
        || d.researchMinutes <= 0
        || d.researchMinutes > 10000
        || d.injuryChance < 0
        || d.injuryChance > 10000
        || d.damagePercent < 0
        || d.damagePercent > 100
        || (!d.resolved && (!d.injuredId.isEmpty() || d.healthLoss != 0))
        || d.healthLoss < 0
        || d.healthLoss > ExplorationConfig.MAX_INJURY
        || (!d.injuredId.isEmpty() && !e.participantIds.contains(d.injuredId))
        || (d.healthLoss > 0 && d.injuredId.isEmpty())
        || (d.resolved != e.resultGenerated)
        || (d.injuryApplied != d.resolved)
        || (d.success && !d.resolved)
        || (d.unlocksApplied != (state == Expedition.State.COMPLETED))
        || e.found.total() != 0
        || e.cargo.total() != 0
        || e.lootRolled
        || e.explorationEvent.interactive()
        || (state != Expedition.State.TRAVELING_TO_TARGET
            && state != Expedition.State.AT_LOCATION
            && state != Expedition.State.EXPLORING
            && state != Expedition.State.RETURNING
            && state != Expedition.State.COMPLETED)
        || (d.resolved
            != (state == Expedition.State.RETURNING || state == Expedition.State.COMPLETED))
        || (state == Expedition.State.EXPLORING && e.phaseDurationMinutes != d.researchMinutes)
        || (!d.openedPoints.isEmpty() && (!d.success || !d.unlocksApplied))
        || (d.unlocksApplied && d.success && !d.openedPoints.equals(district.config.points))
        || !district.config.points.containsAll(d.openedPoints)
        || new java.util.HashSet<>(d.openedPoints).size() != d.openedPoints.size())
      throw new IllegalArgumentException();
    e.recon = d;
  }

  private static void readIds(String saved, java.util.List<String> ids) {
    if (saved.isEmpty()) return;
    ids.addAll(java.util.Arrays.asList(saved.split(",", -1)));
    if (new java.util.HashSet<>(ids).size() != ids.size() || ids.contains(""))
      throw new IllegalArgumentException();
  }
}
