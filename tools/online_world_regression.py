#!/usr/bin/env python3
"""ONLINE 0.1: actual repository/controller/Canvas/input with injected monotonic frame time.
Android doubles do not verify pixels or FPS on a physical Android device.
"""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import exploration_regression as exploration
import refactor_regression as fixtures

PROBE = exploration.PROBE[:exploration.PROBE.index(' static void defaults()')] + r'''
 static class FrameClock implements java.util.function.LongSupplier {long now;public long getAsLong(){return now;}}
 static FrameClock clock;
 static GameView demo(int width,int height){GameView v=fresh();View.width=width;View.height=height*width/420;clock=new FrameClock();v=new GameView(new Context(),new MockOnlineWorldRepository(),clock);v.game.rnd=new Quiet();draw(v);return v;}
 static void radio(GameView v){v.game.screen=GameView.CITY_MAP;draw(v);tap(v,342,55);require(v.game.screen==GameView.ONLINE_WORLD,"radio entry uses existing city map");draw(v);}
 static OnlineWorldGeometry geometry(GameView v){return v.onlineWorld.geometry(v.H/v.scale);}
 static void frame(GameView v,long nanos){clock.now+=nanos;draw(v);}
 static void closeCard(GameView v){OnlineWorldGeometry g=geometry(v);tap(v,180,g.panelBottom-40);draw(v);}
 static void clickZone(GameView v,OnlineZone zone){float nx=zone.id.equals("safe_north")?.37f:zone.id.equals("safe_south")?.37f:zone.id.equals("pve_industry")?.10f:.90f;float ny=zone.id.equals("safe_north")?.12f:zone.id.equals("safe_south")?.87f:zone.id.equals("pve_industry")?.50f:zone.id.equals("pve_infected")?.82f:.20f;OnlineWorldGeometry g=geometry(v);tap(v,g.x(nx),g.y(ny));require(v.onlineWorld.state.zone()==zone,"select region with shared normalized polygon: "+zone.id);}
 static void defaults(){
  OnlineWorldRepository repo=new MockOnlineWorldRepository();OnlineWorldRepository.Snapshot snapshot=repo.load();OnlineWorldState state=new OnlineWorldState(snapshot);
  require(state.connection==OnlineWorldState.Connection.DEMO&&!state.pvpEnabled,"only demo, PvP defaults off");require(state.shelters.size()==4&&state.zones.size()==5&&state.squads.size()==1,"four fictional shelters/five regions/one cosmetic squad");
  Set<String> ids=new HashSet<>();for(OnlineShelter shelter:state.shelters){require(ids.add(shelter.id)&&shelter.id.startsWith("demo_"),"explicit unique demo shelter ID");require(shelter.level>0&&shelter.linkDescription.contains("демо"),"honest shelter level/link");}
  for(OnlineZone.Type type:OnlineZone.Type.values())require(state.zones.stream().filter(z->z.type==type).count()==(type==OnlineZone.Type.PVP?1:2),"expected region count "+type);
  for(OnlineZone zone:state.zones)require(ids.add(zone.id),"unique zone ID");for(OnlineSquad squad:state.squads)require(ids.add(squad.id),"independent patrol ID");
  require(snapshot.streets.size()>=5&&snapshot.buildings.size()>=12&&snapshot.towers.size()==2,"repository also supplies streets/buildings/radio sites");
  try{snapshot.shelters.clear();throw new AssertionError("mutable repository snapshot");}catch(UnsupportedOperationException expected){checks++;}
  float[] triangle={.1f,.1f,.9f,.1f,.5f,.8f};OnlineWorldGeometry.Shape shape=new OnlineWorldGeometry.Shape(triangle);triangle[0]=.99f;require(shape.x(0)==.1f&&shape.contains(.5f,.3f)&&!shape.contains(.9f,.7f)&&shape.contains(.5f,.1f),"immutable polygon with correct interior/boundary/exterior");
  for(float invalid:new float[]{-1,1.1f,Float.NaN,Float.POSITIVE_INFINITY})try{new OnlineWorldGeometry.Point(invalid,.5f);throw new AssertionError("invalid coordinate accepted");}catch(IllegalArgumentException expected){checks++;}
  require(OnlineWorldState.Connection.values().length==5,"future DEMO/CONNECTING/CONNECTED/OFFLINE/ERROR boundary");
  List<OnlineShelter> shelters=new ArrayList<>(snapshot.shelters);OnlineWorldRepository.Snapshot copied=new OnlineWorldRepository.Snapshot(snapshot.connection,shelters,snapshot.zones,snapshot.squads,snapshot.streets,snapshot.buildings,snapshot.towers,snapshot.mist);shelters.clear();require(copied.shelters.size()==4,"snapshot detaches provider-owned lists");
  try{new OnlineWorldRepository.Snapshot(snapshot.connection,Arrays.asList(snapshot.shelters.get(0),snapshot.shelters.get(0)),snapshot.zones,snapshot.squads,snapshot.streets,snapshot.buildings,snapshot.towers,snapshot.mist);throw new AssertionError("duplicate ID accepted");}catch(IllegalArgumentException expected){checks++;}
 }
 static void scaledInput(){
  for(int width:new int[]{420,840})for(int height:new int[]{500,640,840,1200}){
   GameView v=demo(width,height);v.cityMap.districtsLayer=true;String saved=snapshot(v);int calls=android.os.Handler.delays.size();radio(v);require(android.os.Handler.delays.size()==calls,"opening map starts no extra gameplay timer");require(v.cityMap.districtsLayer,"solo map selection/layer retained");Canvas c=draw(v);text(c,"ДЕМО-РЕЖИМ");text(c,"ONLINE 0.4");text(c,"ДОМ");text(c,"ЖУРНАЛ");text(c,"КАРТА");text(c,"ЖИТЕЛИ");
   OnlineWorldGeometry g=geometry(v);require(g.bottom<v.H/v.scale-78&&g.panelBottom<v.H/v.scale-78,"world and cards never cover navigation");
   for(OnlineShelter shelter:v.onlineWorld.state.shelters){tap(v,g.x(shelter.position.x),g.y(shelter.position.y));require(v.onlineWorld.state.shelter()==shelter&&v.game.screen==GameView.ONLINE_WORLD,"select real repository shelter "+shelter.id);frame(v,16_666_667);text(draw(v),"ВИРТУАЛЬНОЕ УБЕЖИЩЕ");text(draw(v),shelter.name);text(draw(v),"Уровень: "+shelter.level);text(draw(v),shelter.linkDescription);closeCard(v);require(!v.onlineWorld.state.selected(),"close pinned above navigation");}
   for(OnlineZone zone:v.onlineWorld.state.zones){clickZone(v,zone);draw(v);text(draw(v),zone.name);text(draw(v),"Опасность: "+zone.danger);closeCard(v);}
   require(saved.equals(snapshot(v)),"selection and decorative frames never mutate solo save/resources/residents");
   tap(v,58,35);require(v.game.screen==GameView.CITY_MAP&&v.cityMap.districtsLayer,"return restores previous city layer");require(saved.equals(snapshot(v)),"return to solo preserves save_v02 exactly");
   radio(v);tap(v,38,v.H/v.scale-40);require(v.game.screen==GameView.HOME,"home tab remains available");radio(v);tap(v,115,v.H/v.scale-40);require(v.game.screen==2,"journal tab remains available");radio(v);tap(v,269,v.H/v.scale-40);require(v.game.screen==GameView.HOME&&v.game.overlay==3,"residents tab remains available");v.game.overlay=0;radio(v);tap(v,192,v.H/v.scale-40);require(v.game.screen==GameView.CITY_MAP,"city tab remains available");
  }
 }
 static void pvpConsent(){
  for(int height:new int[]{500,640,840}){
   GameView v=demo(840,height);radio(v);String before=snapshot(v);OnlineZone zone=v.onlineWorld.state.zones.get(4);OnlineWorldGeometry g=geometry(v);clickZone(v,zone);draw(v);text(draw(v),"PvP выключено");tap(v,180,g.panelBottom-90);require(v.onlineWorld.state.confirmingPvp&&!v.onlineWorld.state.pvpEnabled,"enable only opens warning");draw(v);text(draw(v),"ДОБРОВОЛЬНЫЙ PvP");text(draw(v),"ПОДТВЕРДИТЬ (ДЕМО)");text(draw(v),"ОТКАЗАТЬСЯ");
   tap(v,180,g.panelBottom-40);require(!v.onlineWorld.state.confirmingPvp&&!v.onlineWorld.state.pvpEnabled,"refusal preserves default off");tap(v,180,g.panelBottom-90);tap(v,372,g.panelTop+28);require(!v.onlineWorld.state.selected()&&!v.onlineWorld.state.pvpEnabled,"closing warning never confirms");
   clickZone(v,zone);draw(v);tap(v,180,g.panelBottom-90);draw(v);
   int lines=v.onlineWorld.panelLineCount;require(lines>g.visibleLines()||height==840,"long warning fits or can scroll on compact screens");
   v.onTouchEvent(new MotionEvent(180*v.scale,(g.contentBottom-10)*v.scale,MotionEvent.ACTION_DOWN));
   v.onTouchEvent(new MotionEvent(180*v.scale,(g.contentTop-50)*v.scale,MotionEvent.ACTION_MOVE));
   v.onTouchEvent(new MotionEvent(180*v.scale,(g.panelBottom-90)*v.scale,MotionEvent.ACTION_UP));
   require(v.onlineWorld.state.confirmingPvp&&!v.onlineWorld.state.pvpEnabled,"swipe ending over confirm does not choose PvP");draw(v);
   v.onTouchEvent(new MotionEvent(180*v.scale,(g.panelBottom-40)*v.scale,MotionEvent.ACTION_DOWN));v.onTouchEvent(new MotionEvent(180*v.scale,(g.panelBottom-90)*v.scale,MotionEvent.ACTION_MOVE));v.onTouchEvent(new MotionEvent(180*v.scale,(g.panelBottom-90)*v.scale,MotionEvent.ACTION_UP));require(v.onlineWorld.state.confirmingPvp&&!v.onlineWorld.state.pvpEnabled,"swipe between pinned buttons cannot confirm consent");
   tap(v,180,g.panelBottom-90);require(v.onlineWorld.state.pvpEnabled&&!v.onlineWorld.state.confirmingPvp,"separate explicit confirmation changes only demo consent");draw(v);text(draw(v),"ВЫКЛЮЧИТЬ PvP (ДЕМО)");require(before.equals(snapshot(v)),"no resource costs, damage, new expeditions or save changes for PvP consent");
   tap(v,180,g.panelBottom-90);require(!v.onlineWorld.state.pvpEnabled,"PvP demo can be disabled immediately");closeCard(v);require(before.equals(snapshot(v)),"closing demo can't grant or remove anything");
   v.game.save();GameView restored=new GameView(new Context());require(!restored.onlineWorld.state.pvpEnabled&&restored.game.screen==GameView.HOME,"restart retains disabled demo preparation and solo progress");require(before.equals(snapshot(restored))&&Context.preferenceName.equals("save_v02"),"demo has no save_v02 keys; separate online persistence");
  }
 }
 static void snapshotRefresh(){
  OnlineWorldRepository.Snapshot data=new MockOnlineWorldRepository().load();final boolean[] fail={false};final int[] reads={0};
  OnlineWorldRepository repository=()->{if(fail[0])throw new IllegalStateException("provider fixture");reads[0]++;return new OnlineWorldRepository.Snapshot(reads[0]==1?OnlineWorldState.Connection.DEMO:OnlineWorldState.Connection.OFFLINE,data.shelters,data.zones,data.squads,data.streets,data.buildings,data.towers,data.mist);};
  FrameClock time=new FrameClock();OnlineWorldController c=new OnlineWorldController(repository,time);c.enter();c.frame();time.now=100_000_000;c.frame();OnlineWorldGeometry g=c.geometry(840);c.touch(MotionEvent.ACTION_UP,g.x(.90f),g.y(.20f),g);c.touch(MotionEvent.ACTION_UP,180,g.panelBottom-90,g);require(c.state.confirmingPvp,"pending consent before snapshot replacement");double animation=c.state.animationSeconds;c.refresh();require(reads[0]==2&&c.state.connection==OnlineWorldState.Connection.OFFLINE&&!c.state.selected()&&!c.state.pvpEnabled&&!c.state.confirmingPvp,"snapshot replacement clears stale selections/consent and supports future status transitions");require(animation==c.state.animationSeconds,"repository refresh doesn't restart cosmetic elapsed time");
  OnlineWorldState before=c.state;fail[0]=true;try{c.refresh();throw new AssertionError("provider failure ignored");}catch(IllegalStateException expected){require(c.state==before,"failed repository refresh cannot partially replace data");}
 }
 static void connectionBoundaries(){
  OnlineWorldRepository.Snapshot data=new MockOnlineWorldRepository().load();
  for(OnlineWorldState.Connection connection:OnlineWorldState.Connection.values()){
   OnlineWorldRepository provider=()->new OnlineWorldRepository.Snapshot(connection,data.shelters,data.zones,data.squads,data.streets,data.buildings,data.towers,data.mist);
   OnlineWorldController c=new OnlineWorldController(provider,()->0);OnlineWorldGeometry g=c.geometry(840);c.touch(MotionEvent.ACTION_UP,g.x(.90f),g.y(.20f),g);
   require(c.state.connection==connection&&!connection.badge.isEmpty()&&!connection.description.isEmpty(),"repository status is preserved for future adapters "+connection);
   if(connection!=OnlineWorldState.Connection.DEMO){require(!c.pvpActionAvailable(),"demo consent cannot become live PvP");c.touch(MotionEvent.ACTION_UP,180,g.panelBottom-90,g);c.touch(MotionEvent.ACTION_UP,180,g.panelBottom-90,g);require(!c.state.pvpEnabled&&!c.state.confirmingPvp,"non-demo status exposes no fake live combat actions");}
  }
  GameView v=demo(840,360);radio(v);OnlineWorldGeometry g=geometry(v);OnlineShelter shelter=v.onlineWorld.state.shelters.get(1);tap(v,g.x(shelter.position.x),g.y(shelter.position.y));draw(v);require(g.contentBottom>g.contentTop&&g.panelTop+65<g.panelBottom-114,"minimum viewport still separates title/content/actions");closeCard(v);require(!v.onlineWorld.state.selected(),"minimum viewport close remains available");
 }
 static void frameTime(){
  double referenceTime=-1;float referenceX=0,referenceY=0,referenceOpacity=0,referenceHighlight=0;
  for(int fps:new int[]{15,24,30,60,120}){
   FrameClock time=new FrameClock();OnlineWorldController controller=new OnlineWorldController(new MockOnlineWorldRepository(),time);controller.enter();controller.frame();OnlineWorldGeometry g=controller.geometry(840);OnlineShelter shelter=controller.state.shelters.get(1);controller.touch(MotionEvent.ACTION_UP,g.x(shelter.position.x),g.y(shelter.position.y),g);
   for(int frame=1;frame<=fps;frame++){time.now=frame*1_000_000_000L/fps;controller.frame();}
   require(Math.abs(controller.state.animationSeconds-1)<1e-9,"elapsed cosmetic time independent of FPS "+fps);require(controller.state.cardOpacity>.999&&controller.state.selectionStrength>.99,"frame-time fade and selection easing");
   if(referenceTime<0){referenceTime=controller.state.animationSeconds;referenceX=controller.state.squadPositions[0];referenceY=controller.state.squadPositions[1];referenceOpacity=controller.state.cardOpacity;referenceHighlight=controller.state.selectionStrength;}
   else{require(Math.abs(referenceX-controller.state.squadPositions[0])<.00001&&Math.abs(referenceY-controller.state.squadPositions[1])<.00001,"same patrol point at equal wall duration across FPS");require(Math.abs(referenceOpacity-controller.state.cardOpacity)<.00001&&Math.abs(referenceHighlight-controller.state.selectionStrength)<.00001,"same opacity/highlight at equal wall duration across FPS");}
   double before=controller.state.animationSeconds;float x=controller.state.squadPositions[0];time.now+=600_000_000_000L;controller.frame();require(before==controller.state.animationSeconds&&x==controller.state.squadPositions[0],"long background gap does not teleport patrol");time.now+=10_000_000;controller.frame();require(controller.state.animationSeconds>before,"animation resumes after background");controller.leave();time.now+=200_000_000;controller.frame();before=controller.state.animationSeconds;controller.enter();time.now+=100_000_000_000L;controller.frame();require(before==controller.state.animationSeconds,"reopening starts a fresh frame clock");time.now-=30_000_000;controller.frame();require(before==controller.state.animationSeconds,"clock anomalies do not reverse animation");
  }
  float[] nearEnd=new float[2],afterLoop=new float[2];OnlineSquad squad=new MockOnlineWorldRepository().load().squads.get(0);squad.position(35.9999,nearEnd,0);squad.position(36.0001,afterLoop,0);require(Math.hypot(nearEnd[0]-afterLoop[0],nearEnd[1]-afterLoop[1])<.0001,"closed patrol path doesn't jump at loop boundary");
  float[] speedReference=null;
  for(int speed:new int[]{1,2,4}){
   GameView v=demo(420,840);v.game.speed=speed;radio(v);String before=snapshot(v);int start=v.game.gameMinute;for(int tick=0;tick<4;tick++)v.tick.run();require(v.game.gameMinute==start+4*speed,"same host timer controls only gameplay speed");
   for(int f=0;f<60;f++)frame(v,16_666_667);float[] current={v.onlineWorld.state.squadPositions[0],v.onlineWorld.state.squadPositions[1]};if(speedReference==null)speedReference=current;else require(Arrays.equals(speedReference,current),"patrol same real-time speed at x"+speed);
   v.game.paused=true;before=snapshot(v);double animation=v.onlineWorld.state.animationSeconds;for(int f=0;f<30;f++){frame(v,16_666_667);v.tick.run();}require(before.equals(snapshot(v))&&v.onlineWorld.state.animationSeconds>animation,"pause freezes gameplay but visual effects keep wall-clock time");
   v.game.paused=false;v.game.speed=1;for(int f=0;f<60;f++){if(f==20)v.game.speed=2;if(f==40)v.game.speed=4;frame(v,16_666_667);}require(v.onlineWorld.state.animationSeconds>2.49&&v.onlineWorld.state.animationSeconds<2.51,"switching speeds doesn't retime demo animation");
  }
 }
 static void isolationAndRepository(){
  GameView source=fresh();launch(source,"residential",true,false,3);String id=recon(source).id;source.game.explorationController.district("industrial").state=CityDistrict.State.DISCOVERED;source.game.save();TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);
  GameView control=load();for(int tick=0;tick<40;tick++){control.game.speed=4;control.tick.run();}String expected=snapshot(control);
  Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);clock=new FrameClock();GameView v=new GameView(new Context(),new MockOnlineWorldRepository(),clock);v.game.rnd=new Quiet();draw(v);radio(v);for(int tick=0;tick<40;tick++){v.game.speed=4;frame(v,16_666_667);v.tick.run();}require(expected.equals(snapshot(v)),"same 160 solo minutes in online view vs existing home simulation including successful recon return");require(v.game.expeditionController.find(id).state()==Expedition.State.COMPLETED&&v.game.explorationController.district("residential").state==CityDistrict.State.EXPLORED,"v1.1.1 recon still returns and opens points while radio is visible");
  require(v.cityMap.expeditionPanel,"real completion report remains accessible above demo");v.game.paused=true;String saved=snapshot(v);for(int i=0;i<10;i++)draw(v);require(saved.equals(snapshot(v)),"demo and actual report draw are read-only");
  GameView restored=kill();require(saved.equals(snapshot(restored))&&restored.game.explorationController.district("residential").state==CityDistrict.State.EXPLORED,"single-player progress survives restart after radio session");
  OnlineWorldRepository.Snapshot data=new MockOnlineWorldRepository().load();OnlineShelter unique=new OnlineShelter("test_provider","Узел тестового источника",7,"Тестовый демо-сигнал","Данные сменного repository",.34f,.80f);final int[] reads={0};OnlineWorldRepository repository=()->{reads[0]++;return new OnlineWorldRepository.Snapshot(OnlineWorldState.Connection.DEMO,Arrays.asList(unique),Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),Collections.emptyList());};
  clock=new FrameClock();GameView custom=new GameView(new Context(),repository,clock);custom.game.rnd=new Quiet();draw(custom);radio(custom);require(reads[0]==1&&custom.onlineWorld.state.shelters.size()==1,"map source is repository, not renderer hardcoded catalog");OnlineWorldGeometry g=geometry(custom);tap(custom,g.x(unique.position.x),g.y(unique.position.y));draw(custom);text(draw(custom),unique.name);for(int i=0;i<10;i++)draw(custom);require(reads[0]==1,"no reload or repository calls per animation frame");
  custom.onlineWorld.closeCard();custom.onlineWorldRenderer.setBackdrop(new Bitmap(99));Canvas c=draw(custom);require(c.commands.stream().anyMatch(x->x.startsWith("drawBitmap[bitmap:99")),"optional art backdrop replaces only scenery");View.width*=2;View.height*=2;draw(custom);g=geometry(custom);tap(custom,g.x(unique.position.x),g.y(unique.position.y));require(custom.onlineWorld.state.shelter()==unique,"backdrop replacement and resize preserve object transform/hitbox");
 }
 public static void main(String[] args){defaults();scaledInput();pvpConsent();snapshotRefresh();connectionBoundaries();frameTime();isolationAndRepository();System.out.println("PASS: "+checks+" online world assertions; demo repository/IDs/zones, radio/back/all tabs, PvP warning/refusal/confirmation/no resource effects, 15/24/30/60/120 FPS clock invariance, pause/speeds/background gaps, eight scaled layouts, modular backdrop, isolated saves and continued v1.1.1 exploration.");}
}
'''

def main():
    sources = {"com/lastdom/game/"+p.name: p.read_text() for p in (fixtures.ROOT/fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game;public class R{public static class drawable{public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="online-world-") as directory:
        print(fixtures.run_version(Path(directory), "online01", sources).strip())

if __name__ == "__main__":
    main()
