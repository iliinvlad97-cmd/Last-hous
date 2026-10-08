package com.lastdom.game;

import java.util.Locale;

/**
 * Original simulation, assignments, incidents and expeditions; no rendering or Android lifecycle
 * ownership.
 */
class GameController extends GameState {

  final ExpeditionController expeditionController = new ExpeditionController(this);
  final RoomUpgradeController roomUpgradeController = new RoomUpgradeController(this);
  final SurvivalController survivalController = new SurvivalController(this);
  final ProductionController productionController = new ProductionController(this);
  final RaidController raidController = new RaidController(this);
  private final GameSaveStore saves;
  private final Runnable redraw;

  GameController(android.content.SharedPreferences preferences, Runnable redraw) {
    this.saves = new GameSaveStore(preferences);
    this.redraw = redraw;
    initLocations();
    load();
  }

  void invalidate() {
    redraw.run();
  }

  void save() {
    saves.save(this);
  }

  void load() {
    saves.load(this);
  }

  void initLocations() {
    locations.clear();
    locations.add(new Location("Продуктовый", "еда / вода", 2, 12, 100, true));
    locations.add(new Location("Аптека", "медицина", 3, 18, 100, true));
    locations.add(new Location("Гаражи", "материалы", 4, 24, 100, true));
    locations.add(new Location("Соседний дом", "разное", 5, 30, 100, true));
    locations.add(new Location("Склад", "крупная добыча", 7, 42, 100, false));
    locations.add(new Location("Больница", "редкие припасы", 9, 55, 100, false));
  }

  Resident make(String n, String r, int s) {
    return new Resident(n, r, s);
  }

  void defaults() {
    people.clear();
    people.add(make("Иван", "Инженер", 4));
    people.add(make("Мария", "Врач", 4));
    people.add(make("Сергей", "Охрана", 4));
    people.add(make("Анна", "Сборщик", 3));
    people.add(make("Павел", "Механик", 4));
  }

  void reset() {
    residentVisualReady = false;
    expeditions.clear();
    roomUpgrades.clear();
    productionRemainders.values.clear();
    production.reset();
    resourceAccounting.reset();
    survivalController.reset();
    raidController.reset();
    for (MapLocation location : cityLocations) {
      location.setDepletion(0);
      location.setState(
          location.kind == MapLocation.Kind.WATER || location.kind == MapLocation.Kind.HOSPITAL
              ? MapLocation.State.LOCKED
              : MapLocation.State.AVAILABLE);
    }
    for (ExpeditionLoot.Resource resource : ExpeditionLoot.Resource.values())
      expeditionWarehouse.set(resource, 0);
    day = 1;
    gameMinute = 480;
    speed = 1;
    paused = false;
    food = 28;
    water = 34;
    power = 24;
    mats = 18;
    threat = 12;
    shelter = 100;
    selected = -1;
    selectedRoom = -1;
    screen = 0;
    overlay = 0;
    buildingRoom = -1;
    buildRemaining = 0;
    roomLevels = new int[] {1, 1, 1, 1, 1, 1};
    roomCondition = new int[] {100, 100, 100, 100, 100, 100};
    event = false;
    gameOver = false;
    jobMenu = false;
    expeditionPerson = -1;
    expeditionLocation = -1;
    expeditionRemaining = 0;
    selectedLocation = -1;
    initLocations();
    defaults();
    log.clear();
    addLog("Убежище готово. Дом оживает.");
    save();
    invalidate();
  }

  void advanceMinute() {
    gameMinute++;
    boolean nextDay = gameMinute >= SurvivalConfig.MINUTES_PER_DAY;
    if (nextDay) {
      gameMinute = 0;
      day++;
    }
    survivalController.advanceMinute();
    roomUpgradeController.advanceMinute();
    if (nextDay) dailyCycle();
    expeditionController.advanceMinute();
    raidController.advanceMinute();
    save(); // Atomic survival/resource/clock/phase snapshot, including prepaid ration balances.
  }

  String clock() {
    return String.format(Locale.getDefault(), "%02d:%02d", gameMinute / 60, gameMinute % 60);
  }

  String phase() {
    int h = gameMinute / 60;
    return h >= 6 && h < 12
        ? "УТРО"
        : h >= 12 && h < 18 ? "ДЕНЬ" : h >= 18 && h < 22 ? "ВЕЧЕР" : "НОЧЬ";
  }

  void dailyCycle() {
    int energySpent = Math.min(1, Math.max(0, power));
    power = Math.max(0, power - 1);
    production.energyUsed = ProductionState.add(production.energyUsed, energySpent);
    threat = Math.min(100, threat + 2 + rnd.nextInt(4));
    for (int i = 0; i < 6; i++)
      roomCondition[i] = Math.max(15, roomCondition[i] - (1 + rnd.nextInt(3)));
    if (aliveCount() == 0 || shelter <= 0) gameOver = true;
    else if (rnd.nextInt(100) < 45) triggerEvent();
    else addLog("Новый день начался спокойно.");
  }

