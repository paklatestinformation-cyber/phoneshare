package com.zyvo.phoneshare;

import android.content.Context;
import android.content.res.AssetManager;

import fi.iki.elonen.NanoHTTPD;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class FileServer extends NanoHTTPD {
    private final Context ctx;
    private final File rootDir;
    public static final int PORT = 8080;

    public FileServer(Context ctx, File rootDir) throws IOException {
        super(PORT);
        this.ctx = ctx;
        this.rootDir = rootDir;
        if (!rootDir.exists()) rootDir.mkdirs();
    }

    public File getRootDir() { return rootDir; }
    public void setRootDir(File d) { /* no-op runtime; restart needed */ }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method m = session.getMethod();
        try {
            if (uri.equals("/") || uri.equals("/index.html")) {
                return serveAsset("index.html", "text/html");
            }
            if (uri.startsWith("/api/list")) {
                return listFiles(uri);
            }
            if (uri.startsWith("/api/delete")) {
                return deleteFile(uri);
            }
            if (uri.startsWith("/api/upload")) {
                if (m == Method.POST) return uploadFile(session);
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "POST only");
            }
            if (uri.startsWith("/download/")) {
                return downloadFile(uri);
            }
            if (uri.startsWith("/view/")) {
                return viewFile(uri);
            }
            if (uri.startsWith("/assets/")) {
                return serveAsset(uri.substring(1), guessMime(uri));
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404");
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.toString());
        }
    }

    private Response serveAsset(String path, String mime) {
        try {
            AssetManager am = ctx.getAssets();
            InputStream is = am.open(path);
            return newChunkedResponse(Response.Status.OK, mime, is);
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "asset not found: " + path);
        }
    }

    private Response listFiles(String uri) throws Exception {
        String path = "";
        Map<String, String> params = new HashMap<>();
        if (uri.contains("?")) {
            String q = uri.substring(uri.indexOf("?") + 1);
            for (String kv : q.split("&")) {
                String[] p = kv.split("=", 2);
                if (p.length == 2) params.put(URLDecoder.decode(p[0], "UTF-8"), URLDecoder.decode(p[1], "UTF-8"));
            }
        }
        String sub = params.getOrDefault("path", "");
        File dir = new File(rootDir, sub);
        if (!dir.exists() || !dir.isDirectory()) dir = rootDir;
        JSONArray arr = new JSONArray();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                JSONObject o = new JSONObject();
                o.put("name", f.getName());
                o.put("isDir", f.isDirectory());
                o.put("size", f.length());
                o.put("lastModified", f.lastModified());
                o.put("ext", extension(f.getName()));
                o.put("relPath", (sub.isEmpty() ? "" : sub + "/") + f.getName());
                arr.put(o);
            }
        }
        JSONObject out = new JSONObject();
        out.put("path", sub);
        out.put("items", arr);
        Response r = newFixedLengthResponse(Response.Status.OK, "application/json", out.toString());
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    private Response deleteFile(String uri) {
        Map<String, String> params = parseQuery(uri);
        String rel = params.getOrDefault("path", "");
        File f = new File(rootDir, rel);
        boolean ok = false;
        if (f.exists()) {
            if (f.isDirectory()) ok = deleteDir(f); else ok = f.delete();
        }
        JSONObject o = new JSONObject();
        try { o.put("ok", ok); } catch (Exception ignored) {}
        return newFixedLengthResponse(Response.Status.OK, "application/json", o.toString());
    }

    private boolean deleteDir(File d) {
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) {
            if (f.isDirectory()) deleteDir(f); else f.delete();
        }
        return d.delete();
    }

    private Response uploadFile(IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        Map<String, String> params = session.getParms();
        String sub = params.getOrDefault("path", "");
        File dir = new File(rootDir, sub);
        if (!dir.exists()) dir.mkdirs();
        // find uploaded file temp
        String tmpPath = null;
        String name = params.get("filename");
        for (Map.Entry<String, String> e : files.entrySet()) {
            tmpPath = e.getValue();
            if (name == null) name = e.getKey();
        }
        if (tmpPath == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "no file");
        }
        if (name == null || name.trim().isEmpty()) name = "upload.bin";
        File dest = new File(dir, name);
        // avoid overwrite: append number
        int i = 1;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        while (dest.exists()) { dest = new File(dir, base + "_" + i + ext); i++; }
        // move temp
        File tmp = new File(tmpPath);
        try (InputStream in = new FileInputStream(tmp);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        tmp.delete();
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("name", dest.getName());
        return newFixedLengthResponse(Response.Status.OK, "application/json", o.toString());
    }

    private Response downloadFile(String uri) {
        String rel = urlDecode(uri.substring("/download/".length()));
        File f = new File(rootDir, rel);
        if (!f.exists() || f.isDirectory()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found");
        }
        try {
            FileInputStream fis = new FileInputStream(f);
            Response r = newChunkedResponse(Response.Status.OK, guessMime(f.getName()), fis);
            r.addHeader("Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
            return r;
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.toString());
        }
    }

    private Response viewFile(String uri) {
        String rel = urlDecode(uri.substring("/view/".length()));
        File f = new File(rootDir, rel);
        if (!f.exists() || f.isDirectory()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found");
        }
        try {
            FileInputStream fis = new FileInputStream(f);
            Response r = newChunkedResponse(Response.Status.OK, guessMime(f.getName()), fis);
            r.addHeader("Content-Disposition", "inline; filename=\"" + f.getName() + "\"");
            return r;
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.toString());
        }
    }

    private Map<String, String> parseQuery(String uri) {
        Map<String, String> params = new HashMap<>();
        if (!uri.contains("?")) return params;
        String q = uri.substring(uri.indexOf("?") + 1);
        for (String kv : q.split("&")) {
            String[] p = kv.split("=", 2);
            if (p.length == 2) {
                try { params.put(URLDecoder.decode(p[0], "UTF-8"), URLDecoder.decode(p[1], "UTF-8")); }
                catch (Exception ignored) {}
            }
        }
        return params;
    }

    private String extension(String name) {
        int i = name.lastIndexOf('.');
        return (i >= 0) ? name.substring(i + 1).toLowerCase() : "";
    }

    private String urlDecode(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private String guessMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }
}
