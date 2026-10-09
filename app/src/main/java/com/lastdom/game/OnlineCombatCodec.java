package com.lastdom.game;

import java.io.*;
import java.util.*;

/** Stored round events, snapshots and flags; decoding never calls a random generator or engine. */
final class OnlineCombatCodec {
  static String encode(OnlineBattleRepository.State state) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(OnlineCombatRules.VERSION);
      out.writeInt(state.nextId);
      out.writeBoolean(state.used);
      out.writeInt(state.fighters.size());
      for (OnlineCombatSquad.Fighter fighter : state.fighters) writeFighter(out, fighter);
      out.writeInt(state.battles.size());
      for (OnlineBattleRepository.Battle battle : state.battles) {
        out.writeUTF(battle.id);
        out.writeUTF(battle.zoneId);
        out.writeInt(battle.elapsedSeconds);
        out.writeBoolean(battle.effectsApplied);
        writeReport(out, battle.report);
      }
      out.writeInt(state.expeditions.size());
      for (OnlineCoopExpedition e : state.expeditions) {
        out.writeUTF(e.id);
        out.writeUTF(e.zoneId);
        out.writeUTF(e.allyId);
        out.writeLong(e.seed);
        out.writeInt(e.rulesVersion);
        out.writeInt(e.elapsedMinutes);
        out.writeBoolean(e.rewardApplied);
        writeSquad(out, e.own);
        writeSquad(out, e.ally);
        out.writeBoolean(e.success);
        out.writeInt(e.chance);
        out.writeInt(e.allyContribution);
        out.writeInt(e.staminaCost);
        for (OnlineInventory.Resource resource : OnlineInventory.Resource.values())
          out.writeInt(e.loot.amount(resource));
        writeLoss(out, e.healthLoss);
        writeLoss(out, e.allyHealthLoss);
      }
      out.flush();
      StringBuilder hex = new StringBuilder(bytes.size() * 2);
      char[] digits = "0123456789abcdef".toCharArray();
      for (byte value : bytes.toByteArray()) {
        hex.append(digits[(value & 255) >>> 4]);
        hex.append(digits[value & 15]);
      }
      return hex.toString();
    } catch (IOException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  static OnlineBattleRepository.State decode(String hex) {
    if (hex.length() % 2 != 0 || hex.length() > 2_000_000)
      throw new IllegalArgumentException("Invalid combat save size");
    byte[] bytes = new byte[hex.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      int a = Character.digit(hex.charAt(i * 2), 16),
          b = Character.digit(hex.charAt(i * 2 + 1), 16);
      if (a < 0 || b < 0) throw new IllegalArgumentException("Invalid combat encoding");
      bytes[i] = (byte) (a * 16 + b);
    }
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      if (in.readInt() != OnlineCombatRules.VERSION)
        throw new IllegalArgumentException("Unknown combat rules");
      int next = in.readInt();
      boolean used = in.readBoolean();
      List<OnlineCombatSquad.Fighter> fighters = new ArrayList<>();
      for (int n = count(in, 6); n > 0; n--) fighters.add(readFighter(in));
      List<OnlineBattleRepository.Battle> battles = new ArrayList<>();
      for (int n = count(in, OnlineCombatRules.MAX_HISTORY); n > 0; n--) {
        String id = string(in), zone = string(in);
        int elapsed = in.readInt();
        boolean applied = in.readBoolean();
        battles.add(new OnlineBattleRepository.Battle(id, zone, readReport(in), elapsed, applied));
      }
      List<OnlineCoopExpedition> expeditions = new ArrayList<>();
      for (int n = count(in, OnlineCombatRules.MAX_HISTORY); n > 0; n--) {
        String id = string(in), zone = string(in), allyId = string(in);
        long seed = in.readLong();
        if (in.readInt() != OnlineCombatRules.VERSION)
          throw new IllegalArgumentException("Unknown coop rules");
        int elapsed = in.readInt();
        boolean applied = in.readBoolean();
        OnlineCombatSquad own = readSquad(in), ally = readSquad(in);
        boolean success = in.readBoolean();
        int chance = in.readInt(), contribution = in.readInt(), stamina = in.readInt();
        EnumMap<OnlineInventory.Resource, Integer> loot =
            new EnumMap<>(OnlineInventory.Resource.class);
        for (OnlineInventory.Resource resource : OnlineInventory.Resource.values())
          loot.put(resource, in.readInt());
        List<Integer> losses = readLoss(in), allyLosses = readLoss(in);
        expeditions.add(
            new OnlineCoopExpedition(
                id,
                zone,
                allyId,
                seed,
                elapsed,
                own,
                ally,
                success,
                chance,
                contribution,
                stamina,
                new OnlineInventory(loot),
                losses,
                allyLosses,
                applied));
      }
      if (in.available() != 0) throw new IllegalArgumentException("Trailing combat data");
      return new OnlineBattleRepository.State(fighters, battles, expeditions, next, used);
    } catch (IOException error) {
      throw new IllegalArgumentException("Truncated combat save", error);
    }
  }

  private static int count(DataInputStream in, int max) throws IOException {
    int count = in.readInt();
    if (count < 0 || count > max) throw new IllegalArgumentException("Invalid save count");
    return count;
  }

  private static String string(DataInputStream in) throws IOException {
    String value = in.readUTF();
    if (value.isEmpty() || value.length() > 120)
      throw new IllegalArgumentException("Invalid saved ID/text");
    return value;
  }

  private static <T> T value(T[] values, int ordinal) {
    if (ordinal < 0 || ordinal >= values.length)
      throw new IllegalArgumentException("Invalid saved enum");
    return values[ordinal];
  }

  private static void writeFighter(DataOutputStream out, OnlineCombatSquad.Fighter f)
      throws IOException {
    out.writeUTF(f.id);
    out.writeUTF(f.name);
    out.writeUTF(f.role);
    out.writeInt(f.health);
    out.writeInt(f.stamina);
    out.writeInt(f.attack);
    out.writeInt(f.defense);
    out.writeInt(f.specialization.ordinal());
  }

  private static OnlineCombatSquad.Fighter readFighter(DataInputStream in) throws IOException {
    return new OnlineCombatSquad.Fighter(
        string(in),
        string(in),
        string(in),
        in.readInt(),
        in.readInt(),
        in.readInt(),
        in.readInt(),
        value(OnlineCombatSquad.Specialization.values(), in.readInt()));
  }

  private static void writeSquad(DataOutputStream out, OnlineCombatSquad squad) throws IOException {
    out.writeInt(squad.fighters.size());
    for (OnlineCombatSquad.Fighter f : squad.fighters) writeFighter(out, f);
  }

  private static OnlineCombatSquad readSquad(DataInputStream in) throws IOException {
    List<OnlineCombatSquad.Fighter> fighters = new ArrayList<>();
    for (int n = count(in, 3); n > 0; n--) fighters.add(readFighter(in));
    return new OnlineCombatSquad(fighters);
  }

  private static void writeLoss(DataOutputStream out, List<Integer> losses) throws IOException {
    out.writeInt(losses.size());
    for (int loss : losses) out.writeInt(loss);
  }

  private static List<Integer> readLoss(DataInputStream in) throws IOException {
    List<Integer> values = new ArrayList<>();
    for (int n = count(in, 3); n > 0; n--) values.add(in.readInt());
    return values;
  }

  private static void writeReport(DataOutputStream out, OnlineCombatReport report)
      throws IOException {
    out.writeInt(report.rulesVersion);
    out.writeLong(report.seed);
    writeSquad(out, report.own);
    writeSquad(out, report.enemy);
    out.writeInt(report.tactic.ordinal());
    out.writeInt(report.outcome.ordinal());
    out.writeInt(report.actions.size());
    for (OnlineCombatReport.Action action : report.actions) {
      out.writeInt(action.round);
      out.writeBoolean(action.playerAttack);
      out.writeInt(action.actor);
      out.writeInt(action.target);
      out.writeInt(action.attack);
      out.writeInt(action.blocked);
      out.writeInt(action.before);
      out.writeInt(action.after);
    }
  }

  private static OnlineCombatReport readReport(DataInputStream in) throws IOException {
    int version = in.readInt();
    long seed = in.readLong();
    OnlineCombatSquad own = readSquad(in), enemy = readSquad(in);
    OnlineCombatRules.Tactic tactic = value(OnlineCombatRules.Tactic.values(), in.readInt());
    OnlineCombatReport.Outcome outcome = value(OnlineCombatReport.Outcome.values(), in.readInt());
    List<OnlineCombatReport.Action> actions = new ArrayList<>();
    for (int n = count(in, OnlineCombatRules.MAX_ROUNDS * 6); n > 0; n--)
      actions.add(
          new OnlineCombatReport.Action(
              in.readInt(),
              in.readBoolean(),
              in.readInt(),
              in.readInt(),
              in.readInt(),
              in.readInt(),
              in.readInt(),
              in.readInt()));
    return new OnlineCombatReport(version, seed, own, enemy, tactic, outcome, actions);
  }

  private OnlineCombatCodec() {}
}
