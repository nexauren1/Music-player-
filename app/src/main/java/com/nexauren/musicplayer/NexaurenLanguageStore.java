package com.nexauren.musicplayer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

public final class NexaurenLanguageStore {
    private static final String PREFS = "nexauren_2_language";
    private static final String KEY = "language";
    private static final String DEFAULT = "pt";

    private NexaurenLanguageStore() {}

    public static String get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, DEFAULT);
    }

    public static void set(Context context, String language) {
        String value = "en".equals(language) || "fr".equals(language)
                || "es".equals(language) || "it".equals(language) ? language : "pt";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, value).apply();
    }

    public static String displayName(String language) {
        if ("en".equals(language)) return "English";
        if ("fr".equals(language)) return "Français";
        if ("es".equals(language)) return "Español";
        if ("it".equals(language)) return "Italiano";
        return "Português";
    }

    public static String t(Context context, String key) {
        String lang = get(context);
        switch (key) {
            case "home":
                return tr(lang, "Início", "Home", "Accueil", "Inicio", "Home");
            case "library":
                return tr(lang, "Biblioteca", "Library", "Bibliothèque", "Biblioteca", "Libreria");
            case "playlists":
                return tr(lang, "Playlists", "Playlists", "Playlists", "Playlists", "Playlist");
            case "favorites":
                return tr(lang, "Favoritos", "Favorites", "Favoris", "Favoritos", "Preferiti");
            case "recent":
                return tr(lang, "Recentes", "Recent", "Récents", "Recientes", "Recenti");
            case "most_played":
                return tr(lang, "Mais tocadas", "Most played", "Les plus écoutées", "Más escuchadas", "Più ascoltate");
            case "smart":
                return tr(lang, "Para você", "For you", "Pour vous", "Para ti", "Per te");
            case "random":
                return tr(lang, "Aleatório", "Shuffle all", "Aléatoire", "Aleatorio", "Casuale");
            case "settings":
                return tr(lang, "Definições", "Settings", "Réglages", "Ajustes", "Impostazioni");
            case "now_playing":
                return tr(lang, "Agora tocando", "Now playing", "Lecture en cours", "Reproduciendo", "In riproduzione");
            case "search":
                return tr(lang, "Pesquisar biblioteca", "Search library", "Rechercher dans la bibliothèque", "Buscar en la biblioteca", "Cerca nella libreria");
            case "audio":
                return tr(lang, "Efeitos de áudio", "Audio effects", "Effets audio", "Efectos de audio", "Effetti audio");
            case "language":
                return tr(lang, "Idioma", "Language", "Langue", "Idioma", "Lingua");
            case "appearance":
                return tr(lang, "Aparência", "Appearance", "Apparence", "Apariencia", "Aspetto");
            case "version":
                return tr(lang, "Versão 2.0.0", "Version 2.0.0", "Version 2.0.0", "Versión 2.0.0", "Versione 2.0.0");
            case "skip_silence":
                return tr(lang, "Saltar silêncio", "Skip silence", "Ignorer les silences", "Saltar silencios", "Salta silenzi");
            case "mono":
                return tr(lang, "Modo mono", "Mono mode", "Mode mono", "Modo mono", "Modalità mono");
            case "balance":
                return tr(lang, "Balanço L/R", "L/R balance", "Balance G/D", "Balance L/R", "Bilanciamento L/R");
            case "bass":
                return tr(lang, "Graves", "Bass", "Graves", "Graves", "Bassi");
            case "virtualizer":
                return tr(lang, "Virtualizador", "Virtualizer", "Virtualiseur", "Virtualizador", "Virtualizzatore");
            case "preamp":
                return tr(lang, "Pré-amplificação", "Preamp", "Préampli", "Preamplificador", "Preamp");
            case "reverb":
                return tr(lang, "Reverb", "Reverb", "Réverbération", "Reverb", "Riverbero");
            case "speed":
                return tr(lang, "Velocidade", "Playback speed", "Vitesse", "Velocidad", "Velocità");
            case "save":
                return tr(lang, "Guardar", "Save", "Enregistrer", "Guardar", "Salva");
            case "cancel":
                return tr(lang, "Cancelar", "Cancel", "Annuler", "Cancelar", "Annulla");
            default:
                return key;
        }
    }

    private static String tr(String lang, String pt, String en, String fr, String es, String it) {
        if ("en".equals(lang)) return en;
        if ("fr".equals(lang)) return fr;
        if ("es".equals(lang)) return es;
        if ("it".equals(lang)) return it;
        return pt;
    }
}
