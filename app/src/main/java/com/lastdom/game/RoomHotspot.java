package com.lastdom.game;

/** Interactive room touch area for v0.8.1.1 */
public class RoomHotspot {
    public final int roomId;
    public final float left, top, right, bottom;
    public RoomHotspot(int roomId, float left, float top, float right, float bottom){
        this.roomId=roomId; this.left=left; this.top=top; this.right=right; this.bottom=bottom;
    }
    public boolean contains(float x,float y){
        return x>=left && x<=right && y>=top && y<=bottom;
    }
}
