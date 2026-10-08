#!/usr/bin/env python3
"""Actual Java rest clock/AI/SharedPreferences regressions. Android doubles, not a device test."""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import refactor_regression as fixtures

PROBE = r'''
package com.lastdom.game;
import android.content.*;
import android.graphics.*;
import java.util.*;
public class RegressionProbe {
 static int checks;
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static void close(double a,double b,String s){require(Math.abs(a-b)<.0000001,s+": "+a+" != "+b);}
 static class Quiet extends Random {public int nextInt(int n){return n-1;}public double nextDouble(){return .999;}}
 static GameView load(){GameView v=new GameView(new Context());v.game.rnd=new Quiet();v.onDraw(new Canvas());return v;}
 static GameView fresh(int level,int fatigue){Context.preferences=new MemoryPreferences();GameView v=load();v.game.roomLevels[5]=level;v.game.food=v.game.water=100;for(Resident r:v.game.people)r.fatigue=0;Resident r=sergey(v);r.job="Отдых";r.fatigue=fatigue;return v;}
 static GameView kill(){TreeMap<String,Object> disk=new TreeMap<>(Context.preferences.values);Context.preferences=new MemoryPreferences();Context.preferences.values.putAll(disk);return load();}
 static Resident sergey(GameView v){return v.game.people.get(2);}
 static double fatigue(Resident r){return r.fatigue+r.survivalFractions.getOrDefault("fatigue",0)/(double)SurvivalController.NEED_DENOMINATOR;}
 static void minutes(GameView v,int n){for(int i=0;i<n;i++)v.game.advanceMinute();}
 static void elapsed(){
  for(int level=1;level<=3;level++)for(int elapsed:new int[]{20,60,120}){
   GameView v=fresh(level,60);Resident r=sergey(v);minutes(v,elapsed);double expected=60-new int[]{5,7,10}[level-1]*elapsed/60.0;
   close(fatigue(r),expected,"exact minute recovery L"+level+"/"+elapsed);require(r.fatigue==Math.round(expected),"UI fatigue rounds with saved fractional carry");require(r.job.equals("Отдых")&&!v.game.survivalController.working(r)&&v.game.homeRoomFor(r)==5,"rest is exclusive, bedroom anchor remains");
   v.game.save();double before=fatigue(r);int displayed=r.fatigue;String save=Context.preferences.values.toString();v=kill();r=sergey(v);v.game.save();close(fatigue(r),before,"restart exact remainder");require(r.fatigue==displayed&&save.equals(Context.preferences.values.toString()),"reload does not reset metrics/keys");
   minutes(v,20);close(fatigue(r),expected-new int[]{5,7,10}[level-1]/3.0,"continues after restart");
  }
  GameView v=fresh(1,20);minutes(v,20);require(sergey(v).fatigue==18,"reported device case now 20 -> 18 in 20 minutes");close(fatigue(sergey(v)),18+1/3.0,"20-minute fractional result");
  for(int level:new int[]{-1,0,4,5}){v=fresh(level,60);minutes(v,60);close(fatigue(sergey(v)),60-(level<1?5:level==4?13:16),"future/invalid level consistent scaling");}
  v=fresh(3,1);minutes(v,120);require(sergey(v).fatigue==0&&sergey(v).survivalFractions.get("fatigue")==0,"zero floor, no banked negative fatigue");
 }
 static void speeds(){
  for(int frames:new int[]{0,1,10}){GameView v=fresh(1,60);for(int minute=0;minute<20;minute++){for(int frame=0;frame<frames;frame++)v.onDraw(new Canvas());v.game.advanceMinute();}close(fatigue(sergey(v)),60-5/3.0,"frame-count independent rest "+frames);}

  for(int level=1;level<=3;level++)for(int speed:new int[]{1,2,4}){
   GameView v=fresh(level,60);v.game.speed=speed;for(int i=0;i<120/speed;i++)v.tick.run();close(fatigue(sergey(v)),60-2*new int[]{5,7,10}[level-1],"speed-independent 120 minutes");
   v.game.paused=true;v.game.save();String paused=Context.preferences.values.toString();for(int i=0;i<20;i++)v.tick.run();v.game.save();require(paused.equals(Context.preferences.values.toString()),"pause freezes clock and fatigue");
  }
  GameView v=fresh(2,60);int elapsed=0;for(int speed:new int[]{1,2,4,1,4,2}){v.game.speed=speed;for(int i=0;i<5;i++)v.tick.run();elapsed+=5*speed;v.game.save();v=kill();}close(fatigue(sergey(v)),60-7*elapsed/60.0,"switching speeds and process restart");
 }
 static void assignments(){
  GameView v=fresh(1,20);Resident r=sergey(v);require(v.game.findBestResident("Охрана","")!=2,"AI excludes manually resting guard");
  v.game.eventTitle="МАРОДЁРЫ";v.game.autoRespondToIncident();require(r.job.equals("Отдых"),"incident cannot interrupt manual rest");
  v.game.mats=100;v.game.save();String before=Context.preferences.values.toString();require(!v.game.roomUpgradeController.start(0,r.id).isEmpty(),"resting resident not simultaneously a builder");v.game.save();require(before.equals(Context.preferences.values.toString()),"rejected build atomic");
  minutes(v,235);require(r.fatigue==0&&fatigue(r)>0&&r.job.equals("Отдых"),"rounded zero is not full rest yet");require(v.game.findBestResident("Охрана","")!=2&&!v.game.roomUpgradeController.unavailableReason(r).isEmpty(),"fractional rest protected at displayed zero");minutes(v,5);close(fatigue(r),0,"full manual rest after 240 minutes");require(v.game.roomUpgradeController.unavailableReason(r).isEmpty(),"fully rested resident can build");
  v=fresh(1,80);r=sergey(v);r.job="Охрана";minutes(v,1);require(r.autoRecovery&&r.job.equals("Отдых"),"exhausted guard starts auto rest");minutes(v,599);close(fatigue(r),30,"auto rest reaches old premature 30-point threshold");require(r.autoRecovery&&r.job.equals("Отдых")&&v.game.findBestResident("Охрана","")!=2,"AI keeps resting past 30");v.game.save();v=kill();r=sergey(v);require(r.resumeJob.equals("Охрана")&&r.autoRecovery,"resume job survives restart");minutes(v,359);require(r.job.equals("Отдых")&&fatigue(r)>0,"rest not released before zero");minutes(v,1);close(fatigue(r),0,"auto rest completes at exact zero");require(r.job.equals("Отдых"),"no work fatigue on last rest minute");minutes(v,1);require(r.job.equals("Охрана")&&!r.autoRecovery,"work resumes once on next minute");close(fatigue(r),30/1440.0,"work fatigue only after rest ends");
  v=fresh(1,20);r=sergey(v);r.job="Охрана";minutes(v,60);close(fatigue(r),21.25,"working fatigue remains +30/day");
  v=fresh(3,20);r=sergey(v);r.job="Лечится";minutes(v,60);close(fatigue(r),20-22/24.0,"treatment rate unchanged, no bedroom boost outside rest");
 }
 static void legacy(){
  GameView prior=fresh(1,20);Resident positive=sergey(prior);positive.survivalFractions.put("fatigue",120000);double priorExact=fatigue(positive);minutes(prior,1);require(positive.fatigue==20,"legacy work carry cannot visibly increase fatigue during rest");close(fatigue(positive),priorExact-5/60.0,"legacy positive carry preserves exact decreasing fatigue");

  Context.preferences=new MemoryPreferences();Context.preferences.edit().putInt("day",1).putInt("count",3).putString("p2_name","Сергей").putString("p2_job","Отдых").putInt("p2_fatigue",20).putInt("p2_health",81).putInt("p2_hunger",23).putInt("p2_thirst",31).putInt("p2_morale",54).putInt("upgrade5_schema",1).putInt("survival6_schema",1).putInt("p2_survivalFraction_fatigue",-96000).apply();GameView v=load();Resident r=sergey(v);require(r.fatigue==20&&r.health==81&&r.hunger==23&&r.thirst==31&&r.morale==54,"legacy existing metrics untouched on load");double previous=fatigue(r);minutes(v,20);close(fatigue(r),previous-5/3.0,"existing signed carry retained under new rate");
 }
 public static void main(String[] args){elapsed();speeds();assignments();legacy();System.out.println("PASS: "+checks+" rest-recovery assertions; 20/60/120 minutes, all levels, rounding/fractions, pause/1x/2x/4x/speed switching, restart/legacy metrics, AI/full rest, builder exclusion and unchanged working/treatment rates.");}
}
'''

def main():
    sources = {"com/lastdom/game/" + p.name: p.read_text()
               for p in (fixtures.ROOT / fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="rest-recovery-regression-") as directory:
        print(fixtures.run_version(Path(directory), "rest-recovery", sources).strip())

if __name__ == "__main__":
    main()
