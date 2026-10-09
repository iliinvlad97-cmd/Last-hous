package com.lastdom.game;

import java.util.List;

interface OnlineCityEventRepository {
  default OnlineWorldGameplay.Result createDemoEvent() {
    return new OnlineWorldGameplay.Result(false, "События недоступны");
  }

  default OnlineWorldGameplay.Result startOperation(
      String job,
      String event,
      List<String> fighters,
      List<String> allies,
      long seed,
      boolean confirmed) {
    return new OnlineWorldGameplay.Result(false, "Операции недоступны");
  }
}
