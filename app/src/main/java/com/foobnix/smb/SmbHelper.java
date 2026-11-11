package com.foobnix.smb;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** smbj wrapper: list + download (dangbei-tv patch). All calls block - run off the UI thread. */
public class SmbHelper {

    public static class Entry {
        public final String name;
        public final boolean dir;
        public final long size;

        public Entry(String name, boolean dir, long size) {
            this.name = name;
            this.dir = dir;
            this.size = size;
        }
    }

    public interface Progress {
        void onProgress(long done, long total);
    }

    public interface BookCollector {
        void onBook(String relPath, long size);
    }

    public static String smbUrlOf(SmbServerPrefs.Srv s, String rel) {
        return "smb://" + s.host + "/" + s.share + (rel == null || rel.isEmpty() ? "" : "/" + rel);
    }

    public static boolean isSmbPath(String path) {
        return path != null && path.startsWith("smb://");
    }

    /** smb://host/share/rel -> rel ("zlibrary/epub/x.epub"); "" if not matchable. */
    public static String relFromSmbUrl(String url) {
        if (!isSmbPath(url)) {
            return null;
        }
        String t = url.substring("smb://".length());
        int i1 = t.indexOf('/');
        if (i1 < 0) {
            return "";
        }
        int i2 = t.indexOf('/', i1 + 1);
        if (i2 < 0) {
            return "";
        }
        return t.substring(i2 + 1);
    }

    /** Walk the whole share recursively on ONE connection, calling back for every file. */
    public static void walkBooks(SmbServerPrefs.Srv s, String root, BookCollector cb) throws Exception {
        SMBClient client = new SMBClient();
        try {
            DiskShare share = connect(client, s);
            try {
                walkRec(share, root == null ? "" : root, cb);
            } finally {
                try { share.close(); } catch (Exception ignore) {}
            }
        } finally {
            try { client.close(); } catch (Exception ignore) {}
        }
    }

    private static void walkRec(DiskShare share, String dir, BookCollector cb) throws Exception {
        List<FileIdBothDirectoryInformation> infos = share.list(smbPath(dir));
        for (FileIdBothDirectoryInformation i : infos) {
            String n = i.getFileName();
            if (n == null || n.isEmpty() || n.equals(".") || n.equals("..")) {
                continue;
            }
            boolean isDir = (i.getFileAttributes() & 0x10L) != 0;
            String rel = dir == null || dir.isEmpty() ? n : dir + "/" + n;
            if (isDir) {
                walkRec(share, rel, cb);
            } else {
                cb.onBook(rel, i.getEndOfFile());
            }
        }
    }

    static String smbPath(String p) {
        if (p == null || p.isEmpty()) return "";
        return p.replace("/", "\\");
    }

    private static DiskShare connect(SMBClient client, SmbServerPrefs.Srv s) throws Exception {
        Connection connection = client.connect(s.host, s.port);
        AuthenticationContext ac;
        if (s.user == null || s.user.isEmpty()) {
            ac = AuthenticationContext.guest();
        } else {
            ac = new AuthenticationContext(s.user, s.password.toCharArray(), "");
        }
        Session session = connection.authenticate(ac);
        return (DiskShare) session.connectShare(s.share);
    }

    public static List<Entry> list(SmbServerPrefs.Srv s, String path) throws Exception {
        SMBClient client = new SMBClient();
        try {
            DiskShare share = connect(client, s);
            try {
                List<Entry> out = new ArrayList<Entry>();
                List<FileIdBothDirectoryInformation> infos = share.list(smbPath(path));
                for (FileIdBothDirectoryInformation i : infos) {
                    String n = i.getFileName();
                    if (n == null || n.isEmpty() || n.equals(".") || n.equals("..")) continue;
                    boolean dir = (i.getFileAttributes() & 0x10L) != 0;
                    out.add(new Entry(n, dir, i.getEndOfFile()));
                }
                return out;
            } finally {
                try { share.close(); } catch (Exception ignore) {}
            }
        } finally {
            try { client.close(); } catch (Exception ignore) {}
        }
    }

    public static void download(SmbServerPrefs.Srv s, String path, File dest, long total, Progress cb) throws Exception {
        SMBClient client = new SMBClient();
        try {
            DiskShare share = connect(client, s);
            try {
                com.hierynomus.smbj.share.File f = share.openFile(smbPath(path),
                        EnumSet.of(AccessMask.GENERIC_READ), null, null, SMB2CreateDisposition.FILE_OPEN, null);
                try {
                    InputStream in = new BufferedInputStream(f.getInputStream());
                    OutputStream out = new BufferedOutputStream(new FileOutputStream(dest));
                    try {
                        byte[] buf = new byte[128 * 1024];
                        long done = 0;
                        int r;
                        while ((r = in.read(buf)) > 0) {
                            out.write(buf, 0, r);
                            done += r;
                            if (cb != null) cb.onProgress(done, total);
                        }
                    } finally {
                        try { out.close(); } catch (Exception ignore) {}
                        try { in.close(); } catch (Exception ignore) {}
                    }
                } finally {
                    try { f.close(); } catch (Exception ignore) {}
                }
            } finally {
                try { share.close(); } catch (Exception ignore) {}
            }
        } finally {
            try { client.close(); } catch (Exception ignore) {}
        }
    }
}
