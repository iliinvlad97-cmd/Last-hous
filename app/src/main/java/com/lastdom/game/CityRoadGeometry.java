package com.lastdom.game;

/** Shared city artwork and road graph. Original five streets/building coordinates are retained. */
final class CityRoadGeometry {
  static final float[][] BLOCKS = {
    {.04f, .03f, .10f, .12f}, {.43f, .03f, .12f, .10f}, {.86f, .03f, .10f, .14f},
    {.08f, .20f, .12f, .09f}, {.34f, .19f, .11f, .10f}, {.60f, .20f, .13f, .10f},
    {.82f, .23f, .12f, .10f}, {.04f, .33f, .10f, .14f}, {.32f, .34f, .09f, .13f},
    {.55f, .34f, .10f, .12f}, {.86f, .39f, .10f, .11f}, {.05f, .51f, .11f, .10f},
    {.25f, .50f, .11f, .09f}, {.52f, .50f, .12f, .09f}, {.76f, .51f, .16f, .08f},
    {.04f, .69f, .10f, .11f}, {.33f, .69f, .09f, .09f}, {.76f, .70f, .10f, .12f},
    {.18f, .84f, .13f, .10f}, {.66f, .86f, .13f, .08f}, {.86f, .86f, .10f, .08f}
  };
  static final float[][] ROADS = {
    {.50f, .94f, .50f, .77f, .47f, .64f, .43f, .51f, .48f, .32f, .52f, .02f},
    {.02f, .65f, .22f, .61f, .48f, .66f, .71f, .61f, .98f, .66f},
    {.02f, .39f, .22f, .43f, .47f, .40f, .74f, .37f, .98f, .33f},
    {.24f, .02f, .26f, .18f, .24f, .31f, .20f, .43f, .22f, .61f, .15f, .81f},
    {.76f, .02f, .74f, .18f, .79f, .33f, .72f, .48f, .70f, .62f, .76f, .82f},
    // Narrow perimeter access lane joins otherwise disconnected ends of the original streets.
    // It occupies existing empty margins; buildings and destinations are not moved.
    {.50f, .835f, .02f, .835f, .02f, .015f, .98f, .015f, .98f, .835f, .50f, .835f}
  };
  static final float[] WIDTHS = {38, 24, 23, 17, 17, 12};

  private CityRoadGeometry() {}
}
