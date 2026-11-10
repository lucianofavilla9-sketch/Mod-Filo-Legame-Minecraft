package com.legame;

public class Missions {
    public static final int HUG = 0, FEED = 1, KISS = 2, SLEEP = 3, WALK = 4, HUNT = 5, DANCE = 6;
    public static final int COUNT = 7;
    public static final String[] NAMES = {
        "Abbraccia il partner", "Imbocca il partner", "Bacia il partner", "Dormite vicini",
        "Camminate insieme (blocchi)", "Cacciate mostri insieme", "Ballate insieme (sneak a ritmo)"
    };
    public static final int[] TARGET = {5, 3, 5, 1, 300, 10, 3};
    public static final int[] REWARD = {60, 60, 80, 100, 100, 120, 80};
}
