package com.lastdom.game;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.view.*;

/** Canvas host, drawing primitives, original touch routing and one-second Handler loop. */
public final class GameView extends View {
  static final int HOME = 0, CITY_MAP = 5;
  static final String VERSION_LABEL = "v1.0.0 • SHELTER DEFENSE • STAGE 7";
  Paint p = new Paint(3), stroke = new Paint(3);
  Bitmap shelterBitmap, fullSceneBitmap;
  Handler timer = new Handler();
  int W, H;
  float scale = 1f;
  int bg = Color.rgb(14, 16, 20),
      panel = Color.rgb(28, 31, 37),
      panel2 = Color.rgb(40, 44, 51),
      text = Color.rgb(238, 234, 224),
      muted = Color.rgb(166, 166, 160),
      accent = Color.rgb(213, 143, 70),
      danger = Color.rgb(190, 72, 65),
      good = Color.rgb(101, 160, 104),
      blue = Color.rgb(88, 132, 166);
  Runnable tick =
      new Runnable() {
        public void run() {
          if (!game.paused && !game.gameOver && !game.event) {
            for (int i = 0; i < game.speed && !game.event && !game.gameOver; i++)
              game.advanceMinute();
            if (game.screen == CITY_MAP
                || (game.expeditionController.report() != null
                    && (game.expeditionController.report().state() == Expedition.State.COMPLETED
                        || game.expeditionController.report().state()
                            == Expedition.State.AWAITING_DECISION))) cityMap.openPendingReport();
            defensePanel.pending();
            invalidate();
          }
          timer.postDelayed(this, 1000);
        }
      };

  final GameController game;
  final ShelterRenderer shelterRenderer = new ShelterRenderer(this);
  final ResidentRenderer residentRenderer = new ResidentRenderer(this);
  final HudRenderer hudRenderer = new HudRenderer(this);
  final OverlayRenderer overlayRenderer = new OverlayRenderer(this);
  final ResidentNeedsRenderer residentNeedsRenderer = new ResidentNeedsRenderer(this);
  final ResidentNeedsPanelController residentNeedsPanel = new ResidentNeedsPanelController(this);
  final DefensePanelController defensePanel = new DefensePanelController(this);
  final DefensePanelRenderer defenseRenderer = new DefensePanelRenderer(this);
  final CityMapController cityMap;
  final RoomUpgradePanelController roomUpgradePanel;
  final RoomUpgradeRenderer roomUpgradeRenderer = new RoomUpgradeRenderer(this);
  final CityMapRenderer cityMapRenderer = new CityMapRenderer(this);
  final ExpeditionRenderer expeditionRenderer = new ExpeditionRenderer(this);
  final ExpeditionEventRenderer expeditionEventRenderer = new ExpeditionEventRenderer(this);
  final ExpeditionPreparationRenderer expeditionPreparationRenderer =
      new ExpeditionPreparationRenderer(this);

