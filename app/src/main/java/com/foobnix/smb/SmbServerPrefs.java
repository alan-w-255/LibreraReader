package com.foobnix.smb;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-server SMB settings (dangbei-tv patch). JSON list in SharedPreferences;
 * migrates the old single-server keys on first read.
 */
public class SmbServerPrefs {

    public static class Srv {
        public String host = "";
        public String share = "";
        public String path = "";
        public String user = "";
        public String password = "";
        public int port = 445;

        public String prefix() {
            return "smb://" + host + "/" + share;
        }

        public String label() {
            return prefix() + (path == null || path.isEmpty() ? "" : "/" + path);
        }
    }

    static final String DEF_HOST = "192.168.1.217";
    static final String DEF_SHARE = "syncthing";
    static final String DEF_PATH = "zlibrary";
    static final String DEF_USER = "alan";
    static final String DEF_PASS = "dangbei5279";

    static List<Srv> defaults() {
        Srv s = new Srv();
        s.host = DEF_HOST;
        s.share = DEF_SHARE;
        s.path = DEF_PATH;
        s.user = DEF_USER;
        s.password = DEF_PASS;
        List<Srv> l = new ArrayList<Srv>();
        l.add(s);
        return l;
    }

    public static List<Srv> all(Context c) {
        if (c == null) {
            return defaults();
        }
        SharedPreferences p = c.getSharedPreferences("librera-smb", Context.MODE_PRIVATE);
        String json = p.getString("servers", null);
        if (json != null) {
            try {
                JSONArray a = new JSONArray(json);
                List<Srv> out = new ArrayList<Srv>();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Srv s = new Srv();
                    s.host = o.optString("host");
                    s.share = o.optString("share");
                    s.path = o.optString("path");
                    s.user = o.optString("user");
                    s.password = o.optString("password");
                    s.port = o.optInt("port", 445);
                    if (!s.host.isEmpty() && !s.share.isEmpty()) {
                        out.add(s);
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            } catch (Exception ignore) {
            }
        }
        if (p.contains("host")) {
            Srv s = new Srv();
            s.host = p.getString("host", DEF_HOST);
            s.share = p.getString("share", DEF_SHARE);
            s.path = DEF_PATH;
            s.user = p.getString("user", DEF_USER);
            s.password = p.getString("password", DEF_PASS);
            List<Srv> l = new ArrayList<Srv>();
            l.add(s);
            saveAll(c, l);
            return l;
        }
        return defaults();
    }

    public static void saveAll(Context c, List<Srv> list) {
        if (c == null) {
            return;
        }
        JSONArray a = new JSONArray();
        for (Srv s : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("host", s.host);
                o.put("share", s.share);
                o.put("path", s.path == null ? "" : s.path);
                o.put("user", s.user);
                o.put("password", s.password);
                o.put("port", s.port);
                a.put(o);
            } catch (Exception ignore) {
            }
        }
        c.getSharedPreferences("librera-smb", Context.MODE_PRIVATE).edit()
                .putString("servers", a.toString()).apply();
    }
}
