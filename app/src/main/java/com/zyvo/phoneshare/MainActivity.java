package com.zyvo.phoneshare;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PERM = 100;
    private FileServer server;
    private File rootDir;
    private RecyclerView recycler;
    private FileAdapter adapter;
    private List<File> allFiles = new ArrayList<>();
    private TextView ipText, statusText, emptyText;
    private MaterialButton startBtn, stopBtn;
    private TextInputEditText searchEdit;
    private ToneGenerator tone;

    @Override
    protected void onCreate(Bundle b) {
        // theme
        SharedPreferences sp = getSharedPreferences("ps", MODE_PRIVATE);
        int mode = sp.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        rootDir = Environment.getExternalStorageDirectory();
        if (rootDir == null || !rootDir.exists()) rootDir = getFilesDir();

        ipText = findViewById(R.id.ipText);
        statusText = findViewById(R.id.statusText);
        emptyText = findViewById(R.id.emptyText);
        startBtn = findViewById(R.id.startBtn);
        stopBtn = findViewById(R.id.stopBtn);
        searchEdit = findViewById(R.id.searchEdit);
        recycler = findViewById(R.id.fileList);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        recycler.setAdapter(adapter);

        tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);

        startBtn.setOnClickListener(v -> { clickSound(); startServer(); });
        stopBtn.setOnClickListener(v -> { clickSound(); stopServer(); });
        findViewById(R.id.themeBtn).setOnClickListener(v -> { clickSound(); toggleTheme(); });

        TextView devContact = findViewById(R.id.devContact);
        devContact.setOnClickListener(v -> {
            clickSound();
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+923015614357"));
            startActivity(i);
        });
        TextView devName = findViewById(R.id.devName);
        devName.setOnClickListener(v -> clickSound());

        searchEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int n) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        requestPerms();
        refreshList();
    }

    private void clickSound() {
        try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80); } catch (Exception ignored) {}
    }

    private void requestPerms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(i);
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERM);
            }
        }
    }

    private void startServer() {
        try {
            if (server != null) { Toast.makeText(this, "Already running", Toast.LENGTH_SHORT).show(); return; }
            server = new FileServer(this, rootDir);
            server.start();
            String ip = getIp();
            ipText.setText(ip + ":" + FileServer.PORT);
            statusText.setText("Running — open in browser");
            Toast.makeText(this, "Server started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Start failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        ipText.setText("Not running");
        statusText.setText("Tap Start to share files");
    }

    private String getIp() {
        try {
            // try wifi
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
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

    private void refreshList() {
        allFiles.clear();
        collect(rootDir, 0);
        filter(searchEdit.getText() == null ? "" : searchEdit.getText().toString());
    }

    private void collect(File dir, int depth) {
        if (depth > 2) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            allFiles.add(f);
            if (f.isDirectory() && depth < 2) collect(f, depth + 1);
        }
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
        stopServer();
        try { tone.release(); } catch (Exception ignored) {}
    }

    // ---- adapter ----
    class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private List<File> items = new ArrayList<>();
        void setItems(List<File> i) { items = i; notifyDataSetChanged(); }
        public int getItemCount() { return items.size(); }
        public VH onCreateViewHolder(android.view.ViewGroup p, int t) {
            View v = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, p, false);
            return new VH(v);
        }
        public void onBindViewHolder(VH h, int pos) {
            File f = items.get(pos);
            h.t1.setText(f.getName() + (f.isDirectory() ? "/" : ""));
            long sz = f.length();
            String meta = (f.isDirectory() ? "Folder" : formatSize(sz)) + "  •  " + new java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(f.lastModified()));
            h.t2.setText(meta);
            h.itemView.setOnClickListener(v -> { clickSound(); openFile(f); });
            h.itemView.setOnLongClickListener(v -> { clickSound(); deleteFile(f); return true; });
        }
        class VH extends RecyclerView.ViewHolder {
            TextView t1, t2;
            VH(View v) { super(v); t1 = v.findViewById(android.R.id.text1); t2 = v.findViewById(android.R.id.text2); }
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
        boolean ok = f.isDirectory() ? deleteDir(f) : f.delete();
        Toast.makeText(this, ok ? "Deleted" : "Failed", Toast.LENGTH_SHORT).show();
        refreshList();
    }

    private boolean deleteDir(File d) {
        File[] fs = d.listFiles();
        if (fs != null) for (File x : fs) { if (x.isDirectory()) deleteDir(x); else x.delete(); }
        return d.delete();
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
}
