package com.lastdom.game;

/** Owns validation, payment, builder membership and phase completion in the existing game state. */
final class RoomUpgradeController {
  private final GameController game;

  RoomUpgradeController(GameController game) {
    this.game = game;
  }

  RoomUpgradeTask active() {
    for (RoomUpgradeTask task : game.roomUpgrades) if (!task.completed) return task;
    return null;
  }

  RoomUpgradeTask taskFor(Resident resident) {
    for (RoomUpgradeTask task : game.roomUpgrades)
      if (!task.completed && task.builderId.equals(resident.id)) return task;
    return null;
  }

  String unavailableReason(Resident resident) {
    if (resident == null) return "Житель больше не существует";
    if (!resident.alive || resident.health <= 0) return "Нет здоровья";
    if (game.isOnExpedition(resident)) return "В экспедиции";
    if (game.isBuilding(resident)) return "Занят строительством";
    if (game.isDefending(resident)) return "Назначен на оборону";
    if (game.survivalController.treating(resident)) return "На лечении";
    if (game.survivalController.protectedRest(resident)) return "Восстанавливает силы в спальне";
    return "";
  }

  String blockedReason(int room) {
    if (!RoomUpgradeConfig.valid(room)) return "Комната не существует";
    if (game.gameOver) return "Игра завершена";
    if (game.roomLevels[room] >= RoomUpgradeConfig.MAX_LEVEL) return "Максимальный уровень";
    if (game.roomUpgrades.stream().filter(task -> !task.completed).count()
        >= RoomUpgradeConfig.MAX_ACTIVE) return "Сначала завершите текущее строительство";
    if (game.raidController.repairing()) return "Сначала завершите ремонт баррикад";
    long missing =
        (long)
                game.productionController.upgradeQuote(
                        RoomUpgradeConfig.cost(room, game.roomLevels[room] + 1))
                    .cost
            - game.mats;
    return missing > 0 ? "Не хватает материалов: " + missing : "";
  }

  String start(int room, String builderId) {
    String reason = blockedReason(room);
    if (!reason.isEmpty()) return reason;
    Resident builder = game.expeditionController.resident(builderId);
    reason = unavailableReason(builder);
    if (!reason.isEmpty()) return reason;
    int target = game.roomLevels[room] + 1;
    ProductionController.UpgradeQuote quote =
        game.productionController.upgradeQuote(RoomUpgradeConfig.cost(room, target));
    int cost = quote.cost;
    RoomUpgradeTask task =
        new RoomUpgradeTask(
            java.util.UUID.randomUUID().toString(),
            room,
            target,
            builder.id,
            game.expeditionController.now(),
            RoomUpgradeConfig.minutes(room, target),
            cost,
            true,
            false,
            0,
            false);
    game.productionController.applyUpgradeQuote(quote);
    game.mats -= cost;
    game.roomUpgrades.add(task);
    builder.status = Resident.Status.BUILDING;
    builder.job = "Строительство";
    syncLegacyFields();
    game.addLog(
        "Начато улучшение: "
            + game.rooms[room]
            + " → ур. "
            + target
            + ". Строитель: "
            + builder.name
            + ". Материалы -"
            + cost
            + ".");
    if (quote.saved > 0)
      game.addLog("Мастерская сэкономила " + quote.saved + " материалов при улучшении.");
    game.save();
    game.invalidate();
    return "";
  }

  boolean advanceMinute() {
    boolean changed = false;
    for (RoomUpgradeTask task : game.roomUpgrades) {
      if (task.completed) continue;
      if (task.legacy && task.builderId.isEmpty()) {
        for (Resident resident : game.people)
          if (unavailableReason(resident).isEmpty()) {
            task.builderId = resident.id;
            resident.status = Resident.Status.BUILDING;
            resident.job = "Строительство";
            break;
          }
        if (task.builderId.isEmpty()) continue;
      }
      task.elapsed = Math.min(task.duration, task.elapsed + 1);
      if (task.remaining() == 0) complete(task);
      changed = true;
    }
    syncLegacyFields();
    return changed;
  }

  void complete(RoomUpgradeTask task) {
    if (!game.roomUpgrades.contains(task)
        || task.builderId.isEmpty()
        || task.completed
        || !task.costPaid
        || task.remaining() != 0) return;
    if (game.roomLevels[task.room] != task.targetLevel - 1) return;
    game.roomLevels[task.room] = task.targetLevel;
    game.roomCondition[task.room] = 100;
    task.completed = true;
    Resident builder = game.expeditionController.resident(task.builderId);
    if (builder != null) {
      builder.status = Resident.Status.HOME;
      builder.job = "Отдых";
    }
    syncLegacyFields();
    game.addLog(
        game.rooms[task.room]
            + " улучшена до ур. "
            + task.targetLevel
            + ". "
            + RoomUpgradeConfig.effect(task.room)
            + ": "
            + percent(task.room)
            + "%.");
  }

  int percent(int room) {
    return RoomUpgradeConfig.percent(room, game.roomLevels[room]);
  }

  int scale(int room, int base, String channel) {
    return game.productionRemainders.take(base, percent(room), channel);
  }

  void syncLegacyFields() {
    RoomUpgradeTask task = active();
    game.buildingRoom = task == null ? -1 : task.room;
    game.buildRemaining = task == null ? 0 : task.remaining();
  }

  void restoreMembership() {
    for (RoomUpgradeTask task : game.roomUpgrades)
      if (!task.completed) {
        Resident builder = game.expeditionController.resident(task.builderId);
        if (builder != null) {
          builder.status = Resident.Status.BUILDING;
          builder.job = "Строительство";
        }
      }
    syncLegacyFields();
  }
}
