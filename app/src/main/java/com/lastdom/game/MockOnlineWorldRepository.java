package com.lastdom.game;

import java.util.*;

/** Fictional world and atomic local demo commands. Persistence is isolated behind a save store. */
final class MockOnlineWorldRepository implements OnlineWorldRepository {
  private final OnlineDemoSaveStore store;
  private final Snapshot world;
  private OnlineWorldGameplay.Data data;
  private boolean storageError;
  private final OnlineCombatController combatController = new OnlineCombatController();

  MockOnlineWorldRepository() {
    this(new OnlineDemoSaveStore(null));
  }

  MockOnlineWorldRepository(OnlineDemoSaveStore store) {
    this.store = store;
    world = createWorld();
    try {
      data = OnlineDemoSaveStore.decode(store.read(), this);
    } catch (RuntimeException invalidSave) {
      storageError = true;
      data =
          new OnlineWorldGameplay.Data(
              new OnlineInventory(Collections.emptyMap()),
              OnlineWorldGameplay.catalogue(),
              Collections.emptyList(),
              0,
              "");
    }
  }

  public synchronized Snapshot load() {
    if (!storageError) return world;
    return new Snapshot(
        OnlineWorldState.Connection.ERROR,
        world.shelters,
        world.zones,
        world.squads,
        world.streets,
        world.buildings,
        world.towers,
        world.mist);
  }

  public synchronized boolean writable() {
    return !storageError;
  }

  public synchronized OnlineWorldGameplay.Data gameplay() {
    return data;
  }

  private OnlineWorldGameplay.Result publish(OnlineWorldGameplay.Data replacement, String message) {
    if (storageError)
      return new OnlineWorldGameplay.Result(
          false, "Ошибка демо-сохранения. Одиночная игра не затронута.");
    boolean saved;
    try {
      saved = store.write(replacement);
    } catch (RuntimeException writeError) {
      saved = false;
    }
    if (!saved) {
      storageError = true;
      return new OnlineWorldGameplay.Result(
          false, "Не удалось сохранить действие. Повторите после перезапуска.");
    }
    data = replacement;
    return new OnlineWorldGameplay.Result(true, message);
  }

  public synchronized OnlineWorldGameplay.Result execute(String id, OnlineWorldGameplay.Kind kind) {
    if (storageError)
      return new OnlineWorldGameplay.Result(
          false, "Действия заблокированы: ошибка демо-сохранения");
    OnlineWorldGameplay.Offer offer = data.offer(id);
    if (offer == null || offer.kind != kind)
      return new OnlineWorldGameplay.Result(false, "Неизвестное предложение");
    String reason = data.unavailable(offer);
    if (!reason.isEmpty()) return new OnlineWorldGameplay.Result(false, reason);
    OnlineInventory inventory =
        data.inventory.exchange(offer.cost, offer.quantity, offer.reward, offer.output);
    List<OnlineWorldGameplay.Operation> operations = new ArrayList<>(data.operations);
    operations.add(operation(offer, 0));
    return publish(
        new OnlineWorldGameplay.Data(
            inventory,
            data.offers,
            operations,
            data.reputation + offer.reputation,
            data.pvpZoneId,
            data.combat,
            data.civic),
        offer.kind == OnlineWorldGameplay.Kind.HELP
            ? "Помощь отправлена. Репутация +" + offer.reputation
            : "Сделка выполнена. " + offer.summary());
  }

  public synchronized OnlineWorldGameplay.Result preparePvp(String zoneId, boolean consent) {
    if (!consent || !"pvp_frontier".equals(zoneId))
      return new OnlineWorldGameplay.Result(false, "Требуется добровольное подтверждение");
    if (!data.pvpZoneId.isEmpty())
      return new OnlineWorldGameplay.Result(false, "Демо-отряд уже подготовлен");
    return publish(
        new OnlineWorldGameplay.Data(
            data.inventory,
            data.offers,
            data.operations,
            data.reputation,
            zoneId,
            data.combat,
            data.civic),
        "Демо-отряд подготовлен. Бой не запускается.");
  }

  public synchronized OnlineWorldGameplay.Result disablePvp() {
    if (data.pvpZoneId.isEmpty()) return new OnlineWorldGameplay.Result(false, "PvP уже выключено");
    return publish(
        new OnlineWorldGameplay.Data(
            data.inventory,
            data.offers,
            data.operations,
            data.reputation,
            "",
            data.combat,
            data.civic),
        "Демо-подготовка отменена");
  }

  private OnlineWorldGameplay.Result publishCombat(OnlineCombatController.Change change) {
    if (storageError) return new OnlineWorldGameplay.Result(false, "Ошибка демо-сохранения");
    if (change.data == data) return change.result;
    return change.result.success ? publish(change.data, change.result.message) : change.result;
  }

