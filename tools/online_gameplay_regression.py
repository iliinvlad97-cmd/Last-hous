#!/usr/bin/env python3
"""ONLINE 0.2 actual Java transactions, separate preferences, finite routes and Canvas/input.
Controlled frame clocks and Android doubles are not physical device/FPS testing.
"""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import online_world_regression as online
import refactor_regression as fixtures

PROBE = online.PROBE[:online.PROBE.index(' public static void main(')] + r'''
 static MockOnlineWorldRepository persistent(MemoryPreferences disk){return new MockOnlineWorldRepository(new OnlineDemoSaveStore(disk));}
 static String ledger(OnlineWorldRepository repository){return OnlineDemoSaveStore.encode(repository.gameplay());}
 static GameView interactive(int width,int height){fresh();Context.namedPreferences.clear();View.width=width;View.height=height*width/420;clock=new FrameClock();GameView v=new GameView(new Context(),persistent((MemoryPreferences)new Context().getSharedPreferences(OnlineDemoSaveStore.FILE,0)),clock);v.game.rnd=new Quiet();draw(v);return v;}
 static void chooseShelter(GameView v,String id){v.onlineWorld.closeCard();OnlineWorldGeometry g=geometry(v);OnlineShelter shelter=v.onlineWorld.state.shelters.stream().filter(x->x.id.equals(id)).findFirst().get();tap(v,g.x(shelter.position.x),g.y(shelter.position.y));require(v.onlineWorld.state.shelter()==shelter,"interactive shelter selected");draw(v);}
 static void allTrades(){
  for(OnlineWorldGameplay.Offer offer:OnlineWorldGameplay.catalogue())if(offer.kind==OnlineWorldGameplay.Kind.TRADE){
   MemoryPreferences disk=new MemoryPreferences();MockOnlineWorldRepository repo=persistent(disk);
   if(offer.id.equals("foundry_equipment"))require(repo.execute("foundry_materials",OnlineWorldGameplay.Kind.TRADE).success,"earn demo materials before large trade");
   OnlineWorldGameplay.Data before=repo.gameplay();int commits=disk.commits;
   require(repo.execute(offer.id,OnlineWorldGameplay.Kind.TRADE).success,"every configured trade executable: "+offer.id);
   OnlineWorldGameplay.Data after=repo.gameplay();
   for(OnlineInventory.Resource resource:OnlineInventory.Resource.values())require(after.inventory.amount(resource)==before.inventory.amount(resource)-(offer.cost==resource?offer.quantity:0)+(offer.reward==resource?offer.output:0),"correct atomic exchange "+offer.id+" / "+resource);
   require(after.operations.size()==before.operations.size()+1&&after.reputation==0&&disk.commits==commits+1,"balance/history/route saved together once");
   String saved=ledger(repo);require(!repo.execute(offer.id,OnlineWorldGameplay.Kind.TRADE).success,"duplicate confirmation rejected");require(saved.equals(ledger(repo))&&disk.commits==commits+1,"duplicate never debits, credits, saves or spawns another route");
   MockOnlineWorldRepository restored=persistent(disk);require(saved.equals(ledger(restored)),"inventory/history/action survives fresh repository");require(!restored.execute(offer.id,OnlineWorldGameplay.Kind.TRADE).success,"restart cannot replay trade");
   require(after.inventory!=before.inventory,"immutable inventory replacement");
  }
  MemoryPreferences disk=new MemoryPreferences();MockOnlineWorldRepository repo=persistent(disk);String before=ledger(repo);
  require(!repo.execute("foundry_equipment",OnlineWorldGameplay.Kind.TRADE).success,"insufficient materials blocks trade");require(ledger(repo).equals(before)&&disk.commits==0,"shortage has no partial operation");
  require(repo.gameplay().unavailable(repo.gameplay().offer("foundry_equipment")).contains("5"),"concrete shortage shown");
  require(!repo.execute("missing",OnlineWorldGameplay.Kind.TRADE).success&&!repo.execute("ember_water",OnlineWorldGameplay.Kind.HELP).success,"unknown ID or wrong action kind rejected");require(before.equals(ledger(repo)),"invalid actions preserve every balance");
  for(int quantity:new int[]{-1,0,1000})try{repo.gameplay().inventory.exchange(OnlineInventory.Resource.FOOD,quantity,null,0);throw new AssertionError("invalid debit");}catch(IllegalArgumentException expected){checks++;}
  try{new OnlineInventory(Collections.singletonMap(OnlineInventory.Resource.FOOD,-1));throw new AssertionError("negative balance");}catch(IllegalArgumentException expected){checks++;}
  try{new OnlineInventory(java.util.Map.of(OnlineInventory.Resource.FOOD,OnlineInventory.LIMIT,OnlineInventory.Resource.WATER,1)).exchange(OnlineInventory.Resource.WATER,1,OnlineInventory.Resource.FOOD,1);throw new AssertionError("invalid overflow");}catch(IllegalArgumentException expected){checks++;}
 }
 static void allHelp(){
  for(OnlineWorldGameplay.Offer offer:OnlineWorldGameplay.catalogue())if(offer.kind==OnlineWorldGameplay.Kind.HELP){
   MemoryPreferences disk=new MemoryPreferences();MockOnlineWorldRepository repo=persistent(disk);OnlineWorldGameplay.Data before=repo.gameplay();
   require(repo.execute(offer.id,OnlineWorldGameplay.Kind.HELP).success,"send every assistance request");OnlineWorldGameplay.Data after=repo.gameplay();
   for(OnlineInventory.Resource resource:OnlineInventory.Resource.values())require(after.inventory.amount(resource)==before.inventory.amount(resource)-(offer.cost==resource?offer.quantity:0),"only requested resource consumed");
   require(after.reputation==offer.reputation&&after.operation(offer.id)!=null,"help request completed and rep credited");String saved=ledger(repo);int commits=disk.commits;
   require(!repo.execute(offer.id,OnlineWorldGameplay.Kind.HELP).success&&saved.equals(ledger(repo))&&commits==disk.commits,"help and reputation are exactly once");
   MockOnlineWorldRepository restored=persistent(disk);require(saved.equals(ledger(restored)),"request and rep restored");require(!restored.execute(offer.id,OnlineWorldGameplay.Kind.HELP).success,"no reward replay on relaunch");
   for(int i=0;i<OnlineWorldGameplay.DELIVERY_SECONDS;i++)restored.advanceSecond();require(!restored.gameplay().hasActive()&&restored.gameplay().reputation==offer.reputation,"arrival adds no second reputation reward");require(restored.gameplay().inventory.amount(offer.cost)==after.inventory.amount(offer.cost),"arrival repeats no debit");require(restored.gameplay().operations.size()==1,"completed request remains journal history");
  }
 }
 static class FailingPreferences extends MemoryPreferences {
  boolean fail,applyThenFail;
  @Override public android.content.SharedPreferences.Editor edit(){android.content.SharedPreferences.Editor delegate=super.edit();return new android.content.SharedPreferences.Editor(){
   public android.content.SharedPreferences.Editor putInt(String k,int v){delegate.putInt(k,v);return this;}
   public android.content.SharedPreferences.Editor putBoolean(String k,boolean v){delegate.putBoolean(k,v);return this;}
   public android.content.SharedPreferences.Editor putString(String k,String v){delegate.putString(k,v);return this;}
   public void apply(){delegate.apply();}
   public boolean commit(){if(!fail)return delegate.commit();if(applyThenFail)delegate.commit();return false;}
  };}
 }
 static void saveFailuresAndConcurrency(){
  for(boolean applied:new boolean[]{false,true}) {
   FailingPreferences disk=new FailingPreferences();MockOnlineWorldRepository repo=persistent(disk);require(repo.execute("ember_water",OnlineWorldGameplay.Kind.TRADE).success,"initial valid save");String old=ledger(repo);disk.fail=true;disk.applyThenFail=applied;
   require(!repo.execute("beacon_help",OnlineWorldGameplay.Kind.HELP).success&&old.equals(ledger(repo)),"failed commit never publishes partial in-memory balance");require(!repo.writable()&&!repo.execute("outpost_help",OnlineWorldGameplay.Kind.HELP).success&&!repo.advanceSecond(),"uncertain disk result locks further commands");
   disk.fail=false;MockOnlineWorldRepository restored=persistent(disk);require(restored.writable(),"persisted atomic old or new ledger can restore");require(restored.gameplay().operations.size()==(applied?2:1),"commit uncertainty restores consistent complete transaction");
   if(applied)require(!restored.execute("beacon_help",OnlineWorldGameplay.Kind.HELP).success&&restored.gameplay().reputation==8,"durable operation cannot repeat after reported I/O failure");else require(restored.execute("beacon_help",OnlineWorldGameplay.Kind.HELP).success,"failed non-durable action can retry after restart");
  }
  MemoryPreferences disk=new MemoryPreferences();MockOnlineWorldRepository base=persistent(disk);base.execute("ember_water",OnlineWorldGameplay.Kind.TRADE);String good=ledger(base);
  for(String bad:Arrays.asList("broken",good.replace("1|","99|"),good.replace("ember_water,0;","ember_water,0;ember_water,0;"),good.replace("ember_water,0;","ember_water,-1;"),good.replace("25,","999,"))) {
   MemoryPreferences damaged=new MemoryPreferences();damaged.values.put(OnlineDemoSaveStore.KEY,bad);MockOnlineWorldRepository repo=persistent(damaged);
   require(repo.load().connection==OnlineWorldState.Connection.ERROR&&!repo.writable(),"corrupt/duplicate/inconsistent save fails closed");require(!repo.execute("ember_water",OnlineWorldGameplay.Kind.TRADE).success,"corruption cannot grant new starter resources");require(damaged.getString(OnlineDemoSaveStore.KEY,"").equals(bad)&&damaged.commits==0,"failed load preserves original file for recovery");
  }
  MockOnlineWorldRepository repo=persistent(new MemoryPreferences());java.util.concurrent.atomic.AtomicInteger success=new java.util.concurrent.atomic.AtomicInteger();List<Thread> threads=new ArrayList<>();
  for(int i=0;i<12;i++){Thread thread=new Thread(()->{if(repo.execute("ember_water",OnlineWorldGameplay.Kind.TRADE).success)success.incrementAndGet();});threads.add(thread);thread.start();}
  for(Thread thread:threads)try{thread.join();}catch(InterruptedException e){throw new AssertionError(e);}
  require(success.get()==1&&repo.gameplay().operations.size()==1&&repo.gameplay().inventory.amount(OnlineInventory.Resource.FOOD)==25,"concurrent repeated confirmation commits exactly once");
 }
 static boolean onRoad(float x,float y,OnlineWorldRepository.Snapshot world){
  for(OnlineWorldGeometry.Shape road:world.streets)for(int i=1;i<road.size();i++) {
   double ax=road.x(i-1),ay=road.y(i-1),dx=road.x(i)-ax,dy=road.y(i)-ay;
   double t=Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)));
   if(Math.hypot(x-ax-t*dx,y-ay-t*dy)<.00001)return true;
  }
  return false;
 }
 static void roadRoutesAndRecovery(){
  MemoryPreferences disk=new MemoryPreferences();MockOnlineWorldRepository repo=persistent(disk);OnlineWorldRepository.Snapshot world=repo.load();
  for(OnlineWorldGameplay.Offer offer:repo.gameplay().offers) {
   OnlineWorldGameplay.Operation operation=repo.operation(offer,0);OnlineShelter start=world.shelters.stream().filter(x->x.id.equals(operation.originId)).findFirst().get(),end=world.shelters.stream().filter(x->x.id.equals(operation.targetId)).findFirst().get();float[] point=new float[2];operation.route.position(0,point);require(point[0]==start.position.x&&point[1]==start.position.y,"route begins at real virtual shelter");operation.route.position(1,point);require(point[0]==end.position.x&&point[1]==end.position.y,"route ends at selected shelter");
   for(int sample=0;sample<=100;sample++){operation.route.position(sample/100.,point);require(onRoad(point[0],point[1],world),"delivery follows visible road/driveway segments");}
   operation.route.position(100,point);require(point[0]==end.position.x&&point[1]==end.position.y,"finite route clamps rather than loops");
  }
  require(repo.execute("foundry_materials",OnlineWorldGameplay.Kind.TRADE).success,"start finite delivery");for(int i=0;i<7;i++)repo.advanceSecond();String saved=ledger(repo);MockOnlineWorldRepository loaded=persistent(disk);require(saved.equals(ledger(loaded))&&loaded.gameplay().operation("foundry_materials").elapsedSeconds==7,"mid-route restart restores logical progress");
  for(int i=7;i<24;i++)loaded.advanceSecond();require(!loaded.gameplay().hasActive(),"route completes only at destination duration");String complete=ledger(loaded);int commits=disk.commits;require(!loaded.advanceSecond()&&complete.equals(ledger(loaded))&&commits==disk.commits,"idle/completed route has no repeat effects or writes");
  MockOnlineWorldRepository after=persistent(disk);require(!after.gameplay().hasActive()&&after.gameplay().operations.size()==1,"restart never reactivates historical route");require(after.execute("outpost_help",OnlineWorldGameplay.Kind.HELP).success,"new delivery after old completion");require(after.gameplay().operations.stream().filter(x->x.active()).count()==1&&after.gameplay().operation("outpost_help").elapsedSeconds==0,"only new delivery active, no inherited progress");
 }
 static void deliveryClocks(){
  float[] reference=null;
  for(int fps:new int[]{15,24,30,60,120}) {
   MockOnlineWorldRepository repo=new MockOnlineWorldRepository();repo.execute("foundry_materials",OnlineWorldGameplay.Kind.TRADE);FrameClock time=new FrameClock();OnlineWorldController c=new OnlineWorldController(repo,time);c.enter();c.frame();double previous=0;
   for(int second=0;second<24;second++) {
    for(int f=1;f<=fps;f++) {
     time.now=second*1_000_000_000L+f*1_000_000_000L/fps;c.frame();double current=c.state.deliverySeconds[0];require(current+1e-8>=previous&&current-previous<=1.0/fps+.000001,"frame-time delivery never reverses or jumps, fps="+fps);previous=current;
    }
    if(second==9){float[] point=Arrays.copyOf(c.state.deliveryPositions,2);if(reference==null)reference=point;else require(Math.hypot(reference[0]-point[0],reference[1]-point[1])<.00001,"same route at same time across render rates");}
    c.advanceSecond();
   }
   require(!c.state.gameplay.hasActive()&&Math.abs(c.state.deliverySeconds[0]-24)<.00001,"visually reaches endpoint at logical completion");c.leave();c.enter();require(!c.state.gameplay.hasActive(),"re-entering never resurrects finished route");
  }
  float[] speedReference=null;
  for(int speed:new int[]{1,2,4}) {
   GameView v=interactive(420,840);radio(v);v.game.paused=false;v.game.speed=speed;v.onlineWorld.execute("outpost_help",OnlineWorldGameplay.Kind.HELP);int minute=v.game.gameMinute;
   for(int second=0;second<8;second++){for(int frame=0;frame<60;frame++)frame(v,16_666_667);v.tick.run();}
   require(v.game.gameMinute==minute+8*speed&&v.onlineWorld.state.gameplay.operation("outpost_help").elapsedSeconds==8,"same host timer separates solo minutes and local demo seconds x"+speed);
   float[] point=Arrays.copyOf(v.onlineWorld.state.deliveryPositions,2);if(speedReference==null)speedReference=point;else require(Arrays.equals(speedReference,point),"x1/x2/x4 do not change visual delivery speed");
   v.game.paused=true;String before=snapshot(v);for(int second=0;second<4;second++){for(int frame=0;frame<60;frame++)frame(v,16_666_667);v.tick.run();}require(before.equals(snapshot(v))&&v.onlineWorld.state.gameplay.operation("outpost_help").elapsedSeconds==12,"solo pause does not change resources; local demo visuals/delivery remain independent");
   v.onlineWorld.leave();v.game.screen=GameView.HOME;for(int i=12;i<24;i++)v.tick.run();require(!v.onlineWorld.state.gameplay.hasActive(),"delivery completes while on another screen");v.onlineWorld.enter();v.game.screen=GameView.ONLINE_WORLD;draw(v);require(!v.onlineWorld.state.gameplay.hasActive(),"no stale route when returning to radio");
  }
 }
 static void interactiveInputAndIsolation(){
  for(int width:new int[]{420,840})for(int height:new int[]{360,500,640,840,1200}){
   GameView v=interactive(width,height);radio(v);OnlineWorldGeometry g=geometry(v);v.game.paused=true;String solo=snapshot(v);
   chooseShelter(v,"demo_ember");tap(v,115,g.panelBottom-90);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.TRADE,"trade button opens gameplay panel");draw(v);text(draw(v),"ТОРГОВЛЯ · ДЕМО");require(v.onlineWorld.panelLineCount>0&&g.contentTop<g.contentBottom,"trade content scrolls inside fixed layout");
   tap(v,120,g.panelBottom-90);require(v.onlineWorld.state.gameplay.operations.size()==1&&v.onlineWorld.state.gameplay.inventory.amount(OnlineInventory.Resource.FOOD)==25,"real touch confirms demo trade");draw(v);String trade=OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay);tap(v,120,g.panelBottom-90);require(trade.equals(OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay)),"rapid repeated UI tap idempotent");
   tap(v,350,g.panelBottom-90);require(v.onlineWorld.state.offerId.equals("ember_food"),"next offer button selects second actual offer");tap(v,180,g.panelBottom-40);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.OBJECT,"back returns to same shelter");
   tap(v,285,g.panelBottom-90);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.HELP,"help button opens request");draw(v);tap(v,120,g.panelBottom-90);require(v.onlineWorld.state.gameplay.reputation==5,"UI help grants correct demo reputation");tap(v,120,g.panelBottom-90);require(v.onlineWorld.state.gameplay.reputation==5,"UI reward cannot repeat");
   v.onlineWorld.closeCard();tap(v,340,35);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.INVENTORY,"inventory available from map header");draw(v);text(draw(v),"ДЕМО-ИНВЕНТАРЬ");tap(v,120,g.panelBottom-90);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.HISTORY,"history opened from inventory");draw(v);text(draw(v),"ИСТОРИЯ РАДИОСЕТИ");
   if(v.onlineWorld.panelLineCount>g.visibleLines()){
    v.onTouchEvent(new MotionEvent(170*v.scale,(g.contentBottom-5)*v.scale,MotionEvent.ACTION_DOWN));v.onTouchEvent(new MotionEvent(170*v.scale,(g.contentTop-45)*v.scale,MotionEvent.ACTION_MOVE));v.onTouchEvent(new MotionEvent(170*v.scale,(g.panelBottom-90)*v.scale,MotionEvent.ACTION_UP));require(v.onlineWorld.state.panelScroll>0&&v.onlineWorld.state.panel==OnlineWorldState.Panel.HISTORY,"long history scrolls without triggering pinned action");
   }
   require(solo.equals(snapshot(v)),"trades/help/history change no solo balance, resident, expedition, defense or save_v02 key");
   v.onlineWorld.closeCard();for(int i=0;i<9;i++){v.tick.run();frame(v,16_666_667);}float x=v.onlineWorld.state.deliveryPositions[0],y=v.onlineWorld.state.deliveryPositions[1];tap(v,g.x(x),g.y(y));require(v.onlineWorld.state.panel==OnlineWorldState.Panel.DELIVERY,"animated delivery marker opens progress card");draw(v);text(draw(v),"ДЕМО-ДОСТАВКА");
   v.onlineWorld.closeCard();String separate=OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay);v.save();GameView restart=new GameView(new Context());restart.game.rnd=new Quiet();draw(restart);require(separate.equals(OnlineDemoSaveStore.encode(restart.onlineWorld.state.gameplay))&&solo.equals(snapshot(restart)),"default Android wiring restores separate gameplay and original save");
   require(Context.preferences.values.keySet().stream().noneMatch(k->k.startsWith("online"))&&Context.namedPreferences.containsKey(OnlineDemoSaveStore.FILE),"distinct preference file; no online keys in save_v02");
   for(int i=9;i<24;i++)v.tick.run();require(!v.onlineWorld.state.gameplay.hasActive(),"all historical deliveries finish");String after=OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay);draw(v);draw(v);require(after.equals(OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay)),"drawing completed routes never reapplies effects");
  }
 }

 static int drawnDeliveries(GameView v){int color=OnlineWorldRenderer.alpha(v.accent,170);return (int)draw(v).commands.stream().filter(s->s.startsWith("drawPath")&&s.contains("STROKE:BUTT:"+color+":"+Float.toString(v.sy(2))+":")).count();}
 static void routeCanvasAndButtons(){
  GameView v=interactive(840,640);radio(v);v.game.paused=true;OnlineWorldGeometry g=geometry(v);require(drawnDeliveries(v)==0,"no transaction routes before dispatch");chooseShelter(v,"demo_foundry");tap(v,210,g.panelBottom-90);require(v.onlineWorld.state.panel==OnlineWorldState.Panel.OBJECT,"gap between shelter buttons is inert");tap(v,115,g.panelBottom-90);String first=v.onlineWorld.state.offerId;tap(v,305,g.panelBottom-90);require(v.onlineWorld.state.offerId.equals(first)&&v.onlineWorld.state.gameplay.operations.isEmpty(),"gap between confirm and next does not execute a trade");
  tap(v,115,g.panelBottom-90);v.onlineWorld.closeCard();require(drawnDeliveries(v)==1,"Canvas draws active road route");for(int i=0;i<23;i++)v.tick.run();require(drawnDeliveries(v)==1,"route retained until destination");v.tick.run();require(drawnDeliveries(v)==0,"Canvas removes route at actual completion");GameView restored=new GameView(new Context());restored.game.screen=GameView.ONLINE_WORLD;restored.onlineWorld.enter();draw(restored);require(drawnDeliveries(restored)==0,"loading completed delivery draws no historical line");
  radio(v);v.onlineWorld.execute("ember_water",OnlineWorldGameplay.Kind.TRADE);v.onlineWorld.closeCard();require(drawnDeliveries(v)==1,"new delivery draws only its route, no inherited historical line");String before=snapshot(v);for(int f=0;f<60;f++)frame(v,16_666_667);require(before.equals(snapshot(v)),"route drawing and animation are read-only for solo simulation");
 }
 static void persistentPvp(){
  GameView v=interactive(840,640);radio(v);String solo=snapshot(v);OnlineZone zone=v.onlineWorld.state.zones.get(4);clickZone(v,zone);OnlineWorldGeometry g=geometry(v);draw(v);
  require(!v.onlineWorld.preparePvp(false).success&&!v.onlineWorld.preparePvp(true).success,"API cannot bypass separate warning/consent");require(!v.onlineWorld.state.pvpEnabled,"PvP initially off");tap(v,180,g.panelBottom-90);require(v.onlineWorld.state.confirmingPvp&&!v.onlineWorld.state.pvpEnabled,"first tap only warning");draw(v);
  require(OnlineWorldGameplay.PVP_SQUAD.size()==3&&OnlineWorldGameplay.PVP_SQUAD.stream().noneMatch(s->v.game.people.stream().anyMatch(r->s.contains(r.name))),"squad uses three demo members, no real residents");
  tap(v,180,g.panelBottom-90);require(v.onlineWorld.state.pvpEnabled&&v.onlineWorld.state.gameplay.pvpZoneId.equals(zone.id),"explicit consent saves preparation state");require(solo.equals(snapshot(v)),"preparation has no real costs, injuries or combat");
  String saved=OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay);GameView restart=new GameView(new Context());require(restart.onlineWorld.state.pvpEnabled&&saved.equals(OnlineDemoSaveStore.encode(restart.onlineWorld.state.gameplay)),"prepared squad restores after restart");
  v.onlineWorld.closeCard();clickZone(v,zone);tap(v,180,g.panelBottom-90);require(!v.onlineWorld.state.pvpEnabled,"voluntary preparation can be withdrawn");GameView disabled=new GameView(new Context());require(!disabled.onlineWorld.state.pvpEnabled&&solo.equals(snapshot(disabled)),"withdrawal persists with solo intact");
 }
 public static void main(String[] args){allTrades();allHelp();saveFailuresAndConcurrency();roadRoutesAndRecovery();deliveryClocks();interactiveInputAndIsolation();routeCanvasAndButtons();persistentPvp();System.out.println("PASS: "+checks+" ONLINE 0.2 assertions; all trade/help offers, shortages/duplicate/concurrent confirmations, atomic I/O failures and corrupted saves, exactly-once reputation, road-bound finite routes, restart/screen switching, 15/24/30/60/120 FPS and x1/x2/x4 invariance, 10 scaled layouts, explicit persistent PvP preparation and unchanged save_v02.");}
}
'''

def main():
    sources = {"com/lastdom/game/"+p.name: p.read_text() for p in (fixtures.ROOT/fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game;public class R{public static class drawable{public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="online-gameplay-") as directory:
        print(fixtures.run_version(Path(directory), "online02", sources).strip())

if __name__ == "__main__":
    main()
