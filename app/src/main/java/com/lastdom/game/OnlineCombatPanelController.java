package com.lastdom.game;

import android.view.MotionEvent;
import java.util.*;

/** Selection/input and a cached pixel layout shared with the battle renderer. */
final class OnlineCombatPanelController {
  enum Action {
    INFO,
    FIGHTER,
    TACTIC,
    ALLY,
    REPORT,
    HEALTH
  }

  static final class Row {
    final String title, detail, id;
    final Action action;
    final int index;

    Row(String title, String detail, Action action, String id, int index) {
      this.title = title;
      this.detail = detail;
      this.action = action;
      this.id = id;
      this.index = index;
    }
  }

  static final int ROW_HEIGHT = OnlineUiStyle.ROW_HEIGHT;
  final OnlineWorldController world;
  final OnlinePanelLayout layout = new OnlinePanelLayout();
  final List<String> selected = new ArrayList<>();
  private final List<Row> rows = new ArrayList<>();
  OnlineCombatRules.Tactic tactic = OnlineCombatRules.Tactic.BALANCED;
  String requestId = "", reportId = "", recoveryId = "";
  int allyIndex, scroll, contentHeight, revision = -1;
  boolean confirming, dragging, moved;
  private float startX, startY, lastY;
  double replaySeconds;

  OnlineCombatPanelController(OnlineWorldController world) {
    this.world = world;
  }

  boolean active() {
    switch (world.state.panel) {
      case ROSTER:
      case COMBAT_HISTORY:
      case PVP_PREP:
      case COOP_PREP:
      case BATTLE:
      case COOP_REPORT:
        return true;
      default:
        return false;
    }
  }

  void open(OnlineWorldState.Panel panel) {
    selected.clear();
    confirming = false;
    scroll = 0;
    recoveryId = "";
    requestId = world.state.gameplay.combat.requestId();
    world.openPanel(panel);
  }

  void showReport(String id) {
    if (world.state.gameplay.civic.operation(id) != null) {
      world.civicPanel.showOperation(id);
      return;
    }
    reportId = id;
    OnlineBattleRepository.Battle battle = world.state.gameplay.combat.battle(id);
    confirming = false;
    scroll = 0;
    if (battle != null) {
      world.state.zoneId = battle.zoneId;
      replaySeconds =
          battle.active()
              ? battle.elapsedSeconds
              : battle.report.actions.size() * OnlineCombatRules.ACTION_SECONDS;
      world.openPanel(OnlineWorldState.Panel.BATTLE);
    } else if (world.state.gameplay.combat.expedition(id) != null) {
      world.state.zoneId = world.state.gameplay.combat.expedition(id).zoneId;
      world.openPanel(OnlineWorldState.Panel.COOP_REPORT);
    }
  }

  void frame(double seconds) {
    if (world.state.panel == OnlineWorldState.Panel.BATTLE) replaySeconds += seconds;
  }

  void resumed() {
    OnlineBattleRepository.Battle battle = world.state.gameplay.combat.battle(reportId);
    if (battle != null && battle.active()) replaySeconds = battle.elapsedSeconds;
  }

  String title() {
    switch (world.state.panel) {
      case ROSTER:
        return "ДЕМО-БОЙЦЫ";
      case COMBAT_HISTORY:
        return "БОЕВЫЕ ОТЧЁТЫ";
      case PVP_PREP:
        return confirming ? "ПОДТВЕРЖДЕНИЕ PvP" : "ПОДГОТОВКА PvP";
      case COOP_PREP:
        return confirming ? "ОТПРАВКА PvE" : "СОВМЕСТНАЯ PvE";
      case BATTLE:
        return "ДЕМО-СТОЛКНОВЕНИЕ";
      default:
        return "ОТЧЁТ PvE";
    }
  }

  String subtitle() {
    OnlineZone zone = world.state.zone();
    return zone == null ? "Локальная радиосеть · ONLINE 0.5" : zone.name;
  }

  private void row(String title, String detail) {
    rows.add(new Row(title, detail, Action.INFO, "", 0));
  }

