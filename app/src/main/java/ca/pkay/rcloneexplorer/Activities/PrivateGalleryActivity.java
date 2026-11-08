package ca.pkay.rcloneexplorer.Activities;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ca.pkay.rcloneexplorer.Items.FileItem;
import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.Rclone;
import ca.pkay.rcloneexplorer.gallery.GalleryPaths;
import ca.pkay.rcloneexplorer.gallery.PrivateCache;
import ca.pkay.rcloneexplorer.gallery.GalleryCatalog;

/**
 * Companion gallery for rclone crypt remotes. Catalog and JPEGs are independent from videos,
 * but ALL three live inside the same crypt remote (and hence are encrypted in Google Drive).
 *
 * Append-only per-video metadata preserves independent device additions; legacy catalogs
 * are read-only. Transfers require the Activity to stay open. Offline media is app-private
 * plaintext; small catalog/thumbnail caches use Android Keystore encryption.
 */
public class PrivateGalleryActivity extends AppCompatActivity {
    private static final int PICK_VIDEO = 8151;
    private static final String ROOT = "PrivateLibrary";
    private static final String CATALOG = ROOT + "/catalog.json";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbs = Executors.newFixedThreadPool(3);
    private final Set<String> loadingThumbs = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> failedThumbs = Collections.synchronizedSet(new HashSet<>());
    private final List<JSONObject> items = new ArrayList<>();
    private final List<JSONObject> visible = new ArrayList<>();
    private final List<RemoteItem> cryptRemotes = new ArrayList<>();
    private Rclone rclone;
    private RemoteItem remote;
    private Spinner remoteSpinner;
    private EditText search;
    private TextView status;
    private Button add;
    private GalleryAdapter adapter;
    private volatile boolean indexLoaded;
    private volatile boolean busy;
    private volatile boolean disposed;
    private volatile int cacheGeneration;
    private volatile Thread activeThread;
    private final Object cacheLock = new Object();

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        setTitle("Private Video Library");
        rclone = new Rclone(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(8));
        remoteSpinner = new Spinner(this);
        root.addView(remoteSpinner, new LinearLayout.LayoutParams(-1, dp(52)));
        Button setup = new Button(this);
        setup.setText("Set up Drive / crypt remote");
        setup.setOnClickListener(v -> {
            if (busy) return;
            new AlertDialog.Builder(this).setTitle("Set up an encrypted vault")
                    .setMessage("Create Google Drive with your own OAuth client ID, then a crypt remote pointing at a dedicated test folder. Use standard filename encryption. Return to Private Video Library after setup.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open remote setup", (dialog, which) -> {
                        startActivity(new Intent(this, ca.pkay.rcloneexplorer.RemoteConfig.RemoteConfig.class));
                        finish();
                    }).show();
        });
        root.addView(setup);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        add = new Button(this);
        add.setText("+ Add video");
        Button refresh = new Button(this);
        refresh.setText("Refresh");
        actions.addView(add, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);
        LinearLayout privacy = new LinearLayout(this);
        Button clear = new Button(this);
        clear.setText("Clear local copies");
        clear.setOnClickListener(v -> {
            if (busy || remote == null) return;
            new AlertDialog.Builder(this).setTitle("Clear local copies?")
                    .setMessage("Remove cached catalog, thumbnails and offline videos for this remote from this device. Cloud files remain available.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Clear", (dialog, which) -> clearLocal()).show();
        });
        Button cancel = new Button(this);
        cancel.setText("Cancel transfer");
        cancel.setOnClickListener(v -> {
            Thread active = activeThread;
            if (active != null && busy) { active.interrupt(); setStatus("Cancelling; waiting for current I/O to stop…"); }
        });
        privacy.addView(clear, new LinearLayout.LayoutParams(0, -2, 1));
        privacy.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(privacy);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search your videos");
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        status = new TextView(this);
        status.setText("Select an encrypted remote");
        status.setPadding(dp(6), dp(8), dp(6), dp(8));
        root.addView(status);
        GridView gallery = new GridView(this);
        gallery.setNumColumns(Math.max(2, getResources().getConfiguration().screenWidthDp / 180));
        gallery.setHorizontalSpacing(dp(8));
        gallery.setVerticalSpacing(dp(12));
        gallery.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        adapter = new GalleryAdapter();
        gallery.setAdapter(adapter);
        root.addView(gallery, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        cryptRemotes.addAll(getCryptRemotes());
        List<String> names = new ArrayList<>();
        for (RemoteItem r : cryptRemotes) names.add(r.getName() + " (crypt)");
        if (names.isEmpty()) names.add("No crypt remote — configure one in Round Sync");
        remoteSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        remoteSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (pos < cryptRemotes.size()) chooseRemote(cryptRemotes.get(pos));
            }
        });
        add.setOnClickListener(v -> chooseVideo());
        refresh.setOnClickListener(v -> loadRemote());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        gallery.setOnItemClickListener((parent, view, position, id) -> play(visible.get(position)));
        gallery.setOnItemLongClickListener((parent, view, position, id) -> {
            showOfflineActions(visible.get(position));
            return true;
        });
        add.setEnabled(false);
        if (cryptRemotes.isEmpty()) status.setText("First create Google Drive + crypt remotes in Round Sync.");
    }

    private List<RemoteItem> getCryptRemotes() {
        List<RemoteItem> result = new ArrayList<>();
        for (RemoteItem r : rclone.getRemotes()) if (r.isCrypt()) result.add(r);
        return result;
    }

    private void chooseRemote(RemoteItem next) {
        if (busy) { status.setText("An upload/download is in progress"); return; }
        remote = next;
        indexLoaded = false;
        synchronized (items) { items.clear(); }
        loadingThumbs.clear();
        failedThumbs.clear();
        readLocalCatalog();
        filter(search.getText().toString());
        loadRemote();
    }

    private void setStatus(String s) {
        runOnUiThread(() -> { if (!disposed && !isFinishing()) status.setText(s); });
    }

    private void clearLocal() {
        boolean complete = true;
        synchronized (cacheLock) {
            cacheGeneration++;
            File[] files = vaultDir().listFiles();
            if (files != null) for (File file : files) if (file.isFile() && !file.delete()) complete = false;
        }
        synchronized (items) { items.clear(); }
        indexLoaded = false;
        add.setEnabled(false);
        loadingThumbs.clear();
        failedThumbs.clear();
        filter(search.getText().toString());
        setStatus(complete ? "Local copies cleared. Refresh to reload from cloud." : "Some files could not be removed; retry after reopening.");
    }

    private File vaultDir() {
        String hash = hashName(remote.getName());
        File dir = new File(getNoBackupFilesDir(), "private-gallery/" + hash);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create cache directory");
        return dir;
    }

    private static String hashName(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 12; i++) s.append(String.format(Locale.ROOT, "%02x", hash[i] & 0xff));
            return s.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private File thumbFile(String id) { return new File(vaultDir(), "thumb-" + id + ".enc"); }
    private File offlineFile(String id, String extension) {
        return new File(vaultDir(), "offline-" + id + extension);
    }

    private static boolean safeId(String id) { return GalleryPaths.safeId(id); }

    private void readLocalCatalog() {
        try {
            File f = new File(vaultDir(), "catalog.enc");
            if (!f.exists()) return;
            applyCatalog(new JSONObject(new String(PrivateCache.read(f, 8 * 1024 * 1024), StandardCharsets.UTF_8)));
        } catch (Exception ignored) { setStatus("Local catalog not available"); }
    }

    private void saveLocalCatalog(JSONObject index) throws Exception {
        byte[] bytes = index.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 8 * 1024 * 1024) throw new IllegalStateException("Catalog cache limit exceeded");
        PrivateCache.write(new File(vaultDir(), "catalog.enc"), bytes);
    }

    private void applyCatalog(JSONObject catalog) throws JSONException {
        List<JSONObject> parsed = GalleryCatalog.parse(catalog);
        synchronized (items) { items.clear(); items.addAll(parsed); }
    }

    /** Refresh requires verified remote listing before treating a missing catalog as empty. */
    private void loadRemote() {
        RemoteItem requested = remote;
        if (requested == null || busy) return;
        busy = true;
        indexLoaded = false;
        add.setEnabled(false);
        remoteSpinner.setEnabled(false);
        setStatus("Refreshing encrypted catalog…");
        io.execute(() -> {
            activeThread = Thread.currentThread();
            try {
                List<FileItem> root = rclone.getDirectoryContent(requested, "//" + requested.getName(), false);
                if (root == null) throw new IllegalStateException("Cannot read remote (offline or locked config)");
                boolean hasRoot = false;
                for (FileItem f : root) if (ROOT.equals(f.getName()) && f.isDir()) hasRoot = true;
                JSONObject catalog = new JSONObject().put("version", 1).put("videos", new JSONArray());
                if (hasRoot) {
                    List<FileItem> content = rclone.getDirectoryContent(requested, ROOT, false);
                    if (content == null) throw new IllegalStateException("Cannot read PrivateLibrary");
                    boolean exists = false;
                    for (FileItem f : content) if ("catalog.json".equals(f.getName())) exists = true;
                    if (exists) {
                        catalog = new JSONObject(new String(rclone.readGalleryObject(
                                requested.getName() + ":" + CATALOG, 8 * 1024 * 1024), StandardCharsets.UTF_8));
                        if (catalog.optInt("version") != 1) throw new JSONException("Unsupported legacy catalog");
                    }
                    boolean hasMetadata = false;
                    for (FileItem f : content) if ("metadata".equals(f.getName()) && f.isDir()) hasMetadata = true;
                    if (hasMetadata) {
                        List<FileItem> records = rclone.getDirectoryContent(requested, ROOT + "/metadata", false);
                        if (records == null || records.size() > 10000) throw new IllegalStateException("Cannot list metadata");
                        long totalBytes = catalog.toString().getBytes(StandardCharsets.UTF_8).length;
                        for (FileItem f : records) {
                            if (disposed || Thread.currentThread().isInterrupted()) throw new IllegalStateException("Refresh cancelled");
                            String name = f.getName();
                            if (f.isDir() || !name.endsWith(".json") || !safeId(name.substring(0, name.length() - 5)))
                                throw new JSONException("Unknown metadata object; import disabled");
                            File recordCache = new File(vaultDir(), "record-" + name.substring(0, name.length() - 5) + ".enc");
                            JSONObject cached = null;
                            try {
                                JSONObject envelope = new JSONObject(new String(PrivateCache.read(recordCache, 300 * 1024), StandardCharsets.UTF_8));
                                if (envelope.getLong("size") == f.getSize() && envelope.getLong("modified") == f.getModTime())
                                    cached = envelope.getJSONObject("record");
                            } catch (Exception ignored) { }
                            byte[] bytes = cached == null ? rclone.readGalleryObject(
                                    requested.getName() + ":" + ROOT + "/metadata/" + name, 256 * 1024)
                                    : cached.toString().getBytes(StandardCharsets.UTF_8);
                            totalBytes += bytes.length;
                            if (totalBytes > 8 * 1024 * 1024) throw new IllegalStateException("Catalog limit exceeded");
                            JSONObject record = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                            GalleryCatalog.appendRecord(catalog, name, record);
                            if (cached == null) PrivateCache.write(recordCache, new JSONObject()
                                    .put("size", f.getSize()).put("modified", f.getModTime()).put("record", record)
                                    .toString().getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
                catalog.put("version", 2);
                if (remote != requested || disposed) return;
                applyCatalog(catalog);
                saveLocalCatalog(catalog);
                indexLoaded = true;
                runOnUiThread(() -> { filter(search.getText().toString()); add.setEnabled(true); });
                setStatus("Catalog synchronized • " + items.size() + " videos");
            } catch (Exception e) {
                indexLoaded = false;
                runOnUiThread(() -> add.setEnabled(false));
                setStatus("Cannot sync: " + e.getMessage() + ". Offline cached list remains visible.");
            } finally {
                activeThread = null;
                busy = false;
                runOnUiThread(() -> { if (!disposed) { remoteSpinner.setEnabled(true); add.setEnabled(indexLoaded); } });
            }
        });
    }

    private void filter(String q) {
        String needle = q.toLowerCase(Locale.ROOT);
        synchronized (items) {
            visible.clear();
            for (JSONObject v : items) {
                if (v.optString("title").toLowerCase(Locale.ROOT).contains(needle)) visible.add(v);
            }
        }
        Collections.sort(visible, (a, b) -> Long.compare(b.optLong("added"), a.optLong("added")));
        adapter.notifyDataSetChanged();
    }

    private void chooseVideo() {
        if (!indexLoaded || busy || remote == null) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_VIDEO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_VIDEO && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            importVideo(data.getData());
        }
    }

    private void importVideo(Uri uri) {
        if (!indexLoaded || busy || remote == null) return;
        final RemoteItem target = remote;
        final String originalName = displayName(uri);
        final String id = UUID.randomUUID().toString();
        final String ext = extensionOf(originalName);
        String mime = getContentResolver().getType(uri);
        if (ext == null || mime == null || !mime.startsWith("video/")) {
            setStatus("Unsupported file. Choose MP4, M4V, MOV, WebM, MKV or 3GP video.");
            return;
        }
        final long size = videoSize(uri);
        if (size <= 0) { setStatus("Cannot determine video size; choose a local video file."); return; }
        final String videoPath = ROOT + "/videos/" + id + ext;
        final String imagePath = ROOT + "/thumbnails/" + id + ".jpg";
        busy = true;
        add.setEnabled(false);
        remoteSpinner.setEnabled(false);
        setStatus("Creating preview: " + originalName);
        io.execute(() -> {
            activeThread = Thread.currentThread();
            try {
                File tempThumb = thumbFile(id);
                long duration = makeThumbnail(uri, tempThumb);
                if (!rclone.makeDirectory(target, ROOT)
                        || !rclone.makeDirectory(target, ROOT + "/videos")
                        || !rclone.makeDirectory(target, ROOT + "/thumbnails")
                        || !rclone.makeDirectory(target, ROOT + "/metadata")) {
                    throw new IllegalStateException("Could not create encrypted folders");
                }
                setStatus("Uploading encrypted video: " + originalName + " (keep app open)");
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Cannot open source");
                    final long[] transferred = {0};
                    InputStream counted = new java.io.FilterInputStream(input) {
                        @Override public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
                            int n = in.read(bytes, offset, length);
                            if (n > 0) transferred[0] += n;
                            return n;
                        }
                    };
                    if (!rclone.uploadFromInputStreamChecked(target.getName() + ":" + videoPath, counted)
                            || transferred[0] != size)
                        throw new IllegalStateException("Video upload failed");
                }
                setStatus("Uploading encrypted thumbnail…");
                try (InputStream input = new ByteArrayInputStream(PrivateCache.read(tempThumb, 2 * 1024 * 1024))) {
                    if (!rclone.uploadFromInputStreamChecked(target.getName() + ":" + imagePath, input))
                        throw new IllegalStateException("Thumbnail upload failed");
                }
                // Write index last. Unindexed leftovers are intentionally not silently deleted.
                JSONObject newVideo = new JSONObject()
                        .put("id", id).put("title", originalName).put("durationMs", duration)
                        .put("path", videoPath).put("thumb", imagePath)
                        .put("extension", ext).put("size", size).put("mime", mime)
                        .put("tags", new JSONArray()).put("added", System.currentTimeMillis());
                JSONObject index = new JSONObject().put("version", 2);
                JSONArray arr = new JSONArray();
                synchronized (items) {
                    for (JSONObject v : items) arr.put(v);
                }
                arr.put(newVideo);
                index.put("videos", arr);
                setStatus("Updating encrypted catalog…");
                if (disposed || Thread.currentThread().isInterrupted()) throw new IllegalStateException("Import cancelled");
                byte[] contents = new JSONObject().put("version", 2).put("video", newVideo)
                        .toString().getBytes(StandardCharsets.UTF_8);
                if (!rclone.uploadFromInputStreamChecked(target.getName() + ":" + ROOT + "/metadata/" + id + ".json", new ByteArrayInputStream(contents)))
                    throw new IllegalStateException("Metadata upload failed (video may be orphaned; refresh before retry)");
                applyCatalog(index);
                saveLocalCatalog(index);
                runOnUiThread(() -> filter(search.getText().toString()));
                setStatus("Added: " + originalName);
            } catch (Exception e) {
                setStatus("Import failed: " + e.getMessage());
            } finally {
                activeThread = null;
                busy = false;
                runOnUiThread(() -> { add.setEnabled(indexLoaded); remoteSpinner.setEnabled(true); });
            }
        });
    }

    private long makeThumbnail(Uri uri, File file) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration = value == null ? 0 : Long.parseLong(value);
            Bitmap frame = retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) throw new IllegalStateException("Could not extract a video frame");
            int w = frame.getWidth(), h = frame.getHeight();
            float scale = Math.min(1f, 360f / Math.max(w, h));
            Bitmap thumb = Bitmap.createScaledBitmap(frame, Math.max(1, (int)(w * scale)),
                    Math.max(1, (int)(h * scale)), true);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (!thumb.compress(Bitmap.CompressFormat.JPEG, 75, out)) throw new IllegalStateException("JPEG failed");
                PrivateCache.write(file, out.toByteArray());
            }
            if (thumb != frame) thumb.recycle();
            frame.recycle();
            return duration;
        } finally { retriever.release(); }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && c.getString(index) != null && !c.getString(index).isEmpty())
                    return c.getString(index).substring(0, Math.min(1024, c.getString(index).length()));
            }
        } catch (Exception ignored) { }
        return "Video " + System.currentTimeMillis() + ".mp4";
    }

    private static boolean isAllowedExtension(String extension) {
        for (String ext : new String[]{".mp4", ".m4v", ".mov", ".webm", ".mkv", ".3gp"}) {
            if (ext.equals(extension)) return true;
        }
        return false;
    }

    private String extensionOf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".mp4", ".m4v", ".mov", ".webm", ".mkv", ".3gp"})
            if (lower.endsWith(ext)) return ext;
        // Unknown formats may not be playable in Android's built-in player.
        return null;
    }

    private long videoSize(Uri uri) {
        try (android.content.res.AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(uri, "r")) {
            if (fd != null && fd.getLength() > 0) return fd.getLength();
        } catch (Exception ignored) { }
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) { }
        return -1;
    }

    private void play(JSONObject v) {
        if (remote == null || !safeId(v.optString("id"))) return;
        Intent intent = new Intent(this, PrivateGalleryPlayerActivity.class);
        intent.putExtra("remote", remote.getName());
        intent.putExtra("path", v.optString("path"));
        intent.putExtra("title", v.optString("title"));
        File local = offlineFile(v.optString("id"), v.optString("extension", ".mp4"));
        if (local.isFile()) intent.putExtra("local", local.getAbsolutePath());
        startActivity(intent);
    }

    private void showOfflineActions(JSONObject video) {
        String id = video.optString("id");
        if (!safeId(id)) return;
        String ext = video.optString("extension", ".mp4");
        File local = offlineFile(id, ext);
        if (local.exists()) {
            new AlertDialog.Builder(this).setTitle(video.optString("title"))
                    .setItems(new String[]{"Play", "Remove offline copy"}, (d, which) -> {
                        if (which == 0) play(video);
                        else { if (local.delete()) { adapter.notifyDataSetChanged(); setStatus("Offline copy removed"); } }
                    }).show();
        } else {
            new AlertDialog.Builder(this).setTitle(video.optString("title"))
                    .setItems(new String[]{"Play from cloud", "Download for offline playback"}, (d, which) -> {
                        if (which == 0) play(video);
                        else new AlertDialog.Builder(this).setTitle("Offline privacy")
                                .setMessage("Offline videos use Android app-private storage without extra vault encryption. Catalog and thumbnails are encrypted with Android Keystore. Continue on this device?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Download", (dialog, button) -> downloadOffline(video, local)).show();
                    }).show();
        }
    }

    private void downloadOffline(JSONObject video, File local) {
        if (busy || remote == null) return;
        final RemoteItem target = remote;
        busy = true;
        add.setEnabled(false);
        remoteSpinner.setEnabled(false);
        setStatus("Downloading encrypted video for offline playback…");
        io.execute(() -> {
            activeThread = Thread.currentThread();
            File temp = new File(local.getAbsolutePath() + ".part");
            try {
                long expected = video.optLong("size", -1);
                if (expected <= 0) throw new IllegalStateException("Size unknown; re-import before offline download");
                if (local.getParentFile().getUsableSpace() - 64L * 1024 * 1024 < expected)
                    throw new IllegalStateException("Not enough free storage (64 MB reserve required)");
                if (!rclone.downloadToFileChecked(target.getName() + ":" + video.optString("path"), temp))
                    throw new IllegalStateException("Download failed");
                if (temp.length() != expected) throw new IllegalStateException("Incomplete download; retry");
                if (!temp.renameTo(local)) throw new IllegalStateException("Could not save offline copy");
                setStatus("Available offline: " + video.optString("title"));
                runOnUiThread(adapter::notifyDataSetChanged);
            } catch (Exception e) { temp.delete(); setStatus(e.getMessage()); }
            finally { activeThread = null; busy = false; runOnUiThread(() -> { if (!disposed) { add.setEnabled(indexLoaded); remoteSpinner.setEnabled(true); } }); }
        });
    }

    private static byte[] readAll(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[32768];
        int n;
        while ((n = in.read(chunk)) != -1) {
            if (out.size() + n > maxBytes) throw new IllegalStateException("Catalog too large");
            out.write(chunk, 0, n);
        }
        return out.toByteArray();
    }

    private int dp(float value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }

    private class GalleryAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Object getItem(int pos) { return visible.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override public View getView(int pos, View recycled, ViewGroup parent) {
            JSONObject v = visible.get(pos);
            LinearLayout item = new LinearLayout(PrivateGalleryActivity.this);
            item.setOrientation(LinearLayout.VERTICAL);
            ImageView image = new ImageView(PrivateGalleryActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xff30353b);
            item.addView(image, new LinearLayout.LayoutParams(-1, dp(112)));
            String id = v.optString("id");
            if (safeId(id)) {
                File file = thumbFile(id);
                if (file.isFile()) {
                    try {
                        byte[] bytes = PrivateCache.read(file, 2 * 1024 * 1024);
                        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 1024 || bounds.outHeight > 1024)
                            throw new IllegalStateException("Invalid thumbnail dimensions");
                        Bitmap b = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (b != null) image.setImageBitmap(b);
                    } catch (Exception e) { file.delete(); }
                } else if (!disposed && indexLoaded && !failedThumbs.contains(id) && loadingThumbs.add(id)) {
                    String path = v.optString("thumb");
                    RemoteItem target = remote;
                    int generation = cacheGeneration;
                    if (target != null && path.startsWith(ROOT + "/thumbnails/")) {
                        thumbs.execute(() -> {
                            File temp = new File(file.getAbsolutePath() + ".part");
                            try {
                                byte[] bytes = rclone.readGalleryObject(target.getName() + ":" + path, 2 * 1024 * 1024);
                                synchronized (cacheLock) {
                                    if (!disposed && generation == cacheGeneration) PrivateCache.write(file, bytes);
                                }
                                runOnUiThread(() -> { if (!disposed) notifyDataSetChanged(); });
                            } catch (Exception e) { temp.delete(); failedThumbs.add(id); }
                            finally { loadingThumbs.remove(id); }
                        });
                    } else loadingThumbs.remove(id);
                }
            }
            TextView title = new TextView(PrivateGalleryActivity.this);
            title.setMaxLines(2);
            title.setText(v.optString("title", "Untitled video"));
            title.setPadding(dp(3), dp(5), dp(3), dp(2));
            item.addView(title);
            TextView meta = new TextView(PrivateGalleryActivity.this);
            long seconds = v.optLong("durationMs") / 1000;
            boolean offline = offlineFile(id, v.optString("extension", ".mp4")).isFile();
            meta.setText(String.format(Locale.ROOT, "%d:%02d  •  %s", seconds / 60, seconds % 60,
                    offline ? "Offline" : "Cloud"));
            meta.setTextSize(12);
            item.addView(meta);
            return item;
        }
    }

    @Override protected void onDestroy() {
        disposed = true;
        super.onDestroy();
        io.shutdownNow();
        thumbs.shutdownNow();
    }
}
