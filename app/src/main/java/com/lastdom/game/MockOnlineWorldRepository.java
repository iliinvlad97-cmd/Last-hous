package com.lastdom.game;

import java.util.*;

/**
 * Explicitly fictional sites and patrol. This repository has no Android or network dependencies.
 */
final class MockOnlineWorldRepository implements OnlineWorldRepository {
  public Snapshot load() {
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
                "PvE-район: разрушенные цеха и технические здания. Будущие задания пока"
                    + " недоступны.",
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
                "Опасная PvE-территория у заброшенных складов. Это визуальная демонстрация без"
                    + " боёв.",
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
            new OnlineWorldGeometry.Shape(.08f, .52f, .50f, .52f));
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
