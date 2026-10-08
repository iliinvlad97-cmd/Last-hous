package com.lastdom.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/** All validation happens before any mutation; UI never assigns jobs or creates squads. */
final class ExpeditionController {
  private final GameController game;

  ExpeditionController(GameController game) {
    this.game = game;
  }

  Resident resident(String id) {
    for (Resident resident : game.people) if (resident.id.equals(id)) return resident;
    return null;
  }

  MapLocation location(String id) {
    for (MapLocation location : game.cityLocations) if (location.id.equals(id)) return location;
    return null;
  }

  Expedition active() {
    for (Expedition expedition : game.expeditions) if (expedition.active()) return expedition;
    return null;
  }

  boolean contains(Resident resident) {
    for (Expedition expedition : game.expeditions)
      if (expedition.active() && expedition.participantIds.contains(resident.id)) return true;
    return false;
  }

  String unavailableReason(Resident resident) {
    if (!resident.alive) return "Погиб";
    if (contains(resident) || resident.job.equals("Экспедиция")) return "В экспедиции";
    if (resident.health <= 0) return "Нет здоровья";
    return "";
  }

  String start(String locationId, Collection<String> ids) {
    MapLocation target = location(locationId);
    if (target == null || target.isLocked()) return "Район не исследован";
    long count = game.expeditions.stream().filter(Expedition::active).count();
    if (count >= ExpeditionConfig.MAX_ACTIVE || game.expeditionPerson >= 0)
      return "Сначала завершите текущую экспедицию";
    if (ids == null || ids.isEmpty()) return "Выберите от 1 до 3 жителей";
    if (ids.size() > ExpeditionConfig.MAX_PARTICIPANTS || new HashSet<>(ids).size() != ids.size())
      return "Выберите от 1 до 3 разных жителей";
    List<Resident> squad = new ArrayList<>();
    for (String id : ids) {
      Resident resident = resident(id);
      if (resident == null) return "Житель больше не существует";
      String reason = unavailableReason(resident);
      if (!reason.isEmpty()) return resident.name + ": " + reason;
      squad.add(resident);
    }
    if (game.gameOver) return "Игра завершена";
    Expedition expedition =
        new Expedition(
            target.id,
            new ArrayList<>(ids),
            (game.day - 1L) * 1440 + game.gameMinute,
            ExpeditionConfig.oneWayMinutes(target),
            0,
            Expedition.State.TRAVELING_TO_TARGET);
    game.expeditions.add(expedition);
    for (Resident resident : squad) {
      resident.job = "Экспедиция";
      resident.status = Resident.Status.ON_EXPEDITION;
    }
    game.addLog("Отряд отправлен: " + target.name + " (" + squad.size() + " чел.).");
    game.save();
    game.invalidate();
    return "";
  }

  boolean advanceMinute() {
    boolean changed = false;
    for (Expedition expedition : game.expeditions) {
      changed |= expedition.state() == Expedition.State.TRAVELING_TO_TARGET;
      if (expedition.advanceMinute()) {
        game.addLog("Отряд прибыл. Исследование локации появится в следующем этапе.");
      }
    }
    return changed;
  }

  void restoreMembership() {
    for (Resident resident : game.people)
      if (contains(resident)) {
        resident.job = "Экспедиция";
        resident.status = Resident.Status.ON_EXPEDITION;
      }
  }
}
