package com.lastdom.game;

import android.content.SharedPreferences;
import java.util.*;

/** One atomic value in online_demo_v02. Never reads or edits save_v02. */
final class OnlineDemoSaveStore {
  static final String FILE = "online_demo_v02", KEY = "state";
  private final SharedPreferences preferences;

  OnlineDemoSaveStore(SharedPreferences preferences) {
    this.preferences = preferences;
  }

  String read() {
    return preferences == null ? "" : preferences.getString(KEY, "");
  }

  boolean write(OnlineWorldGameplay.Data data) {
    if (preferences == null) return true;
    return preferences.edit().putString(KEY, encode(data)).commit();
  }

  static String encode(OnlineWorldGameplay.Data data) {
    StringBuilder text = new StringBuilder("1|");
    for (OnlineInventory.Resource resource : OnlineInventory.Resource.values())
      text.append(data.inventory.amount(resource)).append(',');
    text.append('|').append(data.reputation).append('|').append(data.pvpZoneId).append('|');
    for (OnlineWorldGameplay.Operation operation : data.operations)
      text.append(operation.offer.id).append(',').append(operation.elapsedSeconds).append(';');
    return text.toString();
  }

  /** Replay the finite ledger to validate balances, reputation and exactly-once IDs together. */
  static OnlineWorldGameplay.Data decode(String text, MockOnlineWorldRepository repository) {
    if (text.isEmpty()) return OnlineWorldGameplay.Data.initial();
    String[] parts = text.split("\\|", -1);
    if (parts.length != 5 || !parts[0].equals("1"))
      throw new IllegalArgumentException("Unknown demo save");
    OnlineWorldGameplay.Data data = OnlineWorldGameplay.Data.initial();
    Set<String> seen = new HashSet<>();
    List<OnlineWorldGameplay.Operation> operations = new ArrayList<>();
    OnlineInventory inventory = data.inventory;
    int reputation = 0;
    if (!parts[4].isEmpty())
      for (String entry : parts[4].split(";")) {
        String[] fields = entry.split(",", -1);
        if (fields.length != 2 || !seen.add(fields[0]))
          throw new IllegalArgumentException("Duplicate demo operation");
        OnlineWorldGameplay.Offer offer = data.offer(fields[0]);
        if (offer == null) throw new IllegalArgumentException("Unknown demo operation");
        inventory = inventory.exchange(offer.cost, offer.quantity, offer.reward, offer.output);
        reputation += offer.reputation;
        operations.add(repository.operation(offer, Integer.parseInt(fields[1])));
      }
    String zone = parts[3];
    if (!zone.isEmpty() && !zone.equals("pvp_frontier"))
      throw new IllegalArgumentException("Unknown demo PvP zone");
    OnlineWorldGameplay.Data restored =
        new OnlineWorldGameplay.Data(inventory, data.offers, operations, reputation, zone);
    if (!encode(restored).equals(text))
      throw new IllegalArgumentException("Inconsistent demo save");
    return restored;
  }
}