  List<Row> rows() {
    if (revision == world.panelRevision) return rows;
    revision = world.panelRevision;
    rows.clear();
    OnlineWorldState state = world.state;
    OnlineBattleRepository.State data = state.gameplay.combat;
    if (!state.result.isEmpty())
      row(state.resultSuccess ? "Действие выполнено" : "Действие недоступно", state.result);
    switch (state.panel) {
      case ROSTER:
        row(
            "ВОССТАНОВЛЕНИЕ: +"
                + OnlineCombatRules.RECOVER_HEALTH
                + " здоровья, +"
                + OnlineCombatRules.RECOVER_STAMINA
                + " сил",
            "Медикамент для здоровья · вода для выносливости");
        OnlineCombatSquad.Fighter recovery = data.fighter(recoveryId);
        if (recovery != null)
          row(
              "Цена для " + recovery.name,
              "Медикаменты: "
                  + OnlineActionRules.medicine(recovery)
                  + " · вода: "
                  + OnlineActionRules.water(recovery));
        row(
            "Демо-запасы",
            "Медикаменты: "
                + state.gameplay.inventory.amount(OnlineInventory.Resource.MEDICINE)
                + " · Вода: "
                + state.gameplay.inventory.amount(OnlineInventory.Resource.WATER));
        fighters(data, false);
        break;
      case COMBAT_HISTORY:
        if (data.battles.isEmpty() && data.expeditions.isEmpty())
          row("Отчётов пока нет", "Выберите PvP- или PvE-зону на карте");
        for (int id = data.nextId - 1; id >= 1; id--) {
          String job = "online_job_" + id;
          OnlineBattleRepository.Battle battle = data.battle(job);
          OnlineCoopExpedition expedition = data.expedition(job);
          if (battle != null)
            rows.add(
                new Row(
                    "PvP · " + (battle.active() ? "Столкновение" : battle.report.resultLabel()),
                    battle.active() ? "Бой продолжается" : "Завершён · нажмите для просмотра",
                    Action.REPORT,
                    job,
                    0));
          if (expedition != null)
            rows.add(
                new Row(
                    "PvE · " + zoneName(expedition.zoneId),
                    expedition.active()
                        ? "В пути: "
                            + expedition.elapsedMinutes
                            + " / "
                            + expedition.durationMinutes
                            + " мин."
                        : expedition.success
                            ? "Успех · награда уже начислена"
                            : "Неудача · последствия уже применены",
                    Action.REPORT,
                    job,
                    0));
        }
        break;
      case PVP_PREP:
      case COOP_PREP:
        boolean pvp = state.panel == OnlineWorldState.Panel.PVP_PREP;
        row(
            pvp ? "Добровольный локальный бой" : "Совместная демо-экспедиция",
            pvp
                ? "Риск: очень высокий · виртуальные противники"
                : "Время: " + OnlineCombatRules.coopMinutes(state.zoneId) + " игровых минут");
        if (!pvp && state.zone() != null)
          row("Опасность: " + state.zone().danger, "Только демонстрационные участники и ресурсы");
        if (pvp)
          rows.add(
              new Row(
                  "Тактика: " + tactic.label,
                  "Атака "
                      + tactic.attackPercent
                      + "% · защита "
                      + tactic.defensePercent
                      + "% · нажмите",
                  Action.TACTIC,
                  "",
                  0));
        else if (!state.shelters.isEmpty())
          rows.add(
              new Row(
                  "Союзник: " + state.shelters.get(allyIndex % state.shelters.size()).name,
                  "Уровень "
                      + state.shelters.get(allyIndex % state.shelters.size()).level
                      + " · нажмите для смены",
                  Action.ALLY,
                  "",
                  0));
        row(
            "Выбрано: " + selected.size() + " / 3",
            confirming
                ? "ПОДТВЕРДИТЕ: демо-бойцы могут пострадать"
                : "Нажмите на доступных бойцов ниже");
        if (!pvp && !selected.isEmpty()) {
          try {
            row(
                "Шанс успеха: "
                    + OnlineCombatRules.coopChance(
                        state.zoneId,
                        squad(),
                        OnlineCombatSquad.ally(
                            state.shelters.get(allyIndex % state.shelters.size())))
                    + "%",
                "Учитывает здоровье, силы и союзника");
          } catch (IllegalArgumentException unavailable) {
            row("Отряд недоступен", unavailable.getMessage());
          }
        }
        fighters(data, true);
        break;
      case BATTLE:
        OnlineBattleRepository.Battle battle = data.battle(reportId);
        if (battle == null) {
          row("Бой не найден", "");
          break;
        }
        for (int i = 0;
            i < Math.max(battle.report.own.fighters.size(), battle.report.enemy.fighters.size());
            i++) rows.add(new Row("", "", Action.HEALTH, "", i));
        row(
            battle.active() ? "Бой продолжается" : "Итог: " + battle.report.resultLabel(),
            "Правила v" + battle.report.rulesVersion + " · тактика: " + battle.report.tactic.label);
        row("ВАШ ОТРЯД / ВИРТУАЛЬНЫЙ ПРОТИВНИК", "Удары воспроизводятся из сохранённого отчёта");
        row(
            "Последствия",
            battle.effectsApplied
                ? "Здоровье сохранено · выносливость −12"
                : "Применяются один раз после завершения");
        row("Ресурсы одиночной игры не используются", "Наград за повторный просмотр нет");
        break;
      case COOP_REPORT:
        OnlineCoopExpedition e = data.expedition(reportId);
        if (e == null) {
          row("Экспедиция не найдена", "");
          break;
        }
        row(
            e.active()
                ? "Экспедиция выполняется"
                : e.success ? "ЭКСПЕДИЦИЯ УСПЕШНА" : "ЭКСПЕДИЦИЯ НЕ УДАЛАСЬ",
            zoneName(e.zoneId));
        if (!e.active()) {
          StringBuilder lootSummary = new StringBuilder();
          for (OnlineInventory.Resource resource : OnlineInventory.Resource.values())
            if (e.loot.amount(resource) > 0) {
              if (lootSummary.length() > 0) lootSummary.append(" · ");
              lootSummary.append(resource.label).append(" +").append(e.loot.amount(resource));
            }
          row(
              e.success ? "ДОСТАВЛЕНО: " + lootSummary : "Добыча не найдена",
              e.success ? "Награда уже начислена один раз" : "Последствия сохранены · без награды");
        }
        row(
            "Прогресс: " + e.elapsedMinutes * 100 / e.durationMinutes + "%",
            "Осталось: " + (e.durationMinutes - e.elapsedMinutes) + " игровых минут");
        row(
            "Союзник: " + state.shelterName(e.allyId),
            "Вклад в общую силу: " + e.allyContribution + "% · шанс " + e.chance + "%");
        row("ВАШ ОТРЯД", e.active() ? "Занят заданием" : "Выносливость: −" + e.staminaCost);
        for (int i = 0; i < e.own.fighters.size(); i++)
          row(
              e.own.fighters.get(i).name + " · " + e.own.fighters.get(i).role,
              e.active() ? "Участвует в экспедиции" : "Потеря здоровья: " + e.healthLoss.get(i));
        row("ОТРЯД СОЮЗНИКА", e.ally.fighters.size() + " виртуальных участника");
        for (int i = 0; i < e.ally.fighters.size(); i++)
          row(
              e.ally.fighters.get(i).name + " · " + e.ally.fighters.get(i).role,
              e.active()
                  ? "Участвует в экспедиции"
                  : "Здоровье −" + e.allyHealthLoss.get(i) + " · силы −" + e.staminaCost);
        if (!e.active()) {
          row(
              "ДОСТАВЛЕНО В ДЕМО-ИНВЕНТАРЬ",
              e.success ? "Награда уже начислена один раз" : "Награды нет");
          for (OnlineInventory.Resource resource : OnlineInventory.Resource.values())
            if (e.loot.amount(resource) > 0) row(resource.label, "+" + e.loot.amount(resource));
        }
        break;
      default:
        break;
    }
    String blocked = primaryReason();
    if (!blocked.isEmpty())
      rows.add(0, new Row("Действие недоступно", blocked, Action.INFO, "", 0));
    layout.reset(rows.size());
    contentHeight = layout.total();
    return rows;
  }

