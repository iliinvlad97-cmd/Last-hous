#!/usr/bin/env python3
"""Load completed saves produced by the original STORY 1.0 code, without advancing time."""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile, base64, json
import story_regression as base
import story_dialogue_regression as dialogues
import refactor_regression as fixtures
OLD = 'e271dceba59e7647e486051a05a1e3645e5f085d'
LEGACY = base.PROBE[:base.PROBE.index(' public static void main(String[]args)')] + r'''
 static List<MemoryPreferences> legacy(){return Collections.emptyList();}
 static void export(){System.out.println("SAVE");for(Map.Entry<String,Object> e:Context.preferences.values.entrySet()){Object v=e.getValue();System.out.println("VALUE:"+e.getKey()+":"+v.getClass().getSimpleName()+":"+Base64.getEncoder().encodeToString(v.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}}
 public static void main(String[] args){for(int scenario=0;scenario<3;scenario++){
  GameView v=decodeReady();require(v.game.storyController.decode(v.game.people.get(0).id).isEmpty(),"old actual decoding");minute(v,60);
  v.game.storyController.read(StoryConfig.DECODED.id);
  if(scenario>0)require(v.game.storyController.choose(scenario==1?StoryConfig.SHARE.id:StoryConfig.SECRET.id,true).isEmpty(),"old actual choice");
  v.game.paused=true;v.game.save();export();
 }}
}
'''
PROBE = dialogues.PROBE[:dialogues.PROBE.index(' static void migration()')] + r'''
 static void oldActivation(){int index=0;for(MemoryPreferences old:legacy()){
  Context.preferences=old;String choice=old.getString("story1_decision", "");String oldFlags=old.getString("story1_flags", "");GameView v=loaded();
  System.out.println("Loaded old fixture: phase="+v.game.story.phase+", decision="+choice+", flags="+oldFlags+", paused="+v.game.paused+", voicesStage="+v.game.story.voicesStage);
  require(v.game.paused,"old pause persists");
  if(index++==0){require(v.game.story.voicesStage==0,"unfinished STORY1.0 does not activate");v.storyPanel.showJournal();require(v.game.story.voicesStage==0,"journal cannot skip first decision");continue;}
  require(v.game.story.voicesStage==1,"completed old save activates on load WITHOUT game minute");
  require(v.game.story.decisionId.equals(choice)&&v.game.story.flags.contains(StoryFlags.COORDINATES),"old decision and decoded flags preserved");
  require(v.game.story.messages.contains("voices.intro")&&v.game.story.evaTrust==50,"first Eva message and neutral trust");
  int day=v.game.day,minute=v.game.gameMinute,logs=v.game.log.size(),food=v.game.food,water=v.game.water,mats=v.game.mats;String online=OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay);
  for(int i=0;i<5;i++){v.tick.run();v.storyPanel.showJournal();draw(v);}
  require(v.game.day==day&&v.game.gameMinute==minute,"activation does not advance time on pause");require(v.storyPanel.primary().equals("ОТКРЫТЬ РАЗГОВОР"),"paused journal has conversation button");require(v.storyPanel.rows.stream().anyMatch(r->r.text.contains("ГОЛОСА В ЭФИРЕ")),"quest visible");
  StoryPanelLayout layout=new StoryPanelLayout(v.H/v.scale,false);tap(v,180,layout.primaryTop+28);require(v.storyPanel.messageId.equals("voices.intro")&&v.storyPanel.open,"journal actual touch opens Eva");
  v.storyPanel.primaryAction();v.storyPanel.close();v=restart(v);require(v.game.story.dialogueSteps.get("voices.intro")==1&&v.game.story.evaTrust==50,"restart resumes without reset or trust award");
  require(v.game.log.size()==logs&&Collections.frequency(v.game.story.messages,"voices.intro")==1,"load/open/restart no duplicate event");
  require(v.game.food==food&&v.game.water==water&&v.game.mats==mats&&online.equals(OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay)),"solo and online resources unchanged");
  require(Context.preferences.getInt("story1_voicesStage",0)==1,"migration immediately persisted");
 }}
 static void choicesAndConsequences(){for(boolean shared:new boolean[]{true,false}){
  GameView v=ready(shared);require(v.game.story.voicesStage==1,"new play activates immediately on choosing");finish(v,"voices.intro",0);finish(v,"voices.residents",0);finish(v,"voices.eva",0);finish(v,shared?"voices.public":"voices.secret",0);
  String decision=v.game.story.decisionId;int trust=v.game.story.evaTrust;int logs=v.game.log.size();Set<String> flags=new LinkedHashSet<>(v.game.story.flags);
  require(flags.contains(shared?StoryFlags.PUBLIC_SIGNAL:StoryFlags.SECRET_SIGNAL),"test real consequence save");v.game.paused=true;v=restart(v);v.storyPanel.showJournal();require(v.game.story.voicesStage==5&&v.game.story.evaTrust==trust&&v.game.story.decisionId.equals(decision)&&v.game.story.flags.equals(flags)&&v.game.log.size()==logs,"PUBLIC/SECRET saves preserve progress and trust");finish(v,"voices.hook",0);v=restart(v);v.storyPanel.showJournal();require(v.game.story.voicesStage==6&&v.game.story.evaTrust==trust,"completed chain never resets");
 }}
 static void entryPoints(){for(StoryChoice choice:new StoryChoice[]{StoryConfig.SHARE,StoryConfig.SECRET}){
  GameView v=fresh(420,840);v.game.paused=true;v.game.story.phase=StoryState.Phase.DECISION;
  require(v.game.storyController.choose(choice.id,true).isEmpty()&&v.game.story.voicesStage==1,"new decision activates before ANY tick");
 }
 for(MemoryPreferences old:legacy()){
  if(old.getString("story1_decision", "").isEmpty())continue;
  GameView v=fresh(420,840);v.game.paused=true;StoryRepository.load(v.game.story,old);
  require(v.game.story.voicesStage==0,"old state injected through actual repository without lifecycle restore");
  v.storyPanel.showJournal();require(v.game.story.voicesStage==1&&v.storyPanel.primary().equals("ОТКРЫТЬ РАЗГОВОР"),"journal independently reconciles existing save without tick");
 }
 GameView v=fresh(420,840);v.game.story.phase=StoryState.Phase.CHAIN_COMPLETE;v.game.story.decisionId="unknown.choice";Set<String> flags=new LinkedHashSet<>(v.game.story.flags);v.storyPanel.showJournal();
 require(v.game.story.voicesStage==0&&v.game.story.flags.equals(flags)&&v.game.story.decisionId.equals("unknown.choice"),"unknown data not replaced with invented choice/flags");
 require(v.storyPanel.rows.stream().anyMatch(r->r.text.contains("Неизвестный ID первого решения")),"blocked cause visible in journal");
 v.game.story.decisionId=StoryConfig.SHARE.id;v.storyPanel.showJournal();require(v.game.story.voicesStage==0&&v.storyPanel.rows.stream().anyMatch(r->r.text.contains("Нет флага сохранённого решения")),"missing genuine original flag diagnosed without guessing");
 }
 public static void main(String[] args){entryPoints();oldActivation();choicesAndConsequences();conditions();System.out.println("PASS: "+checks+" STORY 1.1.1 activation assertions; original STORY1.0 expedition/decode/choice saves, paused load/journal/resume, both decisions and consequence flags, no duplicate trust/events, unchanged solo/online data.");}
}
'''
def main():
 with tempfile.TemporaryDirectory(prefix='story-activation-') as directory:
  fixtures.PROBE=LEGACY
  old=fixtures.run_version(Path(directory),'story10',base.sources(OLD))
  blocks=[]
  for line in old.splitlines():
   if line=='SAVE':blocks.append([])
   elif line.startswith('VALUE:'):
    _,key,kind,value=line.split(':',3);value=base64.b64decode(value).decode()
    method={'Integer':'putInt','Boolean':'putBoolean','String':'putString'}[kind]
    literal=json.dumps(value,ensure_ascii=False) if kind=='String' else value
    blocks[-1].append(f'e.{method}({json.dumps(key)}, {literal});')
  assert len(blocks)==3
  method=' static List<MemoryPreferences> legacy(){List<MemoryPreferences> result=new ArrayList<>();MemoryPreferences p;SharedPreferences.Editor e;'
  for block in blocks:method+='p=new MemoryPreferences();e=p.edit();'+''.join(block)+'e.commit();result.add(p);'
  method+='return result;}'
  fixtures.PROBE=PROBE.replace(' public static void main(String[] args){entryPoints()',method+' public static void main(String[] args){entryPoints()')
  print(fixtures.run_version(Path(directory),'activation',base.sources()).strip())
if __name__=='__main__':main()
