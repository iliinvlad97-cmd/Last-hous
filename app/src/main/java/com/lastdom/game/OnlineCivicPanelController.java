package com.lastdom.game;

import android.view.MotionEvent;
import java.util.*;

/** Radio sections, scrollable selection and explicit confirmation; no resource mutations. */
final class OnlineCivicPanelController {
  enum Action {
    INFO,
    NAME,
    KEYS,
    ALPHABET,
    DELETE,
    INVITE,
    EVENT,
    FIGHTER,
    ALLY,
    BACK
  }

  static final int ROW = 64;

  static final class Row {
    final String title, detail, id;
    final Action action;

    Row(String title, String detail, Action action, String id) {
      this.title = title;
      this.detail = detail;
      this.action = action;
      this.id = id;
    }
  }

  static final class Motion {
    final OnlineRoute route;
    final float[] position = new float[2];
    double progress, target;

    Motion(OnlineRoute route, double progress) {
      this.route = route;
      this.progress = this.target = progress;
      route.position(progress, position, 0);
    }

    void frame(double dt) {
      progress += (target - progress) * (1 - Math.exp(-dt / .35));
      route.position(progress, position, 0);
    }
  }

  final OnlineWorldController world;
  final List<String> fighters = new ArrayList<>(), allies = new ArrayList<>();
  final Map<String, Motion> routes = new LinkedHashMap<>();
  private final List<Row> rows = new ArrayList<>();
  String name = "РАДИО-СОЮЗ", eventId = "", reportId = "", requestId = "";
  boolean latin, confirming, moved, dragging;
  int scroll, revision = -1;
  private float startX, startY, lastY;

  OnlineCivicPanelController(OnlineWorldController world) {
    this.world = world;
  }

  boolean active() {
    switch (world.state.panel) {
      case ALLIANCE:
      case ALLIANCE_NAME:
      case EVENTS:
      case CITY_EVENT:
      case OPERATION_PREP:
      case OPERATION_REPORT:
        return true;
      default:
        return false;
    }
  }

  void section(OnlineWorldState.Panel panel) {
    world.closeCard();
    world.activateCivic();
    open(panel);
  }

  void open(OnlineWorldState.Panel panel) {
    confirming = false;
    scroll = 0;
    world.openPanel(panel);
  }

  void showEvent(String id) {
    eventId = id;
    open(OnlineWorldState.Panel.CITY_EVENT);
  }

  void showOperation(String id) {
    reportId = id;
    open(OnlineWorldState.Panel.OPERATION_REPORT);
  }

  void sync() {
    Set<String> active = new HashSet<>();
    for (OnlineCoopOperation op : world.state.gameplay.civic.operations)
      if (op.state == OnlineCoopOperation.State.ACTIVE) {
        active.add(op.id);
        OnlineCoopExpedition e = world.state.gameplay.combat.expedition(op.id);
        double p = e.elapsedMinutes / (double) e.durationMinutes;
        Motion motion = routes.get(op.id);
        if (motion == null)
          routes.put(op.id, new Motion(op.route(world.state.gameplay.civic.event(op.eventId)), p));
        else motion.target = p;
      }
    routes.keySet().retainAll(active);
  }

  void frame(double dt) {
    for (Motion route : routes.values()) route.frame(dt);
  }

  void info(String title, String detail) {
    rows.add(new Row(title, detail, Action.INFO, ""));
  }

  void action(String title, String detail, Action a, String id) {
    rows.add(new Row(title, detail, a, id));
  }

