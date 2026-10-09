package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/**
 * Cached wrapped demo cards. Buttons share the controller's geometry and remain above navigation.
 */
final class OnlineWorldPanelRenderer {
  private final GameView view;
  private final List<String> lines = new ArrayList<>();
  private final List<Integer> colors = new ArrayList<>();
  private int revision = -1;
  private float scale;
  private String title = "", subtitle = "";

  OnlineWorldPanelRenderer(GameView view) {
    this.view = view;
  }

  void draw(Canvas c, OnlineWorldGeometry g) {
    OnlineWorldController controller = view.onlineWorld;
    OnlineWorldState state = controller.state;
    if (revision != controller.panelRevision || scale != view.scale) {
      revision = controller.panelRevision;
      scale = view.scale;
      prepare(state);
    }
    controller.panelLineCount = lines.size();
    state.panelScroll = Math.min(state.panelScroll, Math.max(0, lines.size() - g.visibleLines()));
    view.box(c, 0, 0, 420, g.height - 80, opacity(Color.argb(180, 5, 11, 17)), 0);
    view.box(c, 18, g.panelTop, 402, g.panelBottom, opacity(view.panel), 16);
    if (state.confirmationGlow > .01f)
      view.box(
          c,
          34,
          g.panelTop + 72,
          386,
          g.panelTop + 75,
          opacity(OnlineWorldRenderer.alpha(view.good, (int) (state.confirmationGlow * 210))),
          2);
    view.bold(
        c, title, 34, g.panelTop + 34, 16, opacity(state.confirmingPvp ? view.danger : view.text));
    view.bold(c, "×", 372, g.panelTop + 31, 22, opacity(view.muted));
    view.txt(c, subtitle, 34, g.panelTop + 63, 12, opacity(view.accent));
    c.save();
    c.clipRect(view.sy(32), view.sy(g.contentTop - 12), view.sy(390), view.sy(g.contentBottom));
    for (int i = 0; i < g.visibleLines() && i + state.panelScroll < lines.size(); i++)
      view.txt(
          c,
          lines.get(i + state.panelScroll),
          34,
          g.contentTop + i * 19,
          12,
          opacity(colors.get(i + state.panelScroll)));
    c.restore();
    if (lines.size() > g.visibleLines()) {
      view.txt(
          c,
          state.panelScroll == 0 ? "Прокрутите для подробностей ↓" : "↑ Подробности ↓",
          34,
          g.contentBottom + 15,
          10,
          opacity(view.muted));
    }
    if (state.panel == OnlineWorldState.Panel.OBJECT
        && state.shelter() != null
        && controller.demoActionsAvailable()) {
      button(c, g, 34, 204, "ТОРГОВЛЯ", view.accent);
      button(c, g, 216, 386, "ПОМОЩЬ", view.good);
    } else if (state.panel == OnlineWorldState.Panel.TRADE
        || state.panel == OnlineWorldState.Panel.HELP) {
      OnlineWorldGameplay.Offer offer = state.offer();
      boolean available =
          controller.demoActionsAvailable() && state.gameplay.unavailable(offer).isEmpty();
      boolean trade = state.panel == OnlineWorldState.Panel.TRADE;
      button(
          c,
          g,
          34,
          trade ? 300 : 386,
          available ? "ПОДТВЕРДИТЬ" : "НЕДОСТУПНО",
          available ? view.accent : view.panel2);
      if (trade) button(c, g, 310, 386, "ДРУГОЕ", view.panel2);
    } else if (state.panel == OnlineWorldState.Panel.INVENTORY) {
      button(c, g, 34, 386, "ИСТОРИЯ ОПЕРАЦИЙ", view.accent);
    } else if (state.panel == OnlineWorldState.Panel.OBJECT && controller.pvpActionAvailable()) {
      String label =
          state.confirmingPvp
              ? "ПОДТВЕРДИТЬ (ДЕМО)"
              : state.pvpEnabled ? "ВЫКЛЮЧИТЬ PvP (ДЕМО)" : "ВКЛЮЧИТЬ PvP (ДЕМО)";
      button(c, g, 34, 386, label, view.accent);
    }
    view.box(c, 34, g.panelBottom - 66, 386, g.panelBottom - 24, opacity(view.panel2), 10);
    view.bold(
        c,
        state.confirmingPvp
            ? "ОТКАЗАТЬСЯ"
            : state.panel == OnlineWorldState.Panel.TRADE
                    || state.panel == OnlineWorldState.Panel.HELP
                    || state.panel == OnlineWorldState.Panel.HISTORY
                ? "НАЗАД"
                : "ЗАКРЫТЬ",
        155,
        g.panelBottom - 39,
        12,
        opacity(view.text));
  }

