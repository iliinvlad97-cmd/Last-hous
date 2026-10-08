package com.lastdom.game;

import android.graphics.Canvas;
import android.graphics.Color;

/** Read-only cached Canvas cards, pinned actions and a shared adaptive input layout. */
final class DefensePanelRenderer {
  final GameView view;
  private long cachedSignature;
  private float cachedHeight;
  DefensePanelContent content;
  private DefensePanelLayout cachedLayout;

  DefensePanelRenderer(GameView view) {
    this.view = view;
  }

  DefensePanelLayout layout() {
    RaidController controller = view.game.raidController;
    double power = controller.defensePower();
    long signature = signature(power);
    if (content == null || signature != cachedSignature) {
      content = new DefensePanelContent(view, power);
      cachedSignature = signature;
      cachedLayout = null;
    }
    float height = view.H / view.scale;
    if (cachedLayout == null || height != cachedHeight) {
      cachedLayout =
          new DefensePanelLayout(
              height, content.height, content.rowHeight, content.listHeaderHeight);
      cachedHeight = height;
    }
    view.defensePanel.contentHeight = content.height;
    view.defensePanel.scroll =
        Math.min(view.defensePanel.scroll, cachedLayout.maxScroll(content.height));
    view.defensePanel.page =
        Math.min(view.defensePanel.page, view.defensePanel.pages(cachedLayout) - 1);
    return cachedLayout;
  }

  // No formatted strings or temporary collections on unchanged frames.
  private long signature(double power) {
    DefensePanelController ui = view.defensePanel;
    RaidController controller = view.game.raidController;
    long h = ui.mode.ordinal();
    h = 31 * h + Float.floatToIntBits(view.scale);
    h = 31 * h + ui.message.hashCode();
    h = 31 * h + view.game.mats;
    h = 31 * h + view.game.roomLevels[4];
    h = 31 * h + (view.game.roomUpgradeController.active() == null ? 0 : 1);
    h = 31 * h + controller.durability;
    h = 31 * h + Double.doubleToLongBits(power);
    RaidState raid = ui.report;
    if (raid != null) {
      h = 31 * h + raid.id.hashCode();
      h = 31 * h + raid.phase.ordinal();
      h = 31 * h + raid.elapsed;
      h = 31 * h + raid.attackElapsed;
      h = 31 * h + Double.doubleToLongBits(raid.defenseAtStart);
      h = 31 * h + raid.defenders.hashCode();
      h = 31 * h + raid.injuries.hashCode();
      h = 31 * h + raid.victimNames.hashCode();
      h = 31 * h + raid.damageApplied;
      h = 31 * h + raid.foodLost;
      h = 31 * h + raid.waterLost;
      h = 31 * h + raid.materialsLost;
      h = 31 * h + raid.moraleChange;
      h = 31 * h + (raid.outcome == null ? -1 : raid.outcome.ordinal());
    }
    RaidState active = controller.active();
    h = 31 * h + (active == null ? -1 : active.phase.ordinal());
    if (controller.repair != null) {
      h = 31 * h + controller.repair.elapsed;
      h = 31 * h + controller.repair.builderId.hashCode();
      h = 31 * h + (controller.repairing() ? 1 : 0);
    }
    for (int i = 0; i < view.game.people.size(); i++) {
      Resident r = view.game.people.get(i);
      h = 31 * h + r.id.hashCode();
      h = 31 * h + r.name.hashCode();
      h = 31 * h + r.role.hashCode();
      h = 31 * h + r.job.hashCode();
      h = 31 * h + r.health;
      h = 31 * h + r.fatigue;
      h = 31 * h + r.morale;
      h = 31 * h + r.hunger;
      h = 31 * h + r.thirst;
      h = 31 * h + r.skill;
      h = 31 * h + (r.alive ? 1 : 0);
      h = 31 * h + (view.game.isBuilding(r) ? 1 : 0);
      h = 31 * h + (view.game.isOnExpedition(r) ? 1 : 0);
      h = 31 * h + (view.game.survivalController.treating(r) ? 1 : 0);
      h = 31 * h + (view.game.survivalController.protectedRest(r) ? 1 : 0);
    }
    return h;
  }

