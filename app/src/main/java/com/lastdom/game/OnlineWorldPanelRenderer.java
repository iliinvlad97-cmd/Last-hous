package com.lastdom.game;

import android.graphics.*;
import java.util.*;

/**
 * Cached wrapped demo cards. Buttons share the controller's geometry and remain above navigation.
 */
final class OnlineWorldPanelRenderer {
  private final GameView view;
  private final List<String> lines = new ArrayList<>();
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
    view.bold(
        c, title, 34, g.panelTop + 34, 16, opacity(state.confirmingPvp ? view.danger : view.text));
    view.bold(c, "×", 372, g.panelTop + 31, 22, opacity(view.muted));
    view.txt(c, subtitle, 34, g.panelTop + 63, 12, opacity(view.accent));
    c.save();
    c.clipRect(view.sy(32), view.sy(g.contentTop - 12), view.sy(390), view.sy(g.contentBottom));
    for (int i = 0; i < g.visibleLines() && i + state.panelScroll < lines.size(); i++)
      view.txt(
          c, lines.get(i + state.panelScroll), 34, g.contentTop + i * 19, 12, opacity(view.text));
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
    if (controller.pvpActionAvailable()) {
      view.box(c, 34, g.panelBottom - 114, 386, g.panelBottom - 76, opacity(view.accent), 10);
      String label =
          state.confirmingPvp
              ? "ПОДТВЕРДИТЬ (ДЕМО)"
              : state.pvpEnabled ? "ВЫКЛЮЧИТЬ PvP (ДЕМО)" : "ВКЛЮЧИТЬ PvP (ДЕМО)";
      view.bold(c, label, 62, g.panelBottom - 89, 12, opacity(view.bg));
    }
    view.box(c, 34, g.panelBottom - 66, 386, g.panelBottom - 24, opacity(view.panel2), 10);
    view.bold(
        c,
        state.confirmingPvp ? "ОТКАЗАТЬСЯ" : "ЗАКРЫТЬ",
        155,
        g.panelBottom - 39,
        12,
        opacity(view.text));
  }

  private void prepare(OnlineWorldState state) {
    lines.clear();
    OnlineShelter shelter = state.shelter();
    OnlineZone zone = state.zone();
    OnlineSquad squad = state.squad();
    if (state.confirmingPvp) {
      title = "ДОБРОВОЛЬНЫЙ PvP";
      subtitle = "Подтверждение участия";
      fact("ДЕМО: сейчас нет боёв и потерь ресурсов.");
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
      add("БУДУЩИЕ ДЕЙСТВИЯ");
      add("Торговля — после появления сервера.");
      add("Помощь — после появления сервера.");
      add("Информация — эта локальная карточка.");
      add("Обмен ресурсами, помощь и отправка жителей сейчас недоступны.");
    } else if (zone != null) {
      title = "РАЙОН РАДИОСЕТИ";
      subtitle = zone.name;
      if (zone.type == OnlineZone.Type.PVP)
        fact(
            state.pvpEnabled
                ? "Участие: только демо-согласие включено."
                : "Участие: PvP выключено.");
      fact(
          "Тип: "
              + (zone.type == OnlineZone.Type.SAFE
                  ? "Безопасный"
                  : zone.type == OnlineZone.Type.PVE ? "PvE" : "Добровольный PvP"));
      fact("Опасность: " + zone.danger);
      add(zone.description);
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
  }

  private void fact(String paragraph) {
    view.p.setTextSize(view.sy(12));
    view.p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
    String line = "";
    for (String word : paragraph.split(" ")) {
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (!line.isEmpty() && view.p.measureText(candidate) > view.sy(350)) {
        lines.add(line);
        line = word;
      } else line = candidate;
    }
    if (!line.isEmpty()) lines.add(line);
  }

  private int opacity(int color) {
    int alpha = Math.round((color >>> 24) * view.onlineWorld.state.cardOpacity);
    return OnlineWorldRenderer.alpha(color, alpha);
  }
}
