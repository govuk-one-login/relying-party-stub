package uk.gov.di.utils;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.di.helpers.keyHelper.generateRsaKeyPair;

public class PrivateKeyReaderTest {
    private final KeyPair TEST_RSA_KEY_PAIR = generateRsaKeyPair();
    private final RSAPrivateKey testPrivateKey = (RSAPrivateKey) TEST_RSA_KEY_PAIR.getPrivate();
    private final String serializedPrivateKey =
            Base64.getMimeEncoder().encodeToString(testPrivateKey.getEncoded());
    private final RSAPublicKey publicKey = (RSAPublicKey) TEST_RSA_KEY_PAIR.getPublic();
    private final PrivateKeyReader privateKeyReader = new PrivateKeyReader(serializedPrivateKey);

    @Test
    void shouldParsePrivateKeyFromString() {
        var receivedPrivateKey = privateKeyReader.get();
        assertEquals(receivedPrivateKey, testPrivateKey);
    }

    @Test
    void shouldReturnPublicKeyFromPrivate() {
        var receivedPublicKey = privateKeyReader.getPublicKey();
        assertEquals(receivedPublicKey, publicKey);
    }
}
