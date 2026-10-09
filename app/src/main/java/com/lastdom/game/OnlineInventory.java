package com.lastdom.game;

import java.util.*;

/** Separate demo balances: never holds references to GameState or the expedition warehouse. */
final class OnlineInventory {
  enum Resource {
    FOOD("Еда"),
    WATER("Вода"),
    MATERIALS("Материалы"),
    MEDICINE("Медикаменты"),
    EQUIPMENT("Снаряжение");
    final String label;

    Resource(String label) {
      this.label = label;
    }
  }

  static final int LIMIT = 1_000_000;
  private final EnumMap<Resource, Integer> balances;

  OnlineInventory(Map<Resource, Integer> source) {
    balances = new EnumMap<>(Resource.class);
    for (Resource resource : Resource.values()) {
      int value = source.getOrDefault(resource, 0);
      if (value < 0 || value > LIMIT) throw new IllegalArgumentException("Invalid demo balance");
      balances.put(resource, value);
    }
  }

  static OnlineInventory initial() {
    EnumMap<Resource, Integer> values = new EnumMap<>(Resource.class);
    int[] amounts = {30, 30, 25, 10, 5};
    for (Resource resource : Resource.values()) values.put(resource, amounts[resource.ordinal()]);
    return new OnlineInventory(values);
  }

  int amount(Resource resource) {
    return balances.get(resource);
  }

  OnlineInventory credit(OnlineInventory reward) {
    EnumMap<Resource, Integer> values = new EnumMap<>(balances);
    for (Resource resource : Resource.values())
      values.put(resource, Math.addExact(amount(resource), reward.amount(resource)));
    return new OnlineInventory(values);
  }

  OnlineInventory exchange(Resource cost, int quantity, Resource reward, int output) {
    if (quantity <= 0 || output < 0 || amount(cost) < quantity)
      throw new IllegalArgumentException("Insufficient demo resources");
    EnumMap<Resource, Integer> values = new EnumMap<>(balances);
    values.put(cost, values.get(cost) - quantity);
    if (reward != null) values.put(reward, Math.addExact(values.get(reward), output));
    return new OnlineInventory(values);
  }
}
