package com.zyvo.phoneshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PICK = 200;
    private FileServer server;
    private File rootDir;
    private RecyclerView recycler;
    private FileAdapter adapter;
    private List<File> allFiles = new ArrayList<>();
    private TextView ipText, statusText, emptyText, batteryText;
    private MaterialButton batteryBtn;
    private TextInputEditText searchEdit;
    private ProgressBar uploadProgress;
    private ToneHelper tone;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle b) {
        SharedPreferences sp = getSharedPreferences("ps", MODE_PRIVATE);
        int mode = sp.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        rootDir = new File(getFilesDir(), "shared");
        if (!rootDir.exists()) rootDir.mkdirs();
        // Start foreground service so sharing survives sleep/screen-off
        Intent svc = new Intent(this, ShareService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        sp.edit().putBoolean("sharing_enabled", true).apply();
        if (android.os.Build.VERSION.SDK_INT >= 33
                && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 300);
        }

        ipText = findViewById(R.id.ipText);
        statusText = findViewById(R.id.statusText);
        emptyText = findViewById(R.id.emptyText);
        searchEdit = findViewById(R.id.searchEdit);
        uploadProgress = findViewById(R.id.uploadProgress);
        batteryText = findViewById(R.id.batteryText);
        batteryBtn = findViewById(R.id.batteryBtn);
        recycler = findViewById(R.id.fileList);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        recycler.setAdapter(adapter);

        tone = new ToneHelper();

        findViewById(R.id.addFilesBtn).setOnClickListener(v -> { tone.click(); pickFiles(); });
        findViewById(R.id.themeBtn).setOnClickListener(v -> { tone.click(); toggleTheme(); });
        findViewById(R.id.stopBtn).setOnClickListener(v -> {
            tone.click();
            stopService(new Intent(this, ShareService.class));
            getSharedPreferences("ps", MODE_PRIVATE).edit().putBoolean("sharing_enabled", false).apply();
            statusText.setText("Sharing stopped");
            statusText.setTextColor(getColor(R.color.danger));
            ipText.setText("—");
        });

        TextView devContact = findViewById(R.id.devContact);
        devContact.setText("+92 313 5275771");
        devContact.setOnClickListener(v -> {
            tone.click();
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+923135275771"));
            startActivity(i);
        });
        findViewById(R.id.devName).setOnClickListener(v -> tone.click());

        searchEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int n) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        autoStart();
        refreshList();
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() { public void run() { refreshList(); refreshHandler.postDelayed(this, 3000); } };
        refreshHandler.postDelayed(refreshRunnable, 3000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBatteryOptimization();
    }

    private void checkBatteryOptimization() {
        if (batteryText == null) return;
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean ignored = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryText.setText(ignored
                ? "Unrestricted — server stays live with screen off / locked"
                : "Battery optimization ON — server may stop when locked");
        batteryText.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                ignored ? R.color.ok : R.color.danger));
        batteryBtn.setVisibility(ignored ? View.GONE : View.VISIBLE);
        batteryBtn.setOnClickListener(v -> {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
    }

    private void toneClick() { tone.click(); }

    private void autoStart() {
        // Wait briefly for the foreground service to bring up the server
        new Thread(() -> {
            for (int i = 0; i < 20; i++) {
                if (ShareService.isRunning() && ShareService.getServer() != null) break;
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
            runOnUiThread(() -> {
                server = ShareService.getServer();
                String ip = getIp();
                if (ShareService.isRunning()) {
                    ipText.setText(ip + ":" + FileServer.PORT);
                    statusText.setText("Sharing active (background)");
                } else {
                    ipText.setText("Start failed");
                    statusText.setText("Service not running");
                }
            });
        }).start();
    }

    private String getIp() {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            android.net.wifi.WifiInfo wi = wm.getConnectionInfo();
            int ip = wi.getIpAddress();
            if (ip != 0) {
                return String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
            }
        } catch (Exception ignored) {}
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress ia : Collections.list(ni.getInetAddresses())) {
                    if (!ia.isLoopbackAddress() && ia.getHostAddress().indexOf(':') < 0) {
                        return ia.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != Activity.RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++)
                uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        uploadProgress.setVisibility(View.VISIBLE);
        uploadProgress.setMax(uris.size());
        uploadProgress.setProgress(0);
        new Thread(() -> {
            int done = 0;
            for (Uri u : uris) {
                try {
                    String name = queryName(u);
                    if (name == null) name = "file_" + System.currentTimeMillis();
                    File dest = new File(rootDir, name);
                    int i = 1; String base = name, ext = "";
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
                    while (dest.exists()) { dest = new File(rootDir, base + "_" + i + ext); i++; }
                    try (InputStream in = getContentResolver().openInputStream(u);
                         FileOutputStream out = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192]; int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                } catch (Exception ignored) {}
                done++;
                final int d = done;
                runOnUiThread(() -> uploadProgress.setProgress(d));
            }
            runOnUiThread(() -> {
                uploadProgress.setVisibility(View.GONE);
                Toast.makeText(this, "Added " + uris.size() + " file(s)", Toast.LENGTH_SHORT).show();
                refreshList();
            });
        }).start();
    }

    private String queryName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void refreshList() {
        allFiles.clear();
        File[] fs = rootDir.listFiles();
        if (fs != null) for (File f : fs) allFiles.add(f);
        filter(searchEdit.getText() == null ? "" : searchEdit.getText().toString());
    }

    private void filter(String q) {
        List<File> out = new ArrayList<>();
        String lq = q.toLowerCase();
        for (File f : allFiles) {
            if (lq.isEmpty() || f.getName().toLowerCase().contains(lq)) out.add(f);
        }
        adapter.setItems(out);
        emptyText.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toggleTheme() {
        SharedPreferences sp = getSharedPreferences("ps", MODE_PRIVATE);
        int cur = sp.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int next = (cur == AppCompatDelegate.MODE_NIGHT_YES) ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        sp.edit().putInt("theme", next).apply();
        AppCompatDelegate.setDefaultNightMode(next);
        recreate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshHandler != null && refreshRunnable != null) refreshHandler.removeCallbacks(refreshRunnable);
        // Do NOT stop the server here — ShareService keeps it alive in background.
    }

    // ---- adapter ----
    class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private List<File> items = new ArrayList<>();
        void setItems(List<File> i) { items = i; notifyDataSetChanged(); }
        public int getItemCount() { return items.size(); }
        public VH onCreateViewHolder(android.view.ViewGroup p, int t) {
            View v = getLayoutInflater().inflate(R.layout.item_file, p, false);
            return new VH(v);
        }
        public void onBindViewHolder(VH h, int pos) {
            File f = items.get(pos);
            h.name.setText(f.getName());
            h.icon.setText(iconFor(f.getName()));
            loadThumb(f, h);
            long sz = f.length();
            String meta = formatSize(sz) + "  •  " + new java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(f.lastModified()));
            h.meta.setText(meta);
            h.itemView.setOnClickListener(v -> { tone.click(); openFile(f); });
            h.btnDownload.setOnClickListener(v -> { tone.click(); downloadFile(f); });
            h.btnShare.setOnClickListener(v -> { tone.click(); shareFile(f); });
            h.btnDelete.setOnClickListener(v -> { tone.click(); deleteFile(f); });
        }
        class VH extends RecyclerView.ViewHolder {
            TextView icon, name, meta;
            android.widget.ImageView thumb;
            android.widget.ImageButton btnDownload, btnShare, btnDelete;
            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.icon);
                thumb = v.findViewById(R.id.thumb);
                name = v.findViewById(R.id.name);
                meta = v.findViewById(R.id.meta);
                btnDownload = v.findViewById(R.id.btnDownload);
                btnShare = v.findViewById(R.id.btnShare);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }

    private void loadThumb(File f, FileAdapter.VH h) {
        final String path = f.getAbsolutePath();
        h.thumb.setTag(path);
        h.thumb.setVisibility(View.GONE);
        h.icon.setVisibility(View.VISIBLE);
        String n = f.getName().toLowerCase();
        boolean canPreview = n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".bmp")
                || n.endsWith(".mp4") || n.endsWith(".3gp") || n.endsWith(".mov")
                || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".pdf");
        if (!canPreview || f.isDirectory()) return;
        thumbExecutor.execute(() -> {
            byte[] jpeg = ThumbCache.get(f);
            final Bitmap bm = jpeg == null ? null : BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            runOnUiThread(() -> {
                if (bm != null && path.equals(h.thumb.getTag())) {
                    h.thumb.setImageBitmap(bm);
                    h.thumb.setVisibility(View.VISIBLE);
                    h.icon.setVisibility(View.GONE);
                }
            });
        });
    }

    private String iconFor(String name) {
        String n = name.toLowerCase();
        int dot = n.lastIndexOf('.');
        String e = dot >= 0 ? n.substring(dot + 1) : "";
        if (e.equals("png")||e.equals("jpg")||e.equals("jpeg")||e.equals("gif")||e.equals("webp")) return "IMG";
        if (e.equals("pdf")) return "PDF";
        if (e.equals("mp4")) return "VID";
        if (e.equals("mp3")) return "AUD";
        if (e.equals("zip")) return "ZIP";
        if (e.equals("txt")) return "TXT";
        return e.isEmpty() ? "FILE" : e.toUpperCase().substring(0, Math.min(3, e.length()));
    }

    private void downloadFile(File f) {
        new Thread(() -> {
            try {
                String savedName;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, f.getName());
                    cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, guessMime(f.getName()));
                    cv.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                    android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new Exception("MediaStore insert failed");
                    try (java.io.InputStream in = new java.io.FileInputStream(f);
                         java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                        byte[] buf = new byte[8192]; int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    savedName = f.getName();
                } else {
                    File downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                    if (!downloads.exists()) downloads.mkdirs();
                    File dest = new File(downloads, f.getName());
                    int i = 1; String base = f.getName(), ext = "";
                    int dot = f.getName().lastIndexOf('.');
                    if (dot > 0) { base = f.getName().substring(0, dot); ext = f.getName().substring(dot); }
                    while (dest.exists()) { dest = new File(downloads, base + "_" + i + ext); i++; }
                    try (java.io.InputStream in = new java.io.FileInputStream(f);
                         java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                        byte[] buf = new byte[8192]; int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    savedName = dest.getName();
                }
                final String name = savedName;
                runOnUiThread(() -> Toast.makeText(this, "Saved to Downloads: " + name, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                final String msg = e.getMessage();
                runOnUiThread(() -> Toast.makeText(this, "Download failed: " + msg, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void shareFile(File f) {
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(guessMime(f.getName()));
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share " + f.getName()));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openFile(File f) {
        if (f.isDirectory()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
            i.setDataAndType(uri, guessMime(f.getName()));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile(File f) {
        boolean ok = f.delete();
        Toast.makeText(this, ok ? "Deleted" : "Failed", Toast.LENGTH_SHORT).show();
        refreshList();
    }

    private String formatSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024*1024) return String.format("%.1f KB", b/1024.0);
        if (b < 1024*1024*1024) return String.format("%.1f MB", b/(1024.0*1024));
        return String.format("%.1f GB", b/(1024.0*1024*1024));
    }

    private String guessMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")||n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".gif")||n.endsWith(".webp")) return "image/*";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".mp4")) return "video/*";
        if (n.endsWith(".mp3")) return "audio/*";
        if (n.endsWith(".txt")) return "text/plain";
        return "*/*";
    }

    // simple tone helper
    static class ToneHelper {
        private android.media.ToneGenerator tg;
        ToneHelper() {
            try { tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80); } catch (Exception ignored) {}
        }
        void click() {
            try { if (tg != null) tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 80); } catch (Exception ignored) {}
        }
    }
}
