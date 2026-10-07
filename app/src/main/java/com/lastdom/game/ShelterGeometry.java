package com.lastdom.game;

/** Artwork room hitboxes and resident ground anchors in the existing scene coordinates. */
final class ShelterGeometry {

  private ShelterGeometry() {}

  // Standing floor edges measured in shelter_full_scene.png (941 x 1370), not hitbox centres.
  // generator, kitchen, medpoint, workshop, surface, bedroom
  private static final float[] FLOOR_PIXEL_Y = {630f, 860f, 860f, 1088f, 390f, 1088f};
  private static final float[][] SLOT_X = {
    {.18f, .29f, .40f}, {.18f, .29f, .40f}, {.62f, .73f, .84f},
    {.62f, .73f, .84f}, {.28f, .50f, .72f}, {.17f, .29f, .41f}
  };

  static float[] fullSceneRoomRect(int ri, float top, float bottom) {
    float h = bottom - top;
    switch (ri) {
      case 0:
        return sceneRect(
            .055f, .330f, .515f, .480f, top, h); // generator: full visible upper-left room
      case 1:
        return sceneRect(.055f, .485f, .515f, .640f, top, h); // kitchen: full middle-left room
      case 2:
        return sceneRect(.515f, .485f, .930f, .640f, top, h); // medpoint: full middle-right room
      case 5:
        return sceneRect(.055f, .645f, .515f, .805f, top, h); // bedroom: full lower-left room
      case 3:
        return sceneRect(
            .515f, .645f, .930f, .805f, top, h); // workshop/storage: full lower-right room
      case 4:
        return sceneRect(.055f, .180f, .945f, .315f, top, h); // surface barricades
      default:
        return new float[] {-100, -100, -90, -90};
    }
  }

  static float[] sceneRect(float nx1, float ny1, float nx2, float ny2, float top, float h) {
    float left = 8f, width = 404f;
    return new float[] {left + width * nx1, top + h * ny1, left + width * nx2, top + h * ny2};
  }

  static int fullSceneRoomAt(float x, float y, float top, float bottom) {
    // v0.9.5.4: the artwork has an extra visible upper-right work room.
    // Treat it as part of the workshop so the whole visible room is interactive.
    float h = bottom - top;
    float[] upperRight = sceneRect(.515f, .330f, .930f, .480f, top, h);
    if (x >= upperRight[0] && x <= upperRight[2] && y >= upperRight[1] && y <= upperRight[3])
      return 3;
    // Explicit priority prevents a neighbouring room from stealing edge taps.
    int[] order = {0, 1, 2, 5, 3, 4};
    for (int ri : order) {
      float[] q = fullSceneRoomRect(ri, top, bottom);
      if (x >= q[0] && x <= q[2] && y >= q[1] && y <= q[3]) return ri;
    }
    return -1;
  }

  static float[] fullSceneResidentPos(int ri, int slot, float top, float bottom) {
    float h = bottom - top, left = 8f, width = 404f;
    int k = Math.max(0, Math.min(2, slot));
    return new float[] {left + width * SLOT_X[ri][k], top + h * FLOOR_PIXEL_Y[ri] / 1370f};
  }

  static float[] fullSceneResidentPos(int ri, int slot, int occupants, float top, float bottom) {
    float[] ground = fullSceneResidentPos(ri, slot, top, bottom);
    if (occupants > SLOT_X[ri].length) {
      // Larger groups share the same floor and X span instead of stacking on the third slot.
      float fraction = Math.max(0, Math.min(occupants - 1, slot)) / (float) (occupants - 1);
      ground[0] = 8f + 404f * (SLOT_X[ri][0] + (SLOT_X[ri][2] - SLOT_X[ri][0]) * fraction);
    }
    return ground;
  }

  static float[] shelterResidentPos(int ri, int slot) {
    float[][] base = {{162, 235}, {327, 235}, {163, 362}, {327, 362}, {162, 488}, {327, 488}};
    float dx = (slot % 3 - 1) * 18f;
    return new float[] {base[ri][0] + dx, base[ri][1]};
  }

  static int cleanRoomAt(float x, float y) {
    if (x < 12 || x > 408 || y < 150 || y > 535) return -1;
    int col = x < 210 ? 0 : 1;
    int row = y < 280 ? 0 : (y < 410 ? 1 : 2);
    return row * 2 + col;
  }

  static int roomAt(float x, float y, float top, boolean large) {
    float bx = large ? 42 : 55,
        by = top + (large ? 38 : 28),
        bw = large ? 336 : 310,
        rh = large ? 136 : 82;
    if (x < bx || x > bx + bw || y < by || y > by + rh * 3) return -1;
    int col = (int) ((x - bx) / (bw / 2)), row = (int) ((y - by) / rh);
    int i = row * 2 + col;
    return i >= 0 && i < 6 ? i : -1;
  }
}
