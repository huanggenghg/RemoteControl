package com.lumostech.remotecontrol.bean;

public class Bean {
    private float x;
    private float y;

    public void setX(float x) {
        this.x = x;
    }

    public Bean(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    @Override
    public String toString() {
        return "[" +
                "x=" + x +
                ", y=" + y +
                ']';
    }
}