  public synchronized OnlineWorldGameplay.Result startPvp(
      String id,
      String zone,
      List<String> fighters,
      OnlineCombatRules.Tactic tactic,
      long seed,
      boolean confirmed) {
    if (storageError) return new OnlineWorldGameplay.Result(false, "Ошибка демо-сохранения");
    return publishCombat(
        combatController.startPvp(data, id, zone, fighters, tactic, seed, confirmed));
  }

  public synchronized OnlineWorldGameplay.Result startCoop(
      String id, String zone, String ally, List<String> fighters, long seed, boolean confirmed) {
    if (storageError) return new OnlineWorldGameplay.Result(false, "Ошибка демо-сохранения");
    return publishCombat(
        combatController.startCoop(data, world, id, zone, ally, fighters, seed, confirmed));
  }

  public synchronized OnlineWorldGameplay.Result recover(String fighter) {
    return publishCombat(combatController.recover(data, fighter));
  }

  public synchronized boolean advanceMinute() {
    if (storageError) return false;
    OnlineWorldGameplay.Data replacement = combatController.advanceMinute(data);
    replacement = new OnlineCityEventController().advance(replacement);
    return replacement != data && publish(replacement, "").success;
  }

  public synchronized boolean advanceSecond() {
    if (storageError) return false;
    OnlineWorldGameplay.Data replacement = combatController.advanceSecond(data);
    if (data.hasActive()) {
      List<OnlineWorldGameplay.Operation> operations = new ArrayList<>();
      for (OnlineWorldGameplay.Operation operation : data.operations)
        operations.add(operation.active() ? operation.advance() : operation);
      replacement =
          new OnlineWorldGameplay.Data(
              replacement.inventory,
              data.offers,
              operations,
              data.reputation,
              data.pvpZoneId,
              replacement.combat,
              replacement.civic);
    }
    return replacement != data && publish(replacement, "").success;
  }

  public synchronized OnlineWorldGameplay.Result activateCivic() {
    return publishCombat(new OnlineAllianceController().activate(data));
  }

  public synchronized OnlineWorldGameplay.Result createAlliance(String name) {
    return publishCombat(new OnlineAllianceController().create(data, name));
  }

  public synchronized OnlineWorldGameplay.Result invite(String id) {
    return publishCombat(new OnlineAllianceController().invite(data, id));
  }

  public synchronized OnlineWorldGameplay.Result createDemoEvent() {
    return publishCombat(new OnlineCityEventController().spawnDemo(data));
  }

  public synchronized OnlineWorldGameplay.Result startOperation(
      String id,
      String event,
      List<String> fighters,
      List<String> allies,
      long seed,
      boolean confirmed) {
    if (storageError) return new OnlineWorldGameplay.Result(false, "Ошибка демо-сохранения");
    return publishCombat(
        new OnlineCityEventController()
            .start(data, world, id, event, fighters, allies, seed, confirmed));
  }

  OnlineWorldGameplay.Operation operation(OnlineWorldGameplay.Offer offer, int elapsed) {
    String origin = "demo_ember", target = offer.shelterId;
    float[] coordinates;
    switch (target) {
      case "demo_ember":
        origin = "demo_beacon";
        coordinates = new float[] {.23f, .21f, .23f, .315f, .50f, .315f, .50f, .77f, .22f, .77f};
        break;
      case "demo_beacon":
        coordinates = new float[] {.22f, .77f, .50f, .77f, .50f, .315f, .23f, .315f, .23f, .21f};
        break;
      case "demo_foundry":
        coordinates = new float[] {.22f, .77f, .50f, .77f, .50f, .52f, .27f, .52f, .27f, .45f};
        break;
      case "demo_outpost":
        coordinates = new float[] {.22f, .77f, .50f, .77f, .50f, .585f, .75f, .585f, .75f, .70f};
        break;
      default:
        throw new IllegalArgumentException("Unknown demo delivery target");
    }
    return new OnlineWorldGameplay.Operation(
        offer, origin, target, new OnlineRoute(coordinates), elapsed);
  }