  List<Row> rows() {
    if (revision == world.panelRevision) return rows;
    revision = world.panelRevision;
    rows.clear();
    OnlineWorldGameplay.Data d = world.state.gameplay;
    OnlineCivicState c = d.civic;
    OnlineAlliance alliance = c.alliance;
    if (!world.state.result.isEmpty())
      info(world.state.resultSuccess ? "ГОТОВО" : "ДЕЙСТВИЕ НЕДОСТУПНО", world.state.result);
    switch (world.state.panel) {
      case ALLIANCE:
        info("СОЮЗЫ · ДЕМО-РЕЖИМ", "Виртуальные убежища, без сервера");
        if (alliance == null) {
          action("Название: " + name, "Нажмите, чтобы ввести своё название", Action.NAME, "");
          info("Создать демонстрационный союз", "После создания пригласите виртуальные убежища");
        } else {
          info(
              alliance.name,
              "Рейтинг: " + alliance.rating() + " · Репутация: " + alliance.reputation());
          info(
              "Ваш вклад: "
                  + alliance.member(OnlineAllianceMember.PLAYER).contribution
                  + " пунктов",
              "Ваша репутация: "
                  + alliance.member(OnlineAllianceMember.PLAYER).reputation
                  + " · Успешных операций: "
                  + alliance.successes());
          info("УЧАСТНИКИ И ПРИГЛАШЕНИЯ", "Приглашение не даёт наград");
          for (OnlineAllianceMember member : alliance.members) {
            String status = status(member.invitation);
            info(
                member.shelterId.equals(OnlineAllianceMember.PLAYER)
                    ? "Ваш демо-пост"
                    : world.state.shelterName(member.shelterId),
                status
                    + " · Вклад "
                    + member.contribution
                    + " п. · Репутация "
                    + member.reputation
                    + (member.invitation == OnlineAllianceMember.Invitation.PENDING
                        ? " · ответ через " + Math.max(0, member.resolveMinute - c.minute) + " мин."
                        : ""));
          }
          info("ВИРТУАЛЬНЫЕ УБЕЖИЩА", "Нажмите на доступное приглашение");
          for (OnlineShelter shelter : world.state.shelters) {
            OnlineAllianceMember m = alliance.member(shelter.id);
            action(
                shelter.name,
                m == null || m.invitation == OnlineAllianceMember.Invitation.REJECTED
                    ? OnlineAllianceController.rule(shelter.id)
                    : status(m.invitation),
                Action.INVITE,
                shelter.id);
          }
        }
        break;
      case ALLIANCE_NAME:
        info("НАЗВАНИЕ: " + name, "2–28 символов · клавиатура прокручивается");
        action(
            latin ? "РУССКИЕ БУКВЫ" : "ЛАТИНСКИЕ БУКВЫ",
            "Переключить раскладку",
            Action.ALPHABET,
            "");
        String alphabet =
            latin
                ? "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-"
                : "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ0123456789._-";
        for (int i = 0; i < alphabet.length(); i += 7)
          action(alphabet.substring(i, Math.min(i + 7, alphabet.length())), "", Action.KEYS, "");
        action("ПРОБЕЛ", "Добавить пробел", Action.KEYS, "space");
        action("УДАЛИТЬ СИМВОЛ", "Убрать последний символ", Action.DELETE, "");
        action("ОЧИСТИТЬ", "Начать ввод заново", Action.DELETE, "clear");
        break;
      case EVENTS:
        info(
            "ГОРОДСКИЕ СОБЫТИЯ",
            "Демо-минута "
                + c.minute
                + " · следующее через "
                + Math.max(0, c.nextSpawn - c.minute)
                + " мин.");
        if (c.events.isEmpty())
          info("Событий пока нет", "Расписание: первое через 15 минут, затем каждые 120");
        for (int i = c.events.size() - 1; i >= 0; i--) {
          OnlineCityEvent e = c.events.get(i);
          action(
              e.type.name,
              eventStatus(e.state)
                  + " · "
                  + e.type.danger
                  + (e.state == OnlineCityEvent.State.AVAILABLE
                      ? " · осталось " + Math.max(0, e.expiresMinute - c.minute) + " мин."
                      : ""),
              Action.EVENT,
              e.id);
        }
        break;
      case CITY_EVENT:
        OnlineCityEvent event = c.event(eventId);
        if (event == null) {
          info("Событие не найдено", "");
          break;
        }
        info(event.type.name, event.type.description);
        info(
            "РАЙОН: " + zoneName(event.type.zoneId),
            "Опасность: " + event.type.danger + " · " + eventStatus(event.state));
        info(
            "Появилось: минута " + event.appearedMinute,
            "Действует до "
                + event.expiresMinute
                + " · осталось "
                + Math.max(0, event.expiresMinute - c.minute)
                + " мин.");
        info(
            "ОПЕРАЦИЯ: " + event.type.operation,
            "Продолжительность: "
                + OnlineCombatRules.coopMinutes(event.type.zoneId)
                + " игровых минут");
        info(
            "Демо-награда при успехе",
            event.type.zoneId.equals("pve_industry")
                ? "Материалы и снаряжение · возможны повреждения"
                : "Медикаменты и снаряжение · повышенный риск повреждений");
        info(
            "Требуется союз и принятые приглашения",
            "Собственный демо-отряд: 1–3 бойца · 1–2 союзника");
        info("НАГРАДЫ И ВКЛАД", "Только по завершении; повторный просмотр ничего не начисляет");
        break;
      case OPERATION_PREP:
        OnlineCityEvent target = c.event(eventId);
        if (target == null) {
          info("Событие не найдено", "");
          break;
        }
        info(
            target.type.operation,
            "Опасность: "
                + target.type.danger
                + " · "
                + OnlineCombatRules.coopMinutes(target.type.zoneId)
                + " игровых минут");
        info(
            "Выбрано: " + fighters.size() + " бойцов, " + allies.size() + " союзников",
            confirming
                ? "ПОДТВЕРДИТЕ: демо-бойцы могут пострадать"
                : "Нажмите на доступных участников");
        if (!fighters.isEmpty() && !allies.isEmpty()) {
          try {
            OnlineCombatSquad own = own(),
                ally = OnlineCityEventController.allies(world.state.snapshot, allies);
            info(
                "Шанс успеха: " + OnlineCombatRules.coopChance(target.type.zoneId, own, ally) + "%",
                "Сила отряда: "
                    + Math.round(own.strength())
                    + " · союзников: "
                    + Math.round(ally.strength()));
          } catch (IllegalArgumentException invalid) {
            info("Отряд недоступен", invalid.getMessage());
          }
        }
        info("ВАШИ ДЕМО-БОЙЦЫ", "Участники других заданий недоступны");
        for (OnlineCombatSquad.Fighter f : d.combat.fighters)
          action(
              (fighters.contains(f.id) ? "✓ " : "") + f.name + " · " + f.role,
              "Здоровье "
                  + f.health
                  + " · Силы "
                  + f.stamina
                  + (d.combat.unavailable(f.id).isEmpty()
                      ? ""
                      : " · " + d.combat.unavailable(f.id)),
              Action.FIGHTER,
              f.id);
        info("СОЮЗНЫЕ УБЕЖИЩА", "Выберите 1–2 принятых участника");
        if (alliance != null)
          for (OnlineShelter shelter : world.state.shelters) {
            OnlineAllianceMember m = alliance.member(shelter.id);
            if (m != null && m.invitation == OnlineAllianceMember.Invitation.ACCEPTED)
              action(
                  (allies.contains(shelter.id) ? "✓ " : "") + shelter.name,
                  "Уровень "
                      + shelter.level
                      + " · "
                      + (allyUnavailable(shelter.id).isEmpty()
                          ? "виртуальный отряд доступен"
                          : allyUnavailable(shelter.id)),
                  Action.ALLY,
                  shelter.id);
          }
        break;
      case OPERATION_REPORT:
        OnlineCoopOperation operation = c.operation(reportId);
        if (operation == null) {
          info("Отчёт не найден", "");
          break;
        }
        OnlineCoopExpedition trip = d.combat.expedition(operation.id);
        OnlineCityEvent source = c.event(operation.eventId);
        boolean active = operation.state == OnlineCoopOperation.State.ACTIVE;
        info(
            source.type.operation,
            active
                ? "ОПЕРАЦИЯ ВЫПОЛНЯЕТСЯ"
                : trip.success ? "ОПЕРАЦИЯ УСПЕШНА" : "ОПЕРАЦИЯ НЕ УДАЛАСЬ");
        info(
            "Прогресс: " + trip.elapsedMinutes * 100 / trip.durationMinutes + "%",
            "Осталось: " + (trip.durationMinutes - trip.elapsedMinutes) + " игровых минут");
        if (!active)
          info(
              trip.success
                  ? "Награда уже начислена один раз"
                  : "Награды нет · последствия сохранены",
              trip.success
                  ? "Ваша репутация +10 · каждому союзнику +5"
                  : "Репутация и вклад не начислены");
        for (Map.Entry<String, Integer> share : operation.report.shares.entrySet())
          info(
              share.getKey().equals(OnlineAllianceMember.PLAYER)
                  ? "Ваш отряд"
                  : world.state.shelterName(share.getKey()),
              "Вклад в силу: "
                  + share.getValue()
                  + "%"
                  + (active ? "" : " · учтён: " + (trip.success ? share.getValue() : 0)));
        info("ВАШИ УЧАСТНИКИ", active ? "Заняты операцией" : "Выносливость −" + trip.staminaCost);
        for (int i = 0; i < trip.own.fighters.size(); i++)
          info(
              trip.own.fighters.get(i).name,
              active ? "Участвует" : "Здоровье −" + trip.healthLoss.get(i));
        info("ВИРТУАЛЬНЫЕ СОЮЗНИКИ", "Состав сохранён при отправке");
        for (int i = 0; i < trip.ally.fighters.size(); i++)
          info(
              trip.ally.fighters.get(i).name,
              active
                  ? "Участвует"
                  : "Здоровье −" + trip.allyHealthLoss.get(i) + " · силы −" + trip.staminaCost);
        if (!active)
          for (OnlineInventory.Resource r : OnlineInventory.Resource.values())
            if (trip.loot.amount(r) > 0)
              info(r.label, "+" + trip.loot.amount(r) + " в демо-запасы");
        break;
      default:
        break;
    }
    return rows;
  }

