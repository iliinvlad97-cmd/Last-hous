package com.lastdom.game;

import android.content.SharedPreferences;
import java.util.*;

/** Additive fields under the existing story prefix, committed with resources and expeditions. */
final class StoryFactionRepository {
  static void save(StoryFactionState s, SharedPreferences.Editor e) {
    String p = StoryRepository.P + "faction_";
    e.putBoolean(p + "started", s.started).putString(p + "side", s.side);
    StoryRepository.saveMap(e, "factionReputation", s.reputation);
    StoryRepository.put(e, "factionContacts", s.contacts);
    StoryRepository.put(e, "factionCompleted", s.completed);
    StoryRepository.put(e, "factionApplied", s.applied);
    StoryRepository.put(e, "factionSupplied", s.supplied);
    e.putInt(p + "historyCount", s.history.size());
    for (int i = 0; i < s.history.size(); i++) e.putString(p + "history" + i, s.history.get(i));
  }

  static void load(StoryFactionState s, SharedPreferences p) {
    s.reset();
    String k = StoryRepository.P + "faction_";
    s.started = p.getBoolean(k + "started", false);
    s.side = p.getString(k + "side", "");
    Map<String, String> raw = new LinkedHashMap<>();
    StoryRepository.loadStrings(p, "factionReputation", raw);
    for (Map.Entry<String, String> a : raw.entrySet())
      try {
        if (StoryFactionConfig.faction(a.getKey()) != null)
          s.reputation.put(a.getKey(), StoryFactionConfig.clamp(Integer.parseInt(a.getValue())));
      } catch (NumberFormatException ignored) {
      }
    StoryRepository.get(p, "factionContacts", s.contacts);
    StoryRepository.get(p, "factionCompleted", s.completed);
    StoryRepository.get(p, "factionApplied", s.applied);
    StoryRepository.get(p, "factionSupplied", s.supplied);
    int n = Math.max(0, Math.min(10000, p.getInt(k + "historyCount", 0)));
    for (int i = 0; i < n; i++) s.history.add(p.getString(k + "history" + i, ""));
  }

  private StoryFactionRepository() {}
}
