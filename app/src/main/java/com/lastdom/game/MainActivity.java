package com.lastdom.game;

import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import java.util.*;

public class MainActivity extends Activity {
    GameView view;
    @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN); view=new GameView(this); setContentView(view); }
    @Override protected void onPause(){ super.onPause(); view.save(); }

    static class Survivor { String name, role; int skill; boolean alive=true; Survivor(String n,String r,int s){name=n;role=r;skill=s;} }

    class GameView extends View {
        Paint p=new Paint(3); Paint stroke=new Paint(3); Random rnd=new Random(); ArrayList<Survivor> people=new ArrayList<>(); ArrayList<String> log=new ArrayList<>();
        int day=1, food=28, water=34, power=24, mats=18, level=1, selected=-1; boolean event=false, gameOver=false; String eventTitle="",eventText=""; String[] eventChoices=new String[2];
        int W,H; SharedPreferences sp;
        int bg=Color.rgb(16,18,22), panel=Color.rgb(28,31,37), panel2=Color.rgb(37,40,47), text=Color.rgb(235,231,222), muted=Color.rgb(165,164,157), accent=Color.rgb(209,138,69), danger=Color.rgb(190,72,65), good=Color.rgb(101,160,104);
        GameView(Context c){ super(c); p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(2); sp=getSharedPreferences("save",0); load(); }
        void reset(){ day=1;food=28;water=34;power=24;mats=18;level=1;selected=-1;event=false;gameOver=false;people.clear(); people.add(new Survivor("Иван","Инженер",4));people.add(new Survivor("Мария","Врач",3));people.add(new Survivor("Сергей","Охрана",4));people.add(new Survivor("Анна","Сборщик",3));people.add(new Survivor("Павел","Механик",4));log.clear(); log.add("День 1: убежище готово."); save(); invalidate(); }
        void save(){ sp.edit().putInt("day",day).putInt("food",food).putInt("water",water).putInt("power",power).putInt("mats",mats).putInt("level",level).apply(); }
        void load(){ if(!sp.contains("day")){reset();return;} day=sp.getInt("day",1);food=sp.getInt("food",28);water=sp.getInt("water",34);power=sp.getInt("power",24);mats=sp.getInt("mats",18);level=sp.getInt("level",1); people.add(new Survivor("Иван","Инженер",4));people.add(new Survivor("Мария","Врач",3));people.add(new Survivor("Сергей","Охрана",4));people.add(new Survivor("Анна","Сборщик",3));people.add(new Survivor("Павел","Механик",4));log.add("Сохранение восстановлено."); }
        void txt(Canvas c,String s,float x,float y,float size,int col){p.setStyle(Paint.Style.FILL);p.setTextSize(size);p.setColor(col);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));c.drawText(s,x,y,p);}
        void bold(Canvas c,String s,float x,float y,float size,int col){p.setStyle(Paint.Style.FILL);p.setTextSize(size);p.setColor(col);p.setTypeface(Typeface.create("sans",Typeface.BOLD));c.drawText(s,x,y,p);}
        void box(Canvas c,float l,float t,float r,float b,int col,float rad){p.setColor(col);p.setStyle(Paint.Style.FILL);c.drawRoundRect(l,t,r,b,rad,rad,p);}
        @Override protected void onDraw(Canvas c){ super.onDraw(c); W=getWidth();H=getHeight(); c.drawColor(bg); drawTop(c); drawHouse(c); drawResources(c); drawPeople(c); drawButtons(c); if(event) drawEvent(c); if(gameOver) drawGameOver(c); }
        void drawTop(Canvas c){ bold(c,"ПОСЛЕДНИЙ ДОМ",24,42,24,text); txt(c,"ДЕНЬ "+day,24,69,14,muted); txt(c,"УРОВЕНЬ УБЕЖИЩА "+level, W-190,42,13,muted); }
        void drawResources(Canvas c){ int y=94, gap=8, bw=(W-48-gap*3)/4; String[] a={"🍞  "+food,"💧  "+water,"⚡  "+power,"🧰  "+mats}; for(int i=0;i<4;i++){float x=24+i*(bw+gap);box(c,x,y,x+bw,y+50,panel,12);txt(c,a[i],x+10,y+31,15,text);} }
        void drawHouse(Canvas c){ float top=162; box(c,20,top,W-20,top+300,panel,18); // sky
            p.setColor(Color.rgb(24,27,32)); c.drawRect(28,top+8,W-28,top+292,p);
            // moon
            p.setColor(Color.rgb(214,198,155)); c.drawCircle(W-76,top+58,22,p); p.setColor(Color.rgb(24,27,32)); c.drawCircle(W-66,top+49,20,p);
            // building
            float bl=W*.24f, br=W*.76f, bt=top+70, bb=top+265; p.setColor(Color.rgb(72,73,72));c.drawRect(bl,bt,br,bb,p);p.setColor(Color.rgb(94,94,89));c.drawRect(bl-12,bb-18,br+12,bb,p);
            p.setColor(Color.rgb(52,53,52));c.drawRect(bl-6,bt-18,br+6,bt,p);
            for(int row=0;row<3;row++) for(int col=0;col<3;col++){float x=bl+24+col*52,y=bt+25+row*52;p.setColor((row+col+day)%4==0?Color.rgb(105,70,42):Color.rgb(211,165,83));c.drawRect(x,y,x+24,y+31,p);}
            p.setColor(Color.rgb(43,43,42));c.drawRect(W/2-20,bb-58,W/2+20,bb,p); p.setColor(accent);c.drawRect(W/2-6,bb-35,W/2+5,bb-20,p);
            // barricades
            stroke.setColor(accent);stroke.setStrokeWidth(5); for(int i=0;i<5;i++) c.drawLine(bl-20+i*25,bb+6,bl-2+i*25,bb-10,stroke);
            txt(c,"УБЕЖИЩЕ",34,top+285,12,muted);
        }
        void drawPeople(Canvas c){ float y=480; bold(c,"ЖИТЕЛИ",24,y,18,text); txt(c,"Назначьте людей на работу",W-194,y,12,muted); float cy=y+24; int cardH=76; for(int i=0;i<people.size();i++){Survivor s=people.get(i); float t=cy+i*(cardH+8); box(c,20,t,W-20,t+cardH,s.alive?panel:Color.rgb(48,31,32),14); p.setColor(s.alive?accent:danger);c.drawCircle(54,t+38,23,p); txt(c,s.alive?"":"✕",48,t+45,20,Color.WHITE); bold(c,s.name,88,t+25,16,text);txt(c,s.role+"  •  сила "+s.skill,88,t+47,12,muted); box(c,W-150,t+18,W-38,t+57,panel2,10);txt(c,"РАБОТА",W-132,t+42,11,muted); }
        }
        void drawButtons(Canvas c){float y=H-82; box(c,20,y,W/2-8,H-20,accent,14);bold(c,"СЛЕДУЮЩИЙ ДЕНЬ",42,y+38,14,Color.rgb(25,23,20));box(c,W/2+8,y,W-20,H-20,panel2,14);bold(c,"УЛУЧШИТЬ ДОМ",W/2+30,y+38,14,text);}
        void drawEvent(Canvas c){p.setColor(Color.argb(180,0,0,0));c.drawRect(0,0,W,H,p);float l=22,r=W-22,t=H*.28f,b=H*.72f;box(c,l,t,r,b,panel,20);bold(c,eventTitle,l+22,t+45,23,text);wrap(c,eventText,l+22,t+78,r-22,14,muted);box(c,l+22,b-88,r-22,b-50,accent,12);bold(c,eventChoices[0],l+38,b-63,13,Color.rgb(25,23,20));box(c,l+22,b-43,r-22,b-5,panel2,12);bold(c,eventChoices[1],l+38,b-18,13,text);}
        void wrap(Canvas c,String s,float x,float y,float max,float size,int col){p.setTextSize(size);String[] words=s.split(" ");String line="";float yy=y;for(String w:words){if(p.measureText(line+w)>max-x){txt(c,line,x,yy,size,col);yy+=20;line="";}line+=w+" ";}txt(c,line,x,yy,size,col);}
        void drawGameOver(Canvas c){p.setColor(Color.argb(220,0,0,0));c.drawRect(0,0,W,H,p);bold(c,"УБЕЖИЩЕ ПАЛО",W/2-105,H/2-35,25,text);txt(c,"Вы прожили "+day+" дней",W/2-75,H/2,15,muted);box(c,50,H/2+35,W-50,H/2+88,accent,14);bold(c,"НАЧАТЬ ЗАНОВО",W/2-74,H/2+68,14,Color.rgb(25,23,20));}
        void nextDay(){ if(gameOver)return; int alive=0;for(Survivor s:people)if(s.alive)alive++; if(alive==0){gameOver=true;invalidate();return;} food-=Math.max(1,alive/2);water-=alive;power-=Math.max(1,level); if(food<0||water<0||power<0){int idx=rnd.nextInt(people.size());people.get(idx).alive=false;log.add("День "+day+": потерян житель из-за нехватки ресурсов.");food=Math.max(0,food);water=Math.max(0,water);power=Math.max(0,power);} int workers=0;for(Survivor s:people)if(s.alive)workers++;food+=workers/2;water+=workers/2;mats+=workers>0?1:0;day++; triggerEvent();save();invalidate(); }
        void triggerEvent(){int e=rnd.nextInt(6);event=true; if(e==0){eventTitle="ЧУЖАК У ДВЕРИ";eventText="Ночью кто-то просит открыть дверь. Вы не знаете, кто там.";eventChoices[0]="ВПУСТИТЬ";eventChoices[1]="ОТКАЗАТЬ";}else if(e==1){eventTitle="СТАРЫЙ СКЛАД";eventText="Разведчик нашёл склад в соседнем здании. Там могут быть полезные материалы.";eventChoices[0]="ОТПРАВИТЬСЯ";eventChoices[1]="НЕ РИСКОВАТЬ";}else if(e==2){eventTitle="БОЛЕЗНЬ";eventText="Один из жителей заболел. Нужны лекарства или покой.";eventChoices[0]="ЛЕЧИТЬ";eventChoices[1]="ЭКОНОМИТЬ";}else if(e==3){eventTitle="МАРОДЁРЫ";eventText="Несколько вооружённых людей требуют часть запасов.";eventChoices[0]="ОТДАТЬ РЕСУРСЫ";eventChoices[1]="ЗАЩИЩАТЬСЯ";}else if(e==4){eventTitle="ДОЖДЬ";eventText="Начался сильный дождь. Можно собрать воду, если потратить материалы.";eventChoices[0]="СОБИРАТЬ ВОДУ";eventChoices[1]="ПРОПУСТИТЬ";}else{eventTitle="ТИХАЯ НОЧЬ";eventText="Сегодня ничего не произошло. Иногда это лучший исход.";eventChoices[0]="ОТЛИЧНО";eventChoices[1]="ЗАКРЫТЬСЯ";}}
        void choose(int n){ if(!event)return; if(eventTitle.equals("ЧУЖАК У ДВЕРИ")){if(n==0){people.add(new Survivor("Новый","Выживший",2));log.add("В убежище принят новый человек.");}else log.add("Чужаку отказали.");} else if(eventTitle.equals("СТАРЫЙ СКЛАД")){if(n==0){mats+=8;food+=5;log.add("Со склада принесли запасы.");}else log.add("Разведку отменили.");} else if(eventTitle.equals("БОЛЕЗНЬ")){if(n==0){water=Math.max(0,water-3);log.add("Больного удалось стабилизировать.");}else {for(Survivor s:people)if(s.alive){s.alive=false;break;}log.add("Болезнь унесла жизнь.");}} else if(eventTitle.equals("МАРОДЁРЫ")){if(n==0){food=Math.max(0,food-6);water=Math.max(0,water-6);log.add("Пришлось отдать часть запасов.");}else {if(rnd.nextBoolean()){mats=Math.max(0,mats-3);log.add("Защита сработала.");}else {for(Survivor s:people)if(s.alive){s.alive=false;break;}log.add("При обороне погиб житель.");}}} else if(eventTitle.equals("ДОЖДЬ")){if(n==0){mats=Math.max(0,mats-2);water+=12;log.add("Собрали дождевую воду.");}} event=false;save();invalidate();}
        void upgrade(){if(event||gameOver)return;if(mats>=10&&power>=5){mats-=10;power-=5;level++;log.add("Дом улучшен до уровня "+level+".");save();invalidate();}}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float x=e.getX(),y=e.getY(); if(gameOver){if(y>H/2+20){reset();}return true;} if(event){if(y>H*.65f&&y<H*.78f)choose(0); else if(y>=H*.78f)choose(1);return true;} if(y>H-100){if(x<W/2)nextDay();else upgrade();}return true;}
    }
}
