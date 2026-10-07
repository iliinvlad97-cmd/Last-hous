package com.lastdom.game;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.view.*;

/** Canvas host, drawing primitives, original touch routing and one-second Handler loop. */
public final class GameView extends View {
  static final int HOME = 0, CITY_MAP = 5;
  static final String VERSION_LABEL = "v0.9.6 • CITY MAP • STAGE 1";
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
            for (int i = 0; i < game.speed; i++) game.advanceMinute();
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
  final CityMapController cityMap = new CityMapController();
  final CityMapRenderer cityMapRenderer = new CityMapRenderer(this);

  public GameView(Context context) {
    super(context);
    shelterBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.shelter_clean);
    fullSceneBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.shelter_full_scene);
    game = new GameController(context.getSharedPreferences("save_v02", 0), this::invalidate);
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
    if (game.event) overlayRenderer.drawEvent(c);
    if (game.jobMenu) overlayRenderer.drawJobMenu(c);
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
    if (e.getAction() != MotionEvent.ACTION_UP) return true;
    float x = e.getX() / scale, y = e.getY() / scale, hh = H / scale;
    if (game.gameOver) {
      if (y > hh / 2) game.reset();
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
          game.people.get(game.selected).job = game.jobs[i];
          game.jobMenu = false;
          game.overlay = 1;
          game.addLog(game.people.get(game.selected).name + ": " + game.jobs[i] + ".");
          game.save();
          invalidate();
          return true;
        }
      }
      game.jobMenu = false;
      invalidate();
      return true;
    }
    if (game.screen == 0 && game.overlay != 0) {
      float top =
          (game.overlay == 3
              ? Math.max(250, hh - 470)
              : game.overlay == 1 ? Math.max(285, hh - 405) : Math.max(300, hh - 390));
      if (y < top || x > 350 && y < top + 55) {
        game.overlay = 0;
        invalidate();
        return true;
      }
      if (game.overlay == 1) {
        if (y >= top + 211 && y <= top + 257) {
          game.jobMenu = true;
          invalidate();
          return true;
        }
        if (y >= top + 266 && y <= top + 309 && x < 210) {
          game.people.get(game.selected).job = "Отдых";
          game.save();
          invalidate();
          return true;
        }
        if (y >= top + 266 && y <= top + 309 && x >= 210) {
          game.overlay = 0;
          invalidate();
          return true;
        }
      } else if (game.overlay == 2) {
        if (y >= top + 202 && y <= top + 250) {
          game.startUpgrade(game.selectedRoom);
          invalidate();
          return true;
        }
        if (y >= top + 259 && y <= top + 302 && x < 210) {
          int idx = -1;
          for (int i = 0; i < game.people.size(); i++)
            if (game.people.get(i).alive) {
              idx = i;
              break;
            }
          if (idx >= 0) {
            game.selected = idx;
            game.jobMenu = true;
          }
          invalidate();
          return true;
        }
        if (y >= top + 259 && y <= top + 302 && x >= 210) {
          game.overlay = 0;
          invalidate();
          return true;
        }
      } else if (game.overlay == 3) {
        float yy = top + 65;
        for (int i = 0; i < Math.min(6, game.people.size()); i++, yy += 59)
          if (y >= yy && y <= yy + 52) {
            game.selected = i;
            game.overlay = 1;
            invalidate();
            return true;
          }
      }
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
        game.selectedRoom = ri;
        game.overlay = 2;
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
