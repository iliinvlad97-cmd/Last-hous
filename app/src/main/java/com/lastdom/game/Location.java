package com.lastdom.game;

/** Existing city location and its persisted exploration data. */
final class Location {
  String name, type;
  int distance, risk, stock;
  boolean discovered;

  Location(String name, String type, int distance, int risk, int stock, boolean discovered) {
    this.name = name;
    this.type = type;
    this.distance = distance;
    this.risk = risk;
    this.stock = stock;
    this.discovered = discovered;
  }
}
