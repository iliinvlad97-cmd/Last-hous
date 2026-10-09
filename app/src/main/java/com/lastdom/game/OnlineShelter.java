package com.lastdom.game;

/** A virtual demo site, never a Resident or a connected player's shelter. */
final class OnlineShelter {
  final String id, name, linkDescription, description;
  final int level;
  final OnlineWorldGeometry.Point position;

  OnlineShelter(
      String id,
      String name,
      int level,
      String linkDescription,
      String description,
      float x,
      float y) {
    if (id == null || id.isEmpty() || name == null || level < 1)
      throw new IllegalArgumentException("Invalid shelter");
    this.id = id;
    this.name = name;
    this.level = level;
    this.linkDescription = linkDescription;
    this.description = description;
    position = new OnlineWorldGeometry.Point(x, y);
  }
}