  private void fighters(OnlineBattleRepository.State data, boolean selection) {
    for (OnlineCombatSquad.Fighter f : data.fighters) {
      String reason = data.unavailable(f.id);
      String prefix =
          selection && selected.contains(f.id)
              ? "✓ "
              : !selection && f.id.equals(recoveryId) ? "› " : "";
      rows.add(
          new Row(
              prefix + f.name + " · " + f.role,
              "Зд "
                  + f.health
                  + " · Силы "
                  + f.stamina
                  + " · А "
                  + f.attack
                  + " · З "
                  + f.defense
                  + " · "
                  + f.specializationLabel()
                  + (reason.isEmpty() ? "" : " · " + reason),
              Action.FIGHTER,
              f.id,
              0));
    }
  }

  private String zoneName(String id) {
    for (OnlineZone zone : world.state.zones) if (zone.id.equals(id)) return zone.name;
    return id;
  }

  private OnlineCombatSquad squad() {
    List<OnlineCombatSquad.Fighter> fighters = new ArrayList<>();
    for (String id : selected) {
      OnlineCombatSquad.Fighter fighter = world.state.gameplay.combat.fighter(id);
      if (fighter == null) throw new IllegalArgumentException("Боец не найден");
      fighters.add(fighter);
    }
    return new OnlineCombatSquad(fighters);
  }

  String primaryReason() {
    if (!world.demoActionsAvailable()) return "Демо-сеть недоступна";
    OnlineWorldState s = world.state;
    if (s.panel == OnlineWorldState.Panel.ROSTER)
      return OnlineActionRules.recovery(s.gameplay, recoveryId);
    if (s.panel == OnlineWorldState.Panel.PVP_PREP || s.panel == OnlineWorldState.Panel.COOP_PREP)
      return OnlineActionRules.mission(
          s.gameplay,
          s.snapshot,
          requestId,
          s.zoneId,
          selected,
          s.shelters.isEmpty() ? "" : s.shelters.get(allyIndex % s.shelters.size()).id,
          s.panel == OnlineWorldState.Panel.PVP_PREP);
    return "";
  }

