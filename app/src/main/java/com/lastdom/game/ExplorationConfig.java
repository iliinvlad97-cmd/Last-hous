package com.lastdom.game;

import java.util.*;

/** All Stage 9 balance, prerequisites and normalized endpoints. No new resource types. */
final class ExplorationConfig {
  static final int MAX_ACTIVE_RECON = 1, MIN_CHANCE = 1000, MAX_CHANCE = 9500;
  static final int GATHERER_BONUS = 800, EXTRA_MEMBER_BONUS = 200, SKILL_BONUS = 150;
  static final int MISSING_HEALTH_PENALTY = 30, FATIGUE_PENALTY = 20;
  static final int MIN_INJURY = 8,
      MAX_INJURY = 18,
      GUARD_RISK_PERCENT = 85,
      DOCTOR_DAMAGE_PERCENT = 75;

  static final class District {
    final String id, name, marker, loot, prerequisite;
    final int baseChance, researchMinutes, injuryChance;
    final MapLocation.Risk risk;
    final MapLocation.Distance distance;
    final float x, y;
    final List<String> points;

    District(
        String id,
        String name,
        String marker,
        String loot,
        String prerequisite,
        int chance,
        int research,
        int injury,
        MapLocation.Risk risk,
        MapLocation.Distance distance,
        float x,
        float y,
        String... points) {
      this.id = id;
      this.name = name;
      this.marker = marker;
      this.loot = loot;
      this.prerequisite = prerequisite;
      baseChance = chance;
      researchMinutes = research;
      injuryChance = injury;
      this.risk = risk;
      this.distance = distance;
      this.x = x;
      this.y = y;
      this.points = Collections.unmodifiableList(Arrays.asList(points));
    }

    MapLocation markerLocation() {
      return new MapLocation(
          id,
          name,
          marker,
          loot,
          MapLocation.Kind.DISTRICT,
          distance,
          risk,
          x,
          y,
          MapLocation.State.LOCKED);
    }
  }

  static final List<District> DISTRICTS =
      Collections.unmodifiableList(
          Arrays.asList(
              new District(
                  "residential",
                  "Жилой квартал",
                  "ЖИЛОЙ\nКВАРТАЛ",
                  "Еда, вода, материалы",
                  "",
                  8500,
                  60,
                  500,
                  MapLocation.Risk.LOW,
                  MapLocation.Distance.NEAR,
                  .25f,
                  .65f,
                  "apartments",
                  "courtyard"),
              new District(
                  "industrial",
                  "Промышленная зона",
                  "ПРОМЫШЛЕННАЯ\nЗОНА",
                  "Материалы, снаряжение",
                  "residential",
                  7000,
                  90,
                  1200,
                  MapLocation.Risk.HIGH,
                  MapLocation.Distance.MEDIUM,
                  .23f,
                  .36f,
                  "factory",
                  "depot"),
              new District(
                  "warehouses",
                  "Складской комплекс",
                  "СКЛАДСКОЙ\nКОМПЛЕКС",
                  "Еда, вода, материалы",
                  "industrial",
                  6500,
                  120,
                  1500,
                  MapLocation.Risk.MEDIUM,
                  MapLocation.Distance.FAR,
                  .73f,
                  .46f,
                  "food_warehouse",
                  "supply_warehouse",
                  "water"),
              new District(
                  "infected",
                  "Заражённый сектор",
                  "ЗАРАЖЁННЫЙ\nСЕКТОР",
                  "Медикаменты, снаряжение",
                  "warehouses",
                  4500,
                  180,
                  3000,
                  MapLocation.Risk.VERY_HIGH,
                  MapLocation.Distance.FAR,
                  .73f,
                  .16f,
                  "laboratory",
                  "quarantine",
                  "hospital")));

  static District district(String id) {
    for (District d : DISTRICTS) if (d.id.equals(id)) return d;
    return null;
  }

  static List<CityDistrict> initialDistricts() {
    List<CityDistrict> result = new ArrayList<>();
    for (District d : DISTRICTS) result.add(new CityDistrict(d));
    return result;
  }

