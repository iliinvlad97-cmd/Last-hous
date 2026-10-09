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
    String legacy = encodeLegacy(data);
    if (!data.combat.used) return legacy;
    String body = "2" + legacy.substring(1) + "|" + OnlineCombatCodec.encode(data.combat);
    return body + "|" + checksum(body);
  }

  private static String checksum(String body) {
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return Long.toHexString(crc.getValue());
  }

  private static String encodeLegacy(OnlineWorldGameplay.Data data) {
    StringBuilder text = new StringBuilder("1|");
    for (OnlineInventory.Resource resource : OnlineInventory.Resource.values())
      text.append(data.inventory.amount(resource)).append(',');
    text.append('|').append(data.reputation).append('|').append(data.pvpZoneId).append('|');
    for (OnlineWorldGameplay.Operation operation : data.operations)
      text.append(operation.offer.id).append(',').append(operation.elapsedSeconds).append(';');
    return text.toString();
  }

  private static OnlineWorldGameplay.Data decodeCombat(
      String text, MockOnlineWorldRepository repository) {
    int end = text.lastIndexOf('|');
    if (end < 0 || !checksum(text.substring(0, end)).equals(text.substring(end + 1)))
      throw new IllegalArgumentException("Combat save checksum mismatch");
    String[] parts = text.split("\\|", -1);
    if (parts.length != 7) throw new IllegalArgumentException("Unknown combat save");
    String[] amounts = parts[1].split(",", -1);
    if (amounts.length != OnlineInventory.Resource.values().length + 1)
      throw new IllegalArgumentException("Invalid balance count");
    EnumMap<OnlineInventory.Resource, Integer> balances =
        new EnumMap<>(OnlineInventory.Resource.class);
    for (OnlineInventory.Resource resource : OnlineInventory.Resource.values())
      balances.put(resource, Integer.parseInt(amounts[resource.ordinal()]));
    OnlineWorldGameplay.Data initial = OnlineWorldGameplay.Data.initial();
    List<OnlineWorldGameplay.Operation> operations = new ArrayList<>();
    int reputation = 0;
    if (!parts[4].isEmpty())
      for (String entry : parts[4].split(";")) {
        String[] fields = entry.split(",", -1);
        if (fields.length != 2) throw new IllegalArgumentException("Invalid saved operation");
        OnlineWorldGameplay.Offer offer = initial.offer(fields[0]);
        if (offer == null) throw new IllegalArgumentException("Unknown operation");
        reputation += offer.reputation;
        operations.add(repository.operation(offer, Integer.parseInt(fields[1])));
      }
    if (reputation != Integer.parseInt(parts[2])
        || (!parts[3].isEmpty() && !parts[3].equals("pvp_frontier")))
      throw new IllegalArgumentException("Invalid demo reputation/consent");
    OnlineBattleRepository.State combat = OnlineCombatCodec.decode(parts[5]);
    OnlineWorldGameplay.Data restored =
        new OnlineWorldGameplay.Data(
            new OnlineInventory(balances),
            initial.offers,
            operations,
            reputation,
            parts[3],
            combat);
    if (!combat.used || !encode(restored).equals(text))
      throw new IllegalArgumentException("Noncanonical combat save");
    return restored;
  }

  /** Replay the finite ledger to validate balances, reputation and exactly-once IDs together. */
  static OnlineWorldGameplay.Data decode(String text, MockOnlineWorldRepository repository) {
    if (text.isEmpty()) return OnlineWorldGameplay.Data.initial();
    if (text.startsWith("2|")) return decodeCombat(text, repository);
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
    if (!encodeLegacy(restored).equals(text))
      throw new IllegalArgumentException("Inconsistent demo save");
    return restored;
  }
}