  void draw(Canvas c) {
    DefensePanelLayout l = layout();
    DefensePanelController ui = view.defensePanel;
    view.overlayRenderer.dimForOverlay(c);
    view.box(c, 18, l.top, 402, l.bottom, view.panel, 18);
    boolean attack = ui.report != null && ui.report.phase == RaidState.Phase.ATTACK;
    int accent = attack ? view.danger : view.accent;
    view.box(c, 30, l.top + 12, 34, l.top + 60, accent, 2);
    bold(c, content.title, 44, l.top + 33, 14, view.text);
    bold(c, "×", 372, l.top + 31, 22, view.muted);
    text(c, content.subtitle, 44, l.top + 55, 12, view.muted);
    if (attack) {
      // Three short alarm marks, advancing with battle time. Pausing also freezes this effect.
      for (int i = 0; i < 3; i++)
        view.box(
            c,
            334 + i * 10,
            l.top + 46,
            339 + i * 10,
            l.top + 58,
            ui.report.attackElapsed % 3 == i ? view.danger : view.panel2,
            2);
    }
    if (ui.list()) residents(c, l);
    else {
      c.save();
      c.clipRect(view.sy(30), view.sy(l.bodyTop), view.sy(390), view.sy(l.bodyBottom));
      float y = l.bodyTop - ui.scroll;
      for (DefensePanelContent.Block block : content.blocks) {
        if (y + block.height >= l.bodyTop && y <= l.bodyBottom) block(c, block, y);
        y += block.height + 8;
      }
      c.restore();
      if (ui.report != null && !ui.report.active() && ui.mode != DefensePanelController.Mode.REPAIR)
        text(
            c,
            l.maxScroll(content.height) > 0
                ? "ДАЛЬНЕЙШИЕ ДЕЙСТВИЯ • ↑ ↓ Прокрутите"
                : "ДАЛЬНЕЙШИЕ ДЕЙСТВИЯ",
            34,
            l.actionTop - 5,
            9,
            view.muted);
      else if (l.maxScroll(content.height) > 0)
        text(c, "↑ ↓ Прокрутите", 34, l.actionTop - 5, 9, view.muted);
    }
    button(c, 30, l.actionTop, 390, 48, content.primary, content.primaryEnabled, true);
    if (content.secondaryVisible) {
      button(c, 30, l.secondaryTop, 206, 44, content.secondary, content.secondaryEnabled, false);
      button(c, 214, l.secondaryTop, 390, 44, "ЗАКРЫТЬ", true, false);
    } else button(c, 30, l.secondaryTop, 390, 44, "ЗАКРЫТЬ", true, false);
  }

  private void block(Canvas c, DefensePanelContent.Block block, float top) {
    if (block.leftValue != null) {
      view.box(c, 30, top, 206, top + block.height, view.panel2, 10);
      view.box(c, 214, top, 390, top + block.height, view.panel2, 10);
      text(c, block.leftLabel, 42, top + 21, 10, view.muted);
      bold(c, block.leftValue, 42, top + 51, block.leftValueSize, block.color);
      text(c, block.rightLabel, 226, top + 21, 11, view.muted);
      bold(c, block.rightValue, 226, top + 51, block.rightValueSize, view.text);
      return;
    }
    view.box(c, 30, top, 390, top + block.height, view.panel2, 10);
    if (block.percent >= 0) {
      bold(c, block.title, 42, top + 21, 11, view.text);
      bold(c, block.percentLabel, 347, top + 21, 11, block.color);
      view.box(c, 42, top + 33, 378, top + 41, view.bg, 4);
      if (block.percent > 0)
        view.box(c, 42, top + 33, 42 + 336 * block.percent / 100f, top + 41, block.color, 4);
      float y = top + 61;
      for (String line : block.lines) {
        text(c, line, 42, y, 12, view.muted);
        y += 16;
      }
    } else {
      float y = top + 21;
      for (String line : block.headings) {
        bold(c, line, 42, y, 11, block.color);
        y += 15;
      }
      y += 5;
      for (String line : block.lines) {
        text(c, line, 42, y, 13, view.text);
        y += 18;
      }
    }
  }