  static String status(OnlineAllianceMember.Invitation status) {
    return status == OnlineAllianceMember.Invitation.ACCEPTED
        ? "ПРИНЯТО"
        : status == OnlineAllianceMember.Invitation.REJECTED ? "ОТКЛОНЕНО" : "ОЖИДАЕТ ОТВЕТА";
  }

  static String eventStatus(OnlineCityEvent.State state) {
    switch (state) {
      case AVAILABLE:
        return "ДОСТУПНО";
      case ACTIVE:
        return "ОПЕРАЦИЯ";
      case COMPLETED:
        return "ЗАВЕРШЕНО";
      default:
        return "ИСТЕКЛО";
    }
  }

  String zoneName(String id) {
    for (OnlineZone z : world.state.zones) if (z.id.equals(id)) return z.name;
    return id;
  }

  OnlineCombatSquad own() {
    List<OnlineCombatSquad.Fighter> selected = new ArrayList<>();
    for (String id : fighters) selected.add(world.state.gameplay.combat.fighter(id));
    return new OnlineCombatSquad(selected);
  }

  private String allyUnavailable(String id) {
    for (OnlineShelter shelter : world.state.shelters)
      if (shelter.id.equals(id))
        for (OnlineCombatSquad.Fighter f : OnlineCombatSquad.ally(shelter).fighters)
          if (world.state.gameplay.combat.busy(f.id)) return "отряд союзника уже занят";
    return "";
  }

