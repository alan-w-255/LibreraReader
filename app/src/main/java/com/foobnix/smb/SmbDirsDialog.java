package com.foobnix.smb;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.pdf.info.R;
import com.foobnix.ui2.TvFocus;

import java.util.ArrayList;
import java.util.List;

/** Settings dialog: manage the SMB directories that feed the cloud shelf (dangbei-tv patch). */
public class SmbDirsDialog {

    public static void show(final Activity a) {
        final List<SmbServerPrefs.Srv> servers = new ArrayList<SmbServerPrefs.Srv>(SmbServerPrefs.all(a));

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Dips.dpToPx(16);
        box.setPadding(pad, pad / 2, pad, pad / 2);

        TextView hint = new TextView(a);
        hint.setText(R.string.smb_sync_shelf_sub);
        hint.setTextSize(13);
        box.addView(hint);

        for (int i = 0; i < servers.size(); i++) {
            final int idx = i;
            TextView row = new TextView(a);
            row.setText(servers.get(i).label());
            row.setTextSize(16);
            row.setPadding(0, pad, 0, pad / 2);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnFocusChangeListener(TvFocus.COLOR_BUTTON);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    edit(a, servers, idx);
                }
            });
            box.addView(row);
        }

        TextView add = new TextView(a);
        add.setText("+ " + a.getString(R.string.smb_add_dir));
        add.setTextSize(16);
        add.setPadding(0, pad / 2, 0, pad / 2);
        add.setClickable(true);
        add.setFocusable(true);
        add.setOnFocusChangeListener(TvFocus.COLOR_BUTTON);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(a, servers, -1);
            }
        });
        box.addView(add);

        TextView sync = new TextView(a);
        sync.setText(a.getString(R.string.smb_sync_now));
        sync.setTextSize(16);
        sync.setPadding(0, pad / 2, 0, 0);
        sync.setClickable(true);
        sync.setFocusable(true);
        sync.setOnFocusChangeListener(TvFocus.COLOR_BUTTON);
        sync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncNow(a);
            }
        });
        box.addView(sync);

        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);

        new AlertDialog.Builder(a)
                .setTitle(R.string.smb_dirs_title)
                .setView(scroll)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    static void edit(final Activity a, final List<SmbServerPrefs.Srv> servers, final int index) {
        final SmbServerPrefs.Srv s = index < 0 ? new SmbServerPrefs.Srv() : servers.get(index);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Dips.dpToPx(16);
        box.setPadding(pad, pad / 2, pad, pad / 2);
        final EditText host = field(a, box, "Host / IP", s.host);
        final EditText share = field(a, box, "Share", s.share);
        final EditText path = field(a, box, a.getString(R.string.smb_path_hint), s.path);
        final EditText user = field(a, box, "User", s.user);
        final EditText pass = field(a, box, "Password", s.password);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(index < 0 ? R.string.smb_add_dir : R.string.smb_dirs_title)
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    s.host = host.getText().toString().trim();
                    s.share = share.getText().toString().trim();
                    s.path = trimPath(path.getText().toString());
                    s.user = user.getText().toString().trim();
                    s.password = pass.getText().toString();
                    if (s.host.isEmpty() || s.share.isEmpty()) {
                        return;
                    }
                    if (index < 0) {
                        servers.add(s);
                    }
                    SmbServerPrefs.saveAll(a, servers);
                    show(a);
                })
                .setNegativeButton(R.string.cancel, null);
        if (index >= 0) {
            b.setNeutralButton(R.string.smb_delete, (d, w) -> {
                servers.remove(index);
                SmbServerPrefs.saveAll(a, servers);
                show(a);
            });
        }
        b.show();
    }

    static String trimPath(String raw) {
        String p = raw == null ? "" : raw.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    static EditText field(Activity a, LinearLayout box, String hint, String value) {
        EditText et = new EditText(a);
        et.setHint(hint);
        et.setText(value);
        et.setTextSize(15);
        box.addView(et);
        return et;
    }

    public static void syncNow(final Activity a) {
        Toast.makeText(a, R.string.please_wait, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                int[] res = {0, 0};
                Exception err = null;
                try {
                    res = SmbShelf.syncToShelf(a);
                } catch (Exception e) {
                    err = e;
                    LOG.e(e);
                }
                final int added = res[0], local = res[1];
                final Exception ferr = err;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ferr != null) {
                            Toast.makeText(a, R.string.loading_error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(a, a.getString(R.string.smb_sync_result, added, local), Toast.LENGTH_LONG).show();
                        org.greenrobot.eventbus.EventBus.getDefault().post(
                                new com.foobnix.pdf.search.activity.msg.UpdateAllFragments());
                    }
                });
            }
        }).start();
    }
}
