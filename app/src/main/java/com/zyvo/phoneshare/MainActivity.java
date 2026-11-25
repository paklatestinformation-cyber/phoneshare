package com.zyvo.phoneshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PICK = 200;
    private FileServer server;
    private File rootDir;
    private RecyclerView recycler;
    private FileAdapter adapter;
    private List<File> allFiles = new ArrayList<>();
    private TextView ipText, statusText, emptyText;
    private TextInputEditText searchEdit;
    private ProgressBar uploadProgress;
    private ToneHelper tone;

    @Override
    protected void onCreate(Bundle b) {
        SharedPreferences sp = getSharedPreferences("ps", MODE_PRIVATE);
        int mode = sp.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        rootDir = new File(getFilesDir(), "shared");
        if (!rootDir.exists()) rootDir.mkdirs();

        ipText = findViewById(R.id.ipText);
        statusText = findViewById(R.id.statusText);
        emptyText = findViewById(R.id.emptyText);
        searchEdit = findViewById(R.id.searchEdit);
        uploadProgress = findViewById(R.id.uploadProgress);
        recycler = findViewById(R.id.fileList);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        recycler.setAdapter(adapter);

        tone = new ToneHelper();

        findViewById(R.id.addFilesBtn).setOnClickListener(v -> { tone.click(); pickFiles(); });
        findViewById(R.id.themeBtn).setOnClickListener(v -> { tone.click(); toggleTheme(); });

        TextView devContact = findViewById(R.id.devContact);
        devContact.setOnClickListener(v -> {
            tone.click();
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+923015614357"));
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
    }

    private void toneClick() { tone.click(); }

    private void autoStart() {
        try {
            server = new FileServer(this, rootDir);
            server.start();
            String ip = getIp();
            ipText.setText(ip + ":" + FileServer.PORT);
            statusText.setText("Sharing active");
        } catch (Exception e) {
            ipText.setText("Start failed");
            statusText.setText(e.getMessage());
        }
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
        if (server != null) { server.stop(); server = null; }
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
            h.t1.setText(f.getName());
            long sz = f.length();
            String meta = formatSize(sz) + "  •  " + new java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(f.lastModified()));
            h.t2.setText(meta);
            h.itemView.setOnClickListener(v -> { tone.click(); openFile(f); });
            h.itemView.setOnLongClickListener(v -> { tone.click(); deleteFile(f); return true; });
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