  private void residents(Canvas c, DefensePanelLayout l) {
    DefensePanelController ui = view.defensePanel;
    text(c, content.listSummary, 34, l.bodyTop + 18, 12, view.accent);
    float messageTop = l.bodyTop + 34;
    for (DefensePanelContent.Block message : content.blocks) {
      block(c, message, messageTop);
      messageTop += message.height + 8;
    }
    for (int row = 0; row < l.capacity; row++) {
      int index = ui.page * l.capacity + row;
      if (index >= content.residents.size()) break;
      DefensePanelContent.ResidentRow resident = content.residents.get(index);
      float top = l.rowTop + row * l.rowHeight;
      view.box(
          c,
          30,
          top,
          390,
          top + l.rowHeight - DefensePanelLayout.ROW_GAP,
          resident.selected ? Color.rgb(74, 57, 36) : view.panel2,
          9);
      float y = top + 19;
      for (String line : resident.title) {
        bold(c, line, 42, y, 12, resident.available ? view.text : view.muted);
        y += 16;
      }
      text(c, resident.condition, 42, y, 11, view.muted);
      y += 16;
      for (String line : resident.detail) {
        text(c, line, 42, y, 11, resident.available ? view.good : view.danger);
        y += 16;
      }
    }
    text(c, "‹ Назад", 34, l.pageY, 12, view.muted);
    text(c, (ui.page + 1) + " / " + ui.pages(l), 190, l.pageY, 12, view.text);
    text(c, "Далее ›", 318, l.pageY, 12, view.muted);
  }

  private void text(Canvas c, String value, float x, float y, float size, int color) {
    label(c, value, x, y, size, color, false);
  }

  private void bold(Canvas c, String value, float x, float y, float size, int color) {
    label(c, value, x, y, size, color, true);
  }

  private void label(
      Canvas c, String value, float x, float y, float size, int color, boolean bold) {
    view.p.setStyle(android.graphics.Paint.Style.FILL);
    view.p.setTypeface(bold ? DefensePanelContent.BOLD : DefensePanelContent.NORMAL);
    view.p.setTextSize(view.sy(size));
    view.p.setColor(color);
    c.drawText(value, view.sy(x), view.sy(y), view.p);
  }

  private void button(
      Canvas c,
      float left,
      float top,
      float right,
      float height,
      String text,
      boolean enabled,
      boolean primary) {
    view.box(c, left, top, right, top + height, primary && enabled ? view.accent : view.panel2, 10);
    bold(
        c,
        text,
        left + 12,
        top + height / 2 + 5,
        12,
        !enabled ? view.muted : primary ? view.bg : view.text);
  }

  boolean noticeVisible() {
    return !view.defensePanel.open
        && view.game.overlay == 0
        && !view.game.event
        && !view.game.jobMenu
        && !view.game.gameOver
        && !view.cityMap.eventPanel
        && !view.cityMap.expeditionPanel
        && view.game.raidController.active() != null;
  }

  void notice(Canvas c) {
    if (!noticeVisible()) return;
    RaidState raid = view.game.raidController.active();
    float top = view.H / view.scale - 144;
    view.box(c, 18, top, 402, top + 52, view.panel, 10);
    bold(
        c,
        raid.phase == RaidState.Phase.ATTACK ? "НАПАДЕНИЕ НА УБЕЖИЩЕ" : "УГРОЗА УБЕЖИЩУ",
        30,
        top + 20,
        12,
        view.danger);
    text(
        c,
        raid.enemyName() + " • " + raid.remaining() + " мин. • нажмите для обороны",
        30,
        top + 39,
        11,
        view.accent);
  }

  void indicator(Canvas c, float top, float bottom) {
    RaidController controller = view.game.raidController;
    if (controller.durability == 100 && controller.active() == null && !controller.repairing())
      return;
    float[] rect = ShelterGeometry.fullSceneRoomRect(4, top, bottom);
    float x = rect[0] + 4, y = rect[1] + 26;
    view.box(c, x, y, x + 94, y + 18, Color.argb(205, 14, 18, 22), 5);
    bold(
        c,
        "ЩИТ " + controller.durability + "%" + (controller.active() != null ? " !" : ""),
        x + 6,
        y + 12,
        9,
        controller.active() != null ? view.danger : view.accent);
    if (controller.repairing()) {
      view.box(c, x, y + 20, x + 94, y + 37, Color.argb(205, 14, 18, 22), 5);
      text(
          c,
          "РЕМОНТ " + controller.repair.elapsed * 100 / RaidConfig.REPAIR_MINUTES + "%",
          x + 6,
          y + 32,
          9,
          view.accent);
    }
  }
}
