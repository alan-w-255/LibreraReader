package com.foobnix.smb;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.dao2.FileMeta;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.sys.TempHolder;
import com.foobnix.ui2.AppDB;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Cloud bookshelf over SMB (dangbei-tv patch): scan the share, add every book to the
 * Library as an "smb://" placeholder row; opening one transparently downloads it to
 * /sdcard/Books, swaps the DB row to the local path and opens the reader.
 */
public class SmbShelf {

    public static final Set<String> BOOK_EXTS = new HashSet<String>(Arrays.asList(
            "pdf", "epub", "mobi", "azw", "azw3", "fb2", "djvu", "cbz", "cbr", "txt", "rtf",
            "doc", "docx", "odt", "tiff", "html", "htm", "md", "zip"));

    public static boolean isSmb(String path) {
        return SmbHelper.isSmbPath(path);
    }

    /** Scan every configured server and add placeholder rows. Returns {added, alreadyLocal}. Blocks. */
    public static int[] syncToShelf(Context c) throws Exception {
        final List<SmbServerPrefs.Srv> servers = SmbServerPrefs.all(c);
        File booksDir = new File(Environment.getExternalStorageDirectory(), "Books");
        final int[] res = {0, 0};
        final List<String> prefixes = new ArrayList<String>();
        for (final SmbServerPrefs.Srv s : servers) {
        prefixes.add(SmbHelper.smbUrlOf(s, s.path == null ? "" : s.path) + "/");
        SmbHelper.walkBooks(s, s.path == null ? "" : s.path, new SmbHelper.BookCollector() {
            @Override
            public void onBook(String rel, long size) {
                try {
                    String ext = ExtUtils.getFileExtension(rel);
                    if (ext == null || !BOOK_EXTS.contains(ext.toLowerCase(Locale.US))) {
                        return;
                    }
                    String name = rel.substring(rel.lastIndexOf('/') + 1);
                    File local = new File(booksDir, TxtUtils.fixFileName(name));
                    if (local.exists() && local.length() > 0 && (size <= 0 || local.length() == size)) {
                        ensureLocalRow(local.getPath(), name, size);
                        res[1]++;
                        return;
                    }
                    String url = SmbHelper.smbUrlOf(s, rel);
                    FileMeta m = AppDB.get().getOrCreate(url);
                    if (m.getTitle() == null || m.getTitle().isEmpty()) {
                        fillFromFileName(m, name);
                        m.setExt(ext.toUpperCase(Locale.US));
                        m.setSize(size);
                        m.setSizeTxt(human(size));
                    }
                    // the Library shelf only lists rows flagged isSearchBook
                    if (!Boolean.TRUE.equals(m.getIsSearchBook())) {
                        m.setIsSearchBook(true);
                        AppDB.get().save(m);
                    }
                    res[0]++;
                } catch (Exception e) {
                    LOG.e(e);
                }
            }
        });
        }
        purgeStaleRows(prefixes);
        TempHolder.listHash++;
        return res;
    }

    /** Drop shelf rows that belong to no configured server any more. */
    static void purgeStaleRows(List<String> prefixes) {
        try {
            List<FileMeta> all = AppDB.get().getAllSmb();
            for (FileMeta m : all) {
                String p = m.getPath();
                boolean keep = false;
                for (String pre : prefixes) {
                    if (p.startsWith(pre)) {
                        keep = true;
                        break;
                    }
                }
                if (!keep) {
                    AppDB.get().deleteBy(p);
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    static void ensureLocalRow(String localPath, String name, long size) {
        try {
            FileMeta m = AppDB.get().getOrCreate(localPath);
            if (m.getTitle() == null || m.getTitle().isEmpty()) {
                fillFromFileName(m, name);
            }
            if (!Boolean.TRUE.equals(m.getIsSearchBook())) {
                m.setIsSearchBook(true);
                AppDB.get().save(m);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** "Author - Title.ext" heuristic; plain filename otherwise. */
    static void fillFromFileName(FileMeta m, String name) {
        String base = name;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
        }
        String author = "";
        String title = base;
        int dash = base.indexOf(" - ");
        if (dash > 0 && dash < base.length() - 4) {
            author = base.substring(0, dash).trim();
            title = base.substring(dash + 3).trim();
        }
        m.setTitle(title.isEmpty() ? base : title);
        m.setAuthor(author);
    }

    /** Called from ExtUtils.openFile for smb:// rows. UI thread. */
    public static void openFromShelf(final Activity a, final FileMeta meta) {
        final String url = meta.getPath();
        SmbServerPrefs.Srv found = null;
        for (SmbServerPrefs.Srv s : SmbServerPrefs.all(a)) {
            if (url.startsWith(SmbHelper.smbUrlOf(s, "") + "/")) {
                found = s;
                break;
            }
        }
        if (found == null) {
            Toast.makeText(a, R.string.smb_server_changed, Toast.LENGTH_LONG).show();
            return;
        }
        final SmbServerPrefs.Srv s = found;
        final String rel = SmbHelper.relFromSmbUrl(url);
        if (rel == null || rel.isEmpty()) {
            return;
        }
        final String name = rel.substring(rel.lastIndexOf('/') + 1);
        final File dir = new File(Environment.getExternalStorageDirectory(), "Books");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        final File target = new File(dir, TxtUtils.fixFileName(name));
        final long total = meta.getSize() == null ? 0 : meta.getSize();
        if (target.exists() && target.length() > 0 && (total <= 0 || target.length() == total)) {
            migrateAndOpen(a, url, target, name);
            return;
        }
        Toast.makeText(a, a.getString(R.string.download) + " 0%", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    SmbHelper.download(s, rel, target, total, new SmbHelper.Progress() {
                        @Override
                        public void onProgress(final long done, final long tot) {
                            if (tot <= 0) {
                                return;
                            }
                            a.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(a, a.getString(R.string.download) + " "
                                            + (done * 100 / tot) + "%", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                    ok = target.exists() && target.length() > 0;
                } catch (Exception e) {
                    LOG.e(e);
                }
                final boolean fok = ok;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!fok) {
                            target.delete();
                            Toast.makeText(a, R.string.loading_error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        migrateAndOpen(a, url, target, name);
                    }
                });
            }
        }).start();
    }

    /** Swap the smb:// row for the downloaded local row, then open the reader. */
    static void migrateAndOpen(Activity a, String smbUrl, File local, String name) {
        String title = name;
        try {
            AppDB.get().deleteBy(smbUrl);
            FileMeta m = AppDB.get().getOrCreate(local.getPath());
            if (m.getTitle() == null || m.getTitle().isEmpty()) {
                fillFromFileName(m, name);
                m.setExt(ExtUtils.getFileExtension(name));
                AppDB.get().save(m);
            }
            m.setIsSearchBook(true);
            AppDB.get().save(m);
            TempHolder.listHash++;
            title = m.getTitle() == null ? name : m.getTitle();
        } catch (Exception e) {
            LOG.e(e);
        }
        FileMeta open = new FileMeta(local.getPath());
        open.setTitle(title);
        ExtUtils.openFile(a, open);
    }

    static String human(long bytes) {
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        }
        return String.format(Locale.US, "%.2f GB", bytes / 1073741824.0);
    }
}
