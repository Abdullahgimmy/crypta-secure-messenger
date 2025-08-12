// Single-file demo: Android Activity that simulates a hybrid-like handshake using X25519 (ECDH) // and then encrypts/decrypts a message with AES-256-GCM. // Uses BouncyCastle (bcprov) lightweight API for X25519 + HKDF.

package com.example.pqc_demo;

import android.os.Bundle; import android.util.Base64; import android.view.View; import android.widget.Button; import android.widget.EditText; import android.widget.TextView; import androidx.appcompat.app.AppCompatActivity;

import org.bouncycastle.crypto.agreement.X25519Agreement; import org.bouncycastle.crypto.digests.SHA256Digest; import org.bouncycastle.crypto.generators.HKDFBytesGenerator; import org.bouncycastle.crypto.params.HKDFParameters; import org.bouncycastle.crypto.params.X25519PrivateKeyParameters; import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import java.nio.charset.StandardCharsets; import java.security.SecureRandom;

import javax.crypto.Cipher; import javax.crypto.spec.GCMParameterSpec; import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity { // UI TextView tvInfo; EditText etPlain; Button btnRun;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    tvInfo = findViewById(R.id.tvInfo);
    etPlain = findViewById(R.id.etPlain);
    btnRun = findViewById(R.id.btnRun);

    // Ensure BouncyCastle provider is added in your app's Application class or here if needed.
    // For the lightweight API we use here (org.bouncycastle.crypto.*) you don't need the provider.

    btnRun.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                runDemo();
            } catch (Exception e) {
                tvInfo.setText("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    });
}

private void runDemo() throws Exception {
    SecureRandom random = new SecureRandom();

    // 1) Generate X25519 keypairs for Alice and Bob
    X25519PrivateKeyParameters alicePriv = new X25519PrivateKeyParameters(random);
    X25519PublicKeyParameters alicePub = alicePriv.generatePublicKey();

    X25519PrivateKeyParameters bobPriv = new X25519PrivateKeyParameters(random);
    X25519PublicKeyParameters bobPub = bobPriv.generatePublicKey();

    // 2) Compute shared secrets
    byte[] aliceShared = new byte[32];
    X25519Agreement aAgree = new X25519Agreement();
    aAgree.init(alicePriv);
    aAgree.calculateAgreement(bobPub, aliceShared, 0);

    byte[] bobShared = new byte[32];
    X25519Agreement bAgree = new X25519Agreement();
    bAgree.init(bobPriv);
    bAgree.calculateAgreement(alicePub, bobShared, 0);

    // They should be equal
    if (!java.util.Arrays.equals(aliceShared, bobShared))
        throw new IllegalStateException("Shared secrets differ!");

    // 3) Derive symmetric key using HKDF-SHA256
    byte[] salt = null; // can be set to context-specific value
    byte[] info = "X25519-HKDF-AES-Session".getBytes(StandardCharsets.UTF_8);
    HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
    hkdf.init(new HKDFParameters(aliceShared, salt, info));
    byte[] sessionKey = new byte[32]; // 256-bit key
    hkdf.generateBytes(sessionKey, 0, sessionKey.length);

    // Display session key (Base64) for demo only
    String skB64 = Base64.encodeToString(sessionKey, Base64.NO_WRAP);

    // 4) Encrypt user message with AES-256-GCM
    String plain = etPlain.getText().toString();
    if (plain.length() == 0) plain = "Hello from Alice!";
    byte[] iv = new byte[12]; // 96-bit IV recommended for GCM
    random.nextBytes(iv);

    byte[] cipherText = aesGcmEncrypt(sessionKey, iv, plain.getBytes(StandardCharsets.UTF_8));

    // Pack iv + ciphertext for transport
    byte[] packaged = new byte[iv.length + cipherText.length];
    System.arraycopy(iv, 0, packaged, 0, iv.length);
    System.arraycopy(cipherText, 0, packaged, iv.length, cipherText.length);

    String packagedB64 = Base64.encodeToString(packaged, Base64.NO_WRAP);

    // 5) Bob decrypts
    byte[] recvPack = Base64.decode(packagedB64, Base64.NO_WRAP);
    byte[] recvIv = new byte[12];
    System.arraycopy(recvPack, 0, recvIv, 0, 12);
    byte[] recvCt = new byte[recvPack.length - 12];
    System.arraycopy(recvPack, 12, recvCt, 0, recvCt.length);

    byte[] decrypted = aesGcmDecrypt(sessionKey, recvIv, recvCt);
    String decryptedText = new String(decrypted, StandardCharsets.UTF_8);

    // Show results
    StringBuilder sb = new StringBuilder();
    sb.append("Alice public (base64):\n").append(Base64.encodeToString(alicePub.getEncoded(), Base64.NO_WRAP)).append("\n\n");
    sb.append("Bob public (base64):\n").append(Base64.encodeToString(bobPub.getEncoded(), Base64.NO_WRAP)).append("\n\n");
    sb.append("Session key (HKDF, base64):\n").append(skB64).append("\n\n");
    sb.append("Encrypted (iv+cipher, base64):\n").append(packagedB64).append("\n\n");
    sb.append("Decrypted text:\n").append(decryptedText).append("\n");

    tvInfo.setText(sb.toString());
}

private byte[] aesGcmEncrypt(byte[] key, byte[] iv, byte[] plaintext) throws Exception {
    SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
    return cipher.doFinal(plaintext);
}

private byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] ciphertext) throws Exception {
    SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
    cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
    return cipher.doFinal(ciphertext);
}

}