package com.lastdom.game;

import static com.lastdom.game.OnlineInventory.Resource.*;

import java.util.*;

/** Immutable demo catalogue and transaction journal. Completed deliveries are history only. */
final class OnlineWorldGameplay {
  static final int DELIVERY_SECONDS = 24;
  static final List<String> PVP_SQUAD =
      Collections.unmodifiableList(
          Arrays.asList("Радио-1 · разведчик", "Радио-2 · охрана", "Радио-3 · связист"));

  enum Kind {
    TRADE,
    HELP
  }

  static final class Offer {
    final String id, shelterId, title;
    final Kind kind;
    final OnlineInventory.Resource cost, reward;
    final int quantity, output, reputation;

    Offer(
        String id,
        String shelterId,
        String title,
        Kind kind,
        OnlineInventory.Resource cost,
        int quantity,
        OnlineInventory.Resource reward,
        int output,
        int reputation) {
      this.id = id;
      this.shelterId = shelterId;
      this.title = title;
      this.kind = kind;
      this.cost = cost;
      this.quantity = quantity;
      this.reward = reward;
      this.output = output;
      this.reputation = reputation;
    }

    String summary() {
      return kind == Kind.TRADE
          ? "Отдать: "
              + quantity
              + " · "
              + cost.label
              + ". Получить: "
              + output
              + " · "
              + reward.label
              + "."
          : "Помощь: " + quantity + " · " + cost.label + ". Репутация: +" + reputation + ".";
    }
  }

  static List<Offer> catalogue() {
    return Collections.unmodifiableList(
        Arrays.asList(
            trade("ember_water", "demo_ember", FOOD, 5, WATER, 7),
                trade("ember_food", "demo_ember", MATERIALS, 4, FOOD, 6),
            trade("beacon_medicine", "demo_beacon", WATER, 6, MEDICINE, 3),
                trade("beacon_water", "demo_beacon", EQUIPMENT, 2, WATER, 8),
            trade("foundry_materials", "demo_foundry", FOOD, 6, MATERIALS, 8),
                trade("foundry_equipment", "demo_foundry", MATERIALS, 30, EQUIPMENT, 8),
            trade("outpost_food", "demo_outpost", WATER, 5, FOOD, 6),
                trade("outpost_medicine", "demo_outpost", MATERIALS, 5, MEDICINE, 3),
            help("ember_help", "demo_ember", "Требуется вода", WATER, 5, 5),
                help("beacon_help", "demo_beacon", "Нужны медикаменты", MEDICINE, 3, 8),
            help("foundry_help", "demo_foundry", "Материалы для ремонта", MATERIALS, 6, 6),
                help("outpost_help", "demo_outpost", "Требуется вода", WATER, 7, 7)));
  }

  private static Offer trade(
      String id,
      String shelter,
      OnlineInventory.Resource cost,
      int n,
      OnlineInventory.Resource reward,
      int m) {
    return new Offer(id, shelter, "Обмен припасами", Kind.TRADE, cost, n, reward, m, 0);
  }

  private static Offer help(
      String id,
      String shelter,
      String title,
      OnlineInventory.Resource cost,
      int n,
      int reputation) {
    return new Offer(id, shelter, title, Kind.HELP, cost, n, null, 0, reputation);
  }

  static final class Operation {
    final Offer offer;
    final String originId, targetId;
    final OnlineRoute route;
    final int elapsedSeconds;

    Operation(Offer offer, String originId, String targetId, OnlineRoute route, int elapsed) {
      if (elapsed < 0 || elapsed > DELIVERY_SECONDS)
        throw new IllegalArgumentException("Invalid delivery progress");
      this.offer = offer;
      this.originId = originId;
      this.targetId = targetId;
      this.route = route;
      this.elapsedSeconds = elapsed;
    }

    boolean active() {
      return elapsedSeconds < DELIVERY_SECONDS;
    }

    Operation advance() {
      return new Operation(
          offer, originId, targetId, route, Math.min(DELIVERY_SECONDS, elapsedSeconds + 1));
    }

    double progress(double fraction) {
      return Math.min(1, (elapsedSeconds + Math.max(0, Math.min(1, fraction))) / DELIVERY_SECONDS);
    }
  }

  static final class Data {
    final OnlineInventory inventory;
    final List<Offer> offers;
    final List<Operation> operations;
    final int reputation;
    final String pvpZoneId;
    final OnlineBattleRepository.State combat;
    final OnlineCivicState civic;

    Data(
        OnlineInventory inventory,
        List<Offer> offers,
        List<Operation> operations,
        int reputation,
        String pvpZoneId) {
      this(
          inventory,
          offers,
          operations,
          reputation,
          pvpZoneId,
          OnlineBattleRepository.State.initial());
    }

    Data(
        OnlineInventory inventory,
        List<Offer> offers,
        List<Operation> operations,
        int reputation,
        String pvpZoneId,
        OnlineBattleRepository.State combat) {
      this(
          inventory, offers, operations, reputation, pvpZoneId, combat, OnlineCivicState.initial());
    }

    Data(
        OnlineInventory inventory,
        List<Offer> offers,
        List<Operation> operations,
        int reputation,
        String pvpZoneId,
        OnlineBattleRepository.State combat,
        OnlineCivicState civic) {
      this.civic = Objects.requireNonNull(civic);
      this.combat = Objects.requireNonNull(combat);
      Set<String> ids = new HashSet<>(), operationsIds = new HashSet<>();
      for (Offer offer : offers)
        if (!ids.add(offer.id)) throw new IllegalArgumentException("Duplicate demo offer");
      for (Operation operation : operations)
        if (!ids.contains(operation.offer.id) || !operationsIds.add(operation.offer.id))
          throw new IllegalArgumentException("Invalid demo ledger");
      if (reputation < 0) throw new IllegalArgumentException("Negative reputation");
      this.inventory = inventory;
      this.offers = Collections.unmodifiableList(new ArrayList<>(offers));
      this.operations = Collections.unmodifiableList(new ArrayList<>(operations));
      this.reputation = reputation;
      this.pvpZoneId = pvpZoneId;
    }

    Data withCivic(OnlineCivicState replacement) {
      return new Data(inventory, offers, operations, reputation, pvpZoneId, combat, replacement);
    }

    static Data initial() {
      return new Data(OnlineInventory.initial(), catalogue(), Collections.emptyList(), 0, "");
    }

    Offer offer(String id) {
      for (Offer offer : offers) if (offer.id.equals(id)) return offer;
      return null;
    }

    Operation operation(String id) {
      for (Operation operation : operations) if (operation.offer.id.equals(id)) return operation;
      return null;
    }

    boolean hasActive() {
      for (Operation operation : operations) if (operation.active()) return true;
      return false;
    }

    String unavailable(Offer offer) {
      if (offer == null) return "Предложение не найдено";
      if (operation(offer.id) != null)
        return offer.kind == Kind.HELP ? "Помощь уже отправлена" : "Сделка уже выполнена";
      int shortage = offer.quantity - inventory.amount(offer.cost);
      return shortage > 0 ? "Не хватает: " + shortage + " · " + offer.cost.label : "";
    }
  }

  static final class Result {
    final boolean success;
    final String message;

    Result(boolean success, String message) {
      this.success = success;
      this.message = message;
    }
  }
}
