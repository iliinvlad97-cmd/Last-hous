package com.lastdom.game;

/** Room-specific manual assignments using the existing resident jobs and availability rules. */
final class RoomAssignmentController {
  enum Category {
    ASSIGNED("УЖЕ НАЗНАЧЕН"),
    FREE("СВОБОДЕН"),
    TRANSFER("ДОСТУПЕН ДЛЯ ПЕРЕВОДА");
    final String label;

    Category(String label) {
      this.label = label;
    }
  }

  private final GameController game;

  RoomAssignmentController(GameController game) {
    this.game = game;
  }

  boolean supports(int room, String job) {
    return RoomUpgradeConfig.valid(room)
        && (game.roomJobs[room].equals(job) || room == 1 && "Вода".equals(job));
  }

  String unavailableReason(int room, String job, Resident resident) {
    if (!supports(room, job)) return "Работа не относится к этой комнате";
    if (game.gameOver) return "Игра завершена";
    String reason = game.roomUpgradeController.unavailableReason(resident);
    if (!reason.isEmpty()) return reason;
    if (resident.job.equals(job)) return "Уже назначен";
    if (!job.equals("Отдых")) {
      if (resident.health < SurvivalConfig.TREAT_START) return "Нуждается в лечении или отдыхе";
      if (resident.fatigue >= SurvivalConfig.REST_START) return "Нуждается в отдыхе";
    }
    return "";
  }

  String assign(int room, String job, String residentId) {
    Resident resident = game.expeditionController.resident(residentId);
    String reason = unavailableReason(room, job, resident);
    if (!reason.isEmpty()) return reason;
    int index = game.people.indexOf(resident);
    if (!game.assignJob(index, job)) return "Назначение недоступно: житель занят";
    game.addLog(resident.name + ": " + job + " — " + game.rooms[room] + ".");
    game.save();
    return "";
  }

  Category category(int room, String job, Resident resident) {
    if (!supports(room, job)
        || resident == null
        || !resident.alive
        || game.isOnExpedition(resident)
        || game.isStoryBusy(resident)
        || game.isBuilding(resident)
        || game.isDefending(resident)) return null;
    if (resident.job.equals(job)) return Category.ASSIGNED;
    if (!unavailableReason(room, job, resident).isEmpty()) return null;
    return availableCategory(resident);
  }

  Category availableCategory(Resident resident) {
    return resident.job.equals("Отдых") && !game.survivalController.protectedRest(resident)
        ? Category.FREE
        : Category.TRANSFER;
  }

  String unassign(int room, String job, String residentId) {
    Resident resident = game.expeditionController.resident(residentId);
    if (resident == null || !supports(room, job) || !resident.job.equals(job))
      return "Назначение уже изменилось";
    if (job.equals("Отдых")) return "Житель уже отдыхает";
    String reason = unavailableReason(5, "Отдых", resident);
    if (!reason.isEmpty()) return reason;
    if (!game.assignJob(game.people.indexOf(resident), "Отдых")) return "Житель занят";
    game.addLog(resident.name + ": снят с работы «" + job + "», назначен отдых.");
    game.save();
    return "";
  }

  boolean removable(Resident resident) {
    return resident != null
        && !resident.job.equals("Отдых")
        && unavailableReason(5, "Отдых", resident).isEmpty();
  }

  String profession(Resident r, int room, String job, boolean building, boolean defense) {
    if (building)
      return (r.role.equals("Инженер") || r.role.equals("Механик"))
          ? "Профиль подходит; время стройки фиксировано"
          : "Можно строить; скорость одинакова";
    if (defense && r.role.equals("Охрана"))
      return "Профиль подходит: +" + RaidConfig.GUARD_BONUS_PERCENT + "% силы охраны";
    int bonus =
        ((room == 0 || room == 3 || room == 1 && job.equals("Еда"))
                ? ProductionConfig.professionPercent(r, room)
                : 100)
            - 100;
    if (bonus > 0) return "Профиль подходит: +" + bonus + "% к вкладу";
    if (job.equals("Лечение") && r.role.equals("Врач")
        || job.equals("Охрана") && r.role.equals("Охрана"))
      return "Профиль подходит; вклад по навыку";
    return "Можно назначить; спец. бонуса нет";
  }

  String transfer(Resident resident) {
    if (availableCategory(resident) == Category.FREE) return "Назначение свободного жителя";
    int room = game.homeRoomFor(resident);
    return "Перевод: " + (room >= 0 ? game.rooms[room] : resident.job);
  }
}
