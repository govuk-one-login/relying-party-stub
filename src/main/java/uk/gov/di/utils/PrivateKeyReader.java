package uk.gov.di.utils;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

public class PrivateKeyReader {
    private String privateKey;
    private KeyFactory kf;

    public PrivateKeyReader(String privateKey) {
        try {
            this.privateKey = privateKey;
            this.kf = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public RSAPrivateKey get() {
        try {
            return (RSAPrivateKey)
                    kf.generatePrivate(new PKCS8EncodedKeySpec(format(this.privateKey)));
        } catch (InvalidKeySpecException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public RSAPublicKey getPublicKey() {
        try {
            var privateKey = (RSAPrivateCrtKey) get();
            RSAPublicKeySpec publicKeySpec =
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
            return (RSAPublicKey) kf.generatePublic(publicKeySpec);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] format(String privateKey) throws IOException {
        var parse = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*");
        var encoded = parse.matcher(privateKey).replaceFirst("$1");

        return Base64.getMimeDecoder().decode(encoded);
    }
}
