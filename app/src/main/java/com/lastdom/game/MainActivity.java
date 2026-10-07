package com.lastdom.game;

import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.os.Handler;
import java.util.*;

public class MainActivity extends Activity {
    GameView view;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);view=new GameView(this);setContentView(view);}
    @Override protected void onPause(){super.onPause();view.save();}

    static class Survivor {String name,role,job="Отдых";int skill,health=100,hunger=10,fatigue=10,morale=75;boolean alive=true;Survivor(String n,String r,int s){name=n;role=r;skill=s;}}
    static class Location {String name,type;int distance,risk,stock;boolean discovered;Location(String n,String t,int d,int r,int st,boolean seen){name=n;type=t;distance=d;risk=r;stock=st;discovered=seen;}}

    class GameView extends View {
        Paint p=new Paint(3),stroke=new Paint(3); Bitmap shelterBitmap,fullSceneBitmap;Random rnd=new Random();Handler timer=new Handler();SharedPreferences sp;
        ArrayList<Survivor> people=new ArrayList<>();ArrayList<String> log=new ArrayList<>();ArrayList<Location> locations=new ArrayList<>();
        String[] jobs={"Отдых","Еда","Вода","Материалы","Ремонт","Охрана","Лечение"};
        String[] rooms={"Генераторная","Кухня","Медпункт","Мастерская","Баррикады","Спальня"};
        String[] roomJobs={"Ремонт","Еда","Лечение","Материалы","Охрана","Отдых"};
        int[] roomLevels={1,1,1,1,1,1},roomCondition={100,100,100,100,100,100};
        int day=1,gameMinute=480,speed=1,food=28,water=34,power=24,mats=18,threat=12,shelter=100,selected=-1,selectedRoom=-1,screen=0,overlay=0,buildingRoom=-1,buildRemaining=0,incidentRoom=-1;
        boolean paused=false,event=false,gameOver=false,jobMenu=false;String eventTitle="",eventText="";String[] eventChoices=new String[2];
        int expeditionPerson=-1, expeditionLocation=-1, expeditionRemaining=0, selectedLocation=-1; boolean expeditionEvent=false;
        int W,H;float scale=1f;
        float[] residentX=new float[32],residentY=new float[32]; int[] residentVisualRoom=new int[32]; boolean residentVisualReady=false;
        int bg=Color.rgb(14,16,20),panel=Color.rgb(28,31,37),panel2=Color.rgb(40,44,51),text=Color.rgb(238,234,224),muted=Color.rgb(166,166,160),accent=Color.rgb(213,143,70),danger=Color.rgb(190,72,65),good=Color.rgb(101,160,104),blue=Color.rgb(88,132,166);
        GameView(Context c){super(c); shelterBitmap=BitmapFactory.decodeResource(getResources(), R.drawable.shelter_clean); fullSceneBitmap=BitmapFactory.decodeResource(getResources(), R.drawable.shelter_full_scene); sp=getSharedPreferences("save_v02",0);initLocations();load();timer.postDelayed(tick,1000);}
        void initLocations(){locations.clear();locations.add(new Location("Продуктовый","еда / вода",2,12,100,true));locations.add(new Location("Аптека","медицина",3,18,100,true));locations.add(new Location("Гаражи","материалы",4,24,100,true));locations.add(new Location("Соседний дом","разное",5,30,100,true));locations.add(new Location("Склад","крупная добыча",7,42,100,false));locations.add(new Location("Больница","редкие припасы",9,55,100,false));}
        Runnable tick=new Runnable(){public void run(){if(!paused&&!gameOver&&!event){for(int i=0;i<speed;i++)advanceMinute();invalidate();}timer.postDelayed(this,1000);}};
        Survivor make(String n,String r,int s){return new Survivor(n,r,s);}void defaults(){people.clear();people.add(make("Иван","Инженер",4));people.add(make("Мария","Врач",4));people.add(make("Сергей","Охрана",4));people.add(make("Анна","Сборщик",3));people.add(make("Павел","Механик",4));}
        void reset(){residentVisualReady=false;day=1;gameMinute=480;speed=1;paused=false;food=28;water=34;power=24;mats=18;threat=12;shelter=100;selected=-1;selectedRoom=-1;screen=0;overlay=0;buildingRoom=-1;buildRemaining=0;roomLevels=new int[]{1,1,1,1,1,1};roomCondition=new int[]{100,100,100,100,100,100};event=false;gameOver=false;jobMenu=false;expeditionPerson=-1;expeditionLocation=-1;expeditionRemaining=0;selectedLocation=-1;initLocations();defaults();log.clear();addLog("Убежище готово. Дом оживает.");save();invalidate();}
        void advanceMinute(){gameMinute++;if(expeditionPerson>=0){expeditionRemaining--;if(expeditionRemaining<=0)finishExpedition();}if(buildingRoom>=0){buildRemaining--;if(buildRemaining<=0){roomLevels[buildingRoom]++;roomCondition[buildingRoom]=100;addLog(rooms[buildingRoom]+" улучшена до ур. "+roomLevels[buildingRoom]+".");buildingRoom=-1;}}if(gameMinute>=1440){gameMinute=0;day++;dailyCycle();}if(gameMinute%60==0)save();}
        String clock(){return String.format(Locale.getDefault(),"%02d:%02d",gameMinute/60,gameMinute%60);}String phase(){int h=gameMinute/60;return h>=6&&h<12?"УТРО":h>=12&&h<18?"ДЕНЬ":h>=18&&h<22?"ВЕЧЕР":"НОЧЬ";}
        void dailyCycle(){processJobs();int a=aliveCount();food=Math.max(0,food-a);water=Math.max(0,water-a*2);power=Math.max(0,power-Math.max(1,1+roomLevels[0]/2));threat=Math.min(100,threat+2+rnd.nextInt(4));for(int i=0;i<6;i++)roomCondition[i]=Math.max(15,roomCondition[i]-(1+rnd.nextInt(3)));for(Survivor s:people)if(s.alive){s.hunger=Math.min(100,s.hunger+10);if(food==0||water==0)s.health=Math.max(0,s.health-6);if(s.health<=0){s.alive=false;addLog(s.name+" погиб.");}}if(aliveCount()==0||shelter<=0)gameOver=true;else if(rnd.nextInt(100)<45)triggerEvent();else addLog("Новый день начался спокойно.");save();}
        int aliveCount(){int n=0;for(Survivor s:people)if(s.alive)n++;return n;}void addLog(String s){log.add(0,"День "+day+" • "+clock()+": "+s);while(log.size()>35)log.remove(log.size()-1);}
        void save(){SharedPreferences.Editor e=sp.edit();e.putInt("day",day).putInt("gameMinute",gameMinute).putInt("speed",speed).putBoolean("paused",paused).putInt("buildingRoom",buildingRoom).putInt("buildRemaining",buildRemaining).putInt("food",food).putInt("water",water).putInt("power",power).putInt("mats",mats).putInt("threat",threat).putInt("shelter",shelter).putInt("count",people.size());for(int i=0;i<people.size();i++){Survivor s=people.get(i);String k="p"+i+"_";e.putString(k+"name",s.name).putString(k+"role",s.role).putString(k+"job",s.job).putInt(k+"skill",s.skill).putInt(k+"health",s.health).putInt(k+"hunger",s.hunger).putInt(k+"fatigue",s.fatigue).putInt(k+"morale",s.morale).putBoolean(k+"alive",s.alive);}for(int i=0;i<6;i++){e.putInt("room"+i,roomLevels[i]);e.putInt("roomCond"+i,roomCondition[i]);}e.putInt("expPerson",expeditionPerson).putInt("expLoc",expeditionLocation).putInt("expRemain",expeditionRemaining);for(int i=0;i<locations.size();i++){e.putInt("locStock"+i,locations.get(i).stock);e.putBoolean("locSeen"+i,locations.get(i).discovered);}e.putString("log",android.text.TextUtils.join("\n§\n",log));e.apply();}
        void load(){residentVisualReady=false;if(!sp.contains("day")){reset();return;}day=sp.getInt("day",1);gameMinute=sp.getInt("gameMinute",480);speed=sp.getInt("speed",1);paused=sp.getBoolean("paused",false);buildingRoom=sp.getInt("buildingRoom",-1);buildRemaining=sp.getInt("buildRemaining",0);food=sp.getInt("food",28);water=sp.getInt("water",34);power=sp.getInt("power",24);mats=sp.getInt("mats",18);threat=sp.getInt("threat",12);shelter=sp.getInt("shelter",100);expeditionPerson=sp.getInt("expPerson",-1);expeditionLocation=sp.getInt("expLoc",-1);expeditionRemaining=sp.getInt("expRemain",0);for(int i=0;i<locations.size();i++){locations.get(i).stock=sp.getInt("locStock"+i,100);locations.get(i).discovered=sp.getBoolean("locSeen"+i,locations.get(i).discovered);}for(int i=0;i<6;i++){roomLevels[i]=sp.getInt("room"+i,1);roomCondition[i]=sp.getInt("roomCond"+i,100);}int n=sp.getInt("count",5);people.clear();for(int i=0;i<n;i++){String k="p"+i+"_";Survivor s=make(sp.getString(k+"name","Выживший"),sp.getString(k+"role","Житель"),sp.getInt(k+"skill",2));s.job=sp.getString(k+"job","Отдых");s.health=sp.getInt(k+"health",100);s.hunger=sp.getInt(k+"hunger",10);s.fatigue=sp.getInt(k+"fatigue",10);s.morale=sp.getInt(k+"morale",75);s.alive=sp.getBoolean(k+"alive",true);people.add(s);}String l=sp.getString("log","");if(!l.isEmpty())log.addAll(Arrays.asList(l.split("\\n§\\n")));}
        float sy(float y){return y*scale;}void txt(Canvas c,String s,float x,float y,float size,int col){p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextSize(sy(size));p.setColor(col);c.drawText(s,sy(x),sy(y),p);}void bold(Canvas c,String s,float x,float y,float size,int col){p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setTextSize(sy(size));p.setColor(col);c.drawText(s,sy(x),sy(y),p);}void box(Canvas c,float l,float t,float r,float b,int col,float rad){p.setStyle(Paint.Style.FILL);p.setColor(col);c.drawRoundRect(sy(l),sy(t),sy(r),sy(b),sy(rad),sy(rad),p);}void bar(Canvas c,float l,float t,float r,float h,int v,int col){box(c,l,t,r,t+h,Color.rgb(52,55,61),4);box(c,l,t,l+(r-l)*Math.max(0,Math.min(100,v))/100f,t+h,col,4);}
        @Override protected void onDraw(Canvas c){W=getWidth();H=getHeight();scale=W/420f;c.drawColor(bg);if(screen==0)drawMain(c);else if(screen==1)drawSurvivor(c);else if(screen==2)drawJournal(c);else if(screen==3)drawRooms(c);else if(screen==4)drawRoomDetail(c);else drawMap(c);if(event)drawEvent(c);if(jobMenu)drawJobMenu(c);if(gameOver)drawGameOver(c);}
        void drawHeader(Canvas c,String sub){bold(c,"ПОСЛЕДНИЙ ДОМ",20,30,20,text);txt(c,sub,20,49,11,muted);box(c,250,12,400,52,panel2,9);bold(c,"Д"+day+"  "+clock(),264,31,12,text);txt(c,phase(),264,46,9,phase().equals("НОЧЬ")?blue:accent);}
        void drawMain(Canvas c){
            drawHeader(c,"убежище • v0.9.5.4 HITBOX + FLOOR FIX");
            drawResources(c);

            // v0.9.5.1: one continuous world image. No old shelter layer and no separate city strip.
            float hh=H/scale;
            float sceneTop=116f;
            float sceneBottom=Math.max(610f,hh-72f);
            if(fullSceneBitmap!=null){
                Rect src=new Rect(0,0,fullSceneBitmap.getWidth(),fullSceneBitmap.getHeight());
                RectF dst=new RectF(sy(8),sy(sceneTop),sy(412),sy(sceneBottom));
                c.drawBitmap(fullSceneBitmap,src,dst,p);
            }
            // The exit is part of the world. Only an invisible tap target is added here.
            drawFullSceneState(c,sceneTop,sceneBottom);
            drawFullSceneResidents(c,sceneTop,sceneBottom);
            drawNav(c);
            if(overlay==1)drawResidentOverlay(c); else if(overlay==2)drawRoomOverlay(c); else if(overlay==3)drawResidentsOverlay(c);
        }

        void drawFullSceneState(Canvas c,float top,float bottom){
            float h=bottom-top;
            int hour=gameMinute/60;
            if(hour>=22||hour<6){p.setColor(Color.argb(52,5,12,28));c.drawRect(sy(8),sy(top),sy(412),sy(bottom),p);}
            if(power<=0){p.setColor(Color.argb(90,0,3,10));c.drawRect(sy(8),sy(top+h*.23f),sy(412),sy(bottom),p);bold(c,"НЕТ ЭЛЕКТРИЧЕСТВА",142,top+h*.25f,8,danger);}
            if(incidentRoom>=0){float[] q=fullSceneRoomRect(incidentRoom,top,bottom);float pulse=(float)(.5+.5*Math.sin(System.currentTimeMillis()/220.0));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sy(2));p.setColor(Color.argb((int)(120+100*pulse),235,85,60));c.drawRoundRect(sy(q[0]),sy(q[1]),sy(q[2]),sy(q[3]),sy(7),sy(7),p);p.setStyle(Paint.Style.FILL);}
        }

        // v0.9.5.2: hitboxes are mapped directly to the artwork, not to the old 2x3 grid.
        // Values below are normalized positions inside shelter_full_scene.png.
        float[] fullSceneRoomRect(int ri,float top,float bottom){
            float h=bottom-top;
            switch(ri){
                case 0:return sceneRect(.055f,.330f,.515f,.480f,top,h); // generator: full visible upper-left room
                case 1:return sceneRect(.055f,.485f,.515f,.640f,top,h); // kitchen: full middle-left room
                case 2:return sceneRect(.515f,.485f,.930f,.640f,top,h); // medpoint: full middle-right room
                case 5:return sceneRect(.055f,.645f,.515f,.805f,top,h); // bedroom: full lower-left room
                case 3:return sceneRect(.515f,.645f,.930f,.805f,top,h); // workshop/storage: full lower-right room
                case 4:return sceneRect(.055f,.180f,.945f,.315f,top,h); // surface barricades
                default:return new float[]{-100,-100,-90,-90};
            }
        }
        float[] sceneRect(float nx1,float ny1,float nx2,float ny2,float top,float h){
            float left=8f, width=404f;
            return new float[]{left+width*nx1,top+h*ny1,left+width*nx2,top+h*ny2};
        }

        int fullSceneRoomAt(float x,float y,float top,float bottom){
            // v0.9.5.4: the artwork has an extra visible upper-right work room.
            // Treat it as part of the workshop so the whole visible room is interactive.
            float h=bottom-top;
            float[] upperRight=sceneRect(.515f,.330f,.930f,.480f,top,h);
            if(x>=upperRight[0]&&x<=upperRight[2]&&y>=upperRight[1]&&y<=upperRight[3])return 3;
            // Explicit priority prevents a neighbouring room from stealing edge taps.
            int[] order={0,1,2,5,3,4};
            for(int ri:order){float[] q=fullSceneRoomRect(ri,top,bottom);if(x>=q[0]&&x<=q[2]&&y>=q[1]&&y<=q[3])return ri;}
            return -1;
        }

        float[] fullSceneResidentPos(int ri,int slot,float top,float bottom){
            float h=bottom-top,left=8f,width=404f;
            // Dedicated visual slots per room. They intentionally do not use hitbox centres.
            float[][][] pts={
                {{.18f,.475f},{.29f,.475f},{.40f,.475f}}, // generator - feet sit on upper-floor line
                {{.18f,.635f},{.29f,.635f},{.40f,.635f}}, // kitchen
                {{.62f,.635f},{.73f,.635f},{.84f,.635f}}, // medpoint
                {{.62f,.802f},{.73f,.802f},{.84f,.802f}}, // workshop/storage lower-right
                {{.28f,.310f},{.50f,.310f},{.72f,.310f}}, // barricades on surface
                {{.17f,.802f},{.29f,.802f},{.41f,.802f}}  // bedroom lower-left
            };
            int k=Math.max(0,Math.min(2,slot));
            return new float[]{left+width*pts[ri][k][0],top+h*pts[ri][k][1]};
        }

        void drawFullSceneResidents(Canvas c,float top,float bottom){
            int[] slots={0,0,0,0,0,0};
            for(int i=0;i<people.size()&&i<32;i++){
                Survivor s=people.get(i);if(!s.alive||s.job.equals("Экспедиция"))continue;
                int ri=homeRoomFor(s);if(ri<0)continue;int slot=Math.min(2,slots[ri]++);float[] q=fullSceneResidentPos(ri,slot,top,bottom);
                residentX[i]=q[0];residentY[i]=q[1];residentVisualRoom[i]=ri;
                drawDynamicResident(c,s,q[0],q[1],i);
            }
            residentVisualReady=true;
        }


        int homeRoomFor(Survivor s){
            if(!s.alive || s.job.equals("Экспедиция")) return -1;
            if(s.job.equals("Ремонт")) return 0;
            if(s.job.equals("Еда") || s.job.equals("Вода")) return 1;
            if(s.job.equals("Лечение")) return 2;
            if(s.job.equals("Материалы")) return 3;
            if(s.job.equals("Охрана")) return 4;
            return 5;
        }
        float[] shelterResidentPos(int ri,int slot){
            float[][] base={{162,235},{327,235},{163,362},{327,362},{162,488},{327,488}};
            float dx=(slot%3-1)*18f; return new float[]{base[ri][0]+dx,base[ri][1]};
        }
        void drawDynamicRoomState(Canvas c){
            // Dynamic overlays make the shelter react to room state instead of behaving like one flat JPEG.
            float[][] r={{12,150,210,280},{210,150,408,280},{12,280,210,410},{210,280,408,410},{12,410,210,535},{210,410,408,535}};
            for(int i=0;i<6;i++){
                float[] q=r[i]; int cond=roomCondition[i];
                if(cond<70){p.setColor(Color.argb(Math.min(125,(70-cond)*3),90,18,12));c.drawRect(sy(q[0]),sy(q[1]),sy(q[2]),sy(q[3]),p);}
                if(buildingRoom==i){p.setColor(Color.argb(105,220,150,55));c.drawRect(sy(q[0]),sy(q[3]-10),sy(q[0]+(q[2]-q[0])*(1f-Math.min(1f,buildRemaining/(float)(120+roomLevels[i]*90)))),sy(q[3]),p);}
                // Small status plate; no duplicate room title/HUD.
                if(cond<85){box(c,q[2]-45,q[1]+7,q[2]-7,q[1]+23,Color.argb(190,20,22,25),6);bold(c,cond+"%",q[2]-38,q[1]+19,7,cond<45?danger:accent);}
            }
            if(power<=0){p.setColor(Color.argb(115,0,4,12));c.drawRect(sy(8),sy(118),sy(412),sy(568),p);txt(c,"НЕТ ЭЛЕКТРИЧЕСТВА",142,139,8,danger);}
        }
        void drawTimeOfDayLighting(Canvas c){
            int h=gameMinute/60;
            int overlay;
            if(h>=6&&h<10) overlay=Color.argb(34,245,181,103);       // warm morning
            else if(h>=10&&h<18) overlay=Color.argb(10,230,238,245); // daylight
            else if(h>=18&&h<22) overlay=Color.argb(48,221,116,53);  // sunset
            else overlay=Color.argb(112,8,18,38);                    // night
            p.setColor(overlay); c.drawRect(sy(8),sy(118),sy(412),sy(568),p);
            // At night, powered rooms retain a warm pool of light.
            if((h>=18||h<7)&&power>0){
                float[][] centers={{111,215},{309,215},{111,345},{309,345},{111,472},{309,472}};
                for(int i=0;i<6;i++){
                    float cx=centers[i][0],cy=centers[i][1];
                    RadialGradient g=new RadialGradient(sy(cx),sy(cy),sy(78),Color.argb(65,255,188,82),Color.TRANSPARENT,Shader.TileMode.CLAMP);
                    p.setShader(g);c.drawCircle(sy(cx),sy(cy),sy(78),p);p.setShader(null);
                }
            }
        }
        void drawRoomActivityEffects(Canvas c){
            // Activity is tied to actual assignments, so rooms visibly "work".
            int pulse=(gameMinute%4);
            for(int ri=0;ri<6;ri++){
                int workers=0; for(Survivor s:people) if(s.alive&&homeRoomFor(s)==ri&&!s.job.equals("Отдых")) workers++;
                if(workers==0) continue;
                float[][] centers={{111,215},{309,215},{111,345},{309,345},{111,472},{309,472}};
                float x=centers[ri][0],y=centers[ri][1];
                if(ri==0){ // generator vibration / electric pulse
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sy(2));p.setColor(Color.argb(180,235,167,62));
                    c.drawCircle(sy(x-42),sy(y+17),sy(9+pulse*2),p);p.setStyle(Paint.Style.FILL);
                    for(int k=0;k<3;k++){p.setColor(Color.argb(180,255,205,88));c.drawCircle(sy(x+30+k*5),sy(y-18-k*3+pulse),sy(1.6f),p);}
                }else if(ri==1){ // kitchen steam
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sy(2));p.setColor(Color.argb(115,235,235,225));
                    for(int k=0;k<3;k++){float xx=x-18+k*14;c.drawArc(sy(xx-5),sy(y-24-pulse*2),sy(xx+5),sy(y-6-pulse*2),180,180,false,p);}p.setStyle(Paint.Style.FILL);
                }else if(ri==2){ // med monitor
                    p.setColor(Color.argb(210,86,190,113));c.drawCircle(sy(x+58),sy(y-27),sy(3+pulse*.5f),p);
                    p.setStrokeWidth(sy(1.5f));c.drawLine(sy(x+38),sy(y-12),sy(x+45),sy(y-12),p);c.drawLine(sy(x+45),sy(y-12),sy(x+49),sy(y-19),p);c.drawLine(sy(x+49),sy(y-19),sy(x+54),sy(y-7),p);c.drawLine(sy(x+54),sy(y-7),sy(x+61),sy(y-12),p);
                }else if(ri==3){ // workshop sparks
                    for(int k=0;k<5;k++){float a=(k*71+pulse*29)*0.01745f;float rr=8+k*2;p.setColor(Color.argb(220,255,165,55));c.drawCircle(sy(x+22+(float)Math.cos(a)*rr),sy(y+8+(float)Math.sin(a)*rr),sy(1.5f),p);}
                }else if(ri==4){ // guard alert sweep
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sy(2));p.setColor(Color.argb(150,210,93,64));c.drawArc(sy(x-50),sy(y-35),sy(x+50),sy(y+35),200+pulse*10,45,false,p);p.setStyle(Paint.Style.FILL);
                }
            }
            if(buildingRoom>=0){
                float[][] centers={{111,215},{309,215},{111,345},{309,345},{111,472},{309,472}};float x=centers[buildingRoom][0],y=centers[buildingRoom][1];
                for(int k=0;k<4;k++){p.setColor(Color.argb(210,255,177,64));c.drawCircle(sy(x-20+k*12),sy(y+25-(k%2)*8),sy(2),p);}
            }
        }
        void drawEmergencyEffects(Canvas c){
            float t=(System.currentTimeMillis()%4000L)/1000f;
            // Short circuit: smoke, sparks and emergency flicker live inside the generator room.
            if(event && eventTitle.equals("КОРОТКОЕ ЗАМЫКАНИЕ")){
                float flick=(float)((Math.sin(t*18)+1)*.5);
                p.setColor(Color.argb((int)(45+75*flick),210,48,28));c.drawRect(sy(12),sy(150),sy(210),sy(280),p);
                for(int i=0;i<5;i++){float rise=(t*22+i*19)%72;float x=83+i*9+(float)Math.sin(t*2+i)*6;float y=248-rise;p.setColor(Color.argb(Math.max(20,115-(int)rise),105,105,100));c.drawCircle(sy(x),sy(y),sy(6+i*.8f),p);}
                for(int i=0;i<5;i++){float a=t*5+i*1.25f;p.setColor(Color.argb(220,255,184,55));c.drawCircle(sy(128+(float)Math.cos(a)*18),sy(226+(float)Math.sin(a)*12),sy(1.8f),p);}
            }
            // Medical incident: pulsing monitor and a patient silhouette on the bed.
            if(event && eventTitle.equals("БОЛЕЗНЬ")){
                float pulse=(float)((Math.sin(t*7)+1)*.5);p.setColor(Color.argb(170,70,210,115));c.drawCircle(sy(180),sy(319),sy(3+2*pulse),p);
                p.setColor(Color.rgb(186,145,115));c.drawCircle(sy(80),sy(355),sy(5),p);p.setColor(Color.rgb(80,86,82));c.drawRoundRect(sy(85),sy(350),sy(119),sy(361),sy(4),sy(4),p);
            }
            // Threat at the entrance: moving red searchlight / silhouettes outside barricades.
            if(event && (eventTitle.equals("МАРОДЁРЫ")||eventTitle.equals("ЧУЖАК У ДВЕРИ"))){
                float sweep=(float)Math.sin(t*1.7f);p.setColor(Color.argb(55,220,55,40));Path q=new Path();q.moveTo(sy(176),sy(445));q.lineTo(sy(48+sweep*22),sy(410));q.lineTo(sy(92+sweep*22),sy(520));q.close();c.drawPath(q,p);
                p.setColor(Color.argb(190,22,22,22));for(int i=0;i<(eventTitle.equals("МАРОДЁРЫ")?3:1);i++){float xx=42+i*22;c.drawCircle(sy(xx),sy(464),sy(5),p);c.drawRect(sy(xx-4),sy(469),sy(xx+4),sy(486),p);}
            }
            // Construction now looks active rather than only showing a progress bar.
            if(buildingRoom>=0){float[][] cc={{111,215},{309,215},{111,345},{309,345},{111,472},{309,472}};float x=cc[buildingRoom][0],y=cc[buildingRoom][1];for(int i=0;i<4;i++){float phase=(t*25+i*17)%38;p.setColor(Color.argb(210,255,174,55));c.drawLine(sy(x-18+i*10),sy(y+12),sy(x-24+i*10-phase*.15f),sy(y+12-phase*.45f),p);}}
            postInvalidateDelayed(80);
        }
        void ensureResidentVisuals(){
            if(residentVisualReady)return; int[] slots={0,0,0,0,0,0};
            for(int i=0;i<Math.min(people.size(),32);i++){int ri=homeRoomFor(people.get(i));if(ri<0)ri=5;float[] q=shelterResidentPos(ri,Math.min(2,slots[ri]++));residentX[i]=q[0];residentY[i]=q[1];residentVisualRoom[i]=ri;}residentVisualReady=true;
        }
        String residentState(Survivor s,int i){
            if(!s.alive)return "Погиб"; if(s.job.equals("Экспедиция"))return "В городе"; int ri=homeRoomFor(s);
            if(i<32&&residentVisualReady&&(Math.abs(residentX[i]-shelterResidentPos(ri,0)[0])>35||residentVisualRoom[i]!=ri))return "Идёт: "+rooms[ri];
            if(s.health<45)return "Ранен • "+s.job; if(s.fatigue>82)return "Измотан • "+s.job; if(s.job.equals("Отдых"))return "Отдыхает";
            if(s.job.equals("Лечение"))return "Лечит"; if(s.job.equals("Охрана"))return "На посту"; if(s.job.equals("Ремонт"))return "Ремонтирует"; return "Работает: "+s.job;
        }
        void drawLivingResidentsOverShelter(Canvas c){
            ensureResidentVisuals(); int[] slots={0,0,0,0,0,0}; boolean moving=false;
            for(int i=0;i<people.size()&&i<32;i++){
                Survivor s=people.get(i); int ri=homeRoomFor(s); if(ri<0)continue; int slot=Math.min(2,slots[ri]++);float[] target=shelterResidentPos(ri,slot);
                float dx=target[0]-residentX[i],dy=target[1]-residentY[i],dist=(float)Math.sqrt(dx*dx+dy*dy);
                if(dist>2){float step=Math.min(3.8f,dist);residentX[i]+=dx/dist*step;residentY[i]+=dy/dist*step;moving=true;}else{residentX[i]=target[0];residentY[i]=target[1];residentVisualRoom[i]=ri;}
                float anim=(float)Math.sin(System.currentTimeMillis()/190.0+i*1.7);float yy=residentY[i]+(dist>2?Math.abs(anim)*1.8f:s.job.equals("Отдых")?anim*.6f:Math.abs(anim)*.8f);
                drawDynamicResident(c,s,residentX[i],yy,i);
                if(dist>2){p.setColor(Color.argb(180,20,22,25));c.drawRoundRect(sy(residentX[i]-17),sy(yy-34),sy(residentX[i]+17),sy(yy-25),sy(4),sy(4),p);}
            }
            if(moving)postInvalidateDelayed(45);
        }
        void drawDynamicResident(Canvas c,Survivor s,float x,float y,int index){
            // Stage 4: expressive resident sprites drawn in layers (shadow/body/head/gear/prop/status).
            long now=System.currentTimeMillis(); float phase=(now/150.0f)+index*1.37f;
            int ri=homeRoomFor(s); float[] target=ri>=0?shelterResidentPos(ri,0):new float[]{x,y};
            float dist=(float)Math.sqrt((target[0]-x)*(target[0]-x)+(target[1]-y)*(target[1]-y));
            boolean walking=dist>4; boolean resting=s.job.equals("Отдых"); boolean hurt=s.health<45;
            float walk=walking?(float)Math.sin(phase):0f, breathe=(float)Math.sin(phase*.35f);
            float lean=resting?0:(s.job.equals("Ремонт")||s.job.equals("Материалы")?2.2f:1.0f);
            int skin=Color.rgb(199,158,126);
            int cloth=index==0?Color.rgb(52,72,82):index==1?Color.rgb(188,194,187):index==2?Color.rgb(65,78,62):index==3?Color.rgb(108,73,55):Color.rgb(66,78,88);
            if(hurt)cloth=Color.rgb(92,72,70);
            // floor shadow
            p.setColor(Color.argb(105,0,0,0));c.drawOval(sy(x-12),sy(y+17),sy(x+12),sy(y+22),p);
            // legs animate while walking; resting pose is wider and lower
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(sy(3.2f));p.setColor(Color.rgb(42,45,47));
            float leg=walking?walk*5:0; float hipY=y+12+(resting?2:0);
            c.drawLine(sy(x-4),sy(hipY),sy(x-5-leg),sy(y+23),p); c.drawLine(sy(x+4),sy(hipY),sy(x+5+leg),sy(y+23),p);
            // boots
            p.setStrokeWidth(sy(3.8f));c.drawLine(sy(x-7-leg),sy(y+23),sy(x-3-leg),sy(y+23),p);c.drawLine(sy(x+3+leg),sy(y+23),sy(x+7+leg),sy(y+23),p);
            // torso + jacket seam
            p.setColor(cloth);c.drawRoundRect(sy(x-9+lean),sy(y-5+breathe*.3f),sy(x+9+lean),sy(y+14),sy(4),sy(4),p);
            p.setColor(Color.argb(90,255,255,255));p.setStrokeWidth(sy(.8f));c.drawLine(sy(x+lean),sy(y-3),sy(x+lean),sy(y+11),p);
            // head / hair
            p.setColor(skin);c.drawCircle(sy(x+lean*.45f),sy(y-11+breathe*.2f),sy(6.8f),p);
            p.setColor(index==1?Color.rgb(83,55,39):index==3?Color.rgb(55,38,30):Color.rgb(43,36,32));
            c.drawArc(sy(x-7+lean*.45f),sy(y-19),sy(x+7+lean*.45f),sy(y-5),180,185,true,p);
            // arms: job-specific pose
            p.setStrokeWidth(sy(3));p.setColor(cloth);
            if(walking){c.drawLine(sy(x-7+lean),sy(y),sy(x-12-walk*3),sy(y+9),p);c.drawLine(sy(x+7+lean),sy(y),sy(x+12+walk*3),sy(y+8),p);}
            else if(resting){c.drawLine(sy(x-6),sy(y),sy(x-9),sy(y+10),p);c.drawLine(sy(x+6),sy(y),sy(x+9),sy(y+10),p);}
            else {float work=(float)Math.sin(phase*1.5f)*2;c.drawLine(sy(x-7+lean),sy(y),sy(x-13+lean),sy(y+7+work),p);c.drawLine(sy(x+7+lean),sy(y),sy(x+13+lean),sy(y+6-work),p);}
            // role/job props make residents readable without labels.
            if(s.role.equals("Врач")||s.job.equals("Лечение")){p.setColor(Color.WHITE);p.setStrokeWidth(sy(1.8f));c.drawLine(sy(x+lean-3),sy(y+4),sy(x+lean+3),sy(y+4),p);c.drawLine(sy(x+lean),sy(y+1),sy(x+lean),sy(y+7),p);}
            if(s.job.equals("Ремонт")||s.role.equals("Механик")){p.setColor(Color.rgb(180,184,185));p.setStrokeWidth(sy(2));c.drawLine(sy(x+12+lean),sy(y+5),sy(x+17+lean),sy(y),p);c.drawCircle(sy(x+17+lean),sy(y),sy(2),p);}
            if(s.job.equals("Охрана")||s.role.equals("Охрана")){p.setColor(Color.rgb(38,42,40));c.drawRoundRect(sy(x+8),sy(y-1),sy(x+17),sy(y+3),sy(1.5f),sy(1.5f),p);}
            // fatigue / injury feedback
            if(s.fatigue>80){p.setColor(Color.argb(210,210,220,225));p.setTextSize(sy(6));p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("Z",sy(x+10),sy(y-22),p);}
            if(hurt){p.setColor(Color.rgb(225,225,215));c.drawRoundRect(sy(x-7),sy(y-13),sy(x+1),sy(y-10),sy(1),sy(1),p);p.setColor(danger);c.drawCircle(sy(x+10),sy(y-18),sy(3.2f),p);}
            else {p.setColor(resting?blue:good);c.drawCircle(sy(x+10),sy(y-18),sy(2.7f),p);}
            p.setStrokeCap(Paint.Cap.BUTT);
        }
        int shelterResidentAt(float x,float y){ensureResidentVisuals();for(int i=0;i<people.size()&&i<32;i++){Survivor s=people.get(i);if(!s.alive||s.job.equals("Экспедиция"))continue;if(x>=residentX[i]-17&&x<=residentX[i]+17&&y>=residentY[i]-25&&y<=residentY[i]+27)return i;}return -1;}
        void drawResidentDock(Canvas c,float top){
            float cardW=72,gap=6,left=14;
            for(int i=0;i<Math.min(5,people.size());i++){
                Survivor s=people.get(i); float x=left+i*(cardW+gap);
                box(c,x,top,x+cardW,top+62,panel,10);
                drawMiniPortrait(c,s,x+17,top+19,i);
                bold(c,s.name,x+7,top+42,8,text);
                txt(c,shortJob(s.job),x+7,top+55,7,s.job.equals("Отдых")?blue:good);
            }
        }
        void drawMiniPortrait(Canvas c,Survivor s,float x,float y,int i){
            p.setColor(Color.rgb(51,55,58));c.drawCircle(sy(x),sy(y),sy(12),p);
            p.setColor(Color.rgb(199,158,126));c.drawCircle(sy(x),sy(y-3),sy(5),p);
            int cloth=i==0?Color.rgb(55,70,78):i==1?Color.rgb(186,190,180):i==2?Color.rgb(65,73,62):i==3?Color.rgb(94,70,57):Color.rgb(65,74,82);
            p.setColor(cloth);c.drawRoundRect(sy(x-7),sy(y+2),sy(x+7),sy(y+10),sy(4),sy(4),p);
        }
        String shortJob(String j){return j.length()>9?j.substring(0,9):j;}

        int cleanRoomAt(float x,float y){
            if(x<12||x>408||y<150||y>535)return -1;
            int col=x<210?0:1;
            int row=y<280?0:(y<410?1:2);
            return row*2+col;
        }
        int compactResidentAt(float x,float y){
            if(y<578||y>640)return -1;
            float left=14,step=78; int i=(int)((x-left)/step);
            if(i<0||i>=Math.min(5,people.size()))return -1;
            float local=x-(left+i*step); return local<=72?i:-1;
        }
        void drawResources(Canvas c){String[] n={"Еда","Вода","Энергия","Материалы"};int[] v={food,water,power,mats};String[] ic={"F","W","E","M"};for(int i=0;i<4;i++){float x=14+i*101;box(c,x,66,x+94,110,Color.rgb(24,29,34),9);p.setColor(i==0?Color.rgb(199,157,101):i==1?Color.rgb(78,145,184):i==2?Color.rgb(225,159,59):Color.rgb(142,149,151));c.drawCircle(sy(x+16),sy(88),sy(10),p);bold(c,ic[i],x+12,92,8,Color.WHITE);txt(c,n[i],x+30,81,7,muted);bold(c,""+v[i],x+30,101,12,text);}}
        int sky(){int h=gameMinute/60;if(h>=6&&h<12)return Color.rgb(78,75,70);if(h<18)return Color.rgb(72,82,88);if(h<22)return Color.rgb(66,47,51);return Color.rgb(18,23,34);}
        void drawAtmosphere(Canvas c,float top,float bottom){int h=gameMinute/60; if(h>=22||h<6){p.setColor(Color.rgb(214,218,207));c.drawCircle(sy(345),sy(top+32),sy(15),p);p.setColor(sky());c.drawCircle(sy(351),sy(top+27),sy(14),p);}else{p.setColor(Color.rgb(205,164,91));c.drawCircle(sy(345),sy(top+32),sy(13),p);}p.setStrokeWidth(sy(1));p.setColor(Color.argb(65,190,205,215));for(int i=0;i<18;i++){float x=18+((i*67+gameMinute*3)%385),y=top+((i*43+gameMinute*2)%Math.max(1,(int)(bottom-top)));c.drawLine(sy(x),sy(y),sy(x-5),sy(y+13),p);}}
        void drawCutaway(Canvas c,float top,boolean large){float left=20,right=400,height=large?500:305;box(c,left,top,right,top+height,Color.rgb(20,22,25),14);p.setColor(sky());c.drawRect(sy(left+7),sy(top+7),sy(right-7),sy(top+height-7),p);drawRuins(c,left+7,top+7,right-7,top+height-7);float bx=large?42:55,by=top+(large?38:28),bw=large?336:310,rh=large?136:82;p.setColor(Color.rgb(48,43,39));c.drawRect(sy(bx-8),sy(by-12),sy(bx+bw+8),sy(by+rh*3+6),p);for(int i=0;i<6;i++){int row=i/2,col=i%2;float l=bx+col*bw/2,t=by+row*rh,r=l+bw/2,b=t+rh;drawRoom(c,i,l,t,r,b,large);}p.setColor(Color.rgb(28,27,25));c.drawRect(sy(bx+bw/2-3),sy(by),sy(bx+bw/2+3),sy(by+rh*3),p);txt(c,"Нажмите на комнату • жители работают внутри",34,top+height-13,10,muted);}
        void drawRuins(Canvas c,float l,float t,float r,float b){p.setColor(Color.rgb(30,32,34));for(int i=0;i<7;i++){float x=l+i*62;float hh=24+(i%3)*17;c.drawRect(sy(x),sy(b-hh),sy(Math.min(r,x+45)),sy(b),p);for(int w=0;w<2;w++){p.setColor(Color.rgb(69,61,48));c.drawRect(sy(x+8+w*18),sy(b-hh+9),sy(x+13+w*18),sy(b-hh+15),p);p.setColor(Color.rgb(30,32,34));}}}
        void drawRoom(Canvas c,int i,float l,float t,float r,float b,boolean large){int base=roomCondition[i]<40?Color.rgb(70,43,40):Color.rgb(62,57,50);p.setColor(base);c.drawRect(sy(l+2),sy(t+2),sy(r-2),sy(b-2),p);p.setColor(Color.rgb(38,35,32));c.drawRect(sy(l+2),sy(t+2),sy(r-2),sy(t+7),p);p.setColor(Color.rgb(103,84,63));c.drawRect(sy(l+2),sy(b-12),sy(r-2),sy(b-2),p);boolean lit=power>0&&(gameMinute/60>=18||gameMinute/60<7);if(lit){p.setColor(Color.argb(75,240,185,82));c.drawCircle(sy((l+r)/2),sy(t+18),sy((r-l)*.34f),p);p.setColor(Color.rgb(224,174,79));c.drawCircle(sy((l+r)/2),sy(t+11),sy(4),p);}drawRoomDetails(c,i,l,t,r,b,large);drawRoomIcon(c,i,(l+r)/2,t+(b-t)*.49f,large?1.25f:.8f);drawOccupantSprites(c,i,l,t,r,b,large);bold(c,rooms[i],l+8,b-27,large?11:9,text);txt(c,"УР."+roomLevels[i]+" • "+roomCondition[i]+"%",l+8,b-14,large?9:8,roomCondition[i]<40?danger:muted);if(buildingRoom==i){p.setColor(Color.argb(175,20,20,20));c.drawRect(sy(l+2),sy(t+2),sy(r-2),sy(b-2),p);bold(c,"🔨 СТРОЙКА",l+12,t+25,10,good);txt(c,formatBuild(buildRemaining),l+12,t+42,9,text);}}
        void drawOccupantSprites(Canvas c,int ri,float l,float t,float r,float b,boolean large){ArrayList<Survivor> here=new ArrayList<>();for(Survivor s:people)if(s.alive&&s.job.equals(roomJobs[ri])&&!s.job.equals("Экспедиция"))here.add(s);int n=Math.min(3,here.size());for(int j=0;j<n;j++){float x=r-18-j*(large?25:18),y=b-(large?48:38);drawPersonSprite(c,here.get(j),x,y,large?1f:.72f);}}
        void drawPersonSprite(Canvas c,Survivor s,float x,float y,float z){int cloth=s.role.equals("Врач")?Color.rgb(178,184,177):s.role.equals("Охрана")?Color.rgb(74,84,72):s.role.equals("Сборщик")?Color.rgb(102,82,61):Color.rgb(76,91,96);p.setColor(Color.rgb(196,158,125));c.drawCircle(sy(x),sy(y-15*z),sy(5*z),p);p.setColor(cloth);c.drawRoundRect(sy(x-6*z),sy(y-10*z),sy(x+6*z),sy(y+7*z),sy(3*z),sy(3*z),p);p.setStrokeWidth(sy(2*z));p.setColor(Color.rgb(35,33,31));c.drawLine(sy(x-3*z),sy(y+7*z),sy(x-5*z),sy(y+16*z),p);c.drawLine(sy(x+3*z),sy(y+7*z),sy(x+5*z),sy(y+16*z),p);}
        void drawRoomIcon(Canvas c,int i,float x,float y,float z){p.setStyle(Paint.Style.FILL);if(i==0){p.setColor(Color.rgb(70,83,84));c.drawRoundRect(sy(x-31*z),sy(y-17*z),sy(x+31*z),sy(y+19*z),sy(4*z),sy(4*z),p);p.setColor(Color.rgb(31,35,35));for(int q=-1;q<=1;q++)c.drawCircle(sy(x+q*17*z),sy(y+2*z),sy(7*z),p);p.setColor(accent);c.drawCircle(sy(x),sy(y+2*z),sy(4*z),p);p.setColor(Color.rgb(113,96,65));c.drawRect(sy(x-35*z),sy(y+19*z),sy(x+35*z),sy(y+23*z),p);}else if(i==1){p.setColor(Color.rgb(110,82,59));c.drawRect(sy(x-34*z),sy(y),sy(x+34*z),sy(y+18*z),p);p.setColor(Color.rgb(72,76,73));c.drawRect(sy(x-25*z),sy(y-24*z),sy(x+1*z),sy(y),p);p.setColor(Color.rgb(137,55,42));c.drawCircle(sy(x+18*z),sy(y-6*z),sy(7*z),p);p.setColor(Color.rgb(180,166,127));for(int q=0;q<3;q++)c.drawRect(sy(x-30*z+q*12*z),sy(y-8*z),sy(x-24*z+q*12*z),sy(y-2*z),p);}else if(i==2){p.setColor(Color.rgb(164,162,149));c.drawRect(sy(x-32*z),sy(y),sy(x+32*z),sy(y+15*z),p);p.setColor(Color.rgb(205,201,181));c.drawRect(sy(x-27*z),sy(y-8*z),sy(x+12*z),sy(y+3*z),p);p.setColor(danger);c.drawRect(sy(x+18*z),sy(y-25*z),sy(x+24*z),sy(y-7*z),p);c.drawRect(sy(x+12*z),sy(y-19*z),sy(x+30*z),sy(y-13*z),p);}else if(i==3){p.setColor(Color.rgb(108,74,51));c.drawRect(sy(x-35*z),sy(y),sy(x+35*z),sy(y+12*z),p);p.setColor(Color.rgb(124,128,126));c.drawRect(sy(x-22*z),sy(y-15*z),sy(x+20*z),sy(y-7*z),p);p.setColor(Color.rgb(68,69,67));c.drawCircle(sy(x+23*z),sy(y-11*z),sy(8*z),p);p.setStrokeWidth(sy(2*z));c.drawLine(sy(x-27*z),sy(y-19*z),sy(x-12*z),sy(y-5*z),p);}else if(i==4){p.setColor(Color.rgb(96,78,56));for(int q=-1;q<=1;q++)c.drawRect(sy(x-37*z),sy(y+q*12*z),sy(x+37*z),sy(y+(q*12+7)*z),p);p.setColor(Color.rgb(74,74,69));for(int q=-1;q<=1;q++)c.drawCircle(sy(x+q*25*z),sy(y+3*z),sy(3*z),p);}else{p.setColor(Color.rgb(100,79,66));c.drawRect(sy(x-35*z),sy(y),sy(x+35*z),sy(y+16*z),p);p.setColor(Color.rgb(174,160,136));c.drawRect(sy(x-30*z),sy(y-9*z),sy(x-5*z),sy(y+1*z),p);c.drawRect(sy(x+4*z),sy(y-9*z),sy(x+29*z),sy(y+1*z),p);p.setColor(Color.rgb(68,58,52));c.drawRect(sy(x-36*z),sy(y+16*z),sy(x+36*z),sy(y+20*z),p);}}
        String occupants(int ri){StringBuilder s=new StringBuilder();for(Survivor q:people)if(q.alive&&q.job.equals(roomJobs[ri])){if(s.length()>0)s.append(",");s.append(q.name);}return s.length()==0?"—":s.toString();}
        String roomBonus(int i){
            switch(i){
                case 0: return "Стабилизирует энергоснабжение. Более высокий уровень снижает риск поломок и усиливает работу инженеров.";
                case 1: return "Повышает эффективность производства еды и позволяет лучше использовать запасы убежища.";
                case 2: return "Ускоряет лечение раненых и восстановление здоровья жителей, назначенных на лечение.";
                case 3: return "Повышает добычу и обработку материалов, а также эффективность механиков.";
                case 4: return "Усиливает защиту убежища и помогает охране снижать уровень угрозы.";
                case 5: return "Улучшает отдых жителей, быстрее снижает усталость и восстанавливает мораль.";
                default: return "Помещение убежища.";
            }
        }
        void drawRoomDetails(Canvas c,int i,float l,float t,float r,float b,boolean large){float z=large?1f:.65f;p.setStrokeWidth(sy(1));if(i==0){p.setColor(Color.rgb(36,38,39));for(int q=0;q<4;q++)c.drawLine(sy(l+12),sy(t+16+q*9),sy(r-12),sy(t+16+q*9),p);p.setColor(Color.rgb(194,133,50));for(int q=0;q<3;q++)c.drawCircle(sy(l+18+q*13),sy(t+15),sy(2.5f*z),p);}else if(i==1){p.setColor(Color.rgb(73,92,65));c.drawRect(sy(l+8),sy(b-27),sy(r-8),sy(b-15),p);p.setColor(Color.rgb(170,147,103));for(int q=0;q<5;q++)c.drawRect(sy(l+12+q*13),sy(t+15),sy(l+18+q*13),sy(t+24),p);}else if(i==2){p.setColor(Color.rgb(188,196,190));for(int q=0;q<3;q++)c.drawRect(sy(l+10+q*19),sy(t+14),sy(l+23+q*19),sy(t+25),p);p.setColor(Color.rgb(125,150,150));c.drawRect(sy(r-34),sy(t+13),sy(r-12),sy(t+35),p);}else if(i==3){p.setColor(Color.rgb(130,112,87));for(int q=0;q<6;q++){float xx=l+12+q*12;c.drawLine(sy(xx),sy(t+13),sy(xx),sy(t+31),p);}p.setColor(Color.rgb(54,55,53));c.drawRect(sy(l+8),sy(b-30),sy(r-8),sy(b-20),p);}else if(i==4){p.setColor(Color.rgb(89,65,46));for(int q=0;q<5;q++)c.drawLine(sy(l+8),sy(t+15+q*8),sy(r-8),sy(t+5+q*8),p);p.setColor(Color.rgb(132,72,52));c.drawCircle(sy(r-18),sy(t+18),sy(4),p);}else{p.setColor(Color.rgb(74,79,72));c.drawRect(sy((l+r)/2-12),sy(t+12),sy((l+r)/2+12),sy(t+39),p);p.setColor(Color.rgb(122,102,82));c.drawRect(sy(l+9),sy(b-28),sy(l+29),sy(b-16),p);c.drawRect(sy(r-29),sy(b-28),sy(r-9),sy(b-16),p);}if(roomCondition[i]<55){p.setColor(Color.argb(170,40,34,31));p.setStrokeWidth(sy(2));c.drawLine(sy(l+15),sy(t+8),sy(l+31),sy(t+25),p);c.drawLine(sy(l+31),sy(t+25),sy(l+23),sy(t+40),p);}if(power<=0){p.setColor(Color.argb(125,0,0,0));c.drawRect(sy(l+2),sy(t+2),sy(r-2),sy(b-2),p);}}
        void drawPortrait(Canvas c,Survivor s,float x,float y,float rad){p.setColor(Color.rgb(43,48,51));c.drawCircle(sy(x),sy(y),sy(rad),p);int skin=Color.rgb(190,148,116);p.setColor(skin);c.drawCircle(sy(x),sy(y-rad*.15f),sy(rad*.43f),p);int hair=s.name.equals("Мария")||s.name.equals("Анна")?Color.rgb(49,34,28):Color.rgb(45,39,34);p.setColor(hair);c.drawArc(sy(x-rad*.46f),sy(y-rad*.65f),sy(x+rad*.46f),sy(y+rad*.12f),180,180,true,p);p.setColor(s.role.equals("Врач")?Color.rgb(164,174,169):s.role.equals("Охрана")?Color.rgb(72,83,69):Color.rgb(88,76,63));c.drawRoundRect(sy(x-rad*.55f),sy(y+rad*.25f),sy(x+rad*.55f),sy(y+rad*.9f),sy(4),sy(4),p);p.setColor(Color.rgb(28,25,23));c.drawCircle(sy(x-rad*.15f),sy(y-rad*.12f),sy(1.4f),p);c.drawCircle(sy(x+rad*.15f),sy(y-rad*.12f),sy(1.4f),p);}
        void drawPeople(Canvas c,float y){bold(c,"ЖИТЕЛИ",20,y,16,text);int max=Math.min(people.size(),5);for(int i=0;i<max;i++){Survivor s=people.get(i);float t=y+12+i*50;box(c,20,t,400,t+44,s.alive?panel:Color.rgb(48,31,32),9);drawPortrait(c,s,43,t+22,16);bold(c,s.name,68,t+18,12,text);txt(c,s.alive?s.role+" • "+s.job:"ПОГИБ",68,t+34,9,s.alive?muted:danger);bar(c,292,t+10,382,5,s.health,good);txt(c,"❤ "+s.health+"   ☺ "+s.morale,292,t+33,8,muted);}}
        void drawNav(Canvas c){float y=H/scale-66;box(c,16,y,404,y+50,panel,12);String[] n={"ДОМ","ЖУРНАЛ","КАРТА","ЖИТЕЛИ",paused?"▶":"×"+speed};for(int i=0;i<5;i++){float x=18+i*77;if(i==4)box(c,x+2,y+4,x+73,y+46,accent,9);bold(c,n[i],x+7,y+30,8,i==4?Color.rgb(30,27,23):text);}}

        void dimForOverlay(Canvas c){p.setColor(Color.argb(175,0,0,0));c.drawRect(0,0,W,H,p);}
        void drawResidentOverlay(Canvas c){
            if(selected<0||selected>=people.size())return; Survivor s=people.get(selected); dimForOverlay(c); float hh=H/scale,top=Math.max(285,hh-405);
            box(c,18,top,402,hh-78,Color.rgb(25,29,33),18); bold(c,"×",372,top+31,22,muted);
            drawMiniPortrait(c,s,52,top+45,selected); bold(c,s.name,78,top+39,19,text); txt(c,s.role+" • навык "+s.skill,78,top+58,10,accent);
            txt(c,"Сейчас: "+residentState(s,selected),34,top+91,11,text); txt(c,"Здоровье "+s.health+"%",34,top+119,10,muted);bar(c,34,top+128,386,8,s.health,good);
            txt(c,"Голод "+s.hunger+"%",34,top+158,10,muted); txt(c,"Усталость "+s.fatigue+"%",190,top+158,10,muted); txt(c,"Мораль "+s.morale+"%",34,top+184,10,muted);
            box(c,34,top+211,386,top+257,accent,11);bold(c,"СМЕНИТЬ РАБОТУ",116,top+240,11,Color.rgb(30,27,23));
            box(c,34,top+266,206,top+309,panel2,10);bold(c,"ОТДЫХ",91,top+293,10,text); box(c,214,top+266,386,top+309,panel2,10);bold(c,"ЗАКРЫТЬ",264,top+293,10,text);
        }
        void drawRoomOverlay(Canvas c){
            if(selectedRoom<0||selectedRoom>=6)return; int i=selectedRoom; dimForOverlay(c); float hh=H/scale,top=Math.max(300,hh-390);
            box(c,18,top,402,hh-78,Color.rgb(25,29,33),18);bold(c,"×",372,top+31,22,muted);bold(c,rooms[i],34,top+37,18,text);txt(c,"Уровень "+roomLevels[i]+" • состояние "+roomCondition[i]+"%",34,top+58,10,roomCondition[i]<40?danger:muted);
            bar(c,34,top+73,386,8,roomCondition[i],roomCondition[i]<40?danger:good); txt(c,"Работают: "+occupants(i),34,top+111,11,text);wrap(c,roomBonus(i),34,top+139,386,10,muted,15);
            int cost=6+roomLevels[i]*4; if(buildingRoom==i){box(c,34,top+202,386,top+250,panel2,11);bold(c,"СТРОИТСЯ • "+formatBuild(buildRemaining),75,top+232,11,good);}else{box(c,34,top+202,386,top+250,accent,11);bold(c,"УЛУЧШИТЬ • "+cost+" МАТ.",94,top+232,11,Color.rgb(30,27,23));}
            box(c,34,top+259,206,top+302,panel2,10);bold(c,"НАЗНАЧИТЬ",70,top+286,9,text);box(c,214,top+259,386,top+302,panel2,10);bold(c,"ЗАКРЫТЬ",264,top+286,10,text);
        }
        void drawResidentsOverlay(Canvas c){dimForOverlay(c);float hh=H/scale,top=Math.max(250,hh-470);box(c,18,top,402,hh-78,Color.rgb(25,29,33),18);bold(c,"ЖИТЕЛИ",34,top+38,18,text);bold(c,"×",372,top+31,22,muted);float y=top+65;for(int i=0;i<Math.min(6,people.size());i++){Survivor s=people.get(i);box(c,30,y,390,y+52,panel2,10);drawMiniPortrait(c,s,52,y+25,i);bold(c,s.name,76,y+23,11,text);txt(c,s.role+" • "+residentState(s,i),76,y+41,9,s.job.equals("Отдых")?blue:good);y+=59;}}
        void drawRooms(Canvas c){drawHeader(c,"интерактивный разрез");drawCutaway(c,75,true);bottomBack(c);}
        void drawRoomDetail(Canvas c){
            int i=selectedRoom; drawHeader(c,rooms[i]+" • уровень "+roomLevels[i]);
            box(c,20,72,400,310,panel,16);
            if(shelterBitmap!=null){
                int sw=shelterBitmap.getWidth(), sh=shelterBitmap.getHeight();
                int col=i%2,row=i/2;
                int sx0=col==0?10:425, sx1=col==0?420:842;
                int sy0=(i/2==0?85:(i/2==1?350:610)), sy1=Math.min(sh,sy0+255);
                Rect src=new Rect(sx0,sy0,sx1,sy1);
                RectF dst=new RectF(sy(28),sy(82),sy(392),sy(300));
                c.drawBitmap(shelterBitmap,src,dst,p);
            }
            bold(c,"СОСТОЯНИЕ",24,340,12,muted); bar(c,24,350,396,10,roomCondition[i],roomCondition[i]<40?danger:good);
            txt(c,"Работают: "+occupants(i),24,390,12,text); txt(c,"Назначение: "+roomJobs[i],24,414,11,muted);
            wrap(c,roomBonus(i),24,447,396,11,muted,17);
            int cost=6+roomLevels[i]*4,time=120+roomLevels[i]*90;
            if(buildingRoom==i){box(c,24,505,396,563,panel2,12);bold(c,"УЛУЧШЕНИЕ ИДЁТ",42,529,12,good);txt(c,"Осталось "+formatBuild(buildRemaining),42,550,11,text);}
            else{box(c,24,505,396,563,accent,12);bold(c,"УЛУЧШИТЬ • "+cost+" МАТ.",42,529,12,Color.rgb(30,27,23));txt(c,"Время: "+formatBuild(time),42,550,10,Color.rgb(50,42,34));}
            box(c,24,577,396,629,panel2,12);bold(c,"НАЗНАЧИТЬ ЖИТЕЛЯ",42,609,12,text);bottomBack(c);
        }

        void drawMap(Canvas c){drawHeader(c,"карта разрушенного района");float top=68,bottom=H/scale-82;box(c,18,top,402,bottom,Color.rgb(31,34,35),14);p.setColor(Color.rgb(46,48,47));for(int i=0;i<8;i++){float yy=top+35+i*62;c.drawRect(sy(25),sy(yy),sy(395),sy(yy+5),p);}for(int i=0;i<6;i++){float xx=42+i*65;c.drawRect(sy(xx),sy(top+10),sy(xx+4),sy(bottom-8),p);}float hx=210,hy=top+150;drawMapNode(c,hx,hy,"ДОМ",true,0);float[][] pos={{90,top+78},{315,top+86},{92,top+245},{322,top+235},{132,top+370},{300,top+385}};for(int i=0;i<locations.size();i++){Location l=locations.get(i);float x=pos[i][0],y=pos[i][1];p.setStrokeWidth(sy(2));p.setColor(Color.rgb(76,74,67));c.drawLine(sy(hx),sy(hy),sy(x),sy(y),p);drawMapNode(c,x,y,l.discovered?l.name:"?",l.discovered,l.risk);if(l.discovered){txt(c,l.distance+" км",x-18,y+31,8,muted);bar(c,x-30,y+37,x+30,4,l.stock,l.stock<30?danger:good);}}txt(c,"Нажмите на точку, чтобы отправить свободного жителя",30,bottom-18,9,muted);if(expeditionPerson>=0){box(c,30,bottom-70,390,bottom-34,panel2,9);bold(c,"В ПУТИ • "+people.get(expeditionPerson).name,44,bottom-50,10,accent);txt(c,formatBuild(expeditionRemaining),320,bottom-50,10,text);}bottomBack(c);}
        void drawMapNode(Canvas c,float x,float y,String name,boolean seen,int risk){p.setColor(seen?(risk>40?Color.rgb(106,62,56):Color.rgb(78,84,75)):Color.rgb(45,47,48));c.drawCircle(sy(x),sy(y),sy(22),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sy(2));p.setColor(seen?accent:muted);c.drawCircle(sy(x),sy(y),sy(22),p);p.setStyle(Paint.Style.FILL);if(name.equals("ДОМ")){p.setColor(Color.rgb(135,111,79));c.drawRect(sy(x-10),sy(y-7),sy(x+10),sy(y+10),p);Path q=new Path();q.moveTo(sy(x-14),sy(y-7));q.lineTo(sy(x),sy(y-18));q.lineTo(sy(x+14),sy(y-7));q.close();c.drawPath(q,p);}bold(c,name,x-Math.min(35,name.length()*3.2f),y-28,8,seen?text:muted);}
        void drawLocationDialog(Canvas c,int li){ }
        int availableExplorer(){for(int i=0;i<people.size();i++)if(people.get(i).alive&&!people.get(i).job.equals("Экспедиция"))return i;return -1;}
        void startExpedition(int li){if(expeditionPerson>=0){addLog("Сначала дождитесь возвращения текущей экспедиции.");return;}Location l=locations.get(li);if(!l.discovered)return;int pi=availableExplorer();if(pi<0){addLog("Нет свободного жителя для вылазки.");return;}expeditionPerson=pi;expeditionLocation=li;expeditionRemaining=90+l.distance*35;people.get(pi).job="Экспедиция";addLog(people.get(pi).name+" отправился: "+l.name+". Возвращение через "+formatBuild(expeditionRemaining)+".");save();invalidate();}
        void finishExpedition(){if(expeditionPerson<0||expeditionLocation<0)return;Survivor s=people.get(expeditionPerson);Location l=locations.get(expeditionLocation);int night=(gameMinute/60>=22||gameMinute/60<6)?12:0;int risk=Math.min(85,l.risk+night);boolean hurt=rnd.nextInt(100)<risk;int stockFactor=Math.max(20,l.stock);int gain=1+rnd.nextInt(Math.max(2,stockFactor/18));if(l.name.equals("Продуктовый")){food+=gain+3;water+=gain+2;}else if(l.name.equals("Аптека")){s.health=Math.min(100,s.health+8);mats+=gain;}else if(l.name.equals("Гаражи")){mats+=gain+4;}else if(l.name.equals("Склад")){food+=gain;water+=gain;mats+=gain+5;}else if(l.name.equals("Больница")){mats+=gain+3;s.health=Math.min(100,s.health+15);}else{food+=gain;water+=gain;mats+=Math.max(1,gain/2);}l.stock=Math.max(0,l.stock-(8+rnd.nextInt(13)));if(hurt){int dmg=8+rnd.nextInt(20);s.health=Math.max(1,s.health-dmg);addLog(s.name+" вернулся раненым из "+l.name+" (-"+dmg+" здоровья). ");}else addLog(s.name+" вернулся из "+l.name+" с припасами.");s.job="Отдых";s.fatigue=Math.min(100,s.fatigue+22);if(rnd.nextInt(100)<35){for(Location q:locations)if(!q.discovered){q.discovered=true;addLog("Открыта новая точка: "+q.name+".");break;}}expeditionPerson=-1;expeditionLocation=-1;expeditionRemaining=0;save();}
        void drawSurvivor(Canvas c){Survivor s=people.get(selected);drawHeader(c,s.name+" • "+s.role);box(c,20,90,400,350,panel,16);bold(c,s.name,45,135,25,text);txt(c,s.role+" • навык "+s.skill,45,160,12,accent);txt(c,"Здоровье "+s.health+"%",45,205,12,text);bar(c,45,215,370,9,s.health,good);txt(c,"Голод "+s.hunger+"%   Усталость "+s.fatigue+"%",45,255,11,muted);txt(c,"Мораль "+s.morale+"%",45,285,11,muted);txt(c,"Сейчас: "+s.job,45,320,12,accent);box(c,35,390,385,448,accent,12);bold(c,"ИЗМЕНИТЬ РАБОТУ",105,425,13,Color.rgb(30,27,23));bottomBack(c);}
        void drawJournal(Canvas c){drawHeader(c,"журнал событий");float y=90;for(int i=0;i<Math.min(log.size(),10);i++){wrap(c,log.get(i),24,y,396,10,i==0?text:muted,15);y+=48;}bottomBack(c);}
        void bottomBack(Canvas c){float y=H/scale-65;box(c,20,y,400,y+48,panel2,12);bold(c,"← НАЗАД",35,y+30,11,text);}
        String formatBuild(int m){return String.format(Locale.getDefault(),"%02d:%02d",Math.max(0,m)/60,Math.max(0,m)%60);}
        void startUpgrade(int i){if(buildingRoom>=0){addLog("Сначала завершите текущее строительство.");return;}int cost=6+roomLevels[i]*4;if(mats<cost){addLog("Не хватает материалов: нужно "+cost+".");return;}mats-=cost;buildingRoom=i;buildRemaining=120+roomLevels[i]*90;addLog("Начато улучшение: "+rooms[i]+".");save();invalidate();}
        void repairRoom(int i){int cost=Math.max(1,(100-roomCondition[i])/15);if(roomCondition[i]>=95)return;if(mats>=cost){mats-=cost;roomCondition[i]=Math.min(100,roomCondition[i]+30);addLog(rooms[i]+": выполнен ремонт.");}save();}
        void processJobs(){int guards=0,medics=0;for(Survivor s:people)if(s.alive){s.fatigue=Math.max(0,Math.min(100,s.fatigue+(s.job.equals("Отдых")?-22:13)));if(s.fatigue>85&&!s.job.equals("Отдых")){s.health=Math.max(1,s.health-4);s.morale=Math.max(0,s.morale-6);}if(s.health<35&&!s.job.equals("Лечение")&&!s.job.equals("Отдых")){s.fatigue=Math.min(100,s.fatigue+8);}if(s.job.equals("Еда"))food+=3+(s.role.equals("Сборщик")?s.skill:1)+roomLevels[1]/2;else if(s.job.equals("Вода"))water+=4;else if(s.job.equals("Материалы"))mats+=2+(s.role.equals("Механик")?2:0)+roomLevels[3]/2;else if(s.job.equals("Ремонт")){shelter=Math.min(100,shelter+3+s.skill);roomCondition[0]=Math.min(100,roomCondition[0]+2);}else if(s.job.equals("Охрана"))guards+=s.skill+roomLevels[4];else if(s.job.equals("Лечение"))medics+=s.skill+roomLevels[2];else{s.morale=Math.min(100,s.morale+4+roomLevels[5]/2);s.health=Math.min(100,s.health+2);}}threat=Math.max(0,threat-guards);if(medics>0)for(Survivor s:people)if(s.alive&&s.health<100)s.health=Math.min(100,s.health+medics/2);}
        void triggerEvent(){event=true;screen=0;overlay=0;int e=rnd.nextInt(5);incidentRoom=e==0?4:e==1?0:e==2?2:e==3?4:5;if(e==0)ev("ЧУЖАК У ДВЕРИ","Ночью в дверь стучит незнакомец.","ВПУСТИТЬ","ОТКАЗАТЬ");else if(e==1)ev("КОРОТКОЕ ЗАМЫКАНИЕ","В генераторной пахнет гарью. Оборудование перегрелось.","РЕМОНТ","ОТКЛЮЧИТЬ");else if(e==2)ev("БОЛЕЗНЬ","Одному из жителей нужна помощь.","ЛЕЧИТЬ","ОТДЫХ");else if(e==3)ev("МАРОДЁРЫ","У входа замечены вооружённые люди.","ОТДАТЬ ЕДУ","ОБОРОНА");else ev("ТИХАЯ НОЧЬ","Дом наконец затих. Можно восстановить силы.","ОТДЫХ","ДЕЖУРИТЬ");autoRespondToIncident();}

        void autoRespondToIncident(){
            int pick=-1;String wanted=null;
            if(eventTitle.equals("КОРОТКОЕ ЗАМЫКАНИЕ")){wanted="Ремонт";pick=findBestResident("Инженер","Механик");}
            else if(eventTitle.equals("БОЛЕЗНЬ")){wanted="Лечение";pick=findBestResident("Врач","");}
            else if(eventTitle.equals("МАРОДЁРЫ")||eventTitle.equals("ЧУЖАК У ДВЕРИ")){wanted="Охрана";pick=findBestResident("Охрана","");}
            if(pick>=0&&wanted!=null){Survivor s=people.get(pick);if(!s.job.equals("Экспедиция")){s.job=wanted;addLog(s.name+" автоматически реагирует: "+wanted.toLowerCase()+".");}}
        }
        int findBestResident(String role1,String role2){int best=-1,score=-999;for(int i=0;i<people.size();i++){Survivor s=people.get(i);if(!s.alive||s.job.equals("Экспедиция"))continue;int v=s.skill*5-s.fatigue/8+s.health/12;if(s.role.equals(role1)||(!role2.isEmpty()&&s.role.equals(role2)))v+=40;if(v>score){score=v;best=i;}}return best;}
        void ev(String a,String b,String c,String d){eventTitle=a;eventText=b;eventChoices[0]=c;eventChoices[1]=d;}
        void choose(int n){if(eventTitle.equals("ЧУЖАК У ДВЕРИ")){if(n==0){people.add(make("Алекс","Выживший",2));food=Math.max(0,food-2);addLog("В дом принят Алекс.");}else addLog("Чужаку отказали.");}else if(eventTitle.equals("КОРОТКОЕ ЗАМЫКАНИЕ")){if(n==0&&mats>=3){mats-=3;roomCondition[0]=Math.min(100,roomCondition[0]+25);addLog("Генераторную отремонтировали.");}else{power=Math.max(0,power-8);roomCondition[0]=Math.max(10,roomCondition[0]-18);addLog("Генераторная повреждена.");}}else if(eventTitle.equals("БОЛЕЗНЬ")){Survivor q=people.get(rnd.nextInt(people.size()));q.health=Math.max(1,q.health+(n==0?10:-12));addLog(n==0?"Больному помогли.":"Болезнь ослабила жителя.");}else if(eventTitle.equals("МАРОДЁРЫ")){if(n==0)food=Math.max(0,food-7);else{threat=Math.max(0,threat-roomLevels[4]*3);roomCondition[4]=Math.max(10,roomCondition[4]-8);}addLog("Столкновение у баррикад завершилось.");}else{for(Survivor s:people)if(s.alive)s.fatigue=Math.max(0,s.fatigue-(n==0?15:5));addLog("Ночь использовали с пользой.");}event=false;incidentRoom=-1;save();invalidate();}
        void drawIncidentMarker(Canvas c){if(!event||incidentRoom<0)return;float[][] centers={{109,214},{311,214},{109,344},{311,344},{109,474},{311,474}};float x=centers[incidentRoom][0],y=centers[incidentRoom][1];float pulse=(float)((Math.sin(System.currentTimeMillis()/180.0)+1)*.5);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sy(2.5f));p.setColor(Color.argb(180,danger>>16&255,danger>>8&255,danger&255));c.drawCircle(sy(x),sy(y),sy(24+pulse*8),p);p.setStyle(Paint.Style.FILL);box(c,x-13,y-13,x+13,y+13,danger,13);bold(c,"!",x-3.5f,y+6,16,Color.WHITE);}
        void drawEvent(Canvas c){float hh=H/scale,t=Math.max(455,hh-310),b=hh-84;p.setColor(Color.argb(75,0,0,0));c.drawRect(0,sy(t-18),W,H,p);box(c,18,t,402,b,Color.rgb(24,27,32),18);txt(c,"СОБЫТИЕ В УБЕЖИЩЕ",38,t+28,9,accent);bold(c,eventTitle,38,t+55,17,text);wrap(c,eventText,38,t+82,382,11,muted,16);float by=b-98;box(c,36,by,384,by+39,accent,10);bold(c,eventChoices[0],52,by+25,11,Color.rgb(30,27,23));box(c,36,by+48,384,by+87,panel2,10);bold(c,eventChoices[1],52,by+73,11,text);}
        void drawJobMenu(Canvas c){p.setColor(Color.argb(205,0,0,0));c.drawRect(0,0,W,H,p);float t=110;box(c,30,t,390,t+410,panel,16);bold(c,"НАЗНАЧИТЬ РАБОТУ",50,t+35,17,text);for(int i=0;i<jobs.length;i++){float y=t+55+i*45;box(c,48,y,372,y+36,panel2,8);bold(c,jobs[i],64,y+24,11,text);}}
        void drawGameOver(Canvas c){p.setColor(Color.argb(230,0,0,0));c.drawRect(0,0,W,H,p);float hh=H/scale;bold(c,"ПОСЛЕДНИЙ ДОМ ПАЛ",54,hh/2-60,22,text);txt(c,"Вы продержались "+day+" дней",110,hh/2-25,13,muted);box(c,55,hh/2+25,365,hh/2+82,accent,14);bold(c,"НАЧАТЬ ЗАНОВО",132,hh/2+60,13,Color.rgb(30,27,23));}
        void wrap(Canvas c,String s,float x,float y,float max,float size,int col,float step){p.setTextSize(sy(size));String[] ws=s.split(" ");String line="";float yy=y;for(String w:ws){if(p.measureText(line+w)>sy(max-x)){txt(c,line,x,yy,size,col);yy+=step;line="";}line+=w+" ";}txt(c,line,x,yy,size,col);}
        int roomAt(float x,float y,float top,boolean large){float bx=large?42:55,by=top+(large?38:28),bw=large?336:310,rh=large?136:82;if(x<bx||x>bx+bw||y<by||y>by+rh*3)return-1;int col=(int)((x-bx)/(bw/2)),row=(int)((y-by)/rh);int i=row*2+col;return i>=0&&i<6?i:-1;}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true; float x=e.getX()/scale,y=e.getY()/scale,hh=H/scale;
            if(gameOver){if(y>hh/2)reset();return true;}
            if(event){float b=hh-84,by=b-98;if(y>=by&&y<=by+39)choose(0);else if(y>=by+48&&y<=by+87)choose(1);return true;}
            if(jobMenu){float t=110;for(int i=0;i<jobs.length;i++){float yy=t+55+i*45;if(y>=yy&&y<=yy+36){people.get(selected).job=jobs[i];jobMenu=false;overlay=1;addLog(people.get(selected).name+": "+jobs[i]+".");save();invalidate();return true;}}jobMenu=false;invalidate();return true;}
            if(screen==0 && overlay!=0){float top=(overlay==3?Math.max(250,hh-470):overlay==1?Math.max(285,hh-405):Math.max(300,hh-390));if(y<top||x>350&&y<top+55){overlay=0;invalidate();return true;}if(overlay==1){if(y>=top+211&&y<=top+257){jobMenu=true;invalidate();return true;}if(y>=top+266&&y<=top+309&&x<210){people.get(selected).job="Отдых";save();invalidate();return true;}if(y>=top+266&&y<=top+309&&x>=210){overlay=0;invalidate();return true;}}else if(overlay==2){if(y>=top+202&&y<=top+250){startUpgrade(selectedRoom);invalidate();return true;}if(y>=top+259&&y<=top+302&&x<210){int idx=-1;for(int i=0;i<people.size();i++)if(people.get(i).alive){idx=i;break;}if(idx>=0){selected=idx;jobMenu=true;}invalidate();return true;}if(y>=top+259&&y<=top+302&&x>=210){overlay=0;invalidate();return true;}}else if(overlay==3){float yy=top+65;for(int i=0;i<Math.min(6,people.size());i++,yy+=59)if(y>=yy&&y<=yy+52){selected=i;overlay=1;invalidate();return true;}}return true;}
            if(screen==0){
                float sceneTop=116f,sceneBottom=Math.max(610f,hh-72f);float sceneH=sceneBottom-sceneTop;
                if(y>=sceneTop+sceneH*.08f&&y<=sceneTop+sceneH*.27f&&x>=145&&x<=275){screen=5;overlay=0;invalidate();return true;}
                int livePerson=-1;for(int pi=0;pi<people.size()&&pi<32;pi++){Survivor ps=people.get(pi);if(ps.alive&&!ps.job.equals("Экспедиция")&&x>=residentX[pi]-17&&x<=residentX[pi]+17&&y>=residentY[pi]-28&&y<=residentY[pi]+28){livePerson=pi;break;}}
                if(livePerson>=0){selected=livePerson;overlay=1;invalidate();return true;}
                int ri=fullSceneRoomAt(x,y,sceneTop,sceneBottom);if(ri>=0){selectedRoom=ri;overlay=2;invalidate();return true;}
                if(y>hh-78){int i=(int)((x-18)/77);if(i==0){overlay=0;}else if(i==1){screen=2;overlay=0;}else if(i==2){screen=5;overlay=0;}else if(i==3){overlay=3;}else if(i==4){if(paused){paused=false;speed=1;}else if(speed==1)speed=2;else if(speed==2)speed=4;else{paused=true;speed=1;}save();}invalidate();}
            }else if(screen==2){if(y>hh-80){screen=0;invalidate();}}
            else if(screen==5){if(y>hh-80){screen=0;invalidate();}else{float top=68;float[][] pos={{90,top+78},{315,top+86},{92,top+245},{322,top+235},{132,top+370},{300,top+385}};for(int li=0;li<locations.size();li++){float dx=x-pos[li][0],dy=y-pos[li][1];if(dx*dx+dy*dy<1100){startExpedition(li);break;}}}}
            else {screen=0;overlay=0;invalidate();}
            return true;
        }
    }
}