  static List<MapLocation> destinations() {
    List<MapLocation> result = new ArrayList<>();
    for (District d : DISTRICTS) result.add(d.markerLocation());
    result.add(
        point(
            "apartments",
            "Пустующие квартиры",
            "ПУСТУЮЩИЕ\nКВАРТИРЫ",
            MapLocation.Kind.STORE,
            MapLocation.Distance.NEAR,
            MapLocation.Risk.LOW,
            .24f,
            .54f,
            new LootTable()
                .range(ExpeditionLoot.Resource.FOOD, 6, 16)
                .range(ExpeditionLoot.Resource.WATER, 3, 10)
                .range(ExpeditionLoot.Resource.MATERIALS, 1, 5)));
    result.add(
        point(
            "courtyard",
            "Дворовые запасы",
            "ДВОРОВЫЕ\nЗАПАСЫ",
            MapLocation.Kind.STORE,
            MapLocation.Distance.NEAR,
            MapLocation.Risk.MEDIUM,
            .72f,
            .64f,
            new LootTable()
                .range(ExpeditionLoot.Resource.FOOD, 4, 12)
                .range(ExpeditionLoot.Resource.WATER, 4, 12)
                .range(ExpeditionLoot.Resource.MATERIALS, 3, 8)));
    result.add(
        point(
            "factory",
            "Разрушенный цех",
            "РАЗРУШЕННЫЙ\nЦЕХ",
            MapLocation.Kind.GARAGE,
            MapLocation.Distance.MEDIUM,
            MapLocation.Risk.HIGH,
            .24f,
            .34f,
            new LootTable()
                .range(ExpeditionLoot.Resource.MATERIALS, 15, 30)
                .range(ExpeditionLoot.Resource.EQUIPMENT, 2, 6)));
    result.add(
        point(
            "depot",
            "Техническое депо",
            "ТЕХНИЧЕСКОЕ\nДЕПО",
            MapLocation.Kind.GARAGE,
            MapLocation.Distance.MEDIUM,
            MapLocation.Risk.MEDIUM,
            .72f,
            .44f,
            new LootTable()
                .range(ExpeditionLoot.Resource.MATERIALS, 12, 25)
                .range(ExpeditionLoot.Resource.EQUIPMENT, 1, 5)));
    result.add(
        point(
            "food_warehouse",
            "Продовольственный склад",
            "ПРОДОВОЛЬСТВЕННЫЙ\nСКЛАД",
            MapLocation.Kind.STORE,
            MapLocation.Distance.FAR,
            MapLocation.Risk.MEDIUM,
            .73f,
            .35f,
            new LootTable()
                .range(ExpeditionLoot.Resource.FOOD, 15, 30)
                .range(ExpeditionLoot.Resource.WATER, 6, 16)));
    result.add(
        point(
            "supply_warehouse",
            "Склад снабжения",
            "СКЛАД\nСНАБЖЕНИЯ",
            MapLocation.Kind.STORE,
            MapLocation.Distance.FAR,
            MapLocation.Risk.MEDIUM,
            .26f,
            .45f,
            new LootTable()
                .range(ExpeditionLoot.Resource.FOOD, 8, 18)
                .range(ExpeditionLoot.Resource.WATER, 8, 18)
                .range(ExpeditionLoot.Resource.MATERIALS, 8, 20)));
    result.add(
        point(
            "laboratory",
            "Изолированная лаборатория",
            "ИЗОЛИРОВАННАЯ\nЛАБОРАТОРИЯ",
            MapLocation.Kind.HOSPITAL,
            MapLocation.Distance.FAR,
            MapLocation.Risk.VERY_HIGH,
            .25f,
            .36f,
            new LootTable()
                .range(ExpeditionLoot.Resource.MEDICINE, 15, 30)
                .range(ExpeditionLoot.Resource.EQUIPMENT, 2, 8)));
    result.add(
        point(
            "quarantine",
            "Карантинный пост",
            "КАРАНТИННЫЙ\nПОСТ",
            MapLocation.Kind.POLICE,
            MapLocation.Distance.FAR,
            MapLocation.Risk.VERY_HIGH,
            .72f,
            .44f,
            new LootTable()
                .range(ExpeditionLoot.Resource.MEDICINE, 5, 12)
                .range(ExpeditionLoot.Resource.EQUIPMENT, 5, 12)));
    return result;
  }

  private static MapLocation point(
      String id,
      String name,
      String marker,
      MapLocation.Kind kind,
      MapLocation.Distance distance,
      MapLocation.Risk risk,
      float x,
      float y,
      LootTable table) {
    return new MapLocation(
        id,
        name,
        marker,
        "Запасы района",
        kind,
        distance,
        risk,
        x,
        y,
        MapLocation.State.LOCKED,
        table);
  }

  static District owner(String pointId) {
    for (District d : DISTRICTS) if (d.points.contains(pointId)) return d;
    return null;
  }

  static MapLocation.State initialLocationState(MapLocation location) {
    return location.kind == MapLocation.Kind.STORY
            || location.kind == MapLocation.Kind.DISTRICT
            || owner(location.id) != null
        ? MapLocation.State.LOCKED
        : MapLocation.State.AVAILABLE;
  }
}
