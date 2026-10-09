package com.lastdom.game;

import java.util.*;

/** Server-portable immutable demo fighters; no Resident or Android dependency. */
final class OnlineCombatSquad {
  enum Specialization {
    SCOUT,
    GUARD,
    MEDIC,
    ENGINEER,
    MECHANIC
  }

  static final class Fighter {
    final String id, name, role;
    final int health, stamina, attack, defense;
    final Specialization specialization;

    Fighter(
        String id,
        String name,
        String role,
        int health,
        int stamina,
        int attack,
        int defense,
        Specialization specialization) {
      if (id == null
          || id.isEmpty()
          || name == null
          || role == null
          || health < 0
          || health > 100
          || stamina < 0
          || stamina > 100
          || attack < 1
          || attack > 40
          || defense < 0
          || defense > 40
          || specialization == null) throw new IllegalArgumentException("Invalid online fighter");
      this.id = id;
      this.name = name;
      this.role = role;
      this.health = health;
      this.stamina = stamina;
      this.attack = attack;
      this.defense = defense;
      this.specialization = specialization;
    }

    String specializationLabel() {
      switch (specialization) {
        case SCOUT:
          return "Следопыт";
        case GUARD:
          return "Прикрытие";
        case MEDIC:
          return "Первая помощь";
        case ENGINEER:
          return "Укрепления";
        default:
          return "Сбор деталей";
      }
    }

    Fighter condition(int hp, int endurance) {
      return new Fighter(
          id,
          name,
          role,
          Math.max(0, Math.min(100, hp)),
          Math.max(0, Math.min(100, endurance)),
          attack,
          defense,
          specialization);
    }
  }

  final List<Fighter> fighters;

  OnlineCombatSquad(List<Fighter> fighters) {
    if (fighters.isEmpty() || fighters.size() > 3)
      throw new IllegalArgumentException("Online squad needs 1–3 fighters");
    Set<String> ids = new HashSet<>();
    for (Fighter fighter : fighters)
      if (!ids.add(fighter.id)) throw new IllegalArgumentException("Duplicate online fighter");
    this.fighters = Collections.unmodifiableList(new ArrayList<>(fighters));
  }

  boolean contains(String id) {
    for (Fighter f : fighters) if (f.id.equals(id)) return true;
    return false;
  }

  double strength() {
    double result = 0;
    for (Fighter f : fighters)
      result += (f.attack + f.defense) * f.health / 100.0 * (50 + f.stamina / 2.0) / 100.0;
    return result;
  }

  boolean has(Specialization specialization) {
    for (Fighter f : fighters) if (f.specialization == specialization) return true;
    return false;
  }

  static List<Fighter> roster() {
    return Collections.unmodifiableList(
        Arrays.asList(
            new Fighter("radio_1", "Радио-1", "Разведчик", 100, 100, 14, 6, Specialization.SCOUT),
            new Fighter("radio_2", "Радио-2", "Охрана", 100, 100, 16, 10, Specialization.GUARD),
            new Fighter("radio_3", "Радио-3", "Врач", 100, 100, 10, 7, Specialization.MEDIC),
            new Fighter("radio_4", "Радио-4", "Инженер", 100, 100, 12, 8, Specialization.ENGINEER),
            new Fighter("radio_5", "Радио-5", "Механик", 100, 100, 15, 7, Specialization.MECHANIC),
            new Fighter("radio_6", "Радио-6", "Следопыт", 100, 100, 13, 8, Specialization.SCOUT)));
  }

  static OnlineCombatSquad enemy(String encounterId) {
    List<Fighter> fighters = new ArrayList<>();
    for (int i = 0; i < 3; i++)
      fighters.add(
          new Fighter(
              encounterId + "_enemy_" + i,
              "Тень-" + (i + 1),
              "Виртуальный противник",
              90,
              85,
              13 + i,
              7 + i,
              i == 1 ? Specialization.GUARD : Specialization.SCOUT));
    return new OnlineCombatSquad(fighters);
  }

  static OnlineCombatSquad ally(OnlineShelter shelter) {
    return new OnlineCombatSquad(
        Arrays.asList(
            new Fighter(
                shelter.id + "_ally_1",
                "Союзник-1",
                "Охрана",
                100,
                90,
                10 + 3 * shelter.level,
                6 + 2 * shelter.level,
                Specialization.GUARD),
            new Fighter(
                shelter.id + "_ally_2",
                "Союзник-2",
                shelter.level >= 3 ? "Врач" : "Разведчик",
                100,
                90,
                8 + 2 * shelter.level,
                5 + shelter.level,
                shelter.level >= 3 ? Specialization.MEDIC : Specialization.SCOUT)));
  }
}
