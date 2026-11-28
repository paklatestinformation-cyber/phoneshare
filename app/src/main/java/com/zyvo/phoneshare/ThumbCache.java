package com.zyvo.phoneshare;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tiny thumbnails generated on the phone so the PC browser needs no plugins,
 * no internet and no heavy JS library. Cached by path + mtime + size.
 */
final class ThumbCache {
    private static final int MAX_ENTRIES = 80;
    private static final int SIZE = 180;

    private static final LinkedHashMap<String, byte[]> CACHE =
            new LinkedHashMap<String, byte[]>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    private ThumbCache() {}

    static synchronized byte[] get(File file) {
        if (file == null || !file.isFile()) return null;
        String key = file.getAbsolutePath() + "|" + file.lastModified() + "|" + file.length();
        byte[] hit = CACHE.get(key);
        if (hit != null) return hit;
        byte[] made = null;
        try { made = generate(file); } catch (Throwable ignored) {}
        if (made != null) CACHE.put(key, made);
        return made;
    }

    private static byte[] generate(File f) {
        String ext = ext(f.getName());
        Bitmap bm = null;
        if (isImage(ext)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample(bounds.outWidth, bounds.outHeight);
            bm = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } else if (isVideo(ext)) {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(f.getAbsolutePath());
                bm = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (bm == null) bm = r.getFrameAtTime(0);
            } finally {
                try { r.release(); } catch (Exception ignored) {}
            }
        } else if ("pdf".equals(ext)) {
            ParcelFileDescriptor pfd = null;
            PdfRenderer renderer = null;
            try {
                pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(pfd);
                if (renderer.getPageCount() > 0) {
                    PdfRenderer.Page page = renderer.openPage(0);
                    int h = Math.max(1, Math.round(SIZE * (float) page.getHeight() / page.getWidth()));
                    bm = Bitmap.createBitmap(SIZE, h, Bitmap.Config.ARGB_8888);
                    bm.eraseColor(Color.WHITE);
                    page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                }
            } finally {
                if (renderer != null) try { renderer.close(); } catch (Exception ignored) {}
                if (pfd != null) try { pfd.close(); } catch (IOException ignored) {}
            }
        }
        if (bm == null) return null;
        Bitmap square = square(bm);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        square.compress(Bitmap.CompressFormat.JPEG, 82, out);
        if (square != bm) square.recycle();
        bm.recycle();
        return out.toByteArray();
    }

    private static int sample(int w, int h) {
        int s = 1;
        int max = SIZE * 3;
        while (w / s > max || h / s > max) s *= 2;
        return s;
    }

    private static Bitmap square(Bitmap src) {
        int side = Math.min(src.getWidth(), src.getHeight());
        Bitmap cropped = Bitmap.createBitmap(src, (src.getWidth() - side) / 2,
                (src.getHeight() - side) / 2, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true);
        if (cropped != scaled && cropped != src) cropped.recycle();
        return scaled;
    }

    private static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static boolean isImage(String e) {
        return e.equals("jpg") || e.equals("jpeg") || e.equals("png") || e.equals("gif")
                || e.equals("webp") || e.equals("bmp");
    }

    private static boolean isVideo(String e) {
        return e.equals("mp4") || e.equals("3gp") || e.equals("mov") || e.equals("mkv")
                || e.equals("webm");
    }
}
