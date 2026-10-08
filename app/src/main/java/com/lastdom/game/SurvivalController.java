package com.lastdom.game;

/** Individual survival clock, recovery AI and economy. No wall clock or rendering mutations. */
final class SurvivalController {
  static final String[] FRACTIONS = {"hunger", "thirst", "fatigue", "morale", "health"};
  static final String[] OUTPUTS = {
    "energy", "food", "water", "materials", "repair", "condition", "guards"
  };
  static final int NEED_DENOMINATOR = 144000, OUTPUT_DENOMINATOR = 14400000;
  private final GameController game;
  int resourceWarnings;
  long resourceWarningMinute = -SurvivalConfig.WARNING_COOLDOWN;
  String notice = "";
  long noticeMinute = -100;

  SurvivalController(GameController game) {
    this.game = game;
  }

  void reset() {
    resourceWarnings = 0;
    resourceWarningMinute = -SurvivalConfig.WARNING_COOLDOWN;
    notice = "";
    noticeMinute = -100;
  }

  int efficiencyPercent(Resident r) {
    return (int) Math.round(SurvivalConfig.efficiency(r) * 100);
  }

  boolean resting(Resident r) {
    return !game.isOnExpedition(r) && !game.isBuilding(r) && r.job.equals("Отдых");
  }

  boolean protectedRest(Resident r) {
    return resting(r) && (r.autoRecovery || !restComplete(r));
  }

  private boolean restComplete(Resident r) {
    return r.fatigue <= SurvivalConfig.REST_FINISH
        && r.survivalFractions.getOrDefault("fatigue", 0) <= 0;
  }

  boolean treating(Resident r) {
    return !game.isOnExpedition(r) && !game.isBuilding(r) && r.job.equals("Лечится");
  }

  boolean working(Resident r) {
    return r.alive && !game.isOnExpedition(r) && !game.isBuilding(r) && !resting(r) && !treating(r);
  }

  String expeditionReason(Resident r) {
    if (r.health <= SurvivalConfig.CRITICAL_HEALTH) return "Критическое состояние здоровья";
    if (r.hunger >= SurvivalConfig.CRITICAL) return "Критический голод";
    if (r.thirst >= SurvivalConfig.CRITICAL) return "Критическая жажда";
    if (treating(r)) return "На лечении";
    return "";
  }

  void injury(Resident r, int loss) {
    if (loss > 0)
      r.morale =
          SurvivalConfig.clamp(
              r.morale - Math.max(1, loss * SurvivalConfig.INJURY_MORALE_PERCENT / 100));
  }

  private boolean medicalStaffAvailable() {
    for (Resident staff : game.people)
      if (working(staff)
          && staff.job.equals("Лечение")
          && staff.health >= SurvivalConfig.TREAT_START
          && staff.fatigue < SurvivalConfig.REST_START) return true;
    return false;
  }

  private void recoverAI(Resident r) {
    if (!r.alive || game.isOnExpedition(r) || game.isBuilding(r)) return;
    boolean medical = medicalStaffAvailable();
    if (r.health < SurvivalConfig.TREAT_START && medical && !treating(r)) {
      if (!r.autoRecovery) r.resumeJob = r.job;
      r.autoRecovery = true;
      r.job = "Лечится";
    } else if (treating(r) && r.autoRecovery && !medical) {
      r.job = "Отдых"; // Existing passive bedroom recovery remains possible without a doctor.
    } else if (treating(r) && r.autoRecovery && r.health >= SurvivalConfig.TREAT_FINISH) {
      r.job = r.fatigue >= SurvivalConfig.REST_START ? "Отдых" : r.resumeJob;
      if (!r.job.equals("Отдых")) {
        r.autoRecovery = false;
        r.resumeJob = "";
      }
    } else if ((r.fatigue >= SurvivalConfig.REST_START || r.health < SurvivalConfig.TREAT_START)
        && !resting(r)
        && !treating(r)) {
      if (!r.autoRecovery) r.resumeJob = r.job;
      r.autoRecovery = true;
      r.job = "Отдых";
    } else if (resting(r)
        && r.autoRecovery
        && restComplete(r)
        && r.health >= SurvivalConfig.TREAT_START) {
      r.job = r.resumeJob.isEmpty() ? "Отдых" : r.resumeJob;
      r.autoRecovery = false;
      r.resumeJob = "";
    }
  }

  int workPercent(Resident r) {
    if (!working(r)) return 0;
    int room =
        r.job.equals("Еда")
            ? 1
            : r.job.equals("Материалы")
                ? 3
                : r.job.equals("Лечение") ? 2 : r.job.equals("Охрана") ? 4 : -1;
    return (int)
        Math.round(
            SurvivalConfig.efficiency(r)
                * (room < 0 ? 100 : game.roomUpgradeController.percent(room)));
  }

