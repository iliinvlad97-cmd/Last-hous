package com.lastdom.game;

/** Independent radio-world regions; no connection to single-player district unlocks. */
final class OnlineZone {
  enum Type {
    SAFE,
    PVE,
    PVP
  }

  final String id, name, danger, description;
  final Type type;
  final OnlineWorldGeometry.Shape boundary;
  final OnlineWorldGeometry.Point labelPosition;

  OnlineZone(
      String id,
      String name,
      Type type,
      String danger,
      String description,
      float labelX,
      float labelY,
      float... polygon) {
    if (id == null || id.isEmpty() || name == null || type == null || polygon.length < 6)
      throw new IllegalArgumentException("Invalid zone");
    this.id = id;
    this.name = name;
    this.type = type;
    this.danger = danger;
    this.description = description;
    labelPosition = new OnlineWorldGeometry.Point(labelX, labelY);
    boundary = new OnlineWorldGeometry.Shape(polygon);
  }
}