  private void prepare(OnlineWorldState state) {
    lines.clear();
    colors.clear();
    OnlineShelter shelter = state.shelter();
    OnlineZone zone = state.zone();
    OnlineSquad squad = state.squad();
    if (state.panel == OnlineWorldState.Panel.INVENTORY) {
      title = "ДЕМО-ИНВЕНТАРЬ";
      subtitle = "Только ресурсы радиосети";
      fact("Репутация: " + state.gameplay.reputation, view.good);
      for (OnlineInventory.Resource resource : OnlineInventory.Resource.values())
        fact(resource.label + ": " + state.gameplay.inventory.amount(resource));
      fact("Операций: " + state.gameplay.operations.size());
      add("Запасы одиночного убежища не используются. Демо-запасы сохраняются отдельно.");
      add(
          "Подтверждённые сделки и помощь выполняются один раз. Маршрут показывает локальную"
              + " доставку уже учтённых ресурсов.");
    } else if (state.panel == OnlineWorldState.Panel.HISTORY) {
      title = "ИСТОРИЯ РАДИОСЕТИ";
      subtitle = "Сохранённые демо-операции";
      if (state.gameplay.operations.isEmpty()) add("Операций пока нет.");
      for (int i = state.gameplay.operations.size() - 1; i >= 0; i--) {
        OnlineWorldGameplay.Operation operation = state.gameplay.operations.get(i);
        fact(
            (operation.offer.kind == OnlineWorldGameplay.Kind.TRADE ? "ТОРГОВЛЯ · " : "ПОМОЩЬ · ")
                + state.shelterName(operation.offer.shelterId),
            view.accent);
        fact(operation.offer.summary());
        add(
            operation.active()
                ? "Доставка в пути: "
                    + operation.elapsedSeconds
                    + " / "
                    + OnlineWorldGameplay.DELIVERY_SECONDS
                    + " с."
                : "Доставка завершена. Эффекты уже учтены.");
      }
      if (!state.gameplay.pvpZoneId.isEmpty()) add("PvP: демо-отряд подготовлен. Бой не запущен.");
    } else if (state.panel == OnlineWorldState.Panel.TRADE
        || state.panel == OnlineWorldState.Panel.HELP) {
      boolean trade = state.panel == OnlineWorldState.Panel.TRADE;
      title = trade ? "ТОРГОВЛЯ · ДЕМО" : "ПОМОЩЬ · ДЕМО";
      subtitle = shelter == null ? "Убежище недоступно" : shelter.name;
      if (!state.result.isEmpty())
        fact(state.result, state.resultSuccess ? view.good : view.accent);
      OnlineWorldGameplay.Offer offer = state.offer();
      if (offer == null) add("Нет доступных предложений.");
      else {
        String reason = state.gameplay.unavailable(offer);
        fact(
            reason.isEmpty() ? "Доступно для подтверждения" : reason,
            reason.isEmpty() ? view.good : view.accent);
        fact(offer.title);
        if (trade) {
          fact("Получить: " + offer.output + " · " + offer.reward.label, view.good);
          fact("Отдать: " + offer.quantity + " · " + offer.cost.label);
        } else {
          fact("Требуется: " + offer.quantity + " · " + offer.cost.label);
          fact("Награда: +" + offer.reputation + " демо-репутации", view.good);
        }
        fact(
            "В демо-запасах: "
                + state.gameplay.inventory.amount(offer.cost)
                + " · "
                + offer.cost.label);
        OnlineWorldGameplay.Operation operation = state.gameplay.operation(offer.id);
        if (operation != null)
          add(
              operation.active()
                  ? "Доставка в пути"
                  : "Доставка завершена. Повторное выполнение запрещено.");
        else
          add(
              "Доставка: 24 секунды локальной симуляции. Обмен и репутация учитываются сразу при"
                  + " подтверждении.");
        add("Одноразовое предложение · только демонстрационные ресурсы.");
        if (trade) add("Кнопка «ДРУГОЕ» переключает предложения этого убежища.");
      }
    } else if (state.panel == OnlineWorldState.Panel.DELIVERY) {
      title = "ДЕМО-ДОСТАВКА";
      subtitle = "Маршрут по улицам радиосети";
      OnlineWorldGameplay.Operation operation = state.delivery();
      if (operation == null) add("Маршрут недоступен");
      else {
        fact("Откуда: " + state.shelterName(operation.originId));
        fact("Куда: " + state.shelterName(operation.targetId));
        fact(
            operation.active()
                ? "В пути: "
                    + operation.elapsedSeconds * 100 / OnlineWorldGameplay.DELIVERY_SECONDS
                    + "%"
                : "Доставка завершена",
            view.good);
        fact(
            "Осталось: "
                + (OnlineWorldGameplay.DELIVERY_SECONDS - operation.elapsedSeconds)
                + " с.");
        add(operation.offer.summary());
        add("Операция уже учтена. Завершение маршрута не начисляет награду повторно.");
      }
    } else if (state.confirmingPvp) {
      title = "ДОБРОВОЛЬНЫЙ PvP";
      subtitle = "Подтверждение участия";
      fact("ДЕМО: сейчас нет боёв и потерь ресурсов.");
      fact("Состав: 3 виртуальных участника");
      for (String member : OnlineWorldGameplay.PVP_SQUAD) fact(member);
      add("Условия: отдельное согласие. Жители одиночного убежища не участвуют.");
      add("В будущем PvP может привести к столкновениям с другими игроками и потерям груза.");
      add("Участие добровольное. Без подтверждения PvP остаётся выключенным.");
      add("Сейчас включается только демо-отметка согласия: боёв, игроков и потерь ресурсов нет.");
      add("Можно отказаться или закрыть карточку. Это не изменит одиночную игру.");
    } else if (shelter != null) {
      title = "ВИРТУАЛЬНОЕ УБЕЖИЩЕ";
      subtitle = shelter.name;
      fact("Уровень: " + shelter.level);
      fact("Связь: " + shelter.linkDescription);
      add(shelter.description);
      add("ДЕМО-РЕЖИМ: это тестовый объект, а не подключённый игрок.");
      fact("ТОРГОВЛЯ И ПОМОЩЬ", view.accent);
      fact("Предложения: 2 одноразовые сделки");
      fact("Помощь: 1 локальный запрос");
      fact("Демо-репутация: " + state.gameplay.reputation, view.good);
      add("Выберите действие кнопками ниже. Баланс и история доступны через «ЗАПАСЫ» на карте.");
    } else if (zone != null) {
      title = "РАЙОН РАДИОСЕТИ";
      subtitle = zone.name;
      if (zone.type == OnlineZone.Type.PVP)
        fact(state.pvpEnabled ? "Участие: демо-отряд подготовлен." : "Участие: PvP выключено.");
      fact(
          "Тип: "
              + (zone.type == OnlineZone.Type.SAFE
                  ? "Безопасный"
                  : zone.type == OnlineZone.Type.PVE ? "PvE" : "Добровольный PvP"));
      fact("Опасность: " + zone.danger);
      add(zone.description);
      if (!state.result.isEmpty())
        fact(state.result, state.resultSuccess ? view.good : view.accent);
      if (zone.type == OnlineZone.Type.PVP) {
        fact("ДЕМО-ОТРЯД", view.accent);
        for (String member : OnlineWorldGameplay.PVP_SQUAD) fact(member);
        add(
            "Условия: добровольное подтверждение. Настоящий бой не запускается; реальные ресурсы и"
                + " жители не затрагиваются.");
      }
      if (zone.type == OnlineZone.Type.PVP)
        add("Реальных боёв и потерь ресурсов нет. Включение требует отдельного подтверждения.");
      else add("На этом этапе район можно только осмотреть. Задания и бои пока недоступны.");
      add("Демо-карта не открывает и не закрывает районы одиночной игры.");
    } else if (squad != null) {
      title = "ДЕМО-ОТРЯД";
      subtitle = squad.name;
      add("Виртуальный патруль движется по демонстрационному маршруту.");
      add("Он не состоит из жителей вашего убежища и не переносит настоящую добычу.");
      add("Движение использует время кадра. Скорость игровой симуляции не влияет на этот патруль.");
    }
  }