  void advanceMinute() {
    // Staff effects are sampled once per minute; a patient never counts as medical staff.
    for (Resident r : game.people) recoverAI(r);
    double medics = 0;
    int baseMedics = 0;
    for (Resident r : game.people)
      if (working(r) && r.job.equals("Лечение")) {
        medics += (r.skill + 1) * SurvivalConfig.efficiency(r);
        baseMedics += r.skill + 1;
      }
    for (Resident r : game.people) {
      if (!r.alive) continue;
      clamp(r);
      boolean away = game.isOnExpedition(r);
      if (!away) {
        // Reserve one real unit for a resident's next 1440 HOME minutes. The saved balance
        // prevents recharging on reload; away residents neither consume nor use this ration.
        if (r.foodMinutes == 0 && game.food > 0) {
          game.food--;
          r.foodMinutes = 1440;
        }
        if (r.waterMinutes == 0 && game.water > 0) {
          game.water--;
          r.waterMinutes = 1440;
        }
      }
      boolean fed = !away && r.foodMinutes > 0, watered = !away && r.waterMinutes > 0;
      change(r, "hunger", (fed ? -1 : 1) * SurvivalConfig.HUNGER_PER_DAY * 100);
      change(r, "thirst", (watered ? -1 : 1) * SurvivalConfig.THIRST_PER_DAY * 100);
      if (fed) r.foodMinutes--;
      if (watered) r.waterMinutes--;
      boolean rest = resting(r), treatment = treating(r);
      if (rest)
        change(
            r, "fatigue", -SurvivalConfig.bedroomRecoveryPerHour(game.roomLevels[5]) * 2400, true);
      else
        change(
            r,
            "fatigue",
            (treatment
                    ? -SurvivalConfig.TREATMENT_REST_PER_DAY
                    : SurvivalConfig.WORK_FATIGUE_PER_DAY)
                * 100);
      int moraleRate = 0;
      if (r.hunger >= SurvivalConfig.NEED_HEAVY) moraleRate -= SurvivalConfig.BAD_MORALE_PER_DAY;
      if (r.thirst >= SurvivalConfig.NEED_HEAVY) moraleRate -= SurvivalConfig.BAD_MORALE_PER_DAY;
      if (r.fatigue >= 70 || r.health < 40) moraleRate -= SurvivalConfig.BAD_MORALE_PER_DAY;
      if (rest || treatment) moraleRate += SurvivalConfig.REST_MORALE_PER_DAY;
      if (r.hunger < SurvivalConfig.NEED_WARNING
          && r.thirst < SurvivalConfig.NEED_WARNING
          && r.health >= 70
          && r.fatigue < 70
          && (away || game.food > 0 || fed)
          && (away || game.water > 0 || watered)) moraleRate += SurvivalConfig.GOOD_MORALE_PER_DAY;
      change(r, "morale", moraleRate * 100);
      int healthRate = 0;
      if (r.hunger >= SurvivalConfig.CRITICAL)
        healthRate -= SurvivalConfig.CRITICAL_DAMAGE_PER_DAY * 100;
      if (r.thirst >= SurvivalConfig.CRITICAL)
        healthRate -= SurvivalConfig.CRITICAL_DAMAGE_PER_DAY * 100;
      // Preserve the existing free, staff-dependent treatment rule; no medicine cost existed.
      if (!away
          && !game.isBuilding(r)
          && r.hunger < SurvivalConfig.CRITICAL
          && r.thirst < SurvivalConfig.CRITICAL) {
        healthRate +=
            (int)
                Math.round(
                    (baseMedics == 0 ? 0 : (baseMedics / 2) * medics / baseMedics)
                        * game.roomUpgradeController.percent(2));
        if (rest) healthRate += SurvivalConfig.REST_HEALTH_PER_DAY * 100;
      }
      change(r, "health", healthRate);
      warnings(r);
    }
    produce(1);
    int mask = (game.food <= game.aliveCount() ? 1 : 0) | (game.water <= game.aliveCount() ? 2 : 0);
    int crossed = mask & ~resourceWarnings;
    if (crossed != 0 && now() - resourceWarningMinute >= SurvivalConfig.WARNING_COOLDOWN) {
      if ((crossed & 1) != 0) announce("Заканчивается еда");
      if ((crossed & 2) != 0) announce("Заканчивается вода");
      resourceWarningMinute = now();
    }
    resourceWarnings = mask;
  }

  private long now() {
    return game.expeditionController.now();
  }

  private void announce(String text) {
    game.addLog(text);
    notice = text;
    noticeMinute = now();
  }

