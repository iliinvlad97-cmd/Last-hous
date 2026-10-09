package com.lastdom.game;

import android.content.SharedPreferences;
import java.util.*;

/** Additive versioned keys in the same atomic save_v02 commit as expedition delivery. */
final class StoryRepository {
  static final String P = "story1_";

  static void save(StoryState s, SharedPreferences.Editor e) {
    e.putInt(P + "schema", 1)
        .putString(P + "phase", s.phase.name())
        .putString(P + "chapter", s.chapterId)
        .putString(P + "quest", s.questId)
        .putString(P + "specialist", s.specialistId)
        .putString(P + "previousJob", s.previousJob)
        .putString(P + "pending", s.pendingMessage)
        .putString(P + "decision", s.decisionId)
        .putString(P + "ending", s.ending.name())
        .putInt(P + "elapsed", s.decodeElapsed)
        .putString(P + "start", Long.toString(s.decodeStart));
    put(e, "flags", s.flags);
    put(e, "objectives", s.objectives);
    put(e, "items", s.items);
    put(e, "returns", s.processedReturns);
    put(e, "read", s.readMessages);
    put(e, "messages", s.messages);
    e.putInt(P + "attempts", s.attemptResults.size());
    int i = 0;
    for (Map.Entry<String, Boolean> a : s.attemptResults.entrySet()) {
      e.putString(P + "attempt_" + i, a.getKey()).putBoolean(P + "success_" + i, a.getValue());
      i++;
    }
  }

  static void put(SharedPreferences.Editor e, String k, Collection<String> v) {
    e.putString(P + k, android.text.TextUtils.join(",", v));
  }

  static void get(SharedPreferences p, String k, Collection<String> v) {
    String raw = p.getString(P + k, "");
    if (!raw.isEmpty())
      for (String id : raw.split(",")) if (!id.isEmpty() && !v.contains(id)) v.add(id);
  }

  static void load(StoryState s, SharedPreferences p) {
    s.reset();
    if (!p.contains(P + "schema")) return;
    try {
      s.phase = StoryState.Phase.valueOf(p.getString(P + "phase", "DORMANT"));
      s.ending = StoryFlags.Ending.valueOf(p.getString(P + "ending", "NONE"));
    } catch (IllegalArgumentException bad) {
      s.reset();
      return;
    }
    s.chapterId = p.getString(P + "chapter", "chapter.last_signal");
    s.questId = p.getString(P + "quest", "");
    s.specialistId = p.getString(P + "specialist", "");
    s.previousJob = p.getString(P + "previousJob", "Отдых");
    s.pendingMessage = p.getString(P + "pending", "");
    s.decisionId = p.getString(P + "decision", "");
    s.decodeElapsed = Math.max(0, Math.min(StoryConfig.DECODE_MINUTES, p.getInt(P + "elapsed", 0)));
    try {
      s.decodeStart = Math.max(0, Long.parseLong(p.getString(P + "start", "0")));
    } catch (NumberFormatException ignored) {
      s.decodeStart = 0;
    }
    get(p, "flags", s.flags);
    get(p, "objectives", s.objectives);
    get(p, "items", s.items);
    get(p, "returns", s.processedReturns);
    get(p, "read", s.readMessages);
    get(p, "messages", s.messages);
    s.messages.removeIf(id -> StoryConfig.event(id) == null);
    if (StoryConfig.event(s.pendingMessage) == null) s.pendingMessage = "";
    int n = Math.max(0, Math.min(10000, p.getInt(P + "attempts", 0)));
    for (int i = 0; i < n; i++) {
      String id = p.getString(P + "attempt_" + i, "");
      if (!id.isEmpty()) s.attemptResults.putIfAbsent(id, p.getBoolean(P + "success_" + i, false));
    }
  }

  private StoryRepository() {}
}