  public GameView(Context context) {
    super(context);
    shelterBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.shelter_clean);
    fullSceneBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.shelter_full_scene);
    game = new GameController(context.getSharedPreferences("save_v02", 0), this::invalidate);
    cityMap = new CityMapController(game);
    roomUpgradePanel = new RoomUpgradePanelController(game);
    roomUpgradePanel.defenseOpen = defensePanel::openStatus;
    Expedition restored = game.expeditionController.active();
    if (restored != null && restored.state() == Expedition.State.AWAITING_DECISION)
      cityMap.openPendingReport();
    defensePanel.pending();
    timer.postDelayed(tick, 1000);
  }

  public void save() {
    game.save();
  }

  float sy(float y) {
    return y * scale;
  }

  void txt(Canvas c, String s, float x, float y, float size, int col) {
    p.setStyle(Paint.Style.FILL);
    p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
    p.setTextSize(sy(size));
    p.setColor(col);
    c.drawText(s, sy(x), sy(y), p);
  }

  void bold(Canvas c, String s, float x, float y, float size, int col) {
    p.setStyle(Paint.Style.FILL);
    p.setTypeface(Typeface.create("sans", Typeface.BOLD));
    p.setTextSize(sy(size));
    p.setColor(col);
    c.drawText(s, sy(x), sy(y), p);
  }

  void box(Canvas c, float l, float t, float r, float b, int col, float rad) {
    p.setStyle(Paint.Style.FILL);
    p.setColor(col);
    c.drawRoundRect(sy(l), sy(t), sy(r), sy(b), sy(rad), sy(rad), p);
  }

  void bar(Canvas c, float l, float t, float r, float h, int v, int col) {
    box(c, l, t, r, t + h, Color.rgb(52, 55, 61), 4);
    box(c, l, t, l + (r - l) * Math.max(0, Math.min(100, v)) / 100f, t + h, col, 4);
  }

  @Override
  protected void onDraw(Canvas c) {
    W = getWidth();
    H = getHeight();
    scale = W / 420f;
    c.drawColor(bg);
    if (game.screen == 0) shelterRenderer.drawMain(c);
    else if (game.screen == 1) overlayRenderer.drawSurvivor(c);
    else if (game.screen == 2) overlayRenderer.drawJournal(c);
    else if (game.screen == 3) overlayRenderer.drawRooms(c);
    else if (game.screen == 4) overlayRenderer.drawRoomDetail(c);
    else overlayRenderer.drawMap(c);
    if (game.overlay == 0
        && !game.event
        && !cityMap.eventPanel
        && !cityMap.expeditionPanel
        && !defenseRenderer.noticeVisible()
        && !defensePanel.open) residentNeedsRenderer.notice(c);
    defenseRenderer.notice(c);
    if (game.screen != CITY_MAP && cityMap.expeditionPanel)
      expeditionRenderer.drawPanel(c, new CityMapLayout(H / scale));
    if (cityMap.eventPanel) expeditionEventRenderer.draw(c, new CityMapLayout(H / scale));
    if (game.event) overlayRenderer.drawEvent(c);
    if (game.jobMenu) overlayRenderer.drawJobMenu(c);
    if (defensePanel.open) defenseRenderer.draw(c);
    if (game.gameOver) overlayRenderer.drawGameOver(c);
  }

  void wrap(Canvas c, String s, float x, float y, float max, float size, int col, float step) {
    p.setTextSize(sy(size));
    String[] ws = s.split(" ");
    String line = "";
    float yy = y;
    for (String w : ws) {
      if (p.measureText(line + w) > sy(max - x)) {
        txt(c, line, x, yy, size, col);
        yy += step;
        line = "";
      }
      line += w + " ";
    }
    txt(c, line, x, yy, size, col);
  }

  int compactResidentAt(float x, float y) {
    if (y < 578 || y > 640) return -1;
    float left = 14, step = 78;
    int i = (int) ((x - left) / step);
    if (i < 0 || i >= Math.min(5, game.people.size())) return -1;
    float local = x - (left + i * step);
    return local <= 72 ? i : -1;
  }

  private void openCityMap() {
    cityMap.closeSelection();
    game.screen = CITY_MAP;
    game.overlay = 0;
    cityMap.openPendingReport();
    invalidate();
  }

  private boolean navigate(float x, float y, float logicalHeight) {
    if (y <= logicalHeight - 78) return false;
    int index = (int) ((x - 18) / 77);
    if (index == 0) {
      game.screen = HOME;
      game.overlay = 0;
      cityMap.closeSelection();
    } else if (index == 1) {
      game.screen = 2;
      game.overlay = 0;
      cityMap.closeSelection();
    } else if (index == 2) {
      openCityMap();
    } else if (index == 3) {
      game.screen = HOME;
      game.overlay = 3;
      cityMap.closeSelection();
    } else if (index == 4) {
      if (game.paused) {
        game.paused = false;
        game.speed = 1;
      } else if (game.speed == 1) game.speed = 2;
      else if (game.speed == 2) game.speed = 4;
      else {
        game.paused = true;
        game.speed = 1;
      }
      game.save();
    }
    invalidate();
    return true;
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    if (defensePanel.open && !game.gameOver) {
      RoomUpgradeLayout defenseLayout = new RoomUpgradeLayout(H / scale);
      if (defensePanel.scrollTouch(e.getAction(), e.getY() / scale, defenseLayout)) {
        invalidate();
        return true;
      }
      if (e.getAction() == MotionEvent.ACTION_UP)
        defensePanel.touch(e.getX() / scale, e.getY() / scale, defenseLayout);
      invalidate();
      return true;
    }
    if (e.getAction() == MotionEvent.ACTION_UP && defenseRenderer.noticeVisible()) {
      float top = H / scale - 144;
      if (e.getX() / scale >= 18
          && e.getX() / scale <= 402
          && e.getY() / scale >= top
          && e.getY() / scale <= top + 52) {
        defensePanel.openStatus();
        invalidate();
        return true;
      }
    }
    if ((game.screen == HOME && (game.overlay == 1 || game.overlay == 3) || game.screen == 1)
        && !game.event
        && !game.gameOver
        && !game.jobMenu
        && !cityMap.eventPanel
        && !cityMap.expeditionPanel) {
      if (residentNeedsPanel.scrollTouch(
          e.getAction(), e.getY() / scale, new ResidentNeedsLayout(H / scale))) {
        invalidate();
        return true;
      }
    }
    if ((game.screen == HOME && game.overlay == 2 || game.screen == 4)
        && !game.event
        && !game.gameOver
        && !game.jobMenu
        && !cityMap.eventPanel
        && !cityMap.expeditionPanel) {
      RoomUpgradeLayout layout = new RoomUpgradeLayout(H / scale);
      if (roomUpgradePanel.scrollTouch(e.getAction(), e.getY() / scale, layout)) {
        invalidate();
        return true;
      }
    }
    if ((game.screen == CITY_MAP || cityMap.expeditionPanel || cityMap.eventPanel)
        && !game.event
        && !game.gameOver
        && !game.jobMenu) {
      if (cityMap.scrollTouch(e.getAction(), e.getY() / scale, new CityMapLayout(H / scale))) {
        invalidate();
        return true;
      }
    }
    if (e.getAction() != MotionEvent.ACTION_UP) return true;
    float x = e.getX() / scale, y = e.getY() / scale, hh = H / scale;
    if (game.gameOver) {
      if (y > hh / 2) {
        defensePanel.open = false;
        game.reset();
      }
      return true;
    }
    if (game.event) {
      float b = hh - 84, by = b - 98;
      if (y >= by && y <= by + 39) game.choose(0);
      else if (y >= by + 48 && y <= by + 87) game.choose(1);
      return true;
    }
    if (game.jobMenu) {
      float t = 110;
      for (int i = 0; i < game.jobs.length; i++) {
        float yy = t + 55 + i * 45;
        if (y >= yy && y <= yy + 36) {
          boolean assigned = game.assignJob(game.selected, game.jobs[i]);
          game.jobMenu = false;
          game.overlay = 1;
          game.addLog(
              assigned
                  ? game.people.get(game.selected).name + ": " + game.jobs[i] + "."
                  : "Назначение недоступно: житель занят.");
          game.save();
          invalidate();
          return true;
        }
      }
      game.jobMenu = false;
      invalidate();
      return true;
    }
    if (game.screen != CITY_MAP && (cityMap.expeditionPanel || cityMap.eventPanel)) {
      cityMap.onTouch(x, y, new CityMapLayout(hh));
      invalidate();
      return true;
    }
    if (game.screen == HOME && game.overlay == 2 || game.screen == 4) {
      roomUpgradePanel.touch(x, y, new RoomUpgradeLayout(hh));
      invalidate();
      return true;
    }
    if (game.screen == HOME && (game.overlay == 1 || game.overlay == 3) || game.screen == 1) {
      residentNeedsPanel.touch(x, y, new ResidentNeedsLayout(hh));
      invalidate();
      return true;
    }
    if (game.screen == 0) {
      float sceneTop = 116f, sceneBottom = Math.max(610f, hh - 72f);
      float sceneH = sceneBottom - sceneTop;
      int livePerson = residentRenderer.shelterResidentAt(x, y);
      if (livePerson >= 0) {
        game.selected = livePerson;
        game.overlay = 1;
        invalidate();
        return true;
      }
      if (y >= sceneTop + sceneH * .08f && y <= sceneTop + sceneH * .27f && x >= 145 && x <= 275) {
        openCityMap();
        return true;
      }
      int ri = ShelterGeometry.fullSceneRoomAt(x, y, sceneTop, sceneBottom);
      if (ri >= 0) {
        roomUpgradePanel.open(ri);
        invalidate();
        return true;
      }
      navigate(x, y, hh);
    } else if (game.screen == 2) {
      if (y > hh - 80) {
        game.screen = 0;
        invalidate();
      }
    } else if (game.screen == CITY_MAP) {
      CityMapController.TouchResult result = cityMap.onTouch(x, y, new CityMapLayout(hh));
      if (result == CityMapController.TouchResult.HOME) {
        game.screen = HOME;
        game.overlay = 0;
        cityMap.closeSelection();
      } else if (result == CityMapController.TouchResult.NONE) {
        navigate(x, y, hh);
      }
      invalidate();
    } else {
      game.screen = 0;
      game.overlay = 0;
      invalidate();
    }
    return true;
  }
}