  private static Snapshot createWorld() {
    List<OnlineShelter> shelters =
        Arrays.asList(
            new OnlineShelter(
                "demo_ember",
                "Убежище «Искра»",
                2,
                "Стабильный демо-сигнал",
                "Виртуальное убежище в тихом квартале. Его жители поддерживают радиомаяк.",
                .22f,
                .77f),
            new OnlineShelter(
                "demo_beacon",
                "Убежище «Маяк»",
                3,
                "Стабильный демо-сигнал",
                "Учебный узел радиосети среди руин старого жилого района.",
                .23f,
                .21f),
            new OnlineShelter(
                "demo_foundry",
                "Убежище «Литейная»",
                2,
                "Слабый демо-сигнал",
                "Тестовая станция в промышленной зоне. Связь имитирует помехи оборудования.",
                .27f,
                .45f),
            new OnlineShelter(
                "demo_outpost",
                "Убежище «Рубеж»",
                1,
                "Прерывистый демо-сигнал",
                "Виртуальный форпост у заражённой территории. Здесь нет подключённых игроков.",
                .75f,
                .70f));
    List<OnlineZone> zones =
        Arrays.asList(
            new OnlineZone(
                "safe_north",
                "Северный островок",
                OnlineZone.Type.SAFE,
                "Низкая",
                "Безопасный район радиосети. В будущем здесь появятся обмен информацией и помощь.",
                .24f,
                .075f,
                .04f,
                .035f,
                .44f,
                .035f,
                .44f,
                .30f,
                .04f,
                .30f),
            new OnlineZone(
                "safe_south",
                "Тихий квартал",
                OnlineZone.Type.SAFE,
                "Низкая",
                "Второй безопасный район. Демо-убежища не производят ресурсы вашей одиночной игры.",
                .24f,
                .625f,
                .04f,
                .60f,
                .44f,
                .60f,
                .44f,
                .95f,
                .04f,
                .95f),
            new OnlineZone(
                "pve_industry",
                "Промышленный пояс",
                OnlineZone.Type.PVE,
                "Средняя",
                "PvE-район: разрушенные цеха и технические здания. Совместные демо-экспедиции"
                    + " находят материалы и снаряжение.",
                .25f,
                .345f,
                .04f,
                .32f,
                .45f,
                .32f,
                .45f,
                .57f,
                .04f,
                .57f),
            new OnlineZone(
                "pve_infected",
                "Заражённая окраина",
                OnlineZone.Type.PVE,
                "Высокая",
                "Опасная PvE-территория у заброшенных складов. Совместные демо-экспедиции"
                    + " находят медикаменты и снаряжение; участники могут пострадать.",
                .75f,
                .525f,
                .55f,
                .50f,
                .96f,
                .50f,
                .96f,
                .95f,
                .55f,
                .95f),
            new OnlineZone(
                "pvp_frontier",
                "Спорная территория",
                OnlineZone.Type.PVP,
                "Очень высокая",
                "Добровольный PvP-район будущего онлайн-мира. По умолчанию участие выключено.",
                .75f,
                .09f,
                .55f,
                .035f,
                .96f,
                .035f,
                .96f,
                .45f,
                .55f,
                .45f));
    List<OnlineSquad> squads =
        Collections.singletonList(
            new OnlineSquad(
                "demo_patrol",
                "Дозор «Искра»",
                36,
                .22f,
                .77f,
                .50f,
                .77f,
                .50f,
                .52f,
                .27f,
                .52f,
                .27f,
                .45f,
                .27f,
                .52f,
                .50f,
                .52f,
                .50f,
                .77f,
                .22f,
                .77f));
    List<OnlineWorldGeometry.Shape> streets =
        Arrays.asList(
            new OnlineWorldGeometry.Shape(.50f, .015f, .50f, .985f),
            new OnlineWorldGeometry.Shape(.015f, .315f, .985f, .315f),
            new OnlineWorldGeometry.Shape(.015f, .585f, .985f, .585f),
            new OnlineWorldGeometry.Shape(.02f, .77f, .50f, .77f, .98f, .82f),
            new OnlineWorldGeometry.Shape(.27f, .33f, .27f, .585f),
            new OnlineWorldGeometry.Shape(.73f, .03f, .73f, .315f, .85f, .45f),
            new OnlineWorldGeometry.Shape(.08f, .52f, .50f, .52f),
            new OnlineWorldGeometry.Shape(.23f, .21f, .23f, .315f),
            new OnlineWorldGeometry.Shape(.75f, .585f, .75f, .70f));
    List<OnlineWorldGeometry.Block> blocks = new ArrayList<>();
    float[][] rectangles = {
      {.065f, .09f, .07f, .09f}, {.31f, .12f, .08f, .08f}, {.10f, .23f, .065f, .055f},
      {.59f, .15f, .08f, .11f}, {.81f, .13f, .09f, .09f}, {.58f, .36f, .09f, .06f},
      {.085f, .38f, .095f, .09f}, {.33f, .41f, .07f, .075f}, {.10f, .65f, .055f, .08f},
      {.32f, .69f, .08f, .085f}, {.07f, .83f, .09f, .07f}, {.31f, .83f, .075f, .08f},
      {.58f, .62f, .065f, .08f}, {.83f, .66f, .08f, .09f}, {.58f, .85f, .07f, .06f},
      {.82f, .86f, .08f, .045f}
    };
    for (int i = 0; i < rectangles.length; i++) {
      float[] r = rectangles[i];
      blocks.add(new OnlineWorldGeometry.Block(r[0], r[1], r[2], r[3], i == 6 || i == 7));
    }
    return new Snapshot(
        OnlineWorldState.Connection.DEMO,
        shelters,
        zones,
        squads,
        streets,
        blocks,
        Arrays.asList(
            new OnlineWorldGeometry.Point(.50f, .31f), new OnlineWorldGeometry.Point(.50f, .88f)),
        Arrays.asList(
            new OnlineWorldGeometry.Point(.14f, .49f),
            new OnlineWorldGeometry.Point(.67f, .25f),
            new OnlineWorldGeometry.Point(.83f, .88f)));
  }
}