  int aliveCount() {
    int n = 0;
    for (Resident s : people) if (s.alive) n++;
    return n;
  }

  void addLog(String s) {
    log.add(0, "День " + day + " • " + clock() + ": " + s);
    while (log.size() > 35) log.remove(log.size() - 1);
  }

  int homeRoomFor(Resident s) {
    if (!s.alive || isOnExpedition(s)) return -1;
    if (raidController.repairBuilder(s)) return 4;
    if (isDefending(s)) return 4;
    if (isBuilding(s)) {
      RoomUpgradeTask task = roomUpgradeController.taskFor(s);
      return task == null ? -1 : task.room;
    }
    if (s.job.equals("Ремонт")) return 0;
    if (s.job.equals("Еда") || s.job.equals("Вода")) return 1;
    if (s.job.equals("Лечение") || s.job.equals("Лечится")) return 2;
    if (s.job.equals("Материалы")) return 3;
    if (s.job.equals("Охрана")) return 4;
    return 5;
  }

  String occupants(int ri) {
    StringBuilder s = new StringBuilder();
    for (Resident q : people)
      if (homeRoomFor(q) == ri
          && !isBuilding(q)
          && !isDefending(q)
          && !survivalController.treating(q)) {
        if (s.length() > 0) s.append(",");
        s.append(q.name);
      }
    return s.length() == 0 ? "—" : s.toString();
  }

  String roomBonus(int i) {
    switch (i) {
      case 0:
        return "Генератор вырабатывает "
            + RoomUpgradeConfig.BASE_ENERGY_PER_DAY
            + " энергии за игровой день. Улучшение усиливает выработку.";
      case 1:
        return "Снижает расход пищевых запасов при приготовлении и позволяет лучше использовать"
            + " запасы убежища.";
      case 2:
        return "Ускоряет лечение раненых и восстановление здоровья жителей, назначенных на"
            + " лечение.";
      case 3:
        return "Экономит материалы при улучшении комнат. Сырьё поступает из экспедиций.";
      case 4:
        return "Усиливает защиту убежища и помогает охране снижать уровень угрозы.";
      case 5:
        return "Улучшает отдых жителей и быстрее снижает усталость.";
      default:
        return "Помещение убежища.";
    }
  }

  int availableExplorer() {
    for (int i = 0; i < people.size(); i++)
      if (people.get(i).alive
          && !isOnExpedition(people.get(i))
          && !isBuilding(people.get(i))
          && !isDefending(people.get(i))) return i;
    return -1;
  }

  // Compatibility entry point for existing callers; no legacy rewards or auto-return.
  void startExpedition(int li) {
    if (li < 0 || li >= cityLocations.size()) return;
    int index = availableExplorer();
    String result =
        expeditionController.start(
            cityLocations.get(li).id,
            index < 0
                ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(people.get(index).id));
    if (!result.isEmpty()) addLog(result);
  }

  boolean isOnExpedition(Resident resident) {
    return resident.job.equals("Экспедиция") || expeditionController.contains(resident);
  }

  boolean isDefending(Resident resident) {
    return raidController.defending(resident);
  }

  boolean isBuilding(Resident resident) {
    return resident.status == Resident.Status.BUILDING
        || resident.job.equals("Строительство")
        || roomUpgradeController.taskFor(resident) != null
        || raidController.repairBuilder(resident);
  }

  boolean assignJob(int index, String job) {
    if (index < 0
        || index >= people.size()
        || !people.get(index).alive
        || isOnExpedition(people.get(index))
        || isBuilding(people.get(index))
        || isDefending(people.get(index))) return false;
    people.get(index).job = job;
    people.get(index).autoRecovery = false;
    people.get(index).resumeJob = "";
    save();
    invalidate();
    return true;
  }

  String formatBuild(int m) {
    return String.format(
        Locale.getDefault(), "%02d:%02d", Math.max(0, m) / 60, Math.max(0, m) % 60);
  }

  void startUpgrade(int i) {
    // Kept for source compatibility; starting now requires an explicit builder selection.
    addLog("Выберите строителя в панели комнаты.");
  }

  void repairRoom(int i) {
    if (i == 4) {
      addLog("Ремонт баррикад требует выбора строителя в панели обороны.");
      return;
    }
    int cost = Math.max(1, (100 - roomCondition[i]) / 15);
    if (roomCondition[i] >= 95) return;
    if (mats >= cost) {
      mats -= cost;
      roomCondition[i] = Math.min(100, roomCondition[i] + 30);
      addLog(rooms[i] + ": выполнен ремонт.");
    }
    save();
  }

  void processJobs() {
    // Compatibility helper: one day of production only; minute clock owns all needs/recovery.
    survivalController.produce(SurvivalConfig.MINUTES_PER_DAY);
  }