  String primary() {
    switch (world.state.panel) {
      case ALLIANCE:
        return world.state.gameplay.civic.alliance == null ? "СОЗДАТЬ СОЮЗ" : "ГОРОДСКИЕ СОБЫТИЯ";
      case ALLIANCE_NAME:
        return "СОХРАНИТЬ НАЗВАНИЕ";
      case EVENTS:
        return "ДЕМО-СОБЫТИЕ";
      case CITY_EVENT:
        OnlineCityEvent e = world.state.gameplay.civic.event(eventId);
        return e != null && !e.operationId.isEmpty()
            ? e.state == OnlineCityEvent.State.ACTIVE ? "ПРОГРЕСС ОПЕРАЦИИ" : "ОТЧЁТ ОПЕРАЦИИ"
            : "СОВМЕСТНАЯ ОПЕРАЦИЯ";
      case OPERATION_PREP:
        return confirming ? "ОТПРАВИТЬ ОПЕРАЦИЮ" : "К ПОДТВЕРЖДЕНИЮ";
      default:
        return "ГОРОДСКИЕ СОБЫТИЯ";
    }
  }

  void change() {
    world.panelRevision++;
    revision = -1;
  }

  void result(OnlineWorldGameplay.Result result) {
    world.acceptResult(result);
    change();
  }

  private void submit() {
    OnlineWorldGameplay.Data d = world.state.gameplay;
    switch (world.state.panel) {
      case ALLIANCE:
        if (d.civic.alliance == null) result(world.createAlliance(name));
        else open(OnlineWorldState.Panel.EVENTS);
        break;
      case ALLIANCE_NAME:
        open(OnlineWorldState.Panel.ALLIANCE);
        break;
      case EVENTS:
        result(world.spawnEvent());
        break;
      case CITY_EVENT:
        OnlineCityEvent e = d.civic.event(eventId);
        if (e == null) break;
        if (!e.operationId.isEmpty()) showOperation(e.operationId);
        else if (e.state != OnlineCityEvent.State.AVAILABLE)
          result(new OnlineWorldGameplay.Result(false, "Срок события истёк"));
        else if (d.civic.alliance == null)
          result(
              new OnlineWorldGameplay.Result(false, "Сначала создайте союз и пригласите убежище"));
        else {
          fighters.clear();
          allies.clear();
          requestId = d.combat.requestId();
          open(OnlineWorldState.Panel.OPERATION_PREP);
        }
        break;
      case OPERATION_PREP:
        if (fighters.isEmpty() || allies.isEmpty()) {
          result(new OnlineWorldGameplay.Result(false, "Выберите бойцов и принятых союзников"));
          break;
        }
        if (!confirming) {
          confirming = true;
          change();
          break;
        }
        OnlineWorldGameplay.Result outcome =
            world.startOperation(requestId, eventId, fighters, allies);
        result(outcome);
        if (outcome.success) showOperation(requestId);
        break;
      default:
        open(OnlineWorldState.Panel.EVENTS);
        break;
    }
  }

