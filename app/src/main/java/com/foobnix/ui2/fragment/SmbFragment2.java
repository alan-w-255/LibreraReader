package com.foobnix.ui2.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import androidx.core.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.dao2.FileMeta;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.smb.SmbHelper;
import com.foobnix.smb.SmbShelf;
import com.foobnix.smb.SmbServerPrefs;
import com.foobnix.sys.TempHolder;
import com.foobnix.ui2.AppDB;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SMB network books browser (dangbei-tv patch). D-pad friendly list: browse share, download, open. */
public class SmbFragment2 extends UIFragment<Object> {

    public static final Pair<Integer, Integer> PAIR = new Pair<Integer, Integer>(R.string.smb_network_library, R.drawable.glyphicons_417_globe);

    private RecyclerView recyclerView;
    private RowAdapter adapter;
    private TextView pathView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private String currentPath = "";

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_smb, container, false);
        recyclerView = v.findViewById(R.id.recyclerView);
        pathView = v.findViewById(R.id.smbPath);
        View onSettings = v.findViewById(R.id.onSmbSettings);
        adapter = new RowAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        if (onSettings != null) {
            onSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v1) {
                    showSettings();
                }
            });
        }
        reload();
        return v;
    }

    @Override
    public Pair<Integer, Integer> getNameAndIconRes() {
        return PAIR;
    }

    @Override
    public void notifyFragment() {
        reload();
    }

    @Override
    public void resetFragment() {
        currentPath = "";
        reload();
    }

    @Override
    public boolean isBackPressed() {
        if (currentPath == null || currentPath.isEmpty()) {
            return false;
        }
        int i = currentPath.lastIndexOf('/');
        currentPath = i < 0 ? "" : currentPath.substring(0, i);
        reload();
        return true;
    }

    private String child(String name) {
        return currentPath == null || currentPath.isEmpty() ? name : currentPath + "/" + name;
    }

    private void reload() {
        final SmbServerPrefs.Srv s = SmbServerPrefs.get(getContext());
        pathView.setText(String.format(Locale.US, "smb://%s/%s%s", s.host, s.share,
                currentPath == null || currentPath.isEmpty() ? "" : "/" + currentPath));
        adapter.setRows(Arrays.asList(
                new SmbRow(R.drawable.glyphicons_417_globe, "smb://" + s.host + "/" + s.share, "", null)));
        final String path = currentPath == null ? "" : currentPath;
        exec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<SmbHelper.Entry> entries = SmbHelper.list(s, path);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            showEntries(entries);
                        }
                    });
                } catch (final Exception e) {
                    LOG.e(e);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            adapter.setRows(Arrays.asList(new SmbRow(R.drawable.glyphicons_298_circle_empty,
                                    getString(R.string.loading_error), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), null)));
                        }
                    });
                }
            }
        });
    }

    private void showEntries(List<SmbHelper.Entry> entries) {
        List<SmbHelper.Entry> sorted = new ArrayList<SmbHelper.Entry>(entries);
        Collections.sort(sorted, new Comparator<SmbHelper.Entry>() {
            @Override
            public int compare(SmbHelper.Entry a, SmbHelper.Entry b) {
                if (a.dir != b.dir) return a.dir ? -1 : 1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        List<SmbRow> rows = new ArrayList<SmbRow>();
        if (currentPath == null || currentPath.isEmpty()) {
            rows.add(new SmbRow(R.drawable.glyphicons_589_book_open,
                    getString(R.string.smb_sync_shelf), getString(R.string.smb_sync_shelf_sub),
                    new Runnable() {
                @Override
                public void run() {
                    syncShelf();
                }
            }));
        }
        for (SmbHelper.Entry e : sorted) {
            if (e.dir) {
                final String name = e.name;
                rows.add(new SmbRow(R.drawable.glyphicons_145_folder_open, name, "", new Runnable() {
                    @Override
                    public void run() {
                        currentPath = child(name);
                        reload();
                    }
                }));
            } else {
                rows.add(fileRow(e.name, e.size));
            }
        }
        if (rows.isEmpty()) {
            rows.add(new SmbRow(R.drawable.glyphicons_298_circle_empty, "(empty)", "", null));
        }
        adapter.setRows(rows);
    }

    private SmbRow fileRow(final String name, final long size) {
        String ext = ExtUtils.getFileExtension(name);
        boolean isBook = SmbShelf.BOOK_EXTS.contains(ext == null ? "" : ext.toLowerCase(Locale.US));
        return new SmbRow(isBook ? R.drawable.glyphicons_589_book_open : R.drawable.my_glyphicons_114_paragraph_justify,
                name, human(size), new Runnable() {
                    @Override
                    public void run() {
                        downloadAndOpen(name, size);
                    }
                });
    }

    private boolean syncing = false;

    private void syncShelf() {
        LOG.d("SmbSync", "clicked, syncing=", syncing);
        if (syncing) {
            return;
        }
        final SmbServerPrefs.Srv s = SmbServerPrefs.get(getContext());
        syncing = true;
        adapter.setSubtitle(getString(R.string.smb_sync_shelf), "...");
        exec.execute(new Runnable() {
            @Override
            public void run() {
                int[] res = {0, 0};
                Exception err = null;
                try {
                    LOG.d("SmbSync", "scan start");
                    res = com.foobnix.smb.SmbShelf.syncToShelf(getContext(), "");
                    LOG.d("SmbSync", "scan done", res[0], res[1]);
                } catch (Throwable e) {
                    err = (Exception) e;
                    LOG.e(e, "SmbSync fail");
                }
                final int added = res[0], local = res[1];
                final Exception ferr = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        syncing = false;
                        if (ferr != null) {
                            adapter.setSubtitle(getString(R.string.smb_sync_shelf), getString(R.string.loading_error));
                            Toast.makeText(getContext(), R.string.loading_error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        adapter.setSubtitle(getString(R.string.smb_sync_shelf),
                                getString(R.string.smb_sync_result, added, local));
                        Toast.makeText(getContext(), getString(R.string.smb_sync_result, added, local), Toast.LENGTH_LONG).show();
                        org.greenrobot.eventbus.EventBus.getDefault().post(
                                new com.foobnix.pdf.search.activity.msg.UpdateAllFragments());
                    }
                });
            }
        });
    }

    private void downloadAndOpen(final String name, final long total) {
        final SmbServerPrefs.Srv s = SmbServerPrefs.get(getContext());
        final File dir = new File(Environment.getExternalStorageDirectory(), "Books");
        if (!dir.exists()) dir.mkdirs();
        final File target = new File(dir, TxtUtils.fixFileName(name));
        if (target.exists() && target.length() == total && total > 0) {
            openLocal(target, name);
            return;
        }
        final String remote = child(name);
        adapter.setSubtitle(name, getString(R.string.download) + " ... 0%");
        exec.execute(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    SmbHelper.download(s, remote, target, total, new SmbHelper.Progress() {
                        @Override
                        public void onProgress(final long done, final long t) {
                            if (t <= 0) return;
                            final int pct = (int) (done * 100 / t);
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    adapter.setSubtitle(name, getString(R.string.download) + " ... " + pct + "%");
                                }
                            });
                        }
                    });
                    ok = target.exists() && target.length() > 0;
                } catch (Exception e) {
                    LOG.e(e);
                }
                final boolean finalOk = ok;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!finalOk) {
                            target.delete();
                            adapter.setSubtitle(name, human(total));
                            Toast.makeText(getContext(), R.string.loading_error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        adapter.setSubtitle(name, human(target.length()));
                        openLocal(target, name);
                    }
                });
            }
        });
    }

    private void openLocal(File f, String title) {
        try {
            FileMeta meta = AppDB.get().getOrCreate(f.getPath());
            meta.setIsSearchBook(true);
            AppDB.get().save(meta);
            TempHolder.listHash++;
        } catch (Exception e) {
            LOG.e(e);
        }
        FileMeta open = new FileMeta(f.getPath());
        open.setTitle(title);
        ExtUtils.openFile(getActivity(), open);
    }

    private void showSettings() {
        Context c = getContext();
        if (c == null) return;
        final SmbServerPrefs.Srv s = SmbServerPrefs.get(c);
        LinearLayoutBox box = new LinearLayoutBox(c);
        final EditText host = box.addField("Host / IP", s.host);
        final EditText share = box.addField("Share", s.share);
        final EditText user = box.addField("User", s.user);
        final EditText pass = box.addField("Password", s.password);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(c)
               .setTitle(R.string.settings)
                .setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    SmbServerPrefs.Srv n = new SmbServerPrefs.Srv();
                    n.host = host.getText().toString().trim();
                    n.share = share.getText().toString().trim();
                    n.user = user.getText().toString().trim();
                    n.password = pass.getText().toString();
                    SmbServerPrefs.save(c, n);
                    currentPath = "";
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static String human(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.US, "%.2f GB", bytes / 1073741824.0);
    }

    static class SmbRow {
        final int icon;
        final String title;
        String sub;
        final Runnable onClick;

        SmbRow(int icon, String title, String sub, Runnable onClick) {
            this.icon = icon;
            this.title = title;
            this.sub = sub;
            this.onClick = onClick;
        }
    }

    static class LinearLayoutBox extends android.widget.LinearLayout {
        final List<EditText> fields = new ArrayList<EditText>();

        LinearLayoutBox(Context c) {
            super(c);
            setOrientation(VERTICAL);
            int pad = (int) (20 * getResources().getDisplayMetrics().density);
            setPadding(pad, pad / 2, pad, 0);
        }

        EditText addField(String hint, String value) {
            EditText et = new EditText(getContext());
            et.setHint(hint);
            et.setText(value);
            et.setTextSize(15);
            fields.add(et);
            addView(et);
            return et;
        }
    }

    class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

        private final List<SmbRow> rows = new ArrayList<SmbRow>();

        void setRows(List<SmbRow> newRows) {
            rows.clear();
            if (newRows != null) rows.addAll(newRows);
            notifyDataSetChanged();
        }

        void setSubtitle(String title, String sub) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).title.equals(title)) {
                    rows.get(i).sub = sub;
                    notifyItemChanged(i);
                    return;
                }
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.smb_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final SmbRow r = rows.get(position);
            holder.icon.setImageResource(r.icon);
            holder.title.setText(r.title);
            TxtUtils.underlineTextView(holder.title);
            holder.sub.setText(r.sub == null ? "" : r.sub);
            holder.itemView.setOnFocusChangeListener(com.foobnix.ui2.TvFocus.SCALE_BUTTON);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (r.onClick != null) r.onClick.run();
                }
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView sub;

            VH(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.rowIcon);
                title = itemView.findViewById(R.id.rowTitle);
                sub = itemView.findViewById(R.id.rowSub);
            }
        }
    }
}
