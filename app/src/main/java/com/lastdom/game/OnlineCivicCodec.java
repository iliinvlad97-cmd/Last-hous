package com.lastdom.game;

import java.io.*;
import java.util.*;

/** Bounded pure codec; no schedule, random rolls or completion effects during loading. */
final class OnlineCivicCodec {
  static String encode(OnlineCivicState c) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(1);
      out.writeBoolean(c.used);
      out.writeLong(c.minute);
      out.writeLong(c.nextSpawn);
      out.writeLong(c.lastDebug);
      out.writeInt(c.nextEvent);
      out.writeBoolean(c.alliance != null);
      if (c.alliance != null) {
        out.writeUTF(c.alliance.name);
        out.writeInt(c.alliance.members.size());
        for (OnlineAllianceMember m : c.alliance.members) {
          out.writeUTF(m.shelterId);
          out.writeUTF(m.invitationId);
          out.writeInt(m.invitation.ordinal());
          out.writeLong(m.resolveMinute);
          out.writeBoolean(m.willAccept);
          out.writeInt(m.reputation);
          out.writeInt(m.contribution);
          out.writeInt(m.successes);
        }
      }
      out.writeInt(c.events.size());
      for (OnlineCityEvent e : c.events) {
        out.writeUTF(e.id);
        out.writeInt(e.type.ordinal());
        out.writeLong(e.appearedMinute);
        out.writeLong(e.expiresMinute);
        out.writeInt(e.state.ordinal());
        out.writeUTF(e.operationId);
      }
      out.writeInt(c.operations.size());
      for (OnlineCoopOperation op : c.operations) {
        out.writeUTF(op.id);
        out.writeUTF(op.eventId);
        out.writeInt(op.allies.size());
        for (String id : op.allies) out.writeUTF(id);
        out.writeInt(op.state.ordinal());
        out.writeBoolean(op.report.applied);
        out.writeInt(op.report.shares.size());
        for (Map.Entry<String, Integer> share : op.report.shares.entrySet()) {
          out.writeUTF(share.getKey());
          out.writeInt(share.getValue());
        }
      }
      out.flush();
      StringBuilder hex = new StringBuilder(bytes.size() * 2);
      char[] digits = "0123456789abcdef".toCharArray();
      for (byte b : bytes.toByteArray()) {
        hex.append(digits[(b & 255) >>> 4]);
        hex.append(digits[b & 15]);
      }
      return hex.toString();
    } catch (IOException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  static OnlineCivicState decode(String hex) {
    if (hex.length() % 2 != 0 || hex.length() > 1_000_000)
      throw new IllegalArgumentException("Invalid civic size");
    byte[] bytes = new byte[hex.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      int a = Character.digit(hex.charAt(i * 2), 16),
          b = Character.digit(hex.charAt(i * 2 + 1), 16);
      if (a < 0 || b < 0) throw new IllegalArgumentException("Invalid civic encoding");
      bytes[i] = (byte) (a * 16 + b);
    }
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      if (in.readInt() != 1) throw new IllegalArgumentException("Unknown civic rules");
      boolean used = in.readBoolean();
      long minute = in.readLong(), nextSpawn = in.readLong(), lastDebug = in.readLong();
      int nextEvent = in.readInt();
      OnlineAlliance alliance = null;
      if (in.readBoolean()) {
        String name = text(in);
        List<OnlineAllianceMember> members = new ArrayList<>();
        for (int n = count(in, 5); n > 0; n--)
          members.add(
              new OnlineAllianceMember(
                  text(in),
                  text(in),
                  value(OnlineAllianceMember.Invitation.values(), in.readInt()),
                  in.readLong(),
                  in.readBoolean(),
                  in.readInt(),
                  in.readInt(),
                  in.readInt()));
        alliance = new OnlineAlliance(name, members);
      }
      List<OnlineCityEvent> events = new ArrayList<>();
      for (int n = count(in, OnlineCityEvent.MAX_EVENTS); n > 0; n--)
        events.add(
            new OnlineCityEvent(
                text(in),
                value(OnlineCityEvent.Type.values(), in.readInt()),
                in.readLong(),
                in.readLong(),
                value(OnlineCityEvent.State.values(), in.readInt()),
                text(in)));
      List<OnlineCoopOperation> ops = new ArrayList<>();
      for (int n = count(in, OnlineCombatRules.MAX_HISTORY); n > 0; n--) {
        String id = text(in), event = text(in);
        List<String> allies = new ArrayList<>();
        for (int k = count(in, 2); k > 0; k--) allies.add(text(in));
        OnlineCoopOperation.State state = value(OnlineCoopOperation.State.values(), in.readInt());
        boolean applied = in.readBoolean();
        Map<String, Integer> shares = new LinkedHashMap<>();
        for (int k = count(in, 3); k > 0; k--)
          if (shares.put(text(in), in.readInt()) != null)
            throw new IllegalArgumentException("Duplicate contribution");
        ops.add(
            new OnlineCoopOperation(
                id, event, allies, state, new OnlineOperationReport(shares, applied)));
      }
      if (in.available() != 0) throw new IllegalArgumentException("Trailing civic bytes");
      return new OnlineCivicState(
          used, minute, nextSpawn, lastDebug, nextEvent, alliance, events, ops);
    } catch (IOException invalid) {
      throw new IllegalArgumentException("Truncated civic save", invalid);
    }
  }

  private static int count(DataInputStream in, int max) throws IOException {
    int n = in.readInt();
    if (n < 0 || n > max) throw new IllegalArgumentException("Invalid civic count");
    return n;
  }

  private static String text(DataInputStream in) throws IOException {
    String s = in.readUTF();
    if (s.length() > 120) throw new IllegalArgumentException("Invalid civic text");
    return s;
  }

  private static <T> T value(T[] a, int i) {
    if (i < 0 || i >= a.length) throw new IllegalArgumentException("Invalid civic enum");
    return a[i];
  }
}
