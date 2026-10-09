package com.lastdom.game;

/** Stable keys, reserved endings do not stop the survival loop. */
final class StoryFlags {
  static final String SIGNAL = "last_signal.received",
      CARRIER = "last_signal.data_carrier",
      COORDINATES = "last_signal.coordinates",
      SHARE = "last_signal.share",
      SECRET = "last_signal.secret";

  enum Ending {
    NONE,
    NEW_DAWN,
    LAST_BASTION,
    EXODUS
  }

  private StoryFlags() {}
}
