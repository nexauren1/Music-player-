package com.nexauren.musicplayer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

public final class PlayStatsStore {
    private static final String PREFS="nexauren_play_stats";
    private PlayStatsStore(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static void increment(Context c,long id){
        String key="p:"+id;
        prefs(c).edit().putInt(key,prefs(c).getInt(key,0)+1).apply();
    }
    public static int count(Context c,long id){return prefs(c).getInt("p:"+id,0);}
    public static ArrayList<Long> top(Context c,int limit){
        ArrayList<Map.Entry<String,?>> entries=new ArrayList<>(prefs(c).getAll().entrySet());
        entries.removeIf(e->!e.getKey().startsWith("p:"));
        entries.sort((a,b)->Integer.compare(toInt(b.getValue()),toInt(a.getValue())));
        ArrayList<Long> ids=new ArrayList<>();
        for(Map.Entry<String,?> e:entries){
            try{ids.add(Long.parseLong(e.getKey().substring(2)));}catch(Exception ignored){}
            if(ids.size()>=limit)break;
        }
        return ids;
    }
    private static int toInt(Object v){return v instanceof Number?((Number)v).intValue():0;}
}
