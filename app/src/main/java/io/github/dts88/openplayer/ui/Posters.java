package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Posters {
    private static final int MEMORY_BYTES = 48 << 20;
    private static final int CONNECT_MS = 8300;
    private static final int READ_MS = 14600;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(MEMORY_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Posters() {
    }

    static void into(ImageView view, String url, int widthPx) {
        if (url == null || url.isEmpty()) {
            view.setTag(null);
            view.setImageDrawable(null);
            return;
        }
        String key = url + "@" + widthPx;
        view.setTag(key);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        File dir = new File(view.getContext().getCacheDir(), "art");
        IO.execute(() -> {
            Bitmap b = load(dir, url, Math.max(64, widthPx));
            if (b == null) return;
            CACHE.put(key, b);
            UI.post(() -> {
                if (key.equals(view.getTag())) view.setImageBitmap(b);
            });
        });
    }

    static void clear(Context context) {
        CACHE.evictAll();
        File[] files = new File(context.getCacheDir(), "art").listFiles();
        if (files == null) return;
        for (File f : files) f.delete();
    }

    private static Bitmap load(File dir, String url, int width) {
        try {
            File file = fetch(dir, url);
            if (file == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= width) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getPath(), o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    static String nameOf(String url) {
        long h = 0x3C6EF372FE94F82BL;
        for (int i = 0; i < url.length(); i++) h = (h ^ url.charAt(i)) * 0x100000001B3L;
        return Long.toHexString(h) + "-" + url.length() + ".art";
    }

    private static File fetch(File dir, String url) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        File out = new File(dir, nameOf(url));
        if (out.isFile() && out.length() > 0) return out;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        File part = new File(dir, out.getName() + ".part");
        try {
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream(); FileOutputStream o = new FileOutputStream(part)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
        return part.renameTo(out) ? out : null;
    }
}
