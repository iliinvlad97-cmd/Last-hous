#!/usr/bin/env python3
"""Stage 9 tests of actual Java controllers, durable save_v02 snapshots and scaled Canvas input.
Android doubles verify logic; they do not claim an Android device rendering test.
"""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import raid_regression as raids
import refactor_regression as fixtures

PROBE = raids.PROBE[:raids.PROBE.index(' static void frequency()')] + r'''
 static List<String> ids(GameView v,int... indices){List<String> result=new ArrayList<>();for(int i:indices)result.add(v.game.people.get(i).id);return result;}
 static Expedition recon(GameView v){return v.game.expeditionController.active(Expedition.Type.RECON);}
 static long seedFor(int chance,boolean success,boolean injury,int risk){for(long seed=0;seed<100000;seed++){Random random=new Random(seed);boolean a=random.nextInt(10000)<chance,b=random.nextInt(10000)<risk;if(a==success&&b==injury)return seed;}throw new AssertionError("seed search");}
 static void launch(GameView v,String district,boolean success,boolean injury,int... members){
  List<String> squad=ids(v,members);ExplorationConfig.District d=ExplorationConfig.district(district);int risk=d.injuryChance;
  for(int i:members)if(v.game.people.get(i).role.equals("Охрана")){risk=risk*ExplorationConfig.GUARD_RISK_PERCENT/100;break;}
  long seed=seedFor(v.game.explorationController.chanceBasis(district,squad),success,injury,risk);
  v.game.rnd=new Quiet(){public long nextLong(){return seed;}};
  require(v.game.explorationController.start(district,squad).isEmpty(),"launch recon "+district);
 }
 static void fail(GameView v,String district,List<String> ids,String label){String before=snapshot(v);require(!v.game.explorationController.start(district,ids).isEmpty(),label);require(before.equals(snapshot(v)),"failed recon atomic: "+label);}
 static void openChain(GameView v,String until){for(CityDistrict d:v.game.cityDistricts){if(d.config.id.equals(until)){d.state=CityDistrict.State.DISCOVERED;break;}d.state=CityDistrict.State.EXPLORED;}}
 static void defaults(){
  GameView v=fresh();require(v.game.cityDistricts.size()==4&&v.game.cityLocations.size()==22,"four districts/eight extra points plus four locked story points, same destination list");
  require(v.cityMap.visibleLocations().size()==6&&v.cityMap.locations.size()==6,"initial map preserves its original six markers");
  for(int i=0;i<6;i++){MapLocation p=v.game.cityLocations.get(i);require(p.isLocked()==(i>=4),"original availability "+p.id);require(p.lootTable.max(ExpeditionLoot.Resource.FOOD)==(i==0?20:0),"original loot table "+p.id);}
  Set<String> all=new HashSet<>();for(MapLocation p:v.game.cityLocations){require(all.add(p.id),"unique map IDs");require(p.mapX>0&&p.mapX<1&&p.mapY>0&&p.mapY<1,"normalized route endpoint");if(p.kind==MapLocation.Kind.DISTRICT)require(p.lootTable.max(ExpeditionLoot.Resource.WATER)==0,"recon never rolls water loot");}
  for(CityDistrict d:v.game.cityDistricts){require(d.state==(d.config.prerequisite.isEmpty()?CityDistrict.State.DISCOVERED:CityDistrict.State.UNEXPLORED),"initial district state");for(String p:d.config.points)require(v.game.expeditionController.location(p).isLocked(),"unknown points start locked");}
  for(String target:Arrays.asList("industrial","warehouses","infected"))fail(v,target,ids(v,3),"visible prerequisite blocks late district");
  fail(v,"missing",ids(v,3),"unknown district");fail(v,"residential",Collections.emptyList(),"empty recon squad");fail(v,"residential",ids(v,0,1,2,3),"four members");fail(v,"residential",ids(v,0,0),"duplicate member");fail(v,"residential",Arrays.asList("missing"),"missing resident");
  require(!v.game.expeditionController.start("residential",ids(v,3)).isEmpty(),"recon target cannot be dispatched as loot trip");
 }
 static void chances(){
  GameView v=fresh();Resident r=v.game.people.get(0);r.skill=3;
  int base=v.game.explorationController.chanceBasis("residential",ids(v,0));require(base==8950,"base plus actual skill");
  require(v.game.explorationController.chanceBasis("residential",ids(v,3))==9500,"gatherer effect capped at95");
  r.health=60;require(v.game.explorationController.chanceBasis("residential",ids(v,0))==base-1200,"health penalty");r.health=100;r.fatigue=50;require(v.game.explorationController.chanceBasis("residential",ids(v,0))==base-1000,"fatigue penalty");
  r.fatigue=0;require(v.game.explorationController.chanceBasis("industrial",ids(v,0,3))>v.game.explorationController.chanceBasis("industrial",ids(v,0)),"squad/gatherer real effect");
  for(CityDistrict d:v.game.cityDistricts)for(int health:new int[]{0,20,100})for(int fatigue:new int[]{0,50,100})for(int skill:new int[]{-9,0,5,900}){r.health=health;r.fatigue=fatigue;r.skill=skill;int chance=v.game.explorationController.chanceBasis(d.config.id,ids(v,0));require(chance>=1000&&chance<=9500,"chance bounded");}
 }

 static void riskAndFrozenConditions(){
  for(boolean guarded:new boolean[]{false,true}){
   GameView v=fresh();Resident r=v.game.people.get(0);if(guarded)r.role="Охрана";
   long seed=0;while(true){Random random=new Random(seed);random.nextInt(10000);int roll=random.nextInt(10000);if(roll>=425&&roll<500)break;seed++;}
   final long chosen=seed;v.game.rnd=new Quiet(){public long nextLong(){return chosen;}};require(v.game.explorationController.start("residential",ids(v,0)).isEmpty(),"guard risk fixture");
   Expedition e=recon(v);require(e.recon.injuryChance==(guarded?425:500),"guard reduces risk relatively by15%");minutes(v,105);require((e.recon.healthLoss==0)==guarded,"same injury roll prevented by guard effect");
  }
  int normalLoss=0;
  for(boolean doctor:new boolean[]{false,true}){
   GameView v=fresh();Resident r=v.game.people.get(0);if(doctor)r.role="Врач";long seed=seedFor(v.game.explorationController.chanceBasis("residential",ids(v,0)),true,true,500);
   final long chosen=seed;v.game.rnd=new Quiet(){public long nextLong(){return chosen;}};v.game.explorationController.start("residential",ids(v,0));Expedition e=recon(v);require(e.recon.damagePercent==(doctor?75:100),"doctor freezes real mitigation");minutes(v,105);
   if(!doctor)normalLoss=e.recon.healthLoss;else require(e.recon.healthLoss==normalLoss*75/100,"same injury damage mitigated by actual doctor");
  }
  GameView v=fresh();launch(v,"residential",true,false,3);Expedition e=recon(v);int chance=e.recon.chanceBasis,research=e.recon.researchMinutes;v.game.people.get(3).fatigue=80;v.game.save();v=kill();e=recon(v);require(e.recon.chanceBasis==chance&&e.recon.researchMinutes==research,"launch chance and duration retained despite later resident condition");
  String before=snapshot(v);v.game.explorationController.applyReturn(e);require(before.equals(snapshot(v)),"early return-effects call cannot unlock district");
  v=fresh();for(Resident r:v.game.people)r.fatigue=100;rCritical(v);fail(v,"residential",ids(v,0),"all critical residents unavailable");require(v.game.explorationController.unavailableReason("residential").contains("Нет доступных"),"specific no-available-residents reason");
 }
 static void rCritical(GameView v){for(Resident r:v.game.people)r.thirst=100;}
 static void completeTrips(){
  for(boolean success:new boolean[]{false,true})for(int size=1;size<=3;size++){
   GameView v=fresh();int[] members=size==1?new int[]{0}:size==2?new int[]{0,3}:new int[]{0,3,1};launch(v,"residential",success,true,members);
   Expedition e=recon(v);String id=e.id;int chance=e.recon.chanceBasis;long seed=e.recon.seed;
   int food=v.game.food,water=v.game.water,mats=v.game.mats;for(Resident r:v.game.people)if(!v.game.isOnExpedition(r)){r.foodMinutes=r.waterMinutes=1440;}
   for(int index:members){Resident r=v.game.people.get(index);r.foodMinutes=42;r.waterMinutes=53;require(v.game.homeRoomFor(r)==-1&&!v.game.survivalController.working(r)&&!v.game.assignJob(index,"Еда"),"recon resident absent and excluded from work");}
   minutes(v,17);String outbound=snapshot(v);v=kill();e=recon(v);require(outbound.equals(snapshot(v))&&e.id.equals(id)&&e.elapsedMinutes()==17&&e.recon.seed==seed&&e.recon.chanceBasis==chance,"outbound exact restart/seed/chance");
   v.game.rnd=new NoRandom();minutes(v,28);e=recon(v);require(e.state()==Expedition.State.EXPLORING&&e.phaseDurationMinutes==60,"arrival starts district research");
   minutes(v,29);String mid=snapshot(v);v=kill();require(mid.equals(snapshot(v))&&recon(v).elapsedMinutes()==29,"research midpoint restart");v.game.rnd=new NoRandom();
   require(v.game.explorationController.district("residential").state==CityDistrict.State.DISCOVERED,"not unlocked mid-research");minutes(v,31);e=recon(v);
   require(e.state()==Expedition.State.RETURNING&&e.recon.resolved&&e.recon.success==success&&e.resultGenerated&&e.recon.injuryApplied,"resolved outcome starts return");
   require(e.recon.healthLoss>=6&&e.recon.healthLoss<=18,"real bounded injury with doctor mitigation");Resident victim=v.game.expeditionController.resident(e.recon.injuredId);int injured=victim.health;require(injured==100-e.recon.healthLoss,"injury applied once");
   require(e.found.total()==0&&e.cargo.total()==0&&!e.cityEventChecked&&!e.lootRolled,"recon bypasses ordinary loot/city events");
   require(v.game.explorationController.district("residential").state==CityDistrict.State.DISCOVERED&&v.game.expeditionController.location("apartments").isLocked(),"success doesn't unlock until team returns");
   String returning=snapshot(v);v=kill();e=recon(v);require(returning.equals(snapshot(v))&&v.game.expeditionController.resident(e.recon.injuredId).health==injured,"result and injury remain fixed on restart");v.game.rnd=new NoRandom();
   minutes(v,19);float progress=recon(v).routeProgress();require(progress>0&&progress<1,"reverse progress on the same route");String reverse=snapshot(v);v=kill();require(reverse.equals(snapshot(v))&&recon(v).routeProgress()==progress,"reverse progress restart");v.game.rnd=new NoRandom();minutes(v,26);
   e=v.game.expeditionController.find(id);require(!e.active()&&e.state()==Expedition.State.COMPLETED&&e.rewardCredited&&e.recon.unlocksApplied,"returned once through shared completion");
   require(v.game.food==food&&v.game.water==water&&v.game.mats==mats&&v.game.expeditionWarehouse.total()==0,"recon has no bonus loot or extra home rations");
   for(int index:members){Resident r=v.game.people.get(index);require(!v.game.isOnExpedition(r)&&r.status==Resident.Status.HOME&&r.job.equals("Отдых"),"return frees participants");require(r.foodMinutes==42&&r.waterMinutes==53,"away doesn't consume prepaid home ration");require(e.recon.fatigueGain.get(r.id)>0,"fatigue accrued by existing survival clock");}
   require(v.game.explorationController.district("residential").state==(success?CityDistrict.State.EXPLORED:CityDistrict.State.DISCOVERED),"district success/failure");require(v.game.expeditionController.location("apartments").isLocked()!=success,"points unlock only success");
   require(e.recon.openedPoints.size()==(success?2:0),"report point identities");
   if(success){require(v.game.explorationController.district("industrial").state==CityDistrict.State.DISCOVERED,"next district discovered");fail(v,"residential",ids(v,0),"cannot explore successful district again");}
   String done=snapshot(v);v=kill();require(done.equals(snapshot(v)),"completed result exact restart");e=v.game.expeditionController.find(id);int logs=v.game.log.size();v.game.rnd=new NoRandom();for(int i=0;i<5;i++){v.game.expeditionController.complete(e);v.game.explorationController.finishResearch(e);v.game.explorationController.applyReturn(e);v.cityMap.openExpedition(e);draw(v);}require(logs==v.game.log.size()&&done.equals(snapshot(v)),"repeated reports/completion do not reapply resources, injuries, unlocks or log");
   v.game.expeditionController.acknowledge(id);v=kill();require(v.game.explorationController.latestReport("residential").reportAcknowledged,"ack is persistent and report remains accessible");
  }
 }
 static void chain(){
  GameView v=fresh();for(ExplorationConfig.District config:ExplorationConfig.DISTRICTS){CityDistrict d=v.game.explorationController.district(config.id);for(Resident r:v.game.people){r.health=100;r.fatigue=r.hunger=r.thirst=0;r.autoRecovery=false;r.job="Отдых";}launch(v,d.config.id,true,false,3);Expedition e=recon(v);int duration=e.durationMinutes*2+d.config.researchMinutes;minutes(v,duration);require(e.state()==Expedition.State.COMPLETED&&d.state==CityDistrict.State.EXPLORED,"complete chain "+d.config.id);for(String id:d.config.points){MapLocation p=v.game.expeditionController.location(id);require(!p.isLocked(),"new point unlocked "+id);String reason=v.game.expeditionController.start(id,ids(v,0));require(reason.isEmpty(),"new ordinary expedition accessible "+id);Expedition ordinary=v.game.expeditionController.active(Expedition.Type.LOOT);ordinary.restorePhase(Expedition.State.COMPLETED,1,1,v.game.expeditionController.now());ordinary.rewardCredited=true;v.game.people.get(0).job="Отдых";v.game.people.get(0).status=Resident.Status.HOME;}
    v.game.save();v=kill();require(v.game.explorationController.district(d.config.id).state==CityDistrict.State.EXPLORED,"district survives restart "+d.config.id);
  }
  for(MapLocation p:v.cityMap.locations)require(!p.isLocked(),"old four remain open, two old late points now unlocked by explicit conditions");
 }
 static void concurrency(){
  GameView v=fresh();require(v.game.roomUpgradeController.start(0,v.game.people.get(4).id).isEmpty(),"concurrent builder");fail(v,"residential",ids(v,4),"builder cannot recon");
  require(v.game.expeditionController.start("shop",ids(v,0)).isEmpty(),"ordinary trip");String normalId=v.game.expeditionController.active(Expedition.Type.LOOT).id;fail(v,"residential",ids(v,0),"ordinary member cannot join recon");launch(v,"residential",true,false,3,1);Expedition scouting=recon(v);String scoutId=scouting.id;require(v.game.expeditions.stream().filter(Expedition::active).count()==2,"one recon runs alongside one ordinary");
  String before=snapshot(v);require(!v.game.expeditionController.start("garage",ids(v,2)).isEmpty()&&before.equals(snapshot(v)),"ordinary limit unaffected by recon");fail(v,"residential",ids(v,2),"second recon blocked");
  for(int i:new int[]{1,3}){Resident r=v.game.people.get(i);require(!v.game.roomUpgradeController.unavailableReason(r).isEmpty()&&!v.game.raidController.defenderReason(r).isEmpty(),"recon excludes construction/defense");require(v.game.findBestResident(r.role,"")!=i,"AI excludes recon");}invariant(v);
  minutes(v,75);Expedition normal=v.game.expeditionController.find(normalId);require(normal.state()==Expedition.State.AWAITING_RETURN&&recon(v).state()==Expedition.State.EXPLORING,"normal result and recon progress independent");String disk=snapshot(v);v=kill();require(disk.equals(snapshot(v))&&v.game.expeditionController.find(normalId).state()==Expedition.State.AWAITING_RETURN&&recon(v).id.equals(scoutId),"parallel membership restore");
  require(v.game.expeditionController.returnHome(normalId).isEmpty(),"normal return uses exact ID");minutes(v,45);normal=v.game.expeditionController.find(normalId);require(normal.state()==Expedition.State.COMPLETED&&recon(v).state()==Expedition.State.RETURNING,"normal completes while recon returns");minutes(v,30);scouting=v.game.expeditionController.find(scoutId);require(scouting.state()==Expedition.State.COMPLETED&&v.game.expeditionController.active()==null,"both teams release normally");invariant(v);
  v=fresh();launch(v,"residential",true,false,3);require(v.game.expeditionController.start("garage",ids(v,0)).isEmpty(),"reverse launch order supports normal during recon");String s=snapshot(v);v=kill();require(s.equals(snapshot(v))&&v.game.expeditionController.active().type==Expedition.Type.LOOT,"ordinary events retain priority after reverse-order restart");
 }

 static void eventAndReportPriority(){
  GameView v=fresh();launch(v,"residential",true,false,3);String reconId=recon(v).id;require(v.game.expeditionController.start("shop",ids(v,0)).isEmpty(),"normal with recon for event priority");Expedition normal=v.game.expeditionController.active(Expedition.Type.LOOT);String normalId=normal.id;
  minutes(v,45);v.game.rnd=new Quiet(){public double nextDouble(){return 0;}public int nextInt(int n){return 0;}};minutes(v,15);require(normal.state()==Expedition.State.AWAITING_DECISION&&recon(v).elapsedMinutes()==15,"ordinary city event freezes only ordinary squad");v.game.rnd=new Quiet();minutes(v,10);require(recon(v).elapsedMinutes()==25&&normal.elapsedMinutes()==15,"recon continues while ordinary player decision pending");v.game.save();v=kill();normal=v.game.expeditionController.find(normalId);require(v.cityMap.eventPanel&&v.cityMap.panelExpedition().id.equals(normalId),"restored decision selects ordinary exact ID despite recon inserted first");
  String eventId=normal.explorationEvent.instanceId;require(v.game.expeditionController.chooseEvent(eventId,0).isEmpty()&&v.game.expeditionController.continueEvent(eventId).isEmpty(),"ordinary event actions target ordinary squad");v.cityMap.closeSelection();minutes(v,20);require(v.game.expeditionController.find(normalId).state()==Expedition.State.AWAITING_RETURN,"ordinary research resumes");require(v.game.expeditionController.returnHome(normalId).isEmpty(),"normal returns after player decision");minutes(v,60);v.cityMap.closeSelection();v.cityMap.openPendingReport();String first=v.cityMap.panelExpedition().id;require(v.cityMap.expeditionPanel&&v.cityMap.panelExpedition().state()==Expedition.State.COMPLETED,"completed parallel report opens");v.game.expeditionController.acknowledge(first);v.cityMap.closeSelection();v.cityMap.openPendingReport();require(v.cityMap.expeditionPanel&&!v.cityMap.panelExpedition().id.equals(first),"second completed report not lost or repeated");
  v=fresh();launch(v,"residential",true,false,3);minutes(v,150);v.cityMap.closeSelection();v.game.screen=GameView.CITY_MAP;draw(v);CityMapLayout map=new CityMapLayout(v.H/v.scale);tap(v,180,map.expeditionRow(Expedition.Type.RECON)+14);require(!v.cityMap.expeditionPanel,"completed recon has no active indicator");v.game.screen=2;v.journal.reports=true;draw(v);tap(v,180,JournalLayout.TOP+30);require(v.cityMap.expeditionPanel&&v.cityMap.panelExpedition().type==Expedition.Type.RECON,"completed recon remains accessible in journal");
  v=fresh();launch(v,"residential",true,false,3);minutes(v,20);v.game.screen=GameView.CITY_MAP;draw(v);float progress=v.cityMap.reconDisplayProgress;map=new CityMapLayout(v.H/v.scale);MapLocation target=v.game.expeditionController.location("garage");tap(v,map.x(target.mapX),map.y(target.mapY));tap(v,180,map.panelBottom-40);require(v.cityMap.preparation()==target,"prepare normal while recon travels");ExpeditionPreparationLayout prep=new ExpeditionPreparationLayout(map.panelBottom);tap(v,180,prep.rowTop(0)+20);tap(v,180,prep.sendTop+20);require(v.game.expeditionController.active(Expedition.Type.LOOT)!=null&&v.cityMap.reconDisplayProgress==progress&&recon(v).elapsedMinutes()==20,"new normal launch doesn't reset other squad simulation or visual progress");
 }
 static void clocks(){
  GameView v=fresh();launch(v,"residential",true,false,3);TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);String reference=null;
  for(int[] speeds:new int[][]{{1,1,1},{2,2,2},{4,4,4},{1,2,4}}){Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);v=load();for(int speed:speeds){v.game.speed=speed;for(int i=0;i<40/speed;i++)v.tick.run();v=kill();}v.game.speed=1;String value=snapshot(v);if(reference==null)reference=value;else require(reference.equals(value),"identical progress/health/resources/fractions at speeds/mixed/restarts");}
  v.game.paused=true;String paused=snapshot(v);for(int i=0;i<100;i++){v.tick.run();draw(v);}require(paused.equals(snapshot(v)),"paused recon and needs stopped");v.game.paused=false;v.game.speed=4;for(int i=0;i<8;i++)v.tick.run();require(v.game.explorationController.latestReport("residential").state()==Expedition.State.COMPLETED,"x4 finishes return");
 }
 static void migration(){
  GameView v=fresh();require(v.game.expeditionController.start("pharmacy",ids(v,1)).isEmpty(),"old ongoing expedition");minutes(v,14);String id=v.game.expeditionController.active().id;int food=v.game.food,water=v.game.water,health=v.game.people.get(1).health;v.game.roomLevels[1]=3;v.game.people.get(3).foodMinutes=137;v.game.people.get(3).survivalFractions.put("hunger",912);v.game.save();
  Context.preferences.values.keySet().removeIf(k->k.startsWith("district9_")||k.startsWith("exploration9_")||k.endsWith("type9")||k.contains("recon9_")||k.startsWith("map_apartments"));
  v=kill();require(Context.preferenceName.equals("save_v02")&&v.game.expeditionController.active().id.equals(id)&&v.game.expeditionController.active().type==Expedition.Type.LOOT&&v.game.expeditionController.active().elapsedMinutes()==14,"v1.0.2 active ordinary save migration");require(v.game.food==food&&v.game.water==water&&v.game.people.get(1).health==health&&v.game.roomLevels[1]==3&&v.game.people.get(3).foodMinutes==137&&v.game.people.get(3).survivalFractions.get("hunger")==912,"existing resources, room levels and individual fractions unchanged");require(v.game.explorationController.district("residential").state==CityDistrict.State.DISCOVERED&&v.game.expeditionController.location("apartments").isLocked(),"safe missing-field defaults");
  v=fresh();v.game.expeditionController.location("water").setState(MapLocation.State.SEARCHED);v.game.expeditionController.location("hospital").setState(MapLocation.State.AVAILABLE);v.game.save();v=kill();require(!v.game.expeditionController.location("water").isLocked()&&!v.game.expeditionController.location("hospital").isLocked(),"previously opened late points never relocked by new districts");
  v=fresh();launch(v,"residential",true,false,3);Context.preferences.edit().putInt("exp2_0_recon9_chance",-5).commit();v=kill();require(recon(v)==null&&v.game.explorationController.district("residential").state==CityDistrict.State.DISCOVERED,"corrupt recon rejected without opening district");
 }

 static void compactIndicators(){
  for(int[] size:new int[][]{{360,640},{420,560},{420,640},{420,840},{1080,2340}}){
   GameView v=fresh();View.width=size[0];View.height=size[1];draw(v);v.game.screen=GameView.CITY_MAP;launch(v,"residential",true,false,3);require(v.game.expeditionController.start("shop",ids(v,0)).isEmpty(),"parallel route UI fixture");CityMapLayout m=new CityMapLayout(v.H/v.scale);Canvas c=draw(v);
   require(m.expeditionRight(Expedition.Type.LOOT,true)<m.expeditionLeft(Expedition.Type.RECON,true),"parallel indicator rectangles don't overlap");
   for(Expedition.Type type:Expedition.Type.values()){
    float row=m.expeditionRow(type),left=m.expeditionLeft(type,true),right=m.expeditionRight(type,true);
    require(row>=m.top&&row+28<m.bottom&&row+28<v.H/v.scale-78,"indicator stays above nav in short/scaled viewport");
    float sx=m.x(CityMapLayout.SHELTER_X),sy=m.y(CityMapLayout.SHELTER_Y);require(!(sx>=left&&sx<=right&&sy>=row&&sy<=row+28),"shelter center remains visible for both teams");
    tap(v,(left+right)/2,row+14);require(v.cityMap.expeditionPanel&&v.cityMap.panelExpedition().type==type,"side-by-side touch selects correct team");tap(v,180,m.panelBottom-40);
   }
   text(c,"Разведка");text(c,"Экспед.");require(recon(v).elapsedMinutes()==0,"layout and indicator input don't advance expedition");
  }
 }
 static void ui(){
  for(int[] size:new int[][]{{360,640},{420,560},{420,640},{420,840},{1080,2340}}){
   GameView v=fresh();View.width=size[0];View.height=size[1];draw(v);v.game.screen=GameView.CITY_MAP;CityMapLayout m=new CityMapLayout(v.H/v.scale);text(draw(v),"СТАРЫЕ ТОЧКИ");tap(v,200,55);require(v.cityMap.districtsLayer&&v.cityMap.visibleLocations().size()==4,"district layer toggles shared map");text(draw(v),"ЖИЛОЙ");
   for(CityDistrict d:v.game.cityDistricts){tap(v,m.x(d.config.x),m.y(d.config.y));require(v.cityMap.selected()!=null&&v.cityMap.selected().id.equals(d.config.id),"district rendered hitbox "+d.config.id);String before=snapshot(v);Canvas panel=draw(v);text(panel,"РАЙОН ГОРОДА");text(panel,"ОТПРАВИТЬ РАЗВЕДКУ");tap(v,210,m.panelBottom-40);if(!d.config.prerequisite.isEmpty())require(v.cityMap.preparation()==null&&before.equals(snapshot(v)),"late district button cannot bypass prerequisite");v.cityMap.closeSelection();}
   CityDistrict d=v.game.explorationController.district("residential");tap(v,m.x(d.config.x),m.y(d.config.y));tap(v,210,m.panelBottom-40);require(v.cityMap.preparation()!=null,"recon preparation uses current member picker");text(draw(v),"ПОДГОТОВКА РАЗВЕДКИ");ExpeditionPreparationLayout prep=new ExpeditionPreparationLayout(m.panelBottom);tap(v,180,prep.rowTop(0)+20);require(v.cityMap.selectedIds.size()==1,"real member selection");long uiSeed=seedFor(v.game.explorationController.chanceBasis("residential",v.cityMap.selectedIds),true,false,500);v.game.rnd=new Quiet(){public long nextLong(){return uiSeed;}};tap(v,180,prep.sendTop+20);require(recon(v)!=null&&v.cityMap.preparation()==null,"recon UI atomic launch");
   String before=snapshot(v);for(int i=0;i<20;i++)draw(v);require(before.equals(snapshot(v)),"map drawing does not roll or mutate simulation");tap(v,180,m.expeditionRow(Expedition.Type.RECON)+14);require(v.cityMap.expeditionPanel&&v.cityMap.panelExpedition().type==Expedition.Type.RECON,"recon indicator chooses correct squad");text(draw(v),"РАЗВЕДЫВАТЕЛЬНЫЙ ОТРЯД");tap(v,180,m.panelBottom-40);require(!v.cityMap.expeditionPanel,"fixed footer close accessible");
   minutes(v,150);Expedition e=v.game.explorationController.latestReport("residential");v.cityMap.openExpedition(e);Canvas result=draw(v);text(result,e.recon.success?"РАЙОН ИССЛЕДОВАН":"РАЗВЕДКА — НЕУДАЧА");text(result,"ПОНЯТНО");int max=v.cityMap.panelLineCount;String completed=snapshot(v);for(int scroll=0;scroll<max;scroll++){v.cityMap.panelScroll=scroll;draw(v);}require(completed.equals(snapshot(v)),"scrolling reports read-only");tap(v,180,m.panelBottom-40);require(e.reportAcknowledged&&!v.cityMap.expeditionPanel,"result ack doesn't apply reward");
   if(e.recon.success){tap(v,m.x(d.config.x),m.y(d.config.y));draw(v);tap(v,180,m.panelBottom-40);require(!v.cityMap.districtsLayer&&v.cityMap.visibleLocations().size()==2,"show actual opened district points");MapLocation point=v.cityMap.visibleLocations().get(0);tap(v,m.x(point.mapX),m.y(point.mapY));require(v.cityMap.selected()==point,"new point rendered and touch same transform");v.cityMap.closeSelection();tap(v,80,55);require(v.cityMap.visibleLocations().size()==6,"initial map projection remains accessible");}
  }
 }
 public static void main(String[] args){defaults();chances();riskAndFrozenConditions();completeTrips();chain();concurrency();eventAndReportPriority();clocks();migration();compactIndicators();ui();System.out.println("PASS: "+checks+" city exploration assertions; districts/defaults, preserved old points, chance/professions/bounds, success/failure/injury, exact restarts in every phase, parallel ordinary/recon membership, pause/speeds, once-only unlocks/logs/effects, old save migration and five scaled Canvas/touch layouts.");}
}
'''

def main():
    sources={"com/lastdom/game/"+p.name:p.read_text() for p in (fixtures.ROOT/fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"]="package com.lastdom.game;public class R{public static class drawable{public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE=PROBE
    with tempfile.TemporaryDirectory(prefix="exploration9-") as directory:
        print(fixtures.run_version(Path(directory),"exploration9",sources).strip())

if __name__=="__main__": main()