  private void warnings(Resident r) {
    String[] names = {
      "Житель сильно устал",
      "Житель нуждается в лечении",
      "Критический голод",
      "Критическая жажда",
      "Низкая мораль"
    };
    int mask =
        (r.fatigue >= SurvivalConfig.REST_START ? 1 : 0)
            | (r.health < 40 ? 2 : 0)
            | (r.hunger >= SurvivalConfig.CRITICAL ? 4 : 0)
            | (r.thirst >= SurvivalConfig.CRITICAL ? 8 : 0)
            | (r.morale < 40 ? 16 : 0);
    int crossed = mask & ~r.warningMask;
    for (int i = 0; i < names.length; i++)
      if ((crossed & (1 << i)) != 0 && now() - r.warningAt[i] >= SurvivalConfig.WARNING_COOLDOWN) {
        announce(names[i] + ": " + r.name);
        r.warningAt[i] = now();
        r.warningMinute = now();
      }
    r.warningMask = mask;
  }

  private void change(Resident r, String key, int dailyHundredths) {
    change(r, key, dailyHundredths, false);
  }

  private void change(Resident r, String key, int dailyHundredths, boolean roundedRest) {
    long value = r.survivalFractions.getOrDefault(key, 0) + (long) dailyHundredths;
    int delta =
        roundedRest
            // Preserve legacy positive carry without making displayed fatigue rise during rest.
            ? Math.min(0, (int) Math.round(value / (double) NEED_DENOMINATOR))
            : (int) (value / NEED_DENOMINATOR);
    int remainder = (int) (value - (long) delta * NEED_DENOMINATOR);
    int before =
        key.equals("hunger")
            ? r.hunger
            : key.equals("thirst")
                ? r.thirst
                : key.equals("fatigue") ? r.fatigue : key.equals("morale") ? r.morale : r.health;
    int after = SurvivalConfig.clamp(before + delta);
    // Do not bank growth/recovery beyond a bound for later use.
    if (after == 0 && remainder < 0
        || after == 100 && remainder > 0
        || roundedRest && before + delta < 0) remainder = 0;
    r.survivalFractions.put(key, remainder);
    switch (key) {
      case "hunger":
        r.hunger = after;
        break;
      case "thirst":
        r.thirst = after;
        break;
      case "fatigue":
        r.fatigue = after;
        break;
      case "morale":
        r.morale = after;
        break;
      default:
        r.health = after;
    }
  }

  void clamp(Resident r) {
    r.health = SurvivalConfig.clamp(r.health);
    r.hunger = SurvivalConfig.clamp(r.hunger);
    r.thirst = SurvivalConfig.clamp(r.thirst);
    r.fatigue = SurvivalConfig.clamp(r.fatigue);
    r.morale = SurvivalConfig.clamp(r.morale);
  }

  private int output(int base, int room, int effectiveness, int minutes, String key) {
    String channel = "survival6_" + key;
    long amount =
        game.productionRemainders.values.getOrDefault(channel, 0)
            + (long) base
                * (room < 0 ? 100 : game.roomUpgradeController.percent(room))
                * effectiveness
                * minutes;
    game.productionRemainders.values.put(channel, (int) (amount % OUTPUT_DENOMINATOR));
    return (int) Math.min(Integer.MAX_VALUE, amount / OUTPUT_DENOMINATOR);
  }

  private int add(int value, int amount) {
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value) + amount);
  }

  void produce(int minutes) {
    game.power =
        add(game.power, output(RoomUpgradeConfig.BASE_ENERGY_PER_DAY, 0, 100, minutes, "energy"));
    for (Resident r : game.people) {
      if (!working(r)) continue;
      int efficiency = efficiencyPercent(r);
      switch (r.job) {
        case "Еда":
          game.food =
              add(
                  game.food,
                  output(
                      3 + (r.role.equals("Сборщик") ? r.skill : 1),
                      1,
                      efficiency,
                      minutes,
                      "food"));
          break;
        case "Вода": // Kitchen upgrades historically affect food only.
          game.water = add(game.water, output(4, -1, efficiency, minutes, "water"));
          break;
        case "Материалы":
          game.mats =
              add(
                  game.mats,
                  output(
                      2 + (r.role.equals("Механик") ? 2 : 0), 3, efficiency, minutes, "materials"));
          break;
        case "Ремонт":
          game.shelter =
              Math.min(100, game.shelter + output(3 + r.skill, -1, efficiency, minutes, "repair"));
          game.roomCondition[0] =
              Math.min(
                  100, game.roomCondition[0] + output(2, -1, efficiency, minutes, "condition"));
          break;
        case "Охрана":
          game.threat =
              Math.max(0, game.threat - output(r.skill + 1, 4, efficiency, minutes, "guards"));
          break;
        default:
          break;
      }
    }
  }
}
