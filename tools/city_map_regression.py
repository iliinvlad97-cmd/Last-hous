#!/usr/bin/env python3
"""Stage 1 navigation and map interactions using deterministic JVM Android doubles.

Run: python3 tools/city_map_regression.py (Python 3 and JDK 21).
Does not replace rendering/installation tests on an Android device.
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
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static Canvas draw(GameView v){Canvas c=new Canvas();v.onDraw(c);return c;}
 static void text(Canvas c,String s){require(c.commands.stream().anyMatch(x->x.contains(s)),"drawn text: "+s);}
 static void tap(GameView v,float x,float y){require(v.onTouchEvent(new MotionEvent(x*v.scale,y*v.scale,MotionEvent.ACTION_UP)),"touch consumed");}
 static void nav(GameView v,int i,float h){tap(v,38+i*77,h-40);}
 static String save(GameView v){v.game.save();return Context.preferences.values.toString();}
 public static void main(String[] args){
  for(int width:new int[]{420,840})for(int logicalHeight:new int[]{640,840,1200}){
   View.width=width;View.height=logicalHeight*width/420;Context.preferences=new MemoryPreferences();
   GameView v=new GameView(new Context());draw(v);GameController game=v.game;
   List<Resident> residents=new ArrayList<>(game.people);
   String original=save(v);CityMapLayout m=new CityMapLayout(logicalHeight);
   nav(v,2,logicalHeight);require(game.screen==GameView.CITY_MAP,"MAP tab opens city");
   text(draw(v),GameView.VERSION_LABEL);require(v.cityMap.locations.size()==6,"six locations");
   Set<String> ids=new HashSet<>();int available=0,locked=0;
   for(MapLocation p:v.cityMap.locations){
    require(ids.add(p.id),"unique id");require(p.mapX>0&&p.mapX<1&&p.mapY>0&&p.mapY<1,"normalized endpoints");
    tap(v,m.x(p.mapX),m.y(p.mapY));require(v.cityMap.selected()==p,"all marker centers selectable");
    Canvas panel=draw(v);
    if(p.isLocked()){
     locked++;text(panel,"РАЙОН НЕ ИССЛЕДОВАН");text(panel,"Сначала исследуйте:");text(panel,ExplorationConfig.owner(p.id).name);
     tap(v,210,m.panelBottom-40);require(v.cityMap.selected()==null,"locked panel closes");
    }else{
     available++;text(panel,p.name.toUpperCase(Locale.ROOT));text(panel,p.loot);text(panel,p.distance.label);text(panel,p.risk.label);text(panel,"Не исследовано");
     tap(v,210,m.panelBottom-40);require(v.cityMap.preparation()==p,"preparation uses selected location");
     text(draw(v),"ПОДГОТОВКА ЭКСПЕДИЦИИ");require(save(v).equals(original),"preparation alone has no simulation/save side effects");
     tap(v,210,m.panelBottom-80);require(game.screen==GameView.CITY_MAP&&v.cityMap.preparation()==null,"back closes preparation");
    }
   }
   require(available==4&&locked==2,"four available and two locked");
   tap(v,m.x(CityMapLayout.SHELTER_X),m.y(CityMapLayout.SHELTER_Y));require(game.screen==0,"shelter marker returns home");draw(v);
   require(v.game==game&&game.people.equals(residents),"same simulation and residents after navigation");
   float sceneBottom=Math.max(610,logicalHeight-72);tap(v,210,116+(sceneBottom-116)*.16f);
   require(game.screen==GameView.CITY_MAP,"existing scene exit opens city");
   nav(v,0,logicalHeight);require(game.screen==0,"HOME tab returns home");
   nav(v,2,logicalHeight);nav(v,1,logicalHeight);require(game.screen==2,"JOURNAL opens");
   nav(v,0,logicalHeight);nav(v,2,logicalHeight);nav(v,3,logicalHeight);
   require(game.screen==0&&game.overlay==3,"RESIDENTS keeps original overlay");game.overlay=0;
   Resident person=game.people.get(0);person.job="Отдых";game.residentVisualReady=false;draw(v);
   person.job="Ремонт";nav(v,2,logicalHeight);nav(v,0,logicalHeight);
   for(int i=0;i<400;i++)draw(v);
   float[] destination=ShelterGeometry.fullSceneResidentPos(0,0,116,sceneBottom);
   require(Math.abs(game.residentY[0]-destination[1])<.001,"AI reaches generator floor after return");
  }
  View.width=420;View.height=840;Context.preferences=new MemoryPreferences();
  Context.preferences.edit().putInt("day",7).putInt("count",1).putString("p0_name","Старое сохранение").putString("p0_job","Отдых").putInt("expPerson",0).putInt("expLoc",1).putInt("expRemain",12).apply();
  GameView old=new GameView(new Context());draw(old);
  require(Context.preferenceName.equals("save_v02")&&old.game.day==7&&old.game.people.size()==1,"legacy save loads");
  require(old.game.people.get(0).name.equals("Старое сохранение"),"legacy resident loads");
  String snapshot=save(old);nav(old,2,840);CityMapLayout m=new CityMapLayout(840);
  MapLocation p=old.cityMap.locations.get(0);tap(old,m.x(p.mapX),m.y(p.mapY));tap(old,210,m.panelBottom-40);
  require(snapshot.equals(save(old)),"existing expedition remains unchanged by Stage 1");
  require(MapLocation.State.values().length==5,"location states available");
  p.setState(MapLocation.State.SEARCHED);require(p.state()==MapLocation.State.SEARCHED,"state can evolve later");
  System.out.println("PASS: "+checks+" city map assertions; six screen sizes, both entries, six markers, panels, modal priority, navigation, AI and legacy saves.");
 }
}
'''


def main():
    sources = {"com/lastdom/game/" + p.name: p.read_text()
               for p in (fixtures.ROOT / fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="city-map-regression-") as directory:
        print(fixtures.run_version(Path(directory), "city-map", sources).strip())


if __name__ == "__main__":
    main()
