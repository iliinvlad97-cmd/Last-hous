package com.lastdom.game;

import java.util.EnumMap;

/** Quantities shared by found loot, carried cargo and the separate expedition warehouse. */
final class ExpeditionLoot {
  enum Resource {
    WATER("Вода"),
    FOOD("Еда"),
    MEDICINE("Медикаменты"),
    MATERIALS("Материалы"),
    EQUIPMENT("Снаряжение");
    final String label;

    Resource(String label) {
      this.label = label;
    }
  }

  private final EnumMap<Resource, Integer> amounts = new EnumMap<>(Resource.class);

  int get(Resource resource) {
    return amounts.getOrDefault(resource, 0);
  }

  void set(Resource resource, int amount) {
    amounts.put(resource, Math.max(0, amount));
  }

  void add(Resource resource, int amount) {
    set(resource, (int) Math.min(Integer.MAX_VALUE, (long) get(resource) + Math.max(0, amount)));
  }

  int total() {
    long total = 0;
    for (Resource resource : Resource.values()) total += get(resource);
    return (int) Math.min(Integer.MAX_VALUE, total);
  }

  static int weight(Resource resource) {
    return 1;
  }

  ExpeditionLoot cargo(int capacity) {
    ExpeditionLoot cargo = new ExpeditionLoot();
    int remaining = Math.max(0, capacity);
    for (Resource resource : Resource.values()) {
      int quantity = Math.min(get(resource), remaining / weight(resource));
      cargo.set(resource, quantity);
      remaining -= quantity * weight(resource);
    }
    return cargo;
  }
}
