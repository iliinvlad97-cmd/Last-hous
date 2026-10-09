package com.lastdom.game;

import java.util.EnumMap;

/** Location balance: base ranges never include squad bonuses or depletion. */
final class LootTable {
  private final EnumMap<ExpeditionLoot.Resource, int[]> ranges =
      new EnumMap<>(ExpeditionLoot.Resource.class);

  LootTable range(ExpeditionLoot.Resource resource, int min, int max) {
    ranges.put(resource, new int[] {min, max});
    return this;
  }

  int min(ExpeditionLoot.Resource resource) {
    return ranges.containsKey(resource) ? ranges.get(resource)[0] : 0;
  }

  int max(ExpeditionLoot.Resource resource) {
    return ranges.containsKey(resource) ? ranges.get(resource)[1] : 0;
  }

  static LootTable forKind(MapLocation.Kind kind) {
    LootTable table = new LootTable();
    switch (kind) {
      case DISTRICT:
      case STORY:
        return table;
      case STORE:
        return table
            .range(ExpeditionLoot.Resource.FOOD, 8, 20)
            .range(ExpeditionLoot.Resource.WATER, 4, 12)
            .range(ExpeditionLoot.Resource.MATERIALS, 0, 5);
      case PHARMACY:
        return table.range(ExpeditionLoot.Resource.MEDICINE, 5, 15);
      case GARAGE:
        return table.range(ExpeditionLoot.Resource.MATERIALS, 10, 25);
      case POLICE:
        return table.range(ExpeditionLoot.Resource.EQUIPMENT, 3, 10);
      case HOSPITAL:
        return table.range(ExpeditionLoot.Resource.MEDICINE, 15, 30);
      default:
        return table.range(ExpeditionLoot.Resource.WATER, 20, 40);
    }
  }
}
