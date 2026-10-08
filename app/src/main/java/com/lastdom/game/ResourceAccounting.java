package com.lastdom.game;

import android.content.SharedPreferences;
import java.util.Arrays;

/** Read-only observer of real transactions; never credits, charges or advances the simulation. */
final class ResourceAccounting {
  static final int ENERGY = 0, FOOD = 1, WATER = 2, MATERIALS = 3, MEDICINE = 4;
  static final String[] NAMES = {"Энергия", "Еда", "Вода", "Материалы", "Медикаменты"};
  private static final long LIMIT = 1L << 50;
  private static final String KEY = "room_eff8_";
  int day, startMinute, previousDay, previousStart;
  final long[] produced = new long[5], used = new long[5], other = new long[5];
  final long[] previousProduced = new long[5],
      previousUsed = new long[5],
      previousOther = new long[5];
  private long[] balances = new long[5], counters = new long[6];

  void reset() {
    day = startMinute = previousDay = previousStart = 0;
    for (long[] values :
        new long[][] {
          produced, used, other, previousProduced, previousUsed, previousOther, balances, counters
        }) Arrays.fill(values, 0);
  }

  private static long[] stock(GameController g) {
    return new long[] {
      g.power, g.food, g.water, g.mats, g.expeditionWarehouse.get(ExpeditionLoot.Resource.MEDICINE)
    };
  }

  private static long[] totals(GameController g) {
    ProductionState p = g.production;
    return new long[] {
      p.energyProduced,
      p.foodRations,
      p.waterProduced,
      p.waterRations,
      p.energyUsed,
      p.medicineDoses
    };
  }

  private void initialize(GameController g) {
    reset();
    day = g.day;
    startMinute = g.gameMinute;
    balances = stock(g);
    counters = totals(g);
  }

  private static long add(long a, long b) {
    return Math.max(-LIMIT, Math.min(LIMIT, a + b));
  }

  void observe(GameController g) {
    if (day == 0 || g.day < day) {
      initialize(g);
      return;
    }
    long[] nextStock = stock(g), nextTotals = totals(g);
    for (int i = 0; i < counters.length; i++)
      if (nextTotals[i] < counters[i]) {
        initialize(g);
        return;
      }
    if (g.day != day) {
      previousDay = day;
      previousStart = startMinute;
      System.arraycopy(produced, 0, previousProduced, 0, 5);
      System.arraycopy(used, 0, previousUsed, 0, 5);
      System.arraycopy(other, 0, previousOther, 0, 5);
      Arrays.fill(produced, 0);
      Arrays.fill(used, 0);
      Arrays.fill(other, 0);
      day = g.day;
      startMinute = 0;
    }
    long[] incoming = {nextTotals[0] - counters[0], 0, nextTotals[2] - counters[2], 0, 0};
    long[] outgoing = {
      nextTotals[4] - counters[4],
      nextTotals[1] - counters[1],
      nextTotals[3] - counters[3],
      0,
      nextTotals[5] - counters[5]
    };
    for (int i = 0; i < 5; i++) {
      produced[i] = add(produced[i], incoming[i]);
      used[i] = add(used[i], outgoing[i]);
      other[i] = add(other[i], nextStock[i] - balances[i] - incoming[i] + outgoing[i]);
    }
    balances = nextStock;
    counters = nextTotals;
  }

  long change(int resource) {
    return produced[resource] - used[resource] + other[resource];
  }

  void save(SharedPreferences.Editor e) {
    e.putInt(KEY + "schema", 1)
        .putInt(KEY + "day", day)
        .putInt(KEY + "start", startMinute)
        .putInt(KEY + "previousDay", previousDay)
        .putInt(KEY + "previousStart", previousStart);
    put(e, "stock", balances);
    put(e, "totals", counters);
    put(e, "produced", produced);
    put(e, "used", used);
    put(e, "other", other);
    put(e, "previousProduced", previousProduced);
    put(e, "previousUsed", previousUsed);
    put(e, "previousOther", previousOther);
  }

  private static void put(SharedPreferences.Editor e, String name, long[] values) {
    StringBuilder s = new StringBuilder();
    for (long v : values) {
      if (s.length() > 0) s.append(',');
      s.append(v);
    }
    e.putString(KEY + name, s.toString());
  }

  private static long[] read(SharedPreferences sp, String name, int count, boolean signed) {
    String[] parts = sp.getString(KEY + name, "").split(",", -1);
    if (parts.length != count) throw new IllegalArgumentException();
    long[] result = new long[count];
    for (int i = 0; i < count; i++) {
      result[i] = Long.parseLong(parts[i]);
      if (result[i] < (signed ? -LIMIT : 0) || (!name.equals("totals") && result[i] > LIMIT))
        throw new IllegalArgumentException();
    }
    return result;
  }

  void load(GameController g, SharedPreferences sp) {
    initialize(g);
    if (!sp.contains(KEY + "schema")) return;
    try {
      if (sp.getInt(KEY + "day", 0) != g.day) return;
      long[] stock = read(sp, "stock", 5, false), totals = read(sp, "totals", 6, false);
      if (!Arrays.equals(stock, stock(g)) || !Arrays.equals(totals, totals(g))) return;
      long[] p = read(sp, "produced", 5, false),
          u = read(sp, "used", 5, false),
          o = read(sp, "other", 5, true);
      long[] pp = read(sp, "previousProduced", 5, false),
          pu = read(sp, "previousUsed", 5, false),
          po = read(sp, "previousOther", 5, true);
      startMinute = Math.max(0, Math.min(1439, sp.getInt(KEY + "start", g.gameMinute)));
      previousDay = Math.max(0, Math.min(g.day - 1, sp.getInt(KEY + "previousDay", 0)));
      previousStart = Math.max(0, Math.min(1439, sp.getInt(KEY + "previousStart", 0)));
      System.arraycopy(p, 0, produced, 0, 5);
      System.arraycopy(u, 0, used, 0, 5);
      System.arraycopy(o, 0, other, 0, 5);
      System.arraycopy(pp, 0, previousProduced, 0, 5);
      System.arraycopy(pu, 0, previousUsed, 0, 5);
      System.arraycopy(po, 0, previousOther, 0, 5);
      balances = stock;
      counters = totals;
    } catch (IllegalArgumentException e) {
      initialize(g);
    }
  }
}
