package com.lastdom.game;

/**
 * Only carries and prepaid inputs; resource balances remain in GameState and expedition warehouse.
 */
final class ProductionState {
  int medicineMinutes, materialSavingRemainder;
  long energyProduced, waterProduced, foodRations, medicineDoses, materialsSaved;

  static long add(long value, long amount) {
    return value > Long.MAX_VALUE - amount ? Long.MAX_VALUE : value + amount;
  }

  void reset() {
    medicineMinutes = materialSavingRemainder = 0;
    energyProduced = waterProduced = foodRations = medicineDoses = materialsSaved = 0;
  }
}
