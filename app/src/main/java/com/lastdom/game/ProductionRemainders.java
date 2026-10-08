package com.lastdom.game;

import java.util.HashMap;
import java.util.Map;

/** Hundredths retain fractional bonuses without floating point drift or rounding each cycle. */
final class ProductionRemainders {
  static final String[] GLOBAL = {"energy", "food", "materials", "guards", "defense"};
  final Map<String, Integer> values = new HashMap<>();

  int take(int base, int percent, String channel) {
    long hundredths = Math.max(0, (long) base) * percent + values.getOrDefault(channel, 0);
    values.put(channel, (int) (hundredths % 100));
    return (int) Math.min(Integer.MAX_VALUE, hundredths / 100);
  }

  void clear(String channel) {
    values.remove(channel);
  }
}
