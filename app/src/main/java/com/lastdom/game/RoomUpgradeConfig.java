package com.lastdom.game;

/** Existing room indices are retained: barricades 4, bedroom 5. All Stage 5 balance lives here. */
final class RoomUpgradeConfig {
  static final int MAX_LEVEL = 3, MAX_ACTIVE = 1, BASE_ENERGY_PER_DAY = 2;
  private static final int[][] COST = {{20, 45}, {15, 35}, {20, 40}, {25, 50}, {30, 60}, {15, 30}};
  private static final int[][] MINUTES = {
    {120, 240}, {90, 180}, {120, 210}, {150, 270}, {180, 300}, {90, 180}
  };
  private static final int[] BONUS_PERCENT = {25, 20, 25, 20, 20, 25};

  static boolean valid(int room) {
    return room >= 0 && room < COST.length;
  }

  static int cost(int room, int target) {
    return COST[room][target - 2];
  }

  static int minutes(int room, int target) {
    return MINUTES[room][target - 2];
  }

  static int percent(int room, int level) {
    return 100 + (Math.max(1, Math.min(MAX_LEVEL, level)) - 1) * BONUS_PERCENT[room];
  }

  static String effect(int room) {
    switch (room) {
      case 0:
        return "Выработка энергии";
      case 1:
        return "Производство еды";
      case 2:
        return "Скорость лечения";
      case 3:
        return "Производство материалов";
      case 4:
        return "Защита от угроз";
      default:
        return "Восстановление усталости";
    }
  }
}
