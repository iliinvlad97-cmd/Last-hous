package com.lastdom.game;

import android.graphics.Canvas;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.List;

/** Shared wrapped body viewport; buttons stay outside it and remain reachable during scrolling. */
final class MapPanelContent {
  static final float LINE_HEIGHT = 18;

  static int visibleLines(CityMapLayout layout) {
    return visibleLines(layout, 84);
  }

  static int visibleLines(CityMapLayout layout, float footer) {
    return Math.max(
        1, (int) ((layout.panelBottom - footer - (layout.panelTop + 93)) / LINE_HEIGHT));
  }

  static void draw(GameView view, Canvas canvas, CityMapLayout layout, List<String> lines) {
    draw(view, canvas, layout, lines, 84);
  }

  static void draw(
      GameView view, Canvas canvas, CityMapLayout layout, List<String> lines, float footer) {
    List<String> wrapped = new ArrayList<>();
    view.p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
    view.p.setTextSize(view.sy(12));
    for (String row : lines) {
      String line = "";
      for (String word : row.split(" ")) {
        if (!line.isEmpty() && view.p.measureText(line + " " + word) > view.sy(350)) {
          wrapped.add(line);
          line = "";
        }
        line += (line.isEmpty() ? "" : " ") + word;
      }
      wrapped.add(line);
    }
    view.cityMap.panelLineCount = wrapped.size();
    int visible = visibleLines(layout, footer);
    int scroll =
        Math.max(0, Math.min(view.cityMap.panelScroll, Math.max(0, wrapped.size() - visible)));
    for (int i = 0; i < visible && scroll + i < wrapped.size(); i++)
      view.txt(
          canvas,
          wrapped.get(scroll + i),
          34,
          layout.panelTop + 93 + i * LINE_HEIGHT,
          12,
          view.text);
    if (wrapped.size() > visible)
      view.txt(
          canvas,
          "↑ ↓ Прокрутите панель • "
              + (scroll + 1)
              + "–"
              + Math.min(scroll + visible, wrapped.size())
              + " / "
              + wrapped.size(),
          34,
          layout.panelBottom - footer + 9,
          9,
          view.muted);
  }
}