  void triggerEvent() {
    event = true;
    screen = 0;
    overlay = 0;
    int e = rnd.nextInt(5);
    if (e == 3) {
      event = false;
      incidentRoom = -1;
      raidController.checkDailyThreat();
      return;
    }
    incidentRoom = e == 0 ? 4 : e == 1 ? 0 : e == 2 ? 2 : 5;
    if (e == 0) ev("ЧУЖАК У ДВЕРИ", "Ночью в дверь стучит незнакомец.", "ВПУСТИТЬ", "ОТКАЗАТЬ");
    else if (e == 1)
      ev(
          "КОРОТКОЕ ЗАМЫКАНИЕ",
          "В генераторной пахнет гарью. Оборудование перегрелось.",
          "РЕМОНТ",
          "ОТКЛЮЧИТЬ");
    else if (e == 2) ev("БОЛЕЗНЬ", "Одному из жителей нужна помощь.", "ЛЕЧИТЬ", "ОТДЫХ");
    else ev("ТИХАЯ НОЧЬ", "Дом наконец затих. Можно восстановить силы.", "ОТДЫХ", "ДЕЖУРИТЬ");
    autoRespondToIncident();
  }

  void autoRespondToIncident() {
    int pick = -1;
    String wanted = null;
    if (eventTitle.equals("КОРОТКОЕ ЗАМЫКАНИЕ")) {
      wanted = "Ремонт";
      pick = findBestResident("Инженер", "Механик");
    } else if (eventTitle.equals("БОЛЕЗНЬ")) {
      wanted = "Лечение";
      pick = findBestResident("Врач", "");
    } else if (eventTitle.equals("МАРОДЁРЫ") || eventTitle.equals("ЧУЖАК У ДВЕРИ")) {
      wanted = "Охрана";
      pick = findBestResident("Охрана", "");
    }
    if (pick >= 0 && wanted != null) {
      Resident s = people.get(pick);
      if (!isOnExpedition(s) && !isBuilding(s) && !isDefending(s)) {
        s.job = wanted;
        addLog(s.name + " автоматически реагирует: " + wanted.toLowerCase() + ".");
      }
    }
  }

  int findBestResident(String role1, String role2) {
    int best = -1, score = -999;
    for (int i = 0; i < people.size(); i++) {
      Resident s = people.get(i);
      if (!s.alive
          || isOnExpedition(s)
          || isBuilding(s)
          || isDefending(s)
          || survivalController.protectedRest(s)
          || survivalController.treating(s)
          || s.health <= SurvivalConfig.CRITICAL_HEALTH) continue;
      int v = s.skill * 5 - s.fatigue / 8 + s.health / 12;
      if (s.role.equals(role1) || (!role2.isEmpty() && s.role.equals(role2))) v += 40;
      if (v > score) {
        score = v;
        best = i;
      }
    }
    return best;
  }

  void ev(String a, String b, String c, String d) {
    eventTitle = a;
    eventText = b;
    eventChoices[0] = c;
    eventChoices[1] = d;
  }

  void choose(int n) {
    if (eventTitle.equals("ЧУЖАК У ДВЕРИ")) {
      if (n == 0) {
        people.add(make("Алекс", "Выживший", 2));
        food = Math.max(0, food - 2);
        addLog("В дом принят Алекс.");
      } else addLog("Чужаку отказали.");
    } else if (eventTitle.equals("КОРОТКОЕ ЗАМЫКАНИЕ")) {
      if (n == 0 && mats >= 3) {
        mats -= 3;
        roomCondition[0] = Math.min(100, roomCondition[0] + 25);
        addLog("Генераторную отремонтировали.");
      } else {
        power = Math.max(0, power - 8);
        roomCondition[0] = Math.max(10, roomCondition[0] - 18);
        addLog("Генераторная повреждена.");
      }
    } else if (eventTitle.equals("БОЛЕЗНЬ")) {
      java.util.List<Resident> present = new java.util.ArrayList<>();
      for (Resident s : people) if (!isOnExpedition(s)) present.add(s);
      if (!present.isEmpty()) {
        Resident q = present.get(rnd.nextInt(present.size()));
        int before = q.health;
        q.health = SurvivalConfig.clamp(q.health + (n == 0 ? 10 : -12));
        survivalController.injury(q, before - q.health);
        addLog(n == 0 ? "Больному помогли." : "Болезнь ослабила жителя.");
      }
    } else if (eventTitle.equals("МАРОДЁРЫ")) {
      // Old instant raid is superseded: no second theft/damage alongside Stage 7.
      raidController.checkDailyThreat();
    } else {
      for (Resident s : people)
        if (s.alive && !isOnExpedition(s) && !isBuilding(s) && !isDefending(s))
          s.fatigue = Math.max(0, s.fatigue - (n == 0 ? 15 : 5));
      addLog("Ночь использовали с пользой.");
    }
    event = false;
    incidentRoom = -1;
    save();
    invalidate();
  }
}
