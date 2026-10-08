package com.lastdom.game;

/** Room-specific manual assignments using the existing resident jobs and availability rules. */
final class RoomAssignmentController {
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
    if (game.survivalController.treating(resident)) return "На лечении";
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
}
