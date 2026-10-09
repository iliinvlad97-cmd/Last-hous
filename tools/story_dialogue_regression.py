#!/usr/bin/env python3
"""Actual StoryController, persistence, conditional dialogues and Canvas input regressions."""
import sys
sys.dont_write_bytecode=True
from pathlib import Path
import tempfile, subprocess, base64, json
import story_regression as base
import refactor_regression as fixtures
PROBE=base.PROBE[:base.PROBE.index(' static void triggerAndMessages')]+r'''
 static GameView ready(boolean share){GameView v=fresh(420,840);v.game.story.phase=StoryState.Phase.DECISION;v.game.story.flags.add(StoryFlags.COORDINATES);require(v.game.storyController.choose(share?StoryConfig.SHARE.id:StoryConfig.SECRET.id,true).isEmpty(),"first decision");v.game.storyController.advanceMinute();return v;}
 static void finish(GameView v,String id,int answer){StoryDialogue d=StoryDialogue.find(id);while(!v.game.story.completedDialogues.contains(id)){int step=v.game.storyController.step(d);require(v.game.storyController.continueDialogue(id,step,d.lines[step].responses.length==0?-1:answer).isEmpty(),"progress dialogue");}}
 static void branches(){for(boolean shared:new boolean[]{true,false}){
 GameView v=ready(shared);String online=OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay);int food=v.game.food,water=v.game.water,mats=v.game.mats;int hp=v.game.people.get(0).health;double fatigue=v.game.people.get(0).fatigue,morale=v.game.people.get(0).morale;
 require(v.game.story.voicesStage==1&&v.game.story.messages.contains("voices.intro"),"new quest gated after first saved decision");int count=v.game.story.messages.size();v.game.storyController.advanceMinute();require(v.game.story.messages.size()==count,"start once");
 v.storyPanel.showMessage("voices.intro");v.storyPanel.primaryAction();require(v.game.story.dialogueSteps.get("voices.intro")==1,"actual message next line");v.storyPanel.close();v=restart(v);require(v.game.story.dialogueSteps.get("voices.intro")==1,"close/restart exact line");v.storyPanel.showMessage("voices.intro");draw(v);require(v.storyPanel.rows.stream().anyMatch(r->r.text.contains("Реплика 2")),"resume UI line");finish(v,"voices.intro",0);
 Resident absent=v.game.people.get(0);String absentId=absent.id;absent.status=Resident.Status.ON_EXPEDITION;String neutral=v.game.storyController.dialogueText(StoryDialogue.RESIDENTS,0);require(neutral.contains("недоступен")&&!neutral.contains("разговор в убежище"),"away resident not physically present");finish(v,"voices.residents",0);absent.status=Resident.Status.HOME;require(v.game.storyController.dialogueText(StoryDialogue.RESIDENTS,0).equals(neutral),"historical unavailable alternative immutable");
 require(!v.game.storyController.continueDialogue("voices.eva",0,-1).isEmpty(),"responses mandatory");v.storyPanel.showMessage("voices.eva");draw(v);require(!v.storyPanel.primaryEnabled(),"UI requires response");v.storyPanel.response=0;v.storyPanel.primaryAction();int trust=v.game.story.evaTrust;require(trust==55,"evidence answer trust once");require(!v.game.storyController.continueDialogue("voices.eva",0,0).isEmpty()&&v.game.story.evaTrust==trust,"stale double response rejected");v=restart(v);require(v.game.story.evaTrust==55&&v.game.story.answers.size()==1,"answer trust durable before next line");finish(v,"voices.eva",0);
 String branch=shared?"voices.public":"voices.secret",wrong=shared?"voices.secret":"voices.public";require(!v.game.story.messages.contains(wrong),"opposite branch absent");require(!v.game.storyController.continueDialogue(wrong,0,-1).isEmpty(),"opposite branch cannot execute");finish(v,branch,0);int expected=shared?50:60;require(v.game.story.evaTrust==expected,"old choice consequence trust");require(v.game.story.flags.contains(shared?StoryFlags.PUBLIC_SIGNAL:StoryFlags.SECRET_SIGNAL)&&!v.game.story.flags.contains(shared?StoryFlags.SECRET_SIGNAL:StoryFlags.PUBLIC_SIGNAL),"exclusive persistent consequence");require(v.game.story.items.contains("encrypted_station17_fragment")==!shared,"secret fragment only once");finish(v,"voices.hook",0);require(v.game.story.voicesStage==6&&v.game.story.phase==StoryState.Phase.CHAIN_COMPLETE,"chain complete no next chapter");int logs=v.game.log.size(),items=v.game.story.items.size();for(String id:new java.util.ArrayList<>(v.game.story.completedDialogues)){v.storyPanel.showMessage(id);draw(v);v.storyPanel.primaryAction();require(!v.game.storyController.continueDialogue(id,0,0).isEmpty(),"finished replay rejected");}v=restart(v);v.game.storyController.advanceMinute();require(v.game.story.evaTrust==expected&&v.game.log.size()==logs&&v.game.story.items.size()==items,"restart/replay no repeated effects");require(v.game.story.transcripts.size()==10&&v.game.story.answers.size()==1,"all conversation history persisted");require(v.game.food==food&&v.game.water==water&&v.game.mats==mats&&v.game.people.get(0).health==hp&&v.game.people.get(0).fatigue==fatigue&&v.game.people.get(0).morale==morale,"narrative leaves solo metrics untouched");require(online.equals(OnlineDemoSaveStore.encode(v.onlineWorld.state.gameplay)),"online unchanged");require(v.game.story.residentIds.get("Иван").equals(absentId),"actual stable resident IDs");
 }}
 static void conditions(){GameView v=fresh(420,840);for(StoryState.Phase phase:StoryState.Phase.values()){if(phase==StoryState.Phase.CHAIN_COMPLETE)continue;v.game.story.phase=phase;v.game.storyController.startVoices();require(v.game.story.voicesStage==0,"no premature quest");}v.game.story.phase=StoryState.Phase.CHAIN_COMPLETE;v.game.storyController.startVoices();require(v.game.story.voicesStage==0,"completed without decision not enough");v.game.story.decisionId=StoryConfig.SHARE.id;v.game.story.flags.add(StoryFlags.SHARE);v.game.story.flags.add(StoryFlags.SECRET);v.game.storyController.startVoices();require(v.game.story.voicesStage==0,"contradictory flags do not apply both branches");
 for(int height:new int[]{360,640,840,1200}){v=ready(false);View.width=420;View.height=height;draw(v);v.storyPanel.showMessage("voices.intro");draw(v);StoryPanelLayout l=new StoryPanelLayout(v.H/v.scale,false);require(l.closeTop+56<=v.H/v.scale-80&&l.contentBottom>l.contentTop,"buttons above navigation");}
 v=ready(false);finish(v,"voices.intro",0);finish(v,"voices.residents",0);finish(v,"voices.eva",1);require(v.game.story.evaTrust==48,"guarded answer distinct trust");
 }
 static void migration(){for(MemoryPreferences old:legacy()){Context.preferences=old;GameView v=loaded();require(v.game.story.phase==StoryState.Phase.CHAIN_COMPLETE&&v.game.story.voicesStage==1,"actual STORY1.0 save activates immediately on load");String decision=v.game.story.decisionId;int trust=v.game.story.evaTrust;require(trust==50&&v.game.story.messages.contains(StoryConfig.SIGNAL.id),"legacy messages preserved neutral new trust");v.game.storyController.advanceMinute();require(v.game.story.voicesStage==1&&v.game.story.decisionId.equals(decision),"old decision preserved new chain starts");v=restart(v);require(v.game.story.voicesStage==1&&v.game.story.decisionId.equals(decision),"migrated state durable");}}
 static void actualButtons(){GameView v=ready(true);finish(v,"voices.intro",0);finish(v,"voices.residents",0);v.storyPanel.showMessage("voices.eva");draw(v);StoryPanelLayout l=new StoryPanelLayout(v.H/v.scale,false);int row=0;while(!v.storyPanel.rows.get(row).action)row++;v.storyPanel.scroll=v.storyRenderer.rowTop(row);draw(v);tap(v,180,l.contentTop+v.storyRenderer.rowTop(row)-v.storyPanel.scroll+20);require(v.storyPanel.response==0&&v.game.story.evaTrust==50,"actual touch selects without applying effect");tap(v,180,l.primaryTop+28);require(v.game.story.evaTrust==55&&v.game.story.dialogueSteps.get("voices.eva")==1,"fixed confirmation applies selected response");require(v.game.storyController.dialogueText(StoryDialogue.EVA,1).contains("Вы правы"),"next line conditioned on evidence answer");}
 public static void main(String[] args){migration();actualButtons();conditions();branches();System.out.println("PASS: "+checks+" STORY 1.1 assertions: gates, both exclusive branches, real resident IDs/availability, conditional responses, close/resume/restart, exactly-once trust/history/items, Canvas UI, unchanged solo and online data.");}
}
'''
LEGACY=base.PREFIX+r'''
 static void export(){System.out.println("SAVE");for(Map.Entry<String,Object> e:Context.preferences.values.entrySet()){Object v=e.getValue();System.out.println("VALUE:"+e.getKey()+":"+v.getClass().getSimpleName()+":"+Base64.getEncoder().encodeToString(v.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}}
 public static void main(String[] args){for(StoryChoice choice:new StoryChoice[]{StoryConfig.SHARE,StoryConfig.SECRET}){GameView v=fresh(420,840);v.game.story.phase=StoryState.Phase.DECISION;v.game.story.messages.add(StoryConfig.SIGNAL.id);v.game.storyController.choose(choice.id,true);v.game.save();export();}}
}
'''
def main():
 with tempfile.TemporaryDirectory(prefix='story11-') as d:
  fixtures.PROBE=LEGACY
  old=fixtures.run_version(Path(d),'story10',base.sources('e271dceba59e7647e486051a05a1e3645e5f085d'))
  blocks=[]
  for line in old.splitlines():
   if line=='SAVE':blocks.append([])
   elif line.startswith('VALUE:'):
    _,key,kind,value=line.split(':',3);value=base64.b64decode(value).decode()
    method={'Integer':'putInt','Boolean':'putBoolean','String':'putString'}[kind]
    literal=json.dumps(value,ensure_ascii=False) if kind=='String' else value
    blocks[-1].append(f'e.{method}({json.dumps(key)}, {literal});')
  assert len(blocks)==2
  method=' static List<MemoryPreferences> legacy(){List<MemoryPreferences> result=new ArrayList<>();MemoryPreferences p;SharedPreferences.Editor e;'
  for block in blocks:method+='p=new MemoryPreferences();e=p.edit();'+''.join(block)+'e.commit();result.add(p);'
  method+='return result;}'
  fixtures.PROBE=PROBE.replace(' public static void main(String[] args){migration()',method+' public static void main(String[] args){migration()')
  print(fixtures.run_version(Path(d),'dialogues',base.sources()).strip())
if __name__=='__main__':main()
