package com.lastdom.game;

import java.util.ArrayList;
import java.util.Random;

/**
 * Shared session data. Transient selections and visual caches stay here to preserve the existing
 * reset/load behavior.
 */
class GameState {
  Random rnd = new Random();
  ArrayList<Resident> people = new ArrayList<>();
  ArrayList<String> log = new ArrayList<>();
  ArrayList<Location> locations = new ArrayList<>();
  final java.util.List<MapLocation> cityLocations = CityMapController.defaultLocations();
  final java.util.List<CityDistrict> cityDistricts = ExplorationConfig.initialDistricts();
  final ArrayList<Expedition> expeditions = new ArrayList<>();
  final ExpeditionLoot expeditionWarehouse = new ExpeditionLoot();
  final ArrayList<RoomUpgradeTask> roomUpgrades = new ArrayList<>();
  final ProductionState production = new ProductionState();
  final ResourceAccounting resourceAccounting = new ResourceAccounting();
  final ProductionRemainders productionRemainders = new ProductionRemainders();
  String[] jobs = {"Отдых", "Еда", "Вода", "Материалы", "Ремонт", "Охрана", "Лечение"};
  String[] rooms = {"Генераторная", "Кухня", "Медпункт", "Мастерская", "Баррикады", "Спальня"};
  String[] roomJobs = {"Ремонт", "Еда", "Лечение", "Материалы", "Охрана", "Отдых"};
  int[] roomLevels = {1, 1, 1, 1, 1, 1}, roomCondition = {100, 100, 100, 100, 100, 100};
  int day = 1,
      gameMinute = 480,
      speed = 1,
      food = 28,
      water = 34,
      power = 24,
      mats = 18,
      threat = 12,
      shelter = 100,
      selected = -1,
      selectedRoom = -1,
      screen = 0,
      overlay = 0,
      buildingRoom = -1,
      buildRemaining = 0,
      incidentRoom = -1;
  boolean paused = false, event = false, gameOver = false, jobMenu = false;
  String eventTitle = "", eventText = "";
  String[] eventChoices = new String[2];
  int expeditionPerson = -1,
      expeditionLocation = -1,
      expeditionRemaining = 0,
      selectedLocation = -1;
  boolean expeditionEvent = false;
  float[] residentX = new float[32], residentY = new float[32];
  int[] residentVisualRoom = new int[32];
  boolean residentVisualReady = false;
}
