#!/usr/bin/env python3
"""Stage 5 actual Java logic/save/Canvas input. Requires Python 3/JDK 21.

Checks payments/completion, all rooms/levels, per-cycle fractional bonuses,
builder/expedition membership and process restart. Android doubles, not a device test.
"""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import refactor_regression as fixtures

PROBE = r'''
package com.lastdom.game;
import android.content.*;
import android.graphics.*;
import android.view.*;
import java.util.*;
public class RegressionProbe {
 static int checks;
 static void require(boolean ok,String s){if(!ok)throw new AssertionError(s);checks++;}
 static void close(float a,float b,String s){require(Math.abs(a-b)<.001,s);}
 static class SafeRandom extends Random {public double nextDouble(){return .999;}public int nextInt(int n){return n-1;}}
 static GameView load(){GameView v=new GameView(new Context());v.game.rnd=new SafeRandom();draw(v);return v;}
 static GameView fresh(int w,int h){View.width=w;View.height=h;Context.preferences=new MemoryPreferences();GameView v=load();for(Resident r:v.game.people){r.health=100;r.fatigue=0;}return v;}
 static GameView kill(){TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);return load();}
 static Canvas draw(GameView v){Canvas c=new Canvas();v.onDraw(c);return c;}
 static void text(Canvas c,String s){require(c.commands.stream().anyMatch(x->x.contains(s)),"drawn: "+s);}
 static void tap(GameView v,float x,float y){v.onTouchEvent(new MotionEvent(x*v.scale,y*v.scale,MotionEvent.ACTION_UP));}
 static RoomUpgradeTask task(GameView v){return v.game.roomUpgradeController.active();}
 static List<String> ids(GameView v,int... indices){List<String> s=new ArrayList<>();for(int i:indices)s.add(v.game.people.get(i).id);return s;}
 static String disk(GameView v){v.game.save();return Context.preferences.values.toString();}
 static String snapshot(GameView v){RoomUpgradeTask t=task(v);String s=Arrays.toString(v.game.roomLevels)+"/"+v.game.mats+"/"+v.game.buildingRoom+"/"+v.game.buildRemaining+"/"+v.game.people.size();if(t!=null)s+="/"+t.id+"/"+t.room+"/"+t.targetLevel+"/"+t.builderId+"/"+t.startMinute+"/"+t.duration+"/"+t.elapsed+"/"+t.paidCost+"/"+t.costPaid+"/"+t.completed;for(Resident r:v.game.people)s+="/"+r.id+":"+r.status+":"+r.job;return s;}
 static void fail(GameView v,int room,String builder,String message){String before=disk(v),state=snapshot(v);require(!v.game.roomUpgradeController.start(room,builder).isEmpty(),message);require(before.equals(disk(v))&&state.equals(snapshot(v)),"rejected construction is atomic: "+message);}
 static void invariant(GameView v){require(v.game.mats>=0,"nonnegative materials");Set<String> ids=new HashSet<>();for(Resident r:v.game.people)require(ids.add(r.id),"no duplicated residents");require(v.game.roomUpgrades.stream().filter(t->!t.completed).count()<=1,"one active construction");for(int level:v.game.roomLevels)require(level>=1&&level<=3,"bounded room level");}
 static void cycles(){
  int[][] costs={{20,45},{15,35},{20,40},{25,50},{30,60},{15,30}},duration={{120,240},{90,180},{120,210},{150,270},{180,300},{90,180}};
  for(int room=0;room<6;room++){
   GameView v=fresh(420,840);v.game.mats=1000;String builder=v.game.people.get(4).id;
   for(int target=2;target<=3;target++){
    while(v.game.survivalController.protectedRest(v.game.people.get(4)))v.game.advanceMinute();
    int before=v.game.mats;require(v.game.roomUpgradeController.start(room,builder).isEmpty(),"upgrade every room/level");RoomUpgradeTask t=task(v);String id=t.id;
    require(t.targetLevel==target&&t.paidCost==costs[room][target-2]&&t.duration==duration[room][target-2],"exact balance config");
    require(v.game.mats==before-t.paidCost&&t.costPaid&&!t.completed,"paid exactly once at launch");require(Context.preferences.commits>0,"launch durable before UI");
    require(v.game.people.get(4).status==Resident.Status.BUILDING&&v.game.homeRoomFor(v.game.people.get(4))==room,"builder membership and target room");
    require(!v.game.assignJob(4,"Материалы"),"cannot reassign builder");
    fail(v,room,builder,"same resident/room cannot start twice");fail(v,(room+1)%6,v.game.people.get(0).id,"second construction blocked");
    int materials=v.game.mats;for(int speed:new int[]{1,2,4}){int elapsed=task(v).elapsed,minute=v.game.gameMinute;v.game.speed=speed;v.tick.run();require(task(v).elapsed==elapsed+speed&&v.game.gameMinute==minute+speed,"construction speed "+speed);require(Context.preferences.getInt("upgrade5_"+(target-2)+"_elapsed",-1)==task(v).elapsed,"progress persisted each minute");}
    int elapsed=task(v).elapsed;v.game.paused=true;for(int i=0;i<4;i++)v.tick.run();require(task(v).elapsed==elapsed,"construction pause");v.game.paused=false;v.game.save();
    String state=snapshot(v);List<String> log=new ArrayList<>(v.game.log);v=kill();require(state.equals(snapshot(v))&&v.game.log.equals(log),"restart preserves membership/progress, no duplicate journal");require(v.game.mats==materials&&task(v).id.equals(id),"load never repays");
    for(int frame=0;frame<400;frame++)draw(v);float[] anchor=ShelterGeometry.fullSceneResidentPos(room,0,1,116,Math.max(610,v.H/v.scale-72));
    close(v.game.residentY[4],anchor[1],"builder feet on unchanged room floor");
    require(v.residentRenderer.shelterResidentAt(v.game.residentX[4],v.game.residentY[4])==4,"builder actual feet hitbox");
    require(v.game.findBestResident("Механик","")!=4,"AI excludes builder");
    require(!v.game.expeditionController.start("shop",ids(v,4)).isEmpty(),"builder cannot expedition");
    v.game.roomUpgradeController.complete(task(v));require(v.game.roomLevels[room]==target-1,"premature completion rejected");
    while(task(v).remaining()>1)v.game.advanceMinute();v=kill();require(task(v).remaining()==1&&v.game.roomLevels[room]==target-1,"last minute restore");
    v.game.advanceMinute();require(task(v)==null&&v.game.roomLevels[room]==target,"exact single level completion");
    RoomUpgradeTask done=v.game.roomUpgrades.get(target-2);require(done.completed&&done.elapsed==done.duration,"completed flag persisted");
    require(v.game.people.get(4).status==Resident.Status.HOME&&v.game.people.get(4).job.equals("Отдых"),"builder released");
    require(v.game.mats==materials&&v.game.buildingRoom==-1&&v.game.buildRemaining==0,"no extra cost, compatibility timer cleared");
    final int completedLevel=target;long entries=v.game.log.stream().filter(s->s.contains("улучшена до ур. "+completedLevel)).count();require(entries==1,"one completion journal entry");
    for(int restart=0;restart<3;restart++){v=kill();v.game.roomUpgradeController.complete(v.game.roomUpgrades.get(target-2));v.game.advanceMinute();require(v.game.roomLevels[room]==target&&v.game.mats==materials,"completion never replayed after restart");invariant(v);}
   }
   fail(v,room,builder,"above maximum blocked");v.roomUpgradePanel.open(room);text(draw(v),"Максимальный уровень");
  }
 }
 static void invalid(){
  GameView v=fresh(420,840);String id=v.game.people.get(0).id;v.game.mats=19;fail(v,0,id,"insufficient materials");require(v.game.roomUpgradeController.blockedReason(0).equals("Не хватает материалов: 1"),"exact missing materials");
  v.game.mats=100;fail(v,-1,id,"negative room");fail(v,6,id,"unknown room");fail(v,0,"missing","missing builder");fail(v,0,null,"null builder");v.game.people.get(0).alive=false;fail(v,0,id,"dead builder");v.game.people.get(0).alive=true;v.game.people.get(0).health=0;fail(v,0,id,"zero health");v.game.people.get(0).health=100;v.game.gameOver=true;fail(v,0,id,"game over");v.game.gameOver=false;
  require(v.game.expeditionController.start("shop",ids(v,0)).isEmpty(),"launch before build");fail(v,0,id,"expedition participant cannot build");
 }
 static GameView production(int room,int level){GameView v=fresh(420,840);for(Resident r:v.game.people)r.job="Экспедиция";v.game.roomLevels[room]=level;v.game.productionRemainders.values.clear();return v;}
 // Stage 6 moves recovery from processJobs to the existing minute clock.
 static void recoveryMinutes(GameView v,int minutes){v.game.food=v.game.water=100;for(int i=0;i<minutes;i++)v.game.survivalController.advanceMinute();}
 static void recoveryDay(GameView v){v.game.food=v.game.water=100;for(int i=0;i<1440;i++)v.game.survivalController.advanceMinute();}
 static void bonuses(){
  for(int level=1;level<=3;level++){
   GameView v=production(0,level);int power=v.game.power;for(int i=0;i<10;i++){v.game.processJobs();v.game.save();v=kill();}require(v.game.power-power==20*RoomUpgradeConfig.percent(0,level)/100,"generator 100/125/150%, persisted half units");
   v=production(1,level);v.game.people.get(0).job="Еда";int food=v.game.food;for(int i=0;i<5;i++){v.game.people.get(0).fatigue=0;v.game.processJobs();v.game.save();v=kill();}require(v.game.food-food==20*RoomUpgradeConfig.percent(1,level)/100,"kitchen bonus on base4, fractions retained");
   v=production(3,level);v.game.people.get(4).job="Материалы";int materials=v.game.mats;for(int i=0;i<5;i++){v.game.people.get(4).fatigue=0;v.game.processJobs();v.game.save();v=kill();}require(v.game.mats-materials==20*RoomUpgradeConfig.percent(3,level)/100,"workshop base4, additive level percentage");
   v=production(5,level);v.game.people.get(0).job="Отдых";v.game.people.get(2).job="Отдых";int recovered=0,second=0;for(int i=0;i<2;i++){v.game.people.get(0).fatigue=v.game.people.get(2).fatigue=100;recoveryMinutes(v,480);recovered+=100-v.game.people.get(0).fatigue;second+=100-v.game.people.get(2).fatigue;v.game.save();v=kill();}require(recovered==16*new int[]{5,7,10}[level-1]&&second==recovered,"bedroom separate residents, hourly recovery5/7/10 and fractional persistence");
   v=production(2,level);v.game.people.get(1).job="Лечение";v.game.people.get(0).job=v.game.people.get(2).job="Вода";int healed=0,other=0;for(int i=0;i<2;i++){for(int k:new int[]{0,1,2})v.game.people.get(k).fatigue=0;v.game.people.get(0).health=v.game.people.get(2).health=40;recoveryDay(v);healed+=v.game.people.get(0).health-40;other+=v.game.people.get(2).health-40;v.game.save();v=kill();}require(healed==4*RoomUpgradeConfig.percent(2,level)/100&&other==healed,"medical base2, per patient fractions");
   v=production(4,level);v.game.people.get(2).job="Охрана";v.game.people.get(2).skill=2;int defended=0;for(int i=0;i<5;i++){v.game.threat=100;v.game.people.get(2).fatigue=0;v.game.processJobs();defended+=100-v.game.threat;v.game.save();v=kill();}require(defended==15*RoomUpgradeConfig.percent(4,level)/100,"barricades improve real daily guard threat reduction");
   v=production(4,level);defended=0;for(int i=0;i<5;i++){v.game.threat=100;v.game.event=true;v.game.eventTitle="МАРОДЁРЫ";v.game.choose(1);defended+=100-v.game.threat;v=kill();}require(defended==15*RoomUpgradeConfig.percent(4,level)/100,"barricades improve real marauder defense, fractions persist");require(Context.preferences.getInt("upgrade5_defense",0)==RoomUpgradeConfig.percent(4,level),"derived defense saved");
  }
  GameView v=production(5,2);v.game.people.get(0).job="Отдых";v.game.people.get(0).fatigue=1;recoveryDay(v);require(v.game.people.get(0).fatigue==0&&v.game.people.get(0).survivalFractions.getOrDefault("fatigue",0)==0,"rest cap clears unused fraction");
  v=production(2,2);v.game.people.get(1).job="Лечение";v.game.people.get(0).job="Вода";v.game.people.get(0).health=99;recoveryDay(v);require(v.game.people.get(0).health==100&&v.game.people.get(0).survivalFractions.getOrDefault("health",0)==0,"health cap clears unused fraction");
  v=production(2,2);v.game.people.get(0).job="Отдых";v.game.people.get(0).health=98;v.game.people.get(0).survivalFractions.put("health",72000);recoveryDay(v);require(v.game.people.get(0).health==100&&v.game.people.get(0).survivalFractions.getOrDefault("health",0)==0,"rest reaching full health also clears healing fraction");
  v=production(1,3);v.game.food=Integer.MAX_VALUE;v.game.people.get(0).job="Еда";v.game.processJobs();require(v.game.food==Integer.MAX_VALUE,"resource overflow safe");
  v=fresh(420,840);int power=v.game.power;for(int minute=0;minute<1440;minute++)v.game.advanceMinute();require(v.game.power==power+1,"base generator produces2, normal daily consumption1");
 }
 static void integration(){
  GameView v=fresh(420,840);v.game.mats=25;require(v.game.roomUpgradeController.start(3,v.game.people.get(4).id).isEmpty(),"workshop starts with exact budget");require(v.game.mats==0,"exact budget nonnegative");
  require(v.game.expeditionController.start("garage",ids(v,0,1,2)).isEmpty(),"expedition alongside construction");int fatigue=v.game.people.get(4).fatigue;v.game.processJobs();require(v.game.mats==0&&v.game.people.get(4).fatigue==fatigue,"builder does not produce or rest in another room");
  v.game.autoRespondToIncident();require(v.game.people.get(4).job.equals("Строительство"),"AI cannot change construction assignment");
  while(v.game.expeditionController.active().state()!=Expedition.State.AWAITING_RETURN)v.game.advanceMinute();require(task(v)==null&&v.game.roomLevels[3]==2,"both clocks advance, construction completed during research");
  Expedition expedition=v.game.expeditionController.active();int carried=expedition.cargo.get(ExpeditionLoot.Resource.MATERIALS);require(carried>=15&&v.game.mats==0,"materials not awarded before return");v.game.expeditionController.returnHome(expedition.id);while(v.game.expeditionController.active()!=null)v.game.advanceMinute();require(v.game.mats==carried,"returned materials awarded once");v=kill();v.game.expeditionController.complete(v.game.expeditions.get(0));require(v.game.mats==carried,"return grant remains idempotent with upgrades");
  require(!v.game.roomUpgradeController.start(1,v.game.people.get(0).id).isEmpty()&&v.game.mats==carried,"returned resident must finish rest before building");while(v.game.survivalController.protectedRest(v.game.people.get(0)))v.game.advanceMinute();
  require(v.game.roomUpgradeController.start(1,v.game.people.get(0).id).isEmpty()&&v.game.mats==carried-15,"returned resident/resources usable for next upgrade");
  v=fresh(420,840);v.game.mats=100;v.game.roomUpgradeController.start(4,v.game.people.get(4).id);v.game.expeditionController.start("shop",ids(v,0));while(v.game.expeditionController.active().state()!=Expedition.State.EXPLORING)v.game.advanceMinute();v.game.rnd=new SafeRandom(){public double nextDouble(){return 0;}public int nextInt(int n){return 0;}};while(v.game.expeditionController.active().state()!=Expedition.State.AWAITING_DECISION)v.game.advanceMinute();int elapsed=v.game.expeditionController.active().elapsedMinutes();v.game.rnd=new SafeRandom();while(task(v)!=null)v.tick.run();require(v.game.roomLevels[4]==2&&v.game.expeditionController.active().elapsedMinutes()==elapsed,"pending city decision freezes only expedition, construction continues");invariant(v);
 }
 static void migration(){
  View.width=420;View.height=840;Context.preferences=new MemoryPreferences();Context.preferences.edit().putInt("day",7).putInt("count",1).putString("p0_id","legacy").putString("p0_name","Старый").putInt("p0_fatigue",0).putInt("room0",7).putInt("room3",3).putInt("mats",18).apply();GameView v=load();for(int level:v.game.roomLevels)require(level==1,"Stage4 room levels migrate to new baseline1");require(v.game.mats==18&&task(v)==null&&Context.preferenceName.equals("save_v02"),"old keys/namespace/resources preserved");
  Context.preferences.values.put("buildingRoom",3);Context.preferences.values.put("buildRemaining",10);v=kill();require(task(v)!=null&&task(v).legacy&&task(v).remaining()==10&&task(v).targetLevel==2,"legacy paid construction preserves remaining time");require(task(v).paidCost==0&&v.game.people.get(0).status==Resident.Status.BUILDING,"legacy builder assigned without new charge");v.game.save();v=kill();for(int i=0;i<10;i++)v.game.advanceMinute();require(v.game.roomLevels[3]==2&&v.game.mats==18,"legacy completion once without new payment");v=kill();v.game.advanceMinute();require(v.game.roomLevels[3]==2,"legacy completion cannot repeat");
  v.game.roomLevels[3]=3;v.game.save();v=kill();require(v.game.roomLevels[3]==3,"new schema preserves level3");
  Context.preferences=new MemoryPreferences();Context.preferences.edit().putInt("day",2).putInt("count",1).putString("p0_id","waiting").putString("p0_job","Экспедиция").putInt("buildingRoom",0).putInt("buildRemaining",10).putInt("mats",18).apply();v=load();require(task(v).builderId.isEmpty(),"legacy construction cannot steal unavailable resident");int elapsed=task(v).elapsed;v.game.advanceMinute();require(task(v).elapsed==elapsed,"legacy construction waits for a builder without losing progress");v.game.people.get(0).job="Отдых";while(task(v).builderId.isEmpty())v.game.advanceMinute();require(!task(v).builderId.isEmpty()&&task(v).elapsed==elapsed+1&&v.game.people.get(0).status==Resident.Status.BUILDING&&v.game.mats==18,"legacy builder adopted once when available, no repayment");v=kill();require(task(v).elapsed==elapsed+1&&v.game.people.get(0).status==Resident.Status.BUILDING,"adopted legacy membership durable");
  v=fresh(420,840);v.game.mats=100;v.game.roomUpgradeController.start(0,v.game.people.get(0).id);int materials=v.game.mats;Context.preferences.values.put("upgrade5_0_paid",false);v=kill();require(task(v)==null&&v.game.roomLevels[0]==1&&v.game.mats==materials,"invalid unpaid task cannot create level/payment");
 }
 static void ui(){
  for(int width:new int[]{420,840})for(int height:new int[]{640,840,1200})for(int room=0;room<6;room++){
   GameView v=fresh(width,height*width/420);v.game.mats=100;v.roomUpgradePanel.open(room);RoomUpgradeLayout layout=new RoomUpgradeLayout(height);Canvas c=draw(v);text(c,"Стоимость:");text(c,"После улучшения:");text(c,"УЛУЧШИТЬ ДО УРОВНЯ 2");text(c,"НАЗНАЧИТЬ");require(layout.actionTop>layout.top+93&&layout.secondaryTop+43<layout.bottom,"footer inside panel");require(layout.top+93+(layout.visibleLines-1)*18<layout.actionTop-24,"body does not overlap buttons");
   float y=layout.top+110;v.onTouchEvent(new MotionEvent(180*v.scale,y*v.scale,MotionEvent.ACTION_DOWN));v.onTouchEvent(new MotionEvent(180*v.scale,(y-72)*v.scale,MotionEvent.ACTION_MOVE));v.onTouchEvent(new MotionEvent(180*v.scale,(layout.actionTop+20)*v.scale,MotionEvent.ACTION_UP));require(!v.roomUpgradePanel.choosing&&task(v)==null,"scroll release cannot start/choose build");require(v.roomUpgradePanel.lineCount>layout.visibleLines?v.roomUpgradePanel.scroll>0:v.roomUpgradePanel.scroll==0,"scroll bounded by actual text");
   tap(v,180,layout.actionTop+24);require(v.roomUpgradePanel.choosing&&v.game.mats==100,"button chooses builder before payment");text(draw(v),"ВЫБОР СТРОИТЕЛЯ");tap(v,180,layout.rowTop+28);require(!v.roomUpgradePanel.choosing&&!v.roomUpgradePanel.builderId.isEmpty()&&v.game.mats==100,"selecting builder is not charging");text(draw(v),"НАЧАТЬ СТРОИТЕЛЬСТВО");tap(v,180,layout.actionTop+24);require(task(v)!=null&&v.game.mats==100-RoomUpgradeConfig.cost(room,2),"confirmation charges correct cost");text(draw(v),"СТРОИТЕЛЬСТВО ИДЁТ");int paid=v.game.mats;tap(v,180,layout.actionTop+24);require(v.game.mats==paid&&v.game.roomUpgrades.size()==1,"repeated confirmation never charges twice");
   int selected=v.game.selectedRoom;tap(v,180,layout.top+85);require(v.game.selectedRoom==selected&&v.game.overlay==2,"modal text tap does not reach room beneath");tap(v,300,layout.secondaryTop+20);require(v.game.overlay==0,"close button accessible");text(draw(v),"↑ 2 • 0%");
   for(int frame=0;frame<400;frame++)draw(v);Resident builder=v.game.people.get(0);float ground=v.game.residentY[0];require(v.game.homeRoomFor(builder)==room,"builder in target room");float[] anchor=ShelterGeometry.fullSceneResidentPos(room,0,116,Math.max(610,height-72));close(ground,anchor[1],"scaled builder uses old groundY");tap(v,v.game.residentX[0],ground);require(v.game.selected==0&&v.game.overlay==1,"builder still selectable on scene");ResidentNeedsLayout residentLayout=new ResidentNeedsLayout(height);tap(v,180,residentLayout.footer+22);require(!v.game.jobMenu,"builder job menu blocked");invariant(v);
  }
  GameView v=fresh(420,640);for(int i=0;i<8;i++)v.game.people.add(v.game.make("Новый "+i,"Житель",2));for(Resident resident:v.game.people)resident.fatigue=0;v.game.mats=100;v.roomUpgradePanel.open(0);RoomUpgradeLayout l=new RoomUpgradeLayout(640);tap(v,180,l.actionTop+20);while(v.roomUpgradePanel.page<12/l.capacity)tap(v,355,l.pageY);tap(v,180,l.rowTop+(12%l.capacity)*64+25);require(v.roomUpgradePanel.builderId.equals(v.game.people.get(12).id),"builder paging reaches all residents");
  v=fresh(420,840);v.game.mats=100;v.game.expeditionController.start("shop",ids(v,0));v.roomUpgradePanel.open(0);l=new RoomUpgradeLayout(840);tap(v,180,l.actionTop+20);text(draw(v),"В экспедиции");tap(v,180,l.rowTop+25);require(v.roomUpgradePanel.choosing&&v.roomUpgradePanel.builderId.isEmpty()&&task(v)==null,"unavailable row cannot select builder");
  v=fresh(420,840);v.game.mats=100;v.roomUpgradePanel.open(0);l=new RoomUpgradeLayout(840);tap(v,100,l.secondaryTop+20);require(v.game.jobMenu,"original work assignment retained");
 }
 public static void main(String[] args){cycles();invalid();bonuses();integration();migration();ui();System.out.println("PASS: "+checks+" shelter upgrade assertions; all 12 upgrades, atomic costs/completion, builder AI/expedition exclusion, clock/pause, fractional production/healing/rest/defense, restarts, legacy migration, six screen sizes, paging/scroll/touch and existing ground anchors.");}
}
'''


def main():
    sources = {"com/lastdom/game/" + p.name: p.read_text()
               for p in (fixtures.ROOT / fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="shelter-upgrade-regression-") as directory:
        print(fixtures.run_version(Path(directory), "shelter-upgrades", sources).strip())


if __name__ == "__main__":
    main()
