package com.nexauren.musicplayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public final class AppearanceSetupActivity extends AppCompatActivity {
    private static final int PHOTO_REQUEST=7951;
    private FrameLayout root;
    private FrameLayout preview;
    private TextView continueButton;
    private int selectedAccent;
    private String selectedBackground;
    private boolean editing;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        editing=getIntent().getBooleanExtra("edit",false);
        if(!editing&&AppearanceStore.isSetupDone(this)){openMain();return;}
        selectedAccent=AppearanceStore.accent(this);
        selectedBackground=AppearanceStore.background(this);
        build();
    }

    private void build(){
        getWindow().setStatusBarColor(selectedAccent);
        getWindow().setNavigationBarColor(Color.BLACK);

        root=new FrameLayout(this);
        root.setBackground(new AppearanceBackgroundDrawable(selectedAccent,selectedBackground));
        setContentView(root);

        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(20),dp(18),dp(28));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        TextView brand=text("NEXAUREN",28,Color.WHITE);brand.setTypeface(null,1);body.addView(brand,new LinearLayout.LayoutParams(-1,dp(38)));
        TextView sub=text(editing?"Personalizar aparência":"Vamos personalizar o seu player",16,0xFFE2E8F0);body.addView(sub,new LinearLayout.LayoutParams(-1,dp(30)));
        TextView intro=text("Tema e fundo são independentes. Escolha a cor da interface e depois o estilo visual do fundo.",13,0xFFB9C2CF);
        body.addView(intro,new LinearLayout.LayoutParams(-1,dp(54)));

        section("1  TEMA","A cor da interface, botões, barras e destaques.");
        HorizontalScrollView accents=new HorizontalScrollView(this);accents.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=new LinearLayout(this);chips.setGravity(Gravity.CENTER_VERTICAL);
        int[] colors={AppearanceStore.BLUE,AppearanceStore.PURPLE,AppearanceStore.GREEN,AppearanceStore.ORANGE,AppearanceStore.PINK,AppearanceStore.CYAN,AppearanceStore.RED,AppearanceStore.GOLD};
        String[] names={"Azul","Roxo","Verde","Laranja","Rosa","Ciano","Vermelho","Dourado"};
        for(int i=0;i<colors.length;i++){
            final int color=colors[i];
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);
            TextView dot=text("●",32,color);dot.setGravity(Gravity.CENTER);item.addView(dot,new LinearLayout.LayoutParams(dp(64),dp(48)));
            TextView n=text(names[i],11,Color.WHITE);n.setGravity(Gravity.CENTER);item.addView(n,new LinearLayout.LayoutParams(dp(64),dp(22)));
            item.setOnClickListener(v->{selectedAccent=color;refresh();});
            chips.addView(item,new LinearLayout.LayoutParams(dp(74),dp(76)));
        }
        accents.addView(chips,new HorizontalScrollView.LayoutParams(-2,dp(82)));
        body.addView(accents,new LinearLayout.LayoutParams(-1,dp(84)));

        section("2  FUNDO","Desenhos, ambientes e uma foto sua. O fundo não muda a cor do tema.");
        HorizontalScrollView backgrounds=new HorizontalScrollView(this);backgrounds.setHorizontalScrollBarEnabled(false);
        LinearLayout bgRow=new LinearLayout(this);bgRow.setGravity(Gravity.CENTER_VERTICAL);
        addBg(bgRow,"Liso",AppearanceStore.BG_PLAIN);
        addBg(bgRow,"Aurora",AppearanceStore.BG_AURORA);
        addBg(bgRow,"Ondas",AppearanceStore.BG_WAVES);
        addBg(bgRow,"Geometria",AppearanceStore.BG_GEOMETRY);
        addBg(bgRow,"Estrelas",AppearanceStore.BG_STARS);
        LinearLayout photo=new LinearLayout(this);photo.setOrientation(LinearLayout.VERTICAL);photo.setGravity(Gravity.CENTER);
        TextView photoIcon=text("▧",30,Color.WHITE);photoIcon.setGravity(Gravity.CENTER);photo.addView(photoIcon,new LinearLayout.LayoutParams(dp(86),dp(66)));
        TextView photoLabel=text("Foto",11,Color.WHITE);photoLabel.setGravity(Gravity.CENTER);photo.addView(photoLabel,new LinearLayout.LayoutParams(dp(86),dp(20)));
        photo.setOnClickListener(v->pickPhoto());
        bgRow.addView(photo,new LinearLayout.LayoutParams(dp(92),dp(92)));
        backgrounds.addView(bgRow,new HorizontalScrollView.LayoutParams(-2,dp(96)));
        body.addView(backgrounds,new LinearLayout.LayoutParams(-1,dp(98)));

        preview=new FrameLayout(this);
        preview.setPadding(dp(12),dp(12),dp(12),dp(12));
        preview.addView(previewText(),new FrameLayout.LayoutParams(-1,dp(110),Gravity.CENTER));
        body.addView(preview,new LinearLayout.LayoutParams(-1,dp(136)));

        continueButton=text(editing?"APLICAR":"CONTINUAR",15,Color.WHITE);
        continueButton.setTypeface(null,1);continueButton.setGravity(Gravity.CENTER);continueButton.setBackground(round(selectedAccent,28));
        body.addView(continueButton,new LinearLayout.LayoutParams(-1,dp(54)));
        continueButton.setOnClickListener(v->saveAndClose());

        if(editing){
            TextView reset=text("Restaurar padrão",12,0xFFCFD6DE);reset.setGravity(Gravity.CENTER);
            body.addView(reset,new LinearLayout.LayoutParams(-1,dp(44)));
            reset.setOnClickListener(v->{selectedAccent=AppearanceStore.BLUE;selectedBackground=AppearanceStore.BG_PLAIN;AppearanceStore.setPhotoUri(this,null);refresh();});
        }
        refresh();
    }

    private void section(String title,String subtitle){
        TextView a=text(title,13,Color.WHITE);a.setTypeface(null,1);a.setPadding(0,dp(10),0,0);
        ((LinearLayout)((ScrollView)root.getChildAt(0)).getChildAt(0)).addView(a,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView b=text(subtitle,11,0xFFB9C2CF);
        ((LinearLayout)((ScrollView)root.getChildAt(0)).getChildAt(0)).addView(b,new LinearLayout.LayoutParams(-1,dp(34)));
    }

    private void addBg(LinearLayout parent,String label,String kind){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);
        FrameLayout sw=new FrameLayout(this);sw.setBackground(new AppearanceBackgroundDrawable(selectedAccent,kind));
        sw.addView(text(label,11,Color.WHITE),new FrameLayout.LayoutParams(-1,dp(34),Gravity.BOTTOM));
        item.addView(sw,new LinearLayout.LayoutParams(dp(92),dp(68)));
        item.setOnClickListener(v->{selectedBackground=kind;refresh();});
        parent.addView(item,new LinearLayout.LayoutParams(dp(98),dp(92)));
    }

    private TextView previewText(){
        TextView t=text("Nexauren Player",19,Color.WHITE);t.setTypeface(null,1);t.setGravity(Gravity.CENTER);return t;
    }

    private void refresh(){
        getWindow().setStatusBarColor(selectedAccent);
        root.setBackground(new AppearanceBackgroundDrawable(selectedAccent,selectedBackground));
        if(preview!=null){
            preview.setBackground(new AppearanceBackgroundDrawable(selectedAccent,selectedBackground));
            if(preview.getChildCount()>0){
                TextView t=(TextView)preview.getChildAt(0);t.setText("Pré-visualização • "+(selectedBackground.equals(AppearanceStore.BG_PLAIN)?"fundo liso":selectedBackground));
            }
        }
        if(continueButton!=null)continueButton.setBackground(round(selectedAccent,28));
    }

    private void pickPhoto(){
        try{
            Intent i;
            if(Build.VERSION.SDK_INT>=33)i=new Intent(MediaStore.ACTION_PICK_IMAGES);
            else{i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);}
            if(Build.VERSION.SDK_INT>=33){i.setType("image/*");}
            startActivityForResult(i,PHOTO_REQUEST);
        }catch(Exception e){
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PHOTO_REQUEST);
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PHOTO_REQUEST&&resultCode==Activity.RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}
            AppearanceStore.setPhotoUri(this,uri.toString());
            selectedBackground=AppearanceStore.BG_PHOTO;
            Toast.makeText(this,"Foto selecionada como fundo.",Toast.LENGTH_SHORT).show();
            refresh();
        }
    }

    private void saveAndClose(){
        AppearanceStore.setAccent(this,selectedAccent);
        AppearanceStore.setBackground(this,selectedBackground);
        AppearanceStore.markSetupDone(this);
        if(editing){setResult(RESULT_OK);finish();}else openMain();
    }

    private void openMain(){
        startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
