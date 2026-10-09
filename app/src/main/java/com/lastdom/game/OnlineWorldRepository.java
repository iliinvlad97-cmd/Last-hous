package com.lastdom.game;

import java.util.*;

/** World data and authoritative demo commands. No live network adapter is enabled. */
interface OnlineWorldRepository extends OnlineBattleRepository {
  Snapshot load();

  default OnlineWorldGameplay.Data gameplay() {
    return OnlineWorldGameplay.Data.initial();
  }

  default OnlineWorldGameplay.Result execute(String offerId, OnlineWorldGameplay.Kind kind) {
    return new OnlineWorldGameplay.Result(false, "Действия недоступны для этого источника");
  }

  default OnlineWorldGameplay.Result preparePvp(String zoneId, boolean consent) {
    return new OnlineWorldGameplay.Result(false, "Подготовка недоступна для этого источника");
  }

  default OnlineWorldGameplay.Result disablePvp() {
    return new OnlineWorldGameplay.Result(false, "Действие недоступно");
  }

  default boolean writable() {
    return false;
  }

  default boolean advanceSecond() {
    return false;
  }

  final class Snapshot {
    final OnlineWorldState.Connection connection;
    final List<OnlineShelter> shelters;
    final List<OnlineZone> zones;
    final List<OnlineSquad> squads;
    final List<OnlineWorldGeometry.Shape> streets;
    final List<OnlineWorldGeometry.Block> buildings;
    final List<OnlineWorldGeometry.Point> towers, mist;

    Snapshot(
        OnlineWorldState.Connection connection,
        List<OnlineShelter> shelters,
        List<OnlineZone> zones,
        List<OnlineSquad> squads,
        List<OnlineWorldGeometry.Shape> streets,
        List<OnlineWorldGeometry.Block> buildings,
        List<OnlineWorldGeometry.Point> towers,
        List<OnlineWorldGeometry.Point> mist) {
      this.connection = Objects.requireNonNull(connection);
      this.shelters = copy(shelters);
      this.zones = copy(zones);
      this.squads = copy(squads);
      this.streets = copy(streets);
      this.buildings = copy(buildings);
      this.towers = copy(towers);
      this.mist = copy(mist);
      Set<String> ids = new HashSet<>();
      for (OnlineShelter shelter : shelters) unique(ids, shelter.id);
      for (OnlineZone zone : zones) unique(ids, zone.id);
      for (OnlineSquad squad : squads) unique(ids, squad.id);
    }

    private static <T> List<T> copy(List<T> source) {
      for (T item : source) Objects.requireNonNull(item);
      return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static void unique(Set<String> ids, String id) {
      if (!ids.add(id)) throw new IllegalArgumentException("Duplicate online object ID");
    }
  }
}
