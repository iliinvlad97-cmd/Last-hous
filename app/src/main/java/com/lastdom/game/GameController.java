package com.lastdom.game;

import java.util.Locale;

/**
 * Original simulation, assignments, incidents and expeditions; no rendering or Android lifecycle
 * ownership.
 */
class GameController extends GameState {

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
    if (expeditionPerson >= 0) {
      expeditionRemaining--;
      if (expeditionRemaining <= 0) finishExpedition();
    }
    if (buildingRoom >= 0) {
      buildRemaining--;
      if (buildRemaining <= 0) {
        roomLevels[buildingRoom]++;
        roomCondition[buildingRoom] = 100;
        addLog(rooms[buildingRoom] + " улучшена до ур. " + roomLevels[buildingRoom] + ".");
        buildingRoom = -1;
      }
    }
    if (gameMinute >= 1440) {
      gameMinute = 0;
      day++;
      dailyCycle();
    }
    if (gameMinute % 60 == 0) save();
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
    processJobs();
    int a = aliveCount();
    food = Math.max(0, food - a);
    water = Math.max(0, water - a * 2);
    power = Math.max(0, power - Math.max(1, 1 + roomLevels[0] / 2));
    threat = Math.min(100, threat + 2 + rnd.nextInt(4));
    for (int i = 0; i < 6; i++)
      roomCondition[i] = Math.max(15, roomCondition[i] - (1 + rnd.nextInt(3)));
    for (Resident s : people)
      if (s.alive) {
        s.hunger = Math.min(100, s.hunger + 10);
        if (food == 0 || water == 0) s.health = Math.max(0, s.health - 6);
        if (s.health <= 0) {
          s.alive = false;
          addLog(s.name + " погиб.");
        }
      }
    if (aliveCount() == 0 || shelter <= 0) gameOver = true;
    else if (rnd.nextInt(100) < 45) triggerEvent();
    else addLog("Новый день начался спокойно.");
    save();
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
    if (!s.alive || s.job.equals("Экспедиция")) return -1;
    if (s.job.equals("Ремонт")) return 0;
    if (s.job.equals("Еда") || s.job.equals("Вода")) return 1;
    if (s.job.equals("Лечение")) return 2;
    if (s.job.equals("Материалы")) return 3;
    if (s.job.equals("Охрана")) return 4;
    return 5;
  }

  String occupants(int ri) {
    StringBuilder s = new StringBuilder();
    for (Resident q : people)
      if (q.alive && q.job.equals(roomJobs[ri])) {
        if (s.length() > 0) s.append(",");
        s.append(q.name);
      }
    return s.length() == 0 ? "—" : s.toString();
  }

  String roomBonus(int i) {
    switch (i) {
      case 0:
        return "Стабилизирует энергоснабжение. Более высокий уровень снижает риск поломок и"
                   + " усиливает работу инженеров.";
      case 1:
        return "Повышает эффективность производства еды и позволяет лучше использовать запасы"
                   + " убежища.";
      case 2:
        return "Ускоряет лечение раненых и восстановление здоровья жителей, назначенных на"
                   + " лечение.";
      case 3:
        return "Повышает добычу и обработку материалов, а также эффективность механиков.";
      case 4:
        return "Усиливает защиту убежища и помогает охране снижать уровень угрозы.";
      case 5:
        return "Улучшает отдых жителей, быстрее снижает усталость и восстанавливает мораль.";
      default:
        return "Помещение убежища.";
    }
  }

  int availableExplorer() {
    for (int i = 0; i < people.size(); i++)
      if (people.get(i).alive && !people.get(i).job.equals("Экспедиция")) return i;
    return -1;
  }

  void startExpedition(int li) {
    if (expeditionPerson >= 0) {
      addLog("Сначала дождитесь возвращения текущей экспедиции.");
      return;
    }
    Location l = locations.get(li);
    if (!l.discovered) return;
    int pi = availableExplorer();
    if (pi < 0) {
      addLog("Нет свободного жителя для вылазки.");
      return;
    }
    expeditionPerson = pi;
    expeditionLocation = li;
    expeditionRemaining = 90 + l.distance * 35;
    people.get(pi).job = "Экспедиция";
    addLog(
        people.get(pi).name
            + " отправился: "
            + l.name
            + ". Возвращение через "
            + formatBuild(expeditionRemaining)
            + ".");
    save();
    invalidate();
  }

  void finishExpedition() {
    if (expeditionPerson < 0 || expeditionLocation < 0) return;
    Resident s = people.get(expeditionPerson);
    Location l = locations.get(expeditionLocation);
    int night = (gameMinute / 60 >= 22 || gameMinute / 60 < 6) ? 12 : 0;
    int risk = Math.min(85, l.risk + night);
    boolean hurt = rnd.nextInt(100) < risk;
    int stockFactor = Math.max(20, l.stock);
    int gain = 1 + rnd.nextInt(Math.max(2, stockFactor / 18));
    if (l.name.equals("Продуктовый")) {
      food += gain + 3;
      water += gain + 2;
    } else if (l.name.equals("Аптека")) {
      s.health = Math.min(100, s.health + 8);
      mats += gain;
    } else if (l.name.equals("Гаражи")) {
      mats += gain + 4;
    } else if (l.name.equals("Склад")) {
      food += gain;
      water += gain;
      mats += gain + 5;
    } else if (l.name.equals("Больница")) {
      mats += gain + 3;
      s.health = Math.min(100, s.health + 15);
    } else {
      food += gain;
      water += gain;
      mats += Math.max(1, gain / 2);
    }
    l.stock = Math.max(0, l.stock - (8 + rnd.nextInt(13)));
    if (hurt) {
      int dmg = 8 + rnd.nextInt(20);
      s.health = Math.max(1, s.health - dmg);
      addLog(s.name + " вернулся раненым из " + l.name + " (-" + dmg + " здоровья). ");
    } else addLog(s.name + " вернулся из " + l.name + " с припасами.");
    s.job = "Отдых";
    s.fatigue = Math.min(100, s.fatigue + 22);
    if (rnd.nextInt(100) < 35) {
      for (Location q : locations)
        if (!q.discovered) {
          q.discovered = true;
          addLog("Открыта новая точка: " + q.name + ".");
          break;
        }
    }
    expeditionPerson = -1;
    expeditionLocation = -1;
    expeditionRemaining = 0;
    save();
  }

  String formatBuild(int m) {
    return String.format(
        Locale.getDefault(), "%02d:%02d", Math.max(0, m) / 60, Math.max(0, m) % 60);
  }

  void startUpgrade(int i) {
    if (buildingRoom >= 0) {
      addLog("Сначала завершите текущее строительство.");
      return;
    }
    int cost = 6 + roomLevels[i] * 4;
    if (mats < cost) {
      addLog("Не хватает материалов: нужно " + cost + ".");
      return;
    }
    mats -= cost;
    buildingRoom = i;
    buildRemaining = 120 + roomLevels[i] * 90;
    addLog("Начато улучшение: " + rooms[i] + ".");
    save();
    invalidate();
  }

  void repairRoom(int i) {
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
    int guards = 0, medics = 0;
    for (Resident s : people)
      if (s.alive) {
        s.fatigue = Math.max(0, Math.min(100, s.fatigue + (s.job.equals("Отдых") ? -22 : 13)));
        if (s.fatigue > 85 && !s.job.equals("Отдых")) {
          s.health = Math.max(1, s.health - 4);
          s.morale = Math.max(0, s.morale - 6);
        }
        if (s.health < 35 && !s.job.equals("Лечение") && !s.job.equals("Отдых")) {
          s.fatigue = Math.min(100, s.fatigue + 8);
        }
        if (s.job.equals("Еда"))
          food += 3 + (s.role.equals("Сборщик") ? s.skill : 1) + roomLevels[1] / 2;
        else if (s.job.equals("Вода")) water += 4;
        else if (s.job.equals("Материалы"))
          mats += 2 + (s.role.equals("Механик") ? 2 : 0) + roomLevels[3] / 2;
        else if (s.job.equals("Ремонт")) {
          shelter = Math.min(100, shelter + 3 + s.skill);
          roomCondition[0] = Math.min(100, roomCondition[0] + 2);
        } else if (s.job.equals("Охрана")) guards += s.skill + roomLevels[4];
        else if (s.job.equals("Лечение")) medics += s.skill + roomLevels[2];
        else {
          s.morale = Math.min(100, s.morale + 4 + roomLevels[5] / 2);
          s.health = Math.min(100, s.health + 2);
        }
      }
    threat = Math.max(0, threat - guards);
    if (medics > 0)
      for (Resident s : people)
        if (s.alive && s.health < 100) s.health = Math.min(100, s.health + medics / 2);
  }

  void triggerEvent() {
    event = true;
    screen = 0;
    overlay = 0;
    int e = rnd.nextInt(5);
    incidentRoom = e == 0 ? 4 : e == 1 ? 0 : e == 2 ? 2 : e == 3 ? 4 : 5;
    if (e == 0) ev("ЧУЖАК У ДВЕРИ", "Ночью в дверь стучит незнакомец.", "ВПУСТИТЬ", "ОТКАЗАТЬ");
    else if (e == 1)
      ev(
          "КОРОТКОЕ ЗАМЫКАНИЕ",
          "В генераторной пахнет гарью. Оборудование перегрелось.",
          "РЕМОНТ",
          "ОТКЛЮЧИТЬ");
    else if (e == 2) ev("БОЛЕЗНЬ", "Одному из жителей нужна помощь.", "ЛЕЧИТЬ", "ОТДЫХ");
    else if (e == 3) ev("МАРОДЁРЫ", "У входа замечены вооружённые люди.", "ОТДАТЬ ЕДУ", "ОБОРОНА");
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
      if (!s.job.equals("Экспедиция")) {
        s.job = wanted;
        addLog(s.name + " автоматически реагирует: " + wanted.toLowerCase() + ".");
      }
    }
  }

  int findBestResident(String role1, String role2) {
    int best = -1, score = -999;
    for (int i = 0; i < people.size(); i++) {
      Resident s = people.get(i);
      if (!s.alive || s.job.equals("Экспедиция")) continue;
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
      Resident q = people.get(rnd.nextInt(people.size()));
      q.health = Math.max(1, q.health + (n == 0 ? 10 : -12));
      addLog(n == 0 ? "Больному помогли." : "Болезнь ослабила жителя.");
    } else if (eventTitle.equals("МАРОДЁРЫ")) {
      if (n == 0) food = Math.max(0, food - 7);
      else {
        threat = Math.max(0, threat - roomLevels[4] * 3);
        roomCondition[4] = Math.max(10, roomCondition[4] - 8);
      }
      addLog("Столкновение у баррикад завершилось.");
    } else {
      for (Resident s : people) if (s.alive) s.fatigue = Math.max(0, s.fatigue - (n == 0 ? 15 : 5));
      addLog("Ночь использовали с пользой.");
    }
    event = false;
    incidentRoom = -1;
    save();
    invalidate();
  }
}
