package com.lastdom.game;

final class StoryEvent {
  final String id, source, text;

  StoryEvent(String id, String source, String text) {
    this.id = id;
    this.source = source;
    this.text = text;
  }
}
