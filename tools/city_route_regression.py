#!/usr/bin/env python3
"""Cosmetic road A* geometry, actual Canvas/touch and old active save compatibility."""
import sys
sys.dont_write_bytecode=True
from pathlib import Path
import tempfile,base64,json
import story_regression as base
import refactor_regression as fixtures
PREFIX=base.PROBE[:base.PROBE.index(' static void triggerAndMessages')]
LEGACY=PREFIX+r'''
 public static void main(String[]args){GameView v=fresh(420,840);for(Resident r:v.game.people){r.health=100;r.fatigue=0;r.job="Материалы";r.autoRecovery=false;}require(v.game.expeditionController.start("shop",ids(v,0)).isEmpty(),"old real expedition");minute(v,23);v.game.save();System.out.println("SAVE");for(Map.Entry<String,Object>e:Context.preferences.values.entrySet())System.out.println("VALUE:"+e.getKey()+":"+e.getValue().getClass().getSimpleName()+":"+Base64.getEncoder().encodeToString(e.getValue().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
}
'''
PROBE=PREFIX+r'''
 static void collision(float[][] route,CityRoutePlanner planner){for(int i=1;i<route.length;i++){float[] a=route[i-1],b=route[i];int n=Math.max(1,(int)Math.ceil(CityRoutePlanner.length(a,b,planner.height)*4));for(int k=0;k<=n;k++){float t=k/(float)n,x=(a[0]+(b[0]-a[0])*t)*388,y=(a[1]+(b[1]-a[1])*t)*planner.height;for(CityRoutePlanner.Box box:planner.obstacles)if(!(i==route.length-1&&planner.entranceBuildings.contains(box)))require(!(x>box.l+.2&&x<box.r-.2&&y>box.t+.2&&y<box.b-.2),"segment excludes obstacles "+planner.target.id+" H="+planner.height+" segment="+i+" point="+x+","+y+" box="+box.l+","+box.t+","+box.r+","+box.b);}}}
 static void routes(){GameView v=fresh(420,840);v.game.story.flags.add(StoryFlags.SIGNAL);v.game.story.objectives.add("dawn.eva");v.game.story.objectives.add("dawn.review");v.game.story.items.add(StoryInvestigationConfig.KEY);v.game.story.factions.started=true;v.game.storyController.syncMap();v.game.storyController.investigation.syncMap();v.game.storyController.factions.syncMap();int targets=0;for(int h:new int[]{640,840,1200}){CityMapLayout l=new CityMapLayout(h);for(MapLocation m:v.game.cityLocations){if(m.kind==MapLocation.Kind.DISTRICT){v.cityMap.districtsLayer=true;v.cityMap.districtFilterId="";}else{v.cityMap.districtsLayer=false;ExplorationConfig.District owner=ExplorationConfig.owner(m.id);v.cityMap.districtFilterId=owner==null?"":owner.id;if(owner!=null)v.game.explorationController.district(owner.id).state=CityDistrict.State.EXPLORED;}
  List<MapLocation> locations=v.cityMap.visibleLocations();CityRoutePlanner planner=new CityRoutePlanner(m,locations,l.bottom-l.top);float[][] route=v.cityMap.routes.route(m,locations,l);require(route.length>2,"safe route exists: "+h+" "+m.id);require(route==v.cityMap.routes.route(m,locations,l),"path cached per scene/layout/target");float[] start=CityRoutePlanner.point(route,0,l.bottom-l.top),end=CityRoutePlanner.point(route,1,l.bottom-l.top);close(start[0],CityMapLayout.SHELTER_X,"starts home X");close(start[1],CityMapLayout.SHELTER_Y,"starts home Y");close(end[0],m.mapX,"target X");close(end[1],m.mapY,"target Y");collision(route,planner);
  float total=0;for(int i=1;i<route.length;i++)total+=CityRoutePlanner.length(route[i-1],route[i],l.bottom-l.top);float[] prev=start;for(int i=1;i<=400;i++){float[] p=CityRoutePlanner.point(route,i/400f,l.bottom-l.top);require(CityRoutePlanner.length(prev,p,l.bottom-l.top)<=total/400+.01,"bounded constant-length progress through rounded turns");prev=p;}
  int nearStreet=0;for(int sample=0;sample<100;sample++){float[] at=CityRoutePlanner.point(route,sample/99f,l.bottom-l.top);if(planner.streetDistance(at[0]*388,at[1]*(l.bottom-l.top))<=30)nearStreet++;}require(nearStreet>=65,"route uses shared road graph: "+m.id+" "+nearStreet+"%");
  targets++;}}
 require(targets==75,"all 25 old/district/story/faction targets at three portrait heights");
 }
 static void active(){Context.preferences=legacy();GameView v=loaded();Expedition e=v.game.expeditionController.active();require(e.elapsedMinutes()==23&&e.durationMinutes==45,"old active expedition preserves game timing");float progress=e.progress();int food=v.game.food,water=v.game.water;String snapshot=Context.preferences.values.toString();v.game.screen=GameView.CITY_MAP;draw(v);CityMapLayout l=new CityMapLayout(v.H/v.scale);MapLocation m=v.game.expeditionController.location(e.locationId);float[][] route=v.cityMap.routes.route(m,v.cityMap.visibleLocations(),l);float[] p=CityRoutePlanner.point(route,v.cityMap.displayProgress,l.bottom-l.top);tap(v,l.x(p[0]),l.y(p[1]));require(v.cityMap.expeditionPanel,"marker hit routing shares exact drawn path");require(e.progress()==progress&&v.game.food==food&&v.game.water==water&&snapshot.equals(Context.preferences.values.toString()),"cosmetic routing changes no save, resources or time");v.cityMap.expeditionPanel=false;for(int speed:new int[]{1,2,4}){int elapsed=e.elapsedMinutes();v.game.paused=true;v.tick.run();require(e.elapsedMinutes()==elapsed,"pause stops travel");v.game.paused=false;v.game.event=false;v.game.speed=speed;v.tick.run();require(e.elapsedMinutes()==elapsed+speed,"1/2/4 game clock unchanged");draw(v);}v=restart(v);e=v.game.expeditionController.active();int left=e.remainingMinutes();minute(v,left+30);require(e.state()==Expedition.State.AWAITING_RETURN,"normal research result generated once");int cargo=e.cargo.total();require(v.game.expeditionController.returnHome(e.id).isEmpty(),"existing return control");minute(v,45);require(e.state()==Expedition.State.COMPLETED&&e.rewardCredited,"existing delivery complete");String id=e.id;int delivered=v.game.food;draw(v);Canvas c=new Canvas();v.expeditionRenderer.drawRoute(c,l);require(c.commands.isEmpty(),"completed route and marker disappear");v.game.screen=GameView.HOME;draw(v);v.game.screen=GameView.CITY_MAP;draw(v);v=restart(v);e=v.game.expeditionController.find(id);v.game.expeditionController.complete(e);require(v.game.food==delivered&&e.cargo.total()==cargo,"restart never repeats reward");c=new Canvas();v.expeditionRenderer.drawRoute(c,l);require(c.commands.isEmpty(),"completed route absent after tabs and reload");for(Resident r:v.game.people){r.health=100;r.fatigue=0;r.hunger=10;r.thirst=10;r.job="Материалы";r.autoRecovery=false;}require(v.game.expeditionController.start("pharmacy",ids(v,0)).isEmpty(),"new expedition immediately allowed");require(v.game.expeditionController.active().locationId.equals("pharmacy"),"new route uses only current target");}
 public static void main(String[]args){routes();active();System.out.println("PASS: "+checks+" city routing assertions; all 25 targets at 640/840/1200, road/building/icon/label exclusion, curved corners and length progress, cache, actual Canvas/touch, genuine STORY1.2 active save, unchanged 1/2/4 clock and rewards, completed cleanup and new routes.");}
}
'''
def main():
 with tempfile.TemporaryDirectory(prefix='routes13-') as directory:
  fixtures.PROBE=LEGACY
  old=fixtures.run_version(Path(directory),'story12',base.sources('745d784c2a1c43d2985735fcccdddcf9ee1f118d'));values=[]
  for line in old.splitlines():
   if line.startswith('VALUE:'):
    _,key,kind,value=line.split(':',3);value=base64.b64decode(value).decode();method={'Integer':'putInt','Boolean':'putBoolean','String':'putString'}[kind];literal=json.dumps(value,ensure_ascii=False) if kind=='String' else value;values.append(f'e.{method}({json.dumps(key)}, {literal});')
  method=' static MemoryPreferences legacy(){MemoryPreferences p=new MemoryPreferences();SharedPreferences.Editor e=p.edit();'+''.join(values)+'e.commit();return p;}'
  fixtures.PROBE=PROBE.replace(' public static void main(String[]args){routes()',method+' public static void main(String[]args){routes()')
  try:print(fixtures.run_version(Path(directory),'routing',base.sources()).strip())
  except __import__('subprocess').CalledProcessError as error:
   if error.output:print(error.output)
   raise
if __name__=='__main__':main()
