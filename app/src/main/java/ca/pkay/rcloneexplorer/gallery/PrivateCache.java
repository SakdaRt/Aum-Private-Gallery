package ca.pkay.rcloneexplorer.gallery;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Small catalog/thumbnail objects only. No plaintext temporary file is written. */
public final class PrivateCache {
    private static final String ALIAS = "aum-gallery-cache-v1";
    private PrivateCache() { }
    private static synchronized SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public static void write(File file, byte[] bytes) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(file.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream out = atomic.startWrite();
        try {
            out.write(cipher.getIV()); // Android Keystore GCM uses a 12-byte nonce
            out.write(cipher.doFinal(bytes));
            atomic.finishWrite(out);
        } catch (Exception e) { atomic.failWrite(out); throw e; }
    }
    public static byte[] read(File file, int max) throws Exception {
        AtomicFile atomic = new AtomicFile(file);
        try (FileInputStream in = atomic.openRead()) {
            long length = in.getChannel().size();
            if (length < 28 || length > max + 28L) throw new java.io.IOException("Invalid encrypted cache size");
            byte[] all = new byte[(int) length];
            new java.io.DataInputStream(in).readFully(all);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            cipher.updateAAD(file.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return cipher.doFinal(all, 12, all.length - 12);
        }
    }
}