  OnlineWorldController.TouchResult touch(int action, float x, float y, OnlineWorldGeometry g) {
    float bottom = g.height - 148;
    if (action == MotionEvent.ACTION_DOWN) {
      startX = x;
      startY = lastY = y;
      moved = false;
      dragging = y >= 112 && y <= bottom;
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (action == MotionEvent.ACTION_MOVE) {
      if (Math.abs(x - startX) > 8 || Math.abs(y - startY) > 8) moved = true;
      if (dragging) {
        scroll =
            Math.max(
                0,
                Math.min(
                    Math.max(0, rows().size() * ROW - (int) (bottom - 112)),
                    scroll + (int) (lastY - y)));
        lastY = y;
      }
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (action == MotionEvent.ACTION_CANCEL) {
      moved = dragging = false;
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (action != MotionEvent.ACTION_UP) return OnlineWorldController.TouchResult.CONSUMED;
    dragging = false;
    if (moved) {
      moved = false;
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (y >= g.height - 78) return OnlineWorldController.TouchResult.NAVIGATION;
    if (x >= 20 && x <= 96 && y >= 12 && y <= 56) return OnlineWorldController.TouchResult.BACK;
    if (x >= 300 && x <= 400 && y >= 12 && y <= 56) {
      world.openPanel(OnlineWorldState.Panel.INVENTORY);
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (y >= 60 && y <= 108) {
      if (x >= 20 && x <= 140) world.closeCard();
      else if (x >= 146 && x <= 266) open(OnlineWorldState.Panel.ALLIANCE);
      else if (x >= 272 && x <= 400) open(OnlineWorldState.Panel.EVENTS);
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (y >= g.height - 142 && y <= g.height - 86 && x >= 34 && x <= 386) {
      submit();
      return OnlineWorldController.TouchResult.CONSUMED;
    }
    if (x < 34 || x > 386 || y < 112 || y > bottom)
      return OnlineWorldController.TouchResult.CONSUMED;
    int index = (int) ((y - 112 + scroll) / ROW);
    List<Row> list = rows();
    if (index < 0 || index >= list.size()) return OnlineWorldController.TouchResult.CONSUMED;
    Row row = list.get(index);
    switch (row.action) {
      case NAME:
        open(OnlineWorldState.Panel.ALLIANCE_NAME);
        break;
      case ALPHABET:
        latin = !latin;
        change();
        break;
      case DELETE:
        if (row.id.equals("clear")) name = "";
        else if (!name.isEmpty()) name = name.substring(0, name.length() - 1);
        change();
        break;
      case KEYS:
        if (name.length() < 28) {
          if (row.id.equals("space")) name += " ";
          else {
            int col = Math.min(6, (int) ((x - 34) / 50.28f));
            if (col < row.title.length()) name += row.title.charAt(col);
          }
        }
        change();
        break;
      case INVITE:
        result(world.invite(row.id));
        break;
      case EVENT:
        showEvent(row.id);
        break;
      case FIGHTER:
        String reason = world.state.gameplay.combat.unavailable(row.id);
        if (!reason.isEmpty()) result(new OnlineWorldGameplay.Result(false, reason));
        else if (fighters.contains(row.id)) fighters.remove(row.id);
        else if (fighters.size() < 3) fighters.add(row.id);
        else result(new OnlineWorldGameplay.Result(false, "Не более трёх бойцов"));
        confirming = false;
        change();
        break;
      case ALLY:
        String unavailable = allyUnavailable(row.id);
        if (!unavailable.isEmpty()) {
          result(new OnlineWorldGameplay.Result(false, unavailable));
          break;
        }
        if (allies.contains(row.id)) allies.remove(row.id);
        else if (allies.size() < 2) allies.add(row.id);
        else result(new OnlineWorldGameplay.Result(false, "Не более двух союзников"));
        confirming = false;
        change();
        break;
      default:
        break;
    }
    return OnlineWorldController.TouchResult.CONSUMED;
  }
}
