#!/usr/bin/env python3
"""Stage 7.1 actual Java Canvas/input checks. Android doubles, not device screenshots."""
import os
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import refactor_regression as fixtures
import raid_regression as raids

PROBE = raids.PROBE[:raids.PROBE.index(' static void frequency()')] + r'''
 static DefensePanelLayout layout(GameView v){return v.defenseRenderer.layout();}
 static void primary(GameView v){tap(v,180,layout(v).actionTop+24);}
 static void secondary(GameView v,boolean left){tap(v,left?80:290,layout(v).secondaryTop+22);}
 static void bounded(GameView v){
  DefensePanelLayout l=layout(v);
  require(l.top>=70&&l.bottom<=v.H/v.scale-86,"panel above navigation");
  require(l.bodyTop<l.bodyBottom&&l.bodyBottom<l.actionTop,"content viewport separate from actions");
  require(l.actionTop+48<l.secondaryTop&&l.secondaryTop+44<l.bottom,"large fixed action touch areas");
  if(v.defensePanel.list())require(l.rowTop+l.capacity*l.rowHeight-DefensePanelLayout.ROW_GAP<=l.pageY-16,"resident cards don't overlap pager");
  require(v.defensePanel.scroll>=0&&v.defensePanel.scroll<=l.maxScroll(v.defenseRenderer.content.height),"scroll bounds");
 }
 static void widths(GameView v){
  DefensePanelContent data=v.defenseRenderer.content;
  for(DefensePanelContent.Block block:data.blocks){
   if(block.lines!=null)for(String s:block.lines)require(v.p.measureText(s)>=0,"valid body text");
   if(block.headings!=null)for(String s:block.headings){v.p.setTypeface(DefensePanelContent.BOLD);v.p.setTextSize(v.sy(11));require(v.p.measureText(s)<=v.sy(336),"heading fits card");}
   if(block.lines!=null)for(String s:block.lines){v.p.setTypeface(DefensePanelContent.NORMAL);v.p.setTextSize(v.sy(block.percent>=0?12:13));require(v.p.measureText(s)<=v.sy(336),"wrapped text fits card");}
  }
  for(DefensePanelContent.ResidentRow row:data.residents){
   v.p.setTypeface(DefensePanelContent.BOLD);v.p.setTextSize(v.sy(12));for(String s:row.title)require(v.p.measureText(s)<=v.sy(336),"resident name/profession fits");
   v.p.setTypeface(DefensePanelContent.NORMAL);v.p.setTextSize(v.sy(11));for(String s:row.detail)require(v.p.measureText(s)<=v.sy(336),"unavailability fits");
  }
 }
 static void emit(GameView v,String name){
  try{PathHolder.write(name,v);}catch(Exception error){throw new RuntimeException(error);}
 }
 static class PathHolder {
  static void write(String name,GameView v)throws Exception{
   java.nio.file.Path directory=java.nio.file.Path.of("/tmp/defense-ui-previews");java.nio.file.Files.createDirectories(directory);
   Canvas c=new Canvas();v.defenseRenderer.draw(c);
   java.nio.file.Files.write(directory.resolve(name+".txt"),c.commands);
  }
 }
 static void preparation(){
  for(int[] size:new int[][]{{360,640},{420,640},{420,840},{540,960},{1080,2340},{420,1200}}){
   GameView v=fresh();View.width=size[0];View.height=size[1];draw(v);discover(v,RaidState.Enemy.MARAUDERS,40,16);v.defensePanel.pending();Canvas c=draw(v);
   text(c,"ПРИБЛИЖАЕТСЯ НАПАДЕНИЕ");text(c,"Сила текущей обороны");text(c,"ПРОЧНОСТЬ БАРРИКАД");text(c,"ДО НАПАДЕНИЯ: 60 МИН.");bounded(v);widths(v);
   require(c.commands.stream().anyMatch(s->s.startsWith("clipRect")),"body clipped to viewport");
   DefensePanelContent cached=v.defenseRenderer.content;DefensePanelLayout cachedLayout=layout(v);String state=snapshot(v);
   for(int i=0;i<20;i++){draw(v);require(v.defenseRenderer.content==cached&&layout(v)==cachedLayout,"unchanged frames reuse cards/layout");}
   require(state.equals(snapshot(v)),"drawing can't alter/save battle or resources");
   if(size[0]==420&&size[1]==840)emit(v,"preparation");
   primary(v);require(v.defensePanel.mode==DefensePanelController.Mode.DEFENDERS,"prepare opens roster");draw(v);bounded(v);widths(v);
   tap(v,80,layout(v).rowTop+22);require(raid(v).defenders.containsKey(v.game.people.get(0).id),"shared geometry selects resident");
   // Unavailable residents are filtered; the controller still rejects stale/direct assignment.
   v.game.people.get(1).health=20;draw(v);String unavailable=v.game.people.get(1).id;require(v.defenseRenderer.content.residents.stream().noneMatch(row->row.id.equals(unavailable)),"low-health resident not offered for assignment");
   String before=snapshot(v);require(!v.game.raidController.toggleDefender(unavailable).isEmpty()&&before.equals(snapshot(v)),"unavailable resident never assigned, no mutation");
   primary(v);draw(v);text(draw(v),"Иван");bounded(v);
   minutes(v,60);v.defensePanel.pending();draw(v);text(draw(v),"Сила защиты в бою");text(draw(v),"ПРОГРЕСС НАПАДЕНИЯ");bounded(v);
   require(!v.defenseRenderer.content.primaryEnabled&&!v.defenseRenderer.content.secondaryEnabled,"battle locks roster/repair start");
   if(size[0]==420&&size[1]==840)emit(v,"battle");
   primary(v);require(v.defensePanel.mode==DefensePanelController.Mode.STATUS,"disabled battle action does nothing");
  }
 }
 static void results(){
  for(int power:new int[]{15,30,100}){
   GameView v=fresh();discover(v,RaidState.Enemy.MARAUDERS,power,243);minutes(v,90);v.defensePanel.openStatus();draw(v);
   RaidState report=raid(v);double historic=report.defenseAtStart;String old=snapshot(v);
   DefensePanelContent data=v.defenseRenderer.content;
   require(data.blocks.get(1).leftLabel.equals("Сила защиты в бою")&&data.blocks.get(1).leftValue.equals(DefensePanelContent.number(historic)),"headline uses saved battle strength");
   require(data.blocks.get(2).lines.get(0).contains("Текущая сила обороны"),"current defense explicitly separated");
   String title=power==15?"ОБОРОНА УСПЕШНА":power==30?"ЧАСТИЧНЫЙ ПРОРЫВ":"ОБОРОНА ПРОВАЛЕНА";text(draw(v),title);
   for(int i=0;i<5;i++){v.defensePanel.open=false;v.defensePanel.openStatus();draw(v);}require(old.equals(snapshot(v)),"reopening report never reapplies consequences");
   v=kill();v.defensePanel.openStatus();draw(v);require(old.equals(snapshot(v))&&raid(v).defenseAtStart==historic,"result process restart keeps historic power");
   if(power==100){emit(v,"result-top");v.defensePanel.scroll=layout(v).maxScroll(v.defenseRenderer.content.height);emit(v,"result-bottom");}
   v.game.raidController.durability=100;draw(v);require(!v.defenseRenderer.content.secondaryVisible,"unavailable result repair hidden");
   require(v.defenseRenderer.content.blocks.get(1).leftValue.equals(DefensePanelContent.number(historic)),"repair/current defense can't rewrite history");
   v.game.raidController.durability=50;draw(v);require(v.defenseRenderer.content.secondaryVisible,"result repair offered when allowed");
   secondary(v,true);require(v.defensePanel.mode==DefensePanelController.Mode.REPAIR,"result repair navigation");draw(v);bounded(v);
   secondary(v,true);require(v.defensePanel.mode==DefensePanelController.Mode.STATUS,"back retains historic report");primary(v);require(raid(v).phase==RaidState.Phase.COMPLETED&&!v.defensePanel.open,"acknowledge closes result only");
   v.defensePanel.openStatus();draw(v);close(raid(v).defenseAtStart,historic,"historic power after acknowledgement");
  }
 }
 static void repairUi(){
  GameView v=fresh();v.game.raidController.durability=90;v.defensePanel.openStatus();draw(v);secondary(v,true);primary(v);
  require(v.defensePanel.mode==DefensePanelController.Mode.BUILDERS,"repair picker");int mats=v.game.mats;
  tap(v,80,layout(v).rowTop+22);require(v.game.mats==mats-RaidConfig.REPAIR_COST&&v.game.raidController.repairing(),"one charge through real touch path");
  String paid=snapshot(v);for(int i=0;i<10;i++){secondary(v,false);v.defensePanel.openStatus();secondary(v,true);draw(v);primary(v);}
  require(paid.equals(snapshot(v)),"closing/reopening/stale primary cannot pay twice");
  minutes(v,20);String progress=snapshot(v);v=kill();require(progress.equals(snapshot(v))&&v.game.raidController.repair.elapsed==20,"repair restore no payment or progress reset");
  v.defensePanel.openStatus();secondary(v,true);draw(v);emit(v,"repair");
  v.game.paused=true;for(int i=0;i<10;i++)v.tick.run();require(v.game.raidController.repair.elapsed==20,"repair paused with panel open");
  v.game.paused=false;v.game.speed=4;for(int i=0;i<10;i++)v.tick.run();require(v.game.raidController.repair.completed&&v.game.raidController.durability==100,"repair +25 capped100 after60 game minutes");
  require(v.game.mats==mats-RaidConfig.REPAIR_COST,"unchanged balance no double repair cost");
  String complete=snapshot(v);v=kill();v.defensePanel.openStatus();draw(v);primary(v);require(complete.equals(snapshot(v)),"completed full-durability repair can't repeat");
 }
 static void cacheAvailabilityAndLargeStock(){
  GameView v=fresh();v.game.raidController.durability=50;v.game.mats=Integer.MAX_VALUE;v.defensePanel.openStatus();secondary(v,true);draw(v);
  DefensePanelContent.Block stock=v.defenseRenderer.content.blocks.get(1);v.p.setTypeface(DefensePanelContent.BOLD);v.p.setTextSize(v.sy(stock.leftValueSize));
  require(v.p.measureText(stock.leftValue)<=v.sy(152),"large saved stock fits compact stat tile");
  primary(v);Resident builder=v.game.people.get(0);builder.job="Отдых";builder.fatigue=0;builder.autoRecovery=false;builder.survivalFractions.put("fatigue",1);draw(v);
  require(v.defenseRenderer.content.residents.stream().noneMatch(row->row.id.equals(builder.id)),"fractional rest still excludes builder");
  DefensePanelContent previous=v.defenseRenderer.content;builder.survivalFractions.put("fatigue",0);draw(v);
  require(v.defenseRenderer.content!=previous&&v.defenseRenderer.content.residents.stream().anyMatch(row->row.id.equals(builder.id)),"cached availability updates when fractional rest completes");
  builder.autoRecovery=true;draw(v);require(v.defenseRenderer.content.residents.stream().noneMatch(row->row.id.equals(builder.id)),"automatic rest lock reflected without integer stat changes");
  for(int value:new int[]{90,50,20}){v.game.raidController.durability=value;v.defensePanel.mode=DefensePanelController.Mode.REPAIR;draw(v);
   require(v.defenseRenderer.content.blocks.get(0).color==(value>=70?v.good:value>=35?v.accent:v.danger),"green/orange/red durability states");}
 }
 static void historicalReportDuringNewThreat(){
  GameView v=fresh();discover(v,RaidState.Enemy.MARAUDERS,15,243);minutes(v,90);v.defensePanel.openStatus();RaidState history=raid(v);double historic=history.defenseAtStart;
  v.game.day=4;v.game.gameMinute=0;discover(v,RaidState.Enemy.INFECTED,45,243);RaidState active=raid(v);v.game.roomLevels[4]=3;draw(v);
  require(v.defenseRenderer.content.primary.equals("ПОНЯТНО"),"old result retains acknowledgement when new threat arrives");
  require(v.defenseRenderer.content.blocks.get(1).leftValue.equals(DefensePanelContent.number(historic)),"new current force cannot overwrite old battle strength");
  primary(v);require(history.phase==RaidState.Phase.COMPLETED&&active.phase==RaidState.Phase.WARNING&&!v.defensePanel.open,"result action acknowledges only its own raid");
  v.defensePanel.pending();require(v.defensePanel.open&&v.defensePanel.report==active,"new warning still available after result closes");
 }
 static void longContent(){
  for(int[] size:new int[][]{{420,640},{360,640},{1080,2340}}){
   GameView v=fresh();View.width=size[0];View.height=size[1];draw(v);discover(v,RaidState.Enemy.INFECTED,100,243);
   for(int i=0;i<10;i++)v.game.people.add(v.game.make("Очень длинное имя защитника номер "+i,"Житель",2));
   v.game.people.get(0).name="ДлинноеНеразрывноеИмяЗащитникаДляПроверкиПереносаБезОбрезания";
   v.defensePanel.openStatus();primary(v);draw(v);bounded(v);widths(v);
   for(int page=0;page<v.defensePanel.pages(layout(v));page++){
    v.defensePanel.page=page;draw(v);DefensePanelLayout l=layout(v);bounded(v);
    for(int row=0;row<l.capacity&&page*l.capacity+row<v.game.people.size();row++)tap(v,80,l.rowTop+row*l.rowHeight+22);
   }
   primary(v);minutes(v,90);v.defensePanel.pending();draw(v);widths(v);bounded(v);
   DefensePanelLayout l=layout(v);require(l.maxScroll(v.defenseRenderer.content.height)>0,"long roster/injury report scrolls");
   int before=v.defensePanel.scroll;v.onTouchEvent(new MotionEvent(100*v.scale,(l.bodyTop+80)*v.scale,MotionEvent.ACTION_DOWN));
   v.onTouchEvent(new MotionEvent(100*v.scale,(l.bodyTop+20)*v.scale,MotionEvent.ACTION_MOVE));
   v.onTouchEvent(new MotionEvent(100*v.scale,(l.actionTop+20)*v.scale,MotionEvent.ACTION_UP));
   require(v.defensePanel.scroll>before&&raid(v).phase==RaidState.Phase.RESULT&&v.defensePanel.open,"scroll release cannot acknowledge report");
   int max=layout(v).maxScroll(v.defenseRenderer.content.height);v.defensePanel.scroll=99999;draw(v);require(v.defensePanel.scroll==max,"overscroll clamped");bounded(v);
  }
 }
 public static void main(String[] args){preparation();results();repairUi();cacheAvailabilityAndLargeStock();historicalReportDuringNewThreat();longContent();System.out.println("PASS: "+checks+" defense UI assertions; six screen sizes, shared touch geometry, cached read-only cards, bars, frozen historical power/restarts, all outcomes, repair payment/reopen/cap, fixed buttons, wrapped long content/paging/scroll.");}
}
'''

def main():
    sources = {"com/lastdom/game/" + p.name: p.read_text()
               for p in (fixtures.ROOT / fixtures.JAVA_PATH).glob("*.java")}
    sources["com/lastdom/game/R.java"] = "package com.lastdom.game; public class R {public static class drawable {public static final int shelter_clean=1,shelter_full_scene=2;}}"
    # Also exercise wrapping with proportional Cyrillic font metrics, beyond the
    # original fixed-width doubles. This remains a desktop check, not Android rendering.
    fixtures.STUBS["android/graphics/Paint.java"] = fixtures.STUBS["android/graphics/Paint.java"].replace(
        "return s.length()*size*.5f;",
        'return (float)new java.awt.Font("SansSerif",type!=null&&type.toString().endsWith(":1")?1:0,1)'
        '.deriveFont(Math.max(1,size)).getStringBounds(s,new java.awt.font.FontRenderContext(null,true,true)).getWidth();')
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="defense-ui-regression-") as directory:
        os.environ.setdefault("XDG_CACHE_HOME", directory)
        print(fixtures.run_version(Path(directory), "defense-ui", sources).strip())

if __name__ == "__main__":
    main()
