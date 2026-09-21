package com.nexauren.musicplayer;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppearanceStore {
    private static final String PREFS="nexauren_appearance";
    private static final String SETUP="setup_done";
    private static final String ACCENT="accent";
    private static final String BG="background";
    private static final String PHOTO_URI="photo_uri";

    public static final int BLUE=0xFF2196F3;
    public static final int PURPLE=0xFF8B5CF6;
    public static final int GREEN=0xFF22C55E;
    public static final int ORANGE=0xFFF97316;
    public static final int PINK=0xFFEC4899;
    public static final int CYAN=0xFF06B6D4;
    public static final int RED=0xFFEF4444;
    public static final int GOLD=0xFFF59E0B;

    public static final String BG_PLAIN="plain";
    public static final String BG_AURORA="aurora";
    public static final String BG_WAVES="waves";
    public static final String BG_GEOMETRY="geometry";
    public static final String BG_STARS="stars";
    public static final String BG_PHOTO="photo";

    private AppearanceStore(){}

    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public static boolean isSetupDone(Context c){return prefs(c).getBoolean(SETUP,false);}
    public static void markSetupDone(Context c){prefs(c).edit().putBoolean(SETUP,true).apply();}
    public static int accent(Context c){return prefs(c).getInt(ACCENT,BLUE);}
    public static void setAccent(Context c,int color){prefs(c).edit().putInt(ACCENT,color).apply();}
    public static String background(Context c){return prefs(c).getString(BG,BG_PLAIN);}
    public static void setBackground(Context c,String value){prefs(c).edit().putString(BG,value).apply();}
    public static String photoUri(Context c){return prefs(c).getString(PHOTO_URI,null);}
    public static void setPhotoUri(Context c,String uri){prefs(c).edit().putString(PHOTO_URI,uri).apply();}
}
