package com.foobnix.smb;

import android.content.Context;
import android.content.SharedPreferences;

/** Simple single-server SMB settings, pre-seeded for the home LAN (dangbei-tv patch). */
public class SmbServerPrefs {

    public static class Srv {
        public String host = DEF_HOST;
        public String share = DEF_SHARE;
        public String user = DEF_USER;
        public String password = DEF_PASS;
        public int port = 445;
    }

    static final String DEF_HOST = "192.168.1.217";
    static final String DEF_SHARE = "syncthing";
    static final String DEF_USER = "alan";
    static final String DEF_PASS = "dangbei5279";

    public static Srv get(Context c) {
        Srv s = new Srv();
        if (c == null) return s;
        SharedPreferences p = c.getSharedPreferences("librera-smb", Context.MODE_PRIVATE);
        if (p.contains("host")) {
            s.host = p.getString("host", DEF_HOST);
            s.share = p.getString("share", DEF_SHARE);
            s.user = p.getString("user", DEF_USER);
            s.password = p.getString("password", DEF_PASS);
            s.port = p.getInt("port", 445);
        }
        return s;
    }

    public static void save(Context c, Srv s) {
        c.getSharedPreferences("librera-smb", Context.MODE_PRIVATE).edit()
                .putString("host", s.host)
                .putString("share", s.share)
                .putString("user", s.user)
                .putString("password", s.password)
                .putInt("port", s.port)
                .apply();
    }
}