  private void changed() {
    world.panelRevision++;
    revision = -1;
  }

  private void result(OnlineWorldGameplay.Result result) {
    world.acceptResult(result);
    changed();
  }

  OnlineWorldController.TouchResult touch(int action, float x, float y, OnlineWorldGeometry g) {
    if (action == MotionEvent.ACTION_DOWN) {
      startX = x;
      startY = lastY = y;
      moved = false;
      dragging = y >= g.contentTop - 12 && y <= g.contentBottom;
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (action == MotionEvent.ACTION_MOVE) {
      if (Math.abs(x - startX) > 8 || Math.abs(y - startY) > 8) moved = true;
      if (dragging) {
        rows();
        scroll =
            Math.max(
                0,
                Math.min(
                    Math.max(0, contentHeight - (int) (g.contentBottom - g.contentTop + 12)),
                    scroll + (int) (lastY - y)));
        lastY = y;
      }
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (action == MotionEvent.ACTION_CANCEL) {
      dragging = moved = false;
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (action != MotionEvent.ACTION_UP) return OnlineWorldController.TouchResult.CONSUMED;
    dragging = false;
    if (moved) {
      moved = false;
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (y >= g.height - 78) return OnlineWorldController.TouchResult.NAVIGATION;
    if (g.closeButton(x, y)) {
      if (confirming) {
        confirming = false;
        changed();
      } else if (world.state.panel == OnlineWorldState.Panel.COMBAT_HISTORY)
        open(OnlineWorldState.Panel.ROSTER);
      else world.closeCard();
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (y < g.panelTop || y > g.panelBottom || (x >= 350 && y <= g.panelTop + 52)) {
      world.closeCard();
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (g.secondary(x, y)) {
      switch (world.state.panel) {
        case PVP_PREP:
        case COOP_PREP:
          if (!primaryReason().isEmpty()) {
            result(new OnlineWorldGameplay.Result(false, primaryReason()));
            break;
          }
          if (selected.isEmpty()) {
            result(new OnlineWorldGameplay.Result(false, "Выберите от 1 до 3 бойцов"));
            break;
          }
          if (!confirming) {
            confirming = true;
            changed();
            break;
          }
          String id = requestId;
          OnlineWorldGameplay.Result outcome =
              world.startMission(
                  id,
                  selected,
                  tactic,
                  world.state.panel == OnlineWorldState.Panel.COOP_PREP
                          && !world.state.shelters.isEmpty()
                      ? world.state.shelters.get(allyIndex % world.state.shelters.size()).id
                      : "",
                  true);
          result(outcome);
          if (outcome.success) showReport(id);
          break;
        case ROSTER:
          if (g.secondaryRight(x, y, false)) open(OnlineWorldState.Panel.COMBAT_HISTORY);
          else if (g.secondaryLeft(x, y, false)) {
            if (recoveryId.isEmpty())
              result(new OnlineWorldGameplay.Result(false, "Выберите бойца для восстановления"));
            else result(world.recover(recoveryId));
          }
          break;
        case BATTLE:
          replaySeconds = 0;
          changed();
          break;
        case COOP_REPORT:
          open(OnlineWorldState.Panel.COMBAT_HISTORY);
          break;
        default:
          break;
      }
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (y >= g.contentTop - 12 && y <= g.contentBottom) {
      List<Row> items = rows();
      int index = layout.at(y - (g.contentTop - 12) + scroll);
      if (index >= 0 && index < items.size()) {
        Row row = items.get(index);
        switch (row.action) {
          case FIGHTER:
            if (world.state.panel == OnlineWorldState.Panel.ROSTER) recoveryId = row.id;
            else {
              String reason = world.state.gameplay.combat.unavailable(row.id);
              if (!reason.isEmpty()) {
                result(new OnlineWorldGameplay.Result(false, reason));
                break;
              }
              if (selected.contains(row.id)) selected.remove(row.id);
              else if (selected.size() < 3) selected.add(row.id);
              else {
                result(new OnlineWorldGameplay.Result(false, "Не более трёх бойцов"));
                break;
              }
              confirming = false;
            }
            changed();
            break;
          case TACTIC:
            world.state.result = "";
            tactic = OnlineCombatRules.Tactic.values()[(tactic.ordinal() + 1) % 3];
            confirming = false;
            changed();
            break;
          case ALLY:
            world.state.result = "";
            allyIndex = (allyIndex + 1) % world.state.shelters.size();
            confirming = false;
            changed();
            break;
          case REPORT:
            showReport(row.id);
            break;
          default:
            break;
        }
      }
    }
    return OnlineWorldController.TouchResult.CONSUMED;
  }
}
