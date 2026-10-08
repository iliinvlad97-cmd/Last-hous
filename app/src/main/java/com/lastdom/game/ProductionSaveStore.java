package com.lastdom.game;

import android.content.SharedPreferences;

/** Additive keys in the existing atomic save_v02 transaction; loading never produces or pays. */
final class ProductionSaveStore {
  static void save(ProductionState state, SharedPreferences.Editor e) {
    e.putInt("production8_schema", 1)
        .putInt("production8_medicineMinutes", state.medicineMinutes)
        .putInt("production8_materialRemainder", state.materialSavingRemainder)
        .putString("production8_energyProduced", Long.toString(state.energyProduced))
        .putString("production8_waterProduced", Long.toString(state.waterProduced))
        .putString("production8_foodRations", Long.toString(state.foodRations))
        .putString("production8_waterRations", Long.toString(state.waterRations))
        .putString("production8_energyUsed", Long.toString(state.energyUsed))
        .putString("production8_medicineDoses", Long.toString(state.medicineDoses))
        .putString("production8_materialsSaved", Long.toString(state.materialsSaved));
  }

  static void load(ProductionState state, SharedPreferences sp) {
    state.reset();
    state.medicineMinutes =
        Math.max(
            0,
            Math.min(
                ProductionConfig.MEDICINE_DOSE_MINUTES,
                sp.getInt("production8_medicineMinutes", 0)));
    state.materialSavingRemainder =
        Math.max(
            0, Math.min(ProductionConfig.BASIS - 1, sp.getInt("production8_materialRemainder", 0)));
    state.energyProduced = number(sp, "production8_energyProduced");
    state.waterProduced = number(sp, "production8_waterProduced");
    state.foodRations = number(sp, "production8_foodRations");
    state.waterRations = number(sp, "production8_waterRations");
    state.energyUsed = number(sp, "production8_energyUsed");
    state.medicineDoses = number(sp, "production8_medicineDoses");
    state.materialsSaved = number(sp, "production8_materialsSaved");
  }

  private static long number(SharedPreferences sp, String key) {
    try {
      return Math.max(0, Long.parseLong(sp.getString(key, "0")));
    } catch (NumberFormatException error) {
      return 0;
    }
  }

  private ProductionSaveStore() {}
}
