package com.lastdom.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

/** Android lifecycle host for the existing Canvas game. */
public class MainActivity extends Activity {
  private GameView view;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow()
        .setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    view = new GameView(this);
    setContentView(view);
  }

  @Override
  protected void onPause() {
    super.onPause();
    view.save();
  }
}
