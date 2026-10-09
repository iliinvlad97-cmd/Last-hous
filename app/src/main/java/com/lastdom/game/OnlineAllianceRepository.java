package com.lastdom.game;

interface OnlineAllianceRepository {
  default OnlineWorldGameplay.Result activateCivic() {
    return new OnlineWorldGameplay.Result(false, "Союзы недоступны");
  }

  default OnlineWorldGameplay.Result createAlliance(String name) {
    return new OnlineWorldGameplay.Result(false, "Союзы недоступны");
  }

  default OnlineWorldGameplay.Result invite(String shelterId) {
    return new OnlineWorldGameplay.Result(false, "Приглашения недоступны");
  }
}
