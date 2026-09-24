package com.nexauren.musicplayer;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public final class NexaurenSettingsActivity extends AppCompatActivity {
    private LinearLayout content;
    private int accent;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        accent = AppearanceStore.accent(this);
        if (accent == 0) accent = AppearanceStore.BLUE;
        getWindow().setStatusBarColor(accent);
        getWindow().setNavigationBarColor(Color.WHITE);
        build();
    }

    private void build() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(246, 249, 253));
        setContentView(shell);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(5),0,dp(4),0);
        bar.setBackgroundColor(accent);
        TextView back = text("‹", 30, Color.WHITE);
        back.setGravity(Gravity.CENTER);
        bar.addView(back, new LinearLayout.LayoutParams(dp(50), dp(56)));
        back.setOnClickListener(v -> finish());
        TextView title = text(NexaurenLanguageStore.t(this,"settings"), 18, Color.WHITE);
        title.setTypeface(null,1);
        bar.addView(title, new LinearLayout.LayoutParams(0,dp(56),1));
        shell.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14),dp(12),dp(14),dp(24));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        section("Experiência");
        row(NexaurenLanguageStore.t(this,"language"),
                NexaurenLanguageStore.displayName(NexaurenLanguageStore.get(this)),
                "文", v -> chooseLanguage());
        row(NexaurenLanguageStore.t(this,"appearance"),
                "Tema, fundo e personalização",
                "◐", v -> startActivity(new Intent(this,AppearanceSetupActivity.class).putExtra("edit",true)));
        row("Versão", NexaurenLanguageStore.t(this,"version"), "N", null);

        section("Reprodução");
        switchRow(NexaurenLanguageStore.t(this,"skip_silence"), "Remove pausas detetadas entre faixas",
                NexaurenAudioPrefs.skipSilence(this),
                checked -> {
                    NexaurenAudioPrefs.skipSilence(this, checked);
                    PlaybackService.setSkipSilence(checked);
                });
        switchRow(NexaurenLanguageStore.t(this,"mono"), "Mistura canais esquerdo/direito em mono",
                NexaurenAudioPrefs.mono(this),
                checked -> {
                    NexaurenAudioPrefs.mono(this, checked);
                    PlaybackService.setMonoMode(checked);
                });
        row(NexaurenLanguageStore.t(this,"speed"),
                String.format(java.util.Locale.ROOT,"%.2fx",NexaurenAudioPrefs.speed(this)),
                "1×", v -> chooseSpeed());
        row(NexaurenLanguageStore.t(this,"balance"),
                String.format(java.util.Locale.ROOT,"%+.0f%%",NexaurenAudioPrefs.balance(this)*100f),
                "L/R", v -> chooseBalance());

        section(NexaurenLanguageStore.t(this,"audio"));
        switchRow("Efeitos ativos", "Equalizador, graves, virtualizador e pré-amplificação",
                NexaurenAudioPrefs.effects(this),
                checked -> {
                    NexaurenAudioPrefs.effects(this, checked);
                    PlaybackService.setEffectsEnabled(checked);
                    PlaybackService.saveAudioPrefs(this);
                });
        row(NexaurenLanguageStore.t(this,"bass"), NexaurenAudioPrefs.bass(this)+"%", "♬", v -> chooseLevel("Graves", NexaurenAudioPrefs.bass(this), (value) -> {
            NexaurenAudioPrefs.bass(this,value);
            PlaybackService.setBass(value);
            PlaybackService.saveAudioPrefs(this);
        }));
        row(NexaurenLanguageStore.t(this,"virtualizer"), NexaurenAudioPrefs.virtualizer(this)+"%", "◎", v -> chooseLevel("Virtualizador", NexaurenAudioPrefs.virtualizer(this), (value) -> {
            NexaurenAudioPrefs.virtualizer(this,value);
            PlaybackService.setVirtualizer(value);
            PlaybackService.saveAudioPrefs(this);
        }));
        row(NexaurenLanguageStore.t(this,"preamp"), NexaurenAudioPrefs.preamp(this)+"%", "↗", v -> chooseLevel("Pré-amplificação", NexaurenAudioPrefs.preamp(this), (value) -> {
            NexaurenAudioPrefs.preamp(this,value);
            PlaybackService.setPreamp(value);
            PlaybackService.saveAudioPrefs(this);
        }));
        row(NexaurenLanguageStore.t(this,"reverb"),
                NexaurenAudioPrefs.reverb(this)+" • "+NexaurenAudioPrefs.reverbMix(this)+"%",
                "≈", v -> chooseReverb());
        
        section("Ferramentas");
        row("Equalizador avançado","Bandas e presets existentes","EQ",v -> {
            Intent i=new Intent(this,MainActivity.class);
            i.putExtra("page","equalizer");
            startActivity(i);
        });
        row("A-B Repeat","Repetição de segmento A-B no player","A-B",v -> Toast.makeText(this,"A-B está disponível em Agora tocando.",Toast.LENGTH_SHORT).show());
        row("Visualizador","Espectro visual existente","▥",v -> startActivity(new Intent(this,VisualizerActivity.class)));
        row("Interface clássica","Abrir a interface anterior sem a perder","↩",v -> startActivity(new Intent(this,MainActivity.class)));
    }

    private void section(String name) {
        TextView t=text(name.toUpperCase(java.util.Locale.ROOT),12,accent);
        t.setTypeface(null,1);
        t.setPadding(dp(4),dp(18),dp(4),dp(7));
        content.addView(t,new LinearLayout.LayoutParams(-1,dp(34)));
    }

    private void row(String name,String desc,String icon,View.OnClickListener click){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10),dp(7),dp(7),dp(7));
        row.setBackground(round(Color.WHITE,16));
        TextView i=text(icon,20,accent); i.setGravity(Gravity.CENTER);
        row.addView(i,new LinearLayout.LayoutParams(dp(48),dp(60)));
        LinearLayout meta=new LinearLayout(this);meta.setOrientation(LinearLayout.VERTICAL);meta.setPadding(dp(8),0,0,0);
        TextView n=text(name,14,Color.rgb(31,41,55));n.setTypeface(null,1);
        TextView d=text(desc,11,Color.rgb(107,118,133));
        meta.addView(n,new LinearLayout.LayoutParams(-1,dp(27)));
        meta.addView(d,new LinearLayout.LayoutParams(-1,dp(24)));
        row.addView(meta,new LinearLayout.LayoutParams(0,dp(60),1));
        TextView a=text("›",25,accent);a.setGravity(Gravity.CENTER);
        row.addView(a,new LinearLayout.LayoutParams(dp(34),dp(60)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(74));lp.bottomMargin=dp(8);
        content.addView(row,lp);
        if(click!=null) row.setOnClickListener(click);
    }

    private void switchRow(String name,String desc,boolean checked,java.util.function.Consumer<Boolean> action){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10),dp(7),dp(7),dp(7));
        row.setBackground(round(Color.WHITE,16));
        LinearLayout meta=new LinearLayout(this);meta.setOrientation(LinearLayout.VERTICAL);
        TextView n=text(name,14,Color.rgb(31,41,55));n.setTypeface(null,1);
        TextView d=text(desc,11,Color.rgb(107,118,133));
        meta.addView(n,new LinearLayout.LayoutParams(-1,dp(27)));meta.addView(d,new LinearLayout.LayoutParams(-1,dp(24)));
        row.addView(meta,new LinearLayout.LayoutParams(0,dp(60),1));
        Switch sw=new Switch(this);sw.setChecked(checked);row.addView(sw,new LinearLayout.LayoutParams(dp(58),dp(54)));
        sw.setOnCheckedChangeListener((button,isChecked)->action.accept(isChecked));
        content.addView(row,new LinearLayout.LayoutParams(-1,dp(74)));
    }

    private void chooseLanguage(){
        final String[] codes={"pt","en","fr","es","it"};
        final String[] names={"Português","English","Français","Español","Italiano"};
        int checked=0;
        String current=NexaurenLanguageStore.get(this);
        for(int i=0;i<codes.length;i++)if(codes[i].equals(current))checked=i;
        new android.app.AlertDialog.Builder(this)
                .setTitle(NexaurenLanguageStore.t(this,"language"))
                .setSingleChoiceItems(names,checked,(dialog,which)->{
                    NexaurenLanguageStore.set(this,codes[which]);
                    dialog.dismiss();
                    Toast.makeText(this,"Idioma aplicado.",Toast.LENGTH_SHORT).show();
                    recreate();
                }).show();
    }

    private void chooseSpeed(){
        final String[] values={"0,75×","1,00×","1,25×","1,50×","1,75×","2,00×"};
        final float[] speeds={0.75f,1f,1.25f,1.5f,1.75f,2f};
        int checked=1;
        float current=NexaurenAudioPrefs.speed(this);
        for(int i=0;i<speeds.length;i++)if(Math.abs(speeds[i]-current)<0.01f)checked=i;
        new android.app.AlertDialog.Builder(this)
                .setTitle(NexaurenLanguageStore.t(this,"speed"))
                .setSingleChoiceItems(values,checked,(dialog,which)->{
                    NexaurenAudioPrefs.speed(this,speeds[which]);
                    PlaybackService.setPlaybackSpeedPitch(speeds[which],1f);
                    dialog.dismiss();
                    recreate();
                }).show();
    }

    private void chooseBalance(){
        final SeekBar bar=new SeekBar(this);
        bar.setMax(200);
        bar.setProgress((int)(NexaurenAudioPrefs.balance(this)*100f)+100);
        new android.app.AlertDialog.Builder(this)
                .setTitle(NexaurenLanguageStore.t(this,"balance"))
                .setMessage("−100% = esquerda • 0% = centro • +100% = direita")
                .setView(bar)
                .setNegativeButton(NexaurenLanguageStore.t(this,"cancel"),null)
                .setPositiveButton(NexaurenLanguageStore.t(this,"save"),(d,w)->{
                    float value=(bar.getProgress()-100)/100f;
                    NexaurenAudioPrefs.balance(this,value);
                    PlaybackService.setBalance(value);
                    recreate();
                }).show();
    }

    private void chooseLevel(String title,int initial,java.util.function.IntConsumer callback){
        final SeekBar bar=new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(Math.max(0,Math.min(100,initial)));
        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("0% = desligado • 100% = máximo")
                .setView(bar)
                .setNegativeButton(NexaurenLanguageStore.t(this,"cancel"),null)
                .setPositiveButton(NexaurenLanguageStore.t(this,"save"),(d,w)->{
                    callback.accept(bar.getProgress());
                    recreate();
                }).show();
    }

    private void chooseReverb(){
        final String[] names={"Sinal seco","Sala","Studio","Hall"};
        int checked=0;
        String current=NexaurenAudioPrefs.reverb(this);
        for(int i=0;i<names.length;i++)if(names[i].equals(current))checked=i;
        new android.app.AlertDialog.Builder(this)
                .setTitle(NexaurenLanguageStore.t(this,"reverb"))
                .setSingleChoiceItems(names,checked,(dialog,which)->{
                    String preset=names[which];
                    NexaurenAudioPrefs.reverb(this,preset,Math.max(0,NexaurenAudioPrefs.reverbMix(this)));
                    PlaybackService.setReverb(preset,NexaurenAudioPrefs.reverbMix(this));
                    dialog.dismiss();
                    recreate();
                }).setPositiveButton("Mistura",(d,w)->chooseReverbMix()).show();
    }

    private void chooseReverbMix(){
        final SeekBar bar=new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(NexaurenAudioPrefs.reverbMix(this));
        new android.app.AlertDialog.Builder(this)
                .setTitle("Mistura Reverb")
                .setView(bar)
                .setNegativeButton(NexaurenLanguageStore.t(this,"cancel"),null)
                .setPositiveButton(NexaurenLanguageStore.t(this,"save"),(d,w)->{
                    NexaurenAudioPrefs.reverb(this,NexaurenAudioPrefs.reverb(this),bar.getProgress());
                    PlaybackService.setReverb(NexaurenAudioPrefs.reverb(this),bar.getProgress());
                    recreate();
                }).show();
    }

    private TextView text(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+0.5f);}
}
