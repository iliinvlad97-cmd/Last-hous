#!/usr/bin/env python3
"""Monotonic-clock city marker tests using actual Java, Canvas and save_v02 code.

Virtual 15–120 FPS schedules test time invariance; this does not measure device FPS.
"""
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import expedition_regression as expedition
import story_regression as base
import refactor_regression as fixtures

PROBE = expedition.PROBE[:expedition.PROBE.index(' static void fail(')] + r'''
 static long nanos;
 static float[] marker(Canvas c){
  for(String command:c.commands)if(command.startsWith("drawCircle[")){
   String[] args=command.substring(11).split(", ");return new float[]{Float.parseFloat(args[0]),Float.parseFloat(args[1])};
  }throw new AssertionError("marker missing");
 }
 static GameView animated(boolean fresh){
  View.width=420;View.height=840;if(fresh)Context.preferences=new MemoryPreferences();nanos=0;
  GameView v=new GameView(new Context(),new MockOnlineWorldRepository(),()->nanos);
  v.game.rnd=new Random(){public double nextDouble(){return .99;}public int nextInt(int n){return n-1;}};
  v.game.event=false;v.game.paused=false;v.game.screen=GameView.CITY_MAP;draw(v);return v;
 }
 static void frame(GameView v,long time){nanos=time;draw(v);}
 static void geometry(){
  float[][] p={{0,0},{.5f,0},{.5f,0},{.5f,.001f},{.5f,1}};
  CityRouteGeometry g=new CityRouteGeometry(p,388);float[] out=new float[2];
  require(g.points==p&&Math.abs(g.length-582)<.0001,"existing vertices and true cumulative lengths");
  g.point(0,out);close(out[0],0,"start X");close(out[1],0,"start Y");
  g.point(.5f,out);close(out[0],.5f,"midpoint follows total length X");close(out[1],.25f,"midpoint follows total length Y");
  for(int i=1;i<p.length;i++){g.point((float)(g.distances[i]/g.length),out);close(out[0],p[i][0],"segment junction X");close(out[1],p[i][1],"segment junction Y");}
  float shortProgress=(float)((g.distances[2]+g.distances[3])/2/g.length);g.point(shortProgress,out);require(Math.abs(out[1]-.0005f)<.000001f,"tiny segment not skipped");
  float[] previous=new float[2];g.point(0,previous);
  for(int i=1;i<=10000;i++){
   g.point(i/10000f,out);require(out[0]>=0&&out[0]<=.5f&&out[1]>=0&&out[1]<=1,"no off-route coordinates");
   require(out[1]==0||out[0]==.5f,"no diagonal corner cutting");
   require(CityRoutePlanner.length(previous,out,388)<=g.length/10000+.001,"continuous distance through short segments/corners");previous[0]=out[0];previous[1]=out[1];
  }
  for(float progress:new float[]{1,2,100}){g.point(progress,out);close(out[0],.5f,"clamped end X");close(out[1],1,"clamped end Y");}
  g.point(-1,out);close(out[0],0,"clamped negative progress");
  CityRouteGeometry singleton=new CityRouteGeometry(new float[][]{{.3f,.6f}},388);singleton.point(.7f,out);close(out[0],.3f,"singleton");
  require(!new CityRouteGeometry(new float[0][],388).point(.4f,out),"empty route never fabricates a marker");
 }
 static void schedules(){
  for(int speed:new int[]{1,2,4})for(int fps:new int[]{15,24,30,60,120}){
   ExpeditionMarkerAnimation a=new ExpeditionMarkerAnimation();float previous=0;
   for(int i=0;i<=fps*50;i++){
    long time=i*1_000_000_000L/fps;float logical=Math.min(1,(time/1_000_000_000L)*speed/45f);
    float position=a.frame(logical,false,false,time);
    double seconds=time/1e9;float prior=Math.min(1,Math.max(0,(float)(Math.floor(seconds)-1)*speed/45));
    float expected=prior+(logical-prior)*(float)(seconds-Math.floor(seconds));
    require(Math.abs(position-expected)<.00001,"finite linear interpolation independent of FPS/speed "+fps+"/"+speed);
    require(position>=previous&&position<=logical,"outbound monotonic and never predicts beyond game time");
    require(logical-position<=speed/45f+.00001,"outbound lag bounded by one logical tick");
    require(position-previous<=speed/(45f*fps)+.00001,"bounded per-frame displacement even at x4");
    close(a.frame(logical,false,false,time),position,"duplicate draw at same monotonic time cannot move marker");previous=position;
   }
   close(previous,1,"exact finite endpoint");require(!a.moving(),"no endless endpoint invalidations");
   a.clear();previous=1;
   for(int i=0;i<=fps*50;i++){
    long time=i*1_000_000_000L/fps;float logical=Math.max(0,1-(time/1_000_000_000L)*speed/45f);
    float position=a.frame(logical,true,false,time);require(position<=previous&&position>=logical,"return monotonic/no overtaking");
    require(position-logical<=speed/45f+.00001,"return lag bounded by one logical tick");
    double seconds=time/1e9;float prior=Math.max(0,1-Math.max(0,(float)(Math.floor(seconds)-1)*speed/45));float expected=prior+(logical-prior)*(float)(seconds-Math.floor(seconds));require(Math.abs(position-expected)<.00001,"return frame-time invariance");previous=position;
   }
   close(previous,0,"exact return endpoint");require(!a.moving(),"return converges in finite time");
  }
 }
 static void speedAndPause(){
  ExpeditionMarkerAnimation a=new ExpeditionMarkerAnimation();a.frame(0,false,false,0);a.frame(.1f,false,false,1_000_000_000L);
  close(a.frame(.1f,false,false,1_500_000_000L),.05f,"half-second intermediate point");
  close(a.frame(.1f,false,true,2_000_000_000L),.05f,"pause freezes last rendered position");
  close(a.frame(.1f,false,true,90_000_000_000L),.05f,"long pause never accumulates visual time");
  close(a.frame(.1f,false,false,90_000_000_000L),.05f,"resume doesn't jump");
  close(a.frame(.1f,false,false,90_500_000_000L),.075f,"remaining distance resumes smoothly");
  close(a.frame(.1f,false,false,91_000_000_000L),.1f,"finite resume endpoint");
  a.clear();close(a.frame(.63f,false,false,300_000_000_000L),.63f,"restart/tab restoration anchors current authoritative point");
  a.frame(1,false,false,301_000_000_000L);close(a.frame(1,false,false,302_000_000_000L),1,"arrival phase finishes smoothly");
  close(a.frame(1,true,false,302_000_000_000L),1,"return starts at destination");
  a.frame(.9f,true,false,303_000_000_000L);close(a.frame(.9f,true,false,303_500_000_000L),.95f,"return direction");
  GameView v=animated(true);require(v.game.expeditionController.start("shop",ids(v,0)).isEmpty(),"actual normal launch");draw(v);Expedition e=v.game.expeditionController.active();
  int minute=v.game.gameMinute,elapsed=0;long time=0;
  for(int speed:new int[]{1,4,1,2,4,1}){
   v.game.speed=speed;time+=1_000_000_000L;nanos=time;v.tick.run();draw(v);elapsed+=speed;
   require(e.elapsedMinutes()==elapsed&&v.game.gameMinute==minute+elapsed,"logical 1->4->1 and 2 unchanged");
   float start=v.cityMap.displayProgress;frame(v,time+500_000_000L);require(v.cityMap.displayProgress>start&&v.cityMap.displayProgress<e.routeProgress(),"smooth half-second after each speed switch");
  }
  v.game.paused=true;draw(v);float stopped=v.cityMap.displayProgress;String snapshot=save(v);int requests=v.animationRequests;
  for(int i=1;i<=10;i++){nanos=time+i*1_000_000_000L;v.tick.run();draw(v);close(v.cityMap.displayProgress,stopped,"actual paused marker");}
  require(snapshot.equals(save(v))&&requests==v.animationRequests,"pause neither changes save/resources nor schedules animation");
  v.game.paused=false;draw(v);close(v.cityMap.displayProgress,stopped,"actual resume doesn't catch up wall time");
 }
 static void lifecycleAndCache(){
  GameView v=animated(true);v.game.expeditionController.start("shop",ids(v,0));draw(v);Expedition e=v.game.expeditionController.active();
  CityMapLayout l=new CityMapLayout(v.H/v.scale);MapLocation target=v.game.expeditionController.location(e.locationId);
  CityRouteGeometry route=v.cityMap.routes.geometry(target,v.cityMap.visibleLocations(),l);double[] lengths=route.distances;
  nanos=1_000_000_000L;v.tick.run();draw(v);frame(v,1_500_000_000L);
  require(route==v.cityMap.routes.geometry(target,v.cityMap.visibleLocations(),l)&&lengths==route.distances,"frames reuse polyline and cumulative length cache");
  float[] point=new float[2];route.point(v.cityMap.displayProgress,point);Canvas c=new Canvas();v.expeditionRenderer.drawRoute(c,l);float[] center=marker(c);
  close(center[0],l.x(point[0]),"actual Canvas samples cached route X");close(center[1],l.y(point[1]),"actual Canvas samples cached route Y");
  tap(v,l.x(point[0]),l.y(point[1]));require(v.cityMap.expeditionPanel,"touch follows exactly drawn marker");v.cityMap.expeditionPanel=false;
  for(int screen:new int[]{GameView.HOME,2,GameView.ONLINE_WORLD}){
   v.game.screen=screen;draw(v);int requests=v.animationRequests;
   for(int i=0;i<3;i++)v.game.advanceMinute();
   // Ignore the online world's own redraws; the city renderer must not request hidden frames.
   v.expeditionRenderer.drawRoute(new Canvas(),l);require(v.animationRequests==requests,"hidden city doesn't schedule its own animation");
   v.game.screen=GameView.CITY_MAP;v.expeditionRenderer.leave();draw(v);close(v.cityMap.displayProgress,e.routeProgress(),"tab return anchors latest route progress");
  }
  v.onWindowFocusChanged(false);v.game.advanceMinute();draw(v);int requests=v.animationRequests;nanos+=50_000_000_000L;draw(v);require(v.animationRequests==requests,"unfocused city doesn't animate");
  v.onWindowFocusChanged(true);draw(v);close(v.cityMap.displayProgress,e.routeProgress(),"focus restore discards old visual tween");
  v.onWindowVisibilityChanged(View.GONE);for(int i=0;i<2;i++)v.game.advanceMinute();v.onWindowVisibilityChanged(View.VISIBLE);draw(v);close(v.cityMap.displayProgress,e.routeProgress(),"background restore at current logical position");
  String before=save(v);v=animated(false);e=v.game.expeditionController.active();close(v.cityMap.displayProgress,e.routeProgress(),"process restart anchors saved position");require(before.equals(save(v)),"visual restart changes no persisted game fields");
  while(e.state()==Expedition.State.TRAVELING_TO_TARGET)v.game.advanceMinute();while(e.state()==Expedition.State.EXPLORING)v.game.advanceMinute();
  require(v.game.expeditionController.returnHome(e.id).isEmpty(),"return launch");draw(v);for(int i=0;i<5;i++)v.game.advanceMinute();save(v);v=animated(false);e=v.game.expeditionController.active();close(v.cityMap.displayProgress,e.routeProgress(),"return restart uses correct reverse position");
  while(e.active())v.game.advanceMinute();int food=v.game.food;draw(v);Canvas completed=new Canvas();v.expeditionRenderer.drawRoute(completed,l);require(completed.commands.isEmpty(),"completed route and crew immediately absent");
  v.game.expeditionController.complete(e);require(v.game.food==food,"visual completion cannot duplicate rewards");save(v);v=animated(false);completed=new Canvas();v.expeditionRenderer.drawRoute(completed,l);require(completed.commands.isEmpty(),"restart never restores historical marker");
  for(Resident r:v.game.people){r.health=100;r.fatigue=0;r.hunger=r.thirst=10;r.autoRecovery=false;r.job="Материалы";}
  require(v.game.expeditionController.start("pharmacy",ids(v,0)).isEmpty(),"new route after completed trip");draw(v);close(v.cityMap.displayProgress,0,"new expedition never inherits old animation");
 }
 public static void main(String[] args){geometry();schedules();speedAndPause();lifecycleAndCache();System.out.println("PASS: "+checks+" city animation assertions; cumulative arc length/corners/tiny segments, finite 15/24/30/60/120 FPS interpolation, 1/2/4 speed changes, pause/resume, exact Canvas/touch/cache, hidden/focus/background/process restart, return/completed/new routes and unchanged saves/rewards.");}
}
'''

def main():
    fixtures.PROBE = PROBE
    with tempfile.TemporaryDirectory(prefix="city-animation-") as directory:
        print(fixtures.run_version(Path(directory), "animation", base.sources()).strip())

if __name__ == "__main__":
    main()