  private void add(String paragraph) {
    fact(paragraph);
    lines.add("");
    colors.add(view.text);
  }

  private void button(
      Canvas c, OnlineWorldGeometry g, float left, float right, String label, int color) {
    view.box(c, left, g.panelBottom - 114, right, g.panelBottom - 76, opacity(color), 10);
    view.p.setTextSize(view.sy(12));
    view.p.setTypeface(Typeface.create("sans", Typeface.BOLD));
    float x = (left + right - view.p.measureText(label) / view.scale) / 2;
    view.bold(
        c, label, x, g.panelBottom - 89, 12, opacity(color == view.panel2 ? view.text : view.bg));
  }

  private void fact(String paragraph) {
    fact(paragraph, view.text);
  }

  private void fact(String paragraph, int color) {
    view.p.setTextSize(view.sy(12));
    view.p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
    String line = "";
    for (String word : paragraph.split(" ")) {
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (!line.isEmpty() && view.p.measureText(candidate) > view.sy(350)) {
        lines.add(line);
        colors.add(color);
        line = word;
      } else line = candidate;
    }
    if (!line.isEmpty()) {
      lines.add(line);
      colors.add(color);
    }
  }

  private int opacity(int color) {
    int alpha = Math.round((color >>> 24) * view.onlineWorld.state.cardOpacity);
    return OnlineWorldRenderer.alpha(color, alpha);
  }
}
