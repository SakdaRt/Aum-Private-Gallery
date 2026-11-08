package ca.pkay.rcloneexplorer.Activities;

import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.Rclone;
import ca.pkay.rcloneexplorer.gallery.GalleryPaths;

/** Session-authenticated, loopback-only, uncached range playback. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PrivateGalleryPlayerActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Process server;
    private volatile boolean disposed;
    private ExoPlayer player;
    private PlayerView view;
    private TextView message;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setTitle(getIntent().getStringExtra("title"));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        message = new TextView(this);
        message.setText("Preparing video…");
        panel.addView(message);
        view = new PlayerView(this);
        panel.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(panel);
        String local = getIntent().getStringExtra("local");
        if (local != null) {
            try {
                File file = new File(local);
                if (!file.isFile() || !file.getCanonicalPath().startsWith(
                        new File(getNoBackupFilesDir(), "private-gallery").getCanonicalPath() + "/")
                        || !file.getName().startsWith("offline-") || file.getName().endsWith(".part"))
                    throw new IllegalArgumentException();
                prepare(Uri.fromFile(file), null);
            } catch (Exception e) { message.setText("Offline copy unavailable"); }
            return;
        }
        String remote = getIntent().getStringExtra("remote");
        String path = getIntent().getStringExtra("path");
        if (remote == null || !GalleryPaths.videoPath(path)) {
            message.setText("Invalid video path");
            return;
        }
        worker.execute(() -> openStream(remote, path));
    }

    private void prepare(Uri uri, @Nullable String authorization) {
        if (disposed) return;
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000).setReadTimeoutMs(60000)
                .setAllowCrossProtocolRedirects(false);
        if (authorization != null) http.setDefaultRequestProperties(
                Collections.singletonMap("Authorization", authorization));
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(
                new DefaultDataSource.Factory(this, http))).build();
        view.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                message.setText("Playback failed. Check network and Android codec support; try MP4/H.264/AAC.");
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) message.setText("Playback ready");
            }
        });
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.play();
    }

    private void openStream(String name, String path) {
        Process process = null;
        try {
            Rclone rclone = new Rclone(getApplicationContext());
            RemoteItem remote = rclone.getRemoteItemFromName(name);
            if (remote == null || !remote.isCrypt()) throw new IllegalStateException();
            int port;
            try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                port = socket.getLocalPort();
            }
            String user = UUID.randomUUID().toString();
            String password = UUID.randomUUID().toString();
            String prefix = UUID.randomUUID().toString();
            String auth = "Basic " + Base64.encodeToString((user + ":" + password)
                    .getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            process = rclone.servePrivateGallery(remote, port, user, password, prefix);
            server = process;
            if (disposed) return;
            Uri uri = Uri.parse("http://127.0.0.1:" + port).buildUpon().appendPath(prefix)
                    .appendPath(path.substring("PrivateLibrary/videos/".length())).build();
            for (int attempt = 0; attempt < 40 && !disposed; attempt++) {
                try { process.exitValue(); throw new IllegalStateException(); }
                catch (IllegalThreadStateException running) { /* Process is still alive on API23+. */ }
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(uri.toString()).openConnection(Proxy.NO_PROXY);
                    conn.setRequestMethod("HEAD");
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("Authorization", auth);
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(1500);
                    int response = conn.getResponseCode();
                    if (response == 200 || response == 206) {
                        runOnUiThread(() -> prepare(uri, auth));
                        process = null;
                        return;
                    }
                    if (response == 401 || response == 403 || response == 404) throw new IllegalStateException();
                } catch (java.io.IOException ignored) { }
                finally { if (conn != null) conn.disconnect(); }
                Thread.sleep(250);
            }
            throw new IllegalStateException();
        } catch (Exception e) {
            runOnUiThread(() -> { if (!disposed) message.setText("Cannot open cloud video. Check remote, network and permissions."); });
        } finally { if (process != null) process.destroy(); }
    }

    @Override protected void onStop() {
        disposed = true;
        if (player != null) { player.release(); player = null; }
        if (server != null) server.destroy();
        worker.shutdownNow();
        super.onStop();
        finish();
    }
}
