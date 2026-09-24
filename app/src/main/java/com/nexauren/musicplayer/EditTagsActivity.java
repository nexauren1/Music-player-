package com.nexauren.musicplayer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public final class EditTagsActivity extends AppCompatActivity {
    private static final int PICK_ART = 9001;
    private static final int WRITE_REQUEST = 9002;

    private long trackId;
    private Uri trackUri;
    private ImageView cover;
    private EditText titleField, artistField, albumField, albumArtistField, genreField;
    private EditText yearField, trackField, discField, composerField;
    private ContentValues pendingValues;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        trackId = getIntent().getLongExtra("track_id", -1L);
        if (trackId < 0) { finish(); return; }
        trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId);
        buildUi();
        loadMetadata();
        loadCover();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(AppearanceStore.accent(this));
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8,11,16));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(AppearanceStore.accent(this));
        TextView back = icon("‹", 38);
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(56)));
        back.setOnClickListener(v -> finish());
        TextView title = text("Editar etiquetas",19,Color.WHITE);
        bar.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));
        TextView save = text("GUARDAR",13,Color.WHITE);
        save.setTypeface(null,1);save.setGravity(Gravity.CENTER);
        bar.addView(save,new LinearLayout.LayoutParams(dp(92),dp(56)));
        save.setOnClickListener(v -> saveTags());
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(16),dp(16),dp(28));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout artCard=panel();
        artCard.setGravity(Gravity.CENTER);
        artCard.setOrientation(LinearLayout.VERTICAL);
        cover=new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setImageResource(R.drawable.music_placeholder);
        cover.setBackground(round(0xFF17202C,22));
        cover.setClipToOutline(true);
        artCard.addView(cover,new LinearLayout.LayoutParams(dp(210),dp(210)));
        TextView artTitle=text("Capa",16,Color.WHITE);artTitle.setTypeface(null,1);artTitle.setGravity(Gravity.CENTER);
        artCard.addView(artTitle,new LinearLayout.LayoutParams(-1,dp(30)));
        TextView choose=button("Escolher capa");
        artCard.addView(choose,new LinearLayout.LayoutParams(-1,dp(46)));
        choose.setOnClickListener(v->pickArtwork());
        TextView remove=text("Remover capa personalizada",12,0xFF9DA6B0);remove.setGravity(Gravity.CENTER);
        artCard.addView(remove,new LinearLayout.LayoutParams(-1,dp(28)));
        remove.setOnClickListener(v->{ArtworkStore.clear(this,trackId);cover.setImageResource(R.drawable.music_placeholder);});
        body.addView(artCard,new LinearLayout.LayoutParams(-1,dp(336)));

        body.addView(section("Informações principais"));
        titleField=field("Título");
        artistField=field("Artista");
        albumField=field("Álbum");
        albumArtistField=field("Artista do álbum");
        genreField=field("Género");
        body.addView(titleField);body.addView(artistField);body.addView(albumField);body.addView(albumArtistField);body.addView(genreField);

        body.addView(section("Informações avançadas"));
        yearField=field("Ano");
        trackField=field("Faixa");
        discField=field("Disco");
        composerField=field("Compositor");
        body.addView(yearField);body.addView(trackField);body.addView(discField);body.addView(composerField);

        TextView note=text("As etiquetas de texto podem ser gravadas no MediaStore quando o provedor do Android permitir. A capa personalizada é guardada pelo Nexauren.",12,0xFF9DA6B0);
        note.setPadding(dp(6),dp(10),dp(6),0);
        body.addView(note,new LinearLayout.LayoutParams(-1,dp(78)));
        SystemBarInsets.apply(root);
        setContentView(root);
    }

    private void loadMetadata() {
        if (ContextCompat.checkSelfPermission(this, Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return;
        String[] p={MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ARTIST,MediaStore.Audio.Media.YEAR,MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.COMPOSER};
        try(android.database.Cursor c=getContentResolver().query(trackUri,p,null,null,null)){
            if(c!=null&&c.moveToFirst()){
                set(titleField,c,"title");set(artistField,c,"artist");set(albumField,c,"album");
                set(albumArtistField,c,"albumArtist");set(yearField,c,"year");set(trackField,c,"track");set(composerField,c,"composer");
                genreField.setText(TagStore.get(this,trackId,"genre",""));
                discField.setText(TagStore.get(this,trackId,"disc",""));
            }
        }catch(Exception ignored){}
        titleField.setText(TagStore.get(this,trackId,"title",titleField.getText().toString()));
        artistField.setText(TagStore.get(this,trackId,"artist",artistField.getText().toString()));
        albumField.setText(TagStore.get(this,trackId,"album",albumField.getText().toString()));
        albumArtistField.setText(TagStore.get(this,trackId,"albumArtist",albumArtistField.getText().toString()));
        yearField.setText(TagStore.get(this,trackId,"year",yearField.getText().toString()));
        trackField.setText(TagStore.get(this,trackId,"track",trackField.getText().toString()));
        composerField.setText(TagStore.get(this,trackId,"composer",composerField.getText().toString()));
    }

    private void set(EditText field,android.database.Cursor c,String key){
        int i=c.getColumnIndex(key);
        if(i>=0&&!c.isNull(i))field.setText(c.getString(i));
    }

    private void loadCover() {
        if (ArtworkStore.exists(this,trackId)) {
            Bitmap b=BitmapFactory.decodeFile(ArtworkStore.file(this,trackId).getAbsolutePath());
            if(b!=null)cover.setImageBitmap(b);
            return;
        }
        new Thread(()->{
            Bitmap b=null;MediaMetadataRetriever r=new MediaMetadataRetriever();
            try{r.setDataSource(this,trackUri);byte[]data=r.getEmbeddedPicture();if(data!=null)b=BitmapFactory.decodeByteArray(data,0,data.length);}
            catch(Exception ignored){}finally{try{r.release();}catch(Exception ignored){}}
            Bitmap result=b;runOnUiThread(()->{if(result!=null){cover.clearColorFilter();cover.setImageBitmap(result);}});
        }).start();
    }

    private void pickArtwork() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_ART);
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==PICK_ART&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
                if(in!=null&&ArtworkStore.save(this,trackId,in)) loadCover();
                else Toast.makeText(this,"Não foi possível guardar a capa.",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Não foi possível ler a imagem.",Toast.LENGTH_SHORT).show();}
        }else if(req==WRITE_REQUEST&&result==RESULT_OK){writePending();}
    }

    private void saveTags(){
        String title=titleField.getText().toString().trim();
        String artist=artistField.getText().toString().trim();
        String album=albumField.getText().toString().trim();
        String albumArtist=albumArtistField.getText().toString().trim();
        String genre=genreField.getText().toString().trim();
        String year=yearField.getText().toString().trim();
        String track=trackField.getText().toString().trim();
        String disc=discField.getText().toString().trim();
        String composer=composerField.getText().toString().trim();

        TagStore.save(this,trackId,title,artist,album,albumArtist,genre,year,track,disc,composer);

        ContentValues values=new ContentValues();
        values.put(MediaStore.Audio.Media.TITLE,title);
        values.put(MediaStore.Audio.Media.ARTIST,artist);
        values.put(MediaStore.Audio.Media.ALBUM,album);
        if(Build.VERSION.SDK_INT>=29)values.put(MediaStore.Audio.Media.ALBUM_ARTIST,albumArtist);
        values.put(MediaStore.Audio.Media.YEAR,parseInt(year));
        values.put(MediaStore.Audio.Media.TRACK,parseInt(track));
        values.put(MediaStore.Audio.Media.COMPOSER,composer);
        pendingValues=values;

        if(Build.VERSION.SDK_INT>=30){
            try{
                android.app.PendingIntent request=MediaStore.createWriteRequest(getContentResolver(),java.util.Collections.singletonList(trackUri));
                startIntentSenderForResult(request.getIntentSender(),WRITE_REQUEST,null,0,0,0,null);
                return;
            }catch(Exception ignored){}
        }
        writePending();
    }

    private int parseInt(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}

    private void writePending(){
        try{
            if(pendingValues!=null)getContentResolver().update(trackUri,pendingValues,null,null);
            Toast.makeText(this,"Etiquetas guardadas.",Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);finish();
        }catch(Exception e){Toast.makeText(this,"O Android não permitiu alterar este ficheiro.",Toast.LENGTH_LONG).show();}
        pendingValues=null;
    }

    private LinearLayout panel(){LinearLayout p=new LinearLayout(this);p.setPadding(dp(12),dp(12),dp(12),dp(12));p.setBackground(round(0xFF121925,22));return p;}
    private TextView section(String s){TextView t=text(s,14,0xFF2D9DEB);t.setTypeface(null,1);t.setPadding(dp(4),dp(18),dp(4),dp(6));return t;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(Color.WHITE);e.setHintTextColor(0xFF7F8A97);e.setSingleLine(true);e.setTextSize(15);e.setPadding(dp(8),0,dp(8),0);e.setBackground(round(0xFF151C27,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(5),0,dp(5));e.setLayoutParams(p);return e;}
    private TextView button(String s){TextView t=text(s,14,Color.WHITE);t.setTypeface(null,1);t.setGravity(Gravity.CENTER);t.setBackground(round(0xFF2D9DEB,22));return t;}
    private TextView icon(String s,int z){TextView t=text(s,z,Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
    private TextView text(String s,float z,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private android.graphics.drawable.GradientDrawable round(int c,int r){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
