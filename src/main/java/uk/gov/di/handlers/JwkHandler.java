package uk.gov.di.handlers;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.javalin.http.Context;

import java.io.Serializable;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JwkHandler {
    private final JWKSet jwkSet;

    public JwkHandler(Map<String, Serializable> jwksConfig) {
        RSAKey key =
                new RSAKey.Builder((RSAPublicKey) jwksConfig.get("public_key"))
                        .keyID(jwksConfig.get("public_key_id").toString())
                        .keyUse(KeyUse.SIGNATURE)
                        .build();

        List<JWK> keys = new ArrayList<>();
        try {
            keys.add(JWK.parse(key.toString()));
            this.jwkSet = new JWKSet(keys);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public void handle(Context ctx) {
        ctx.contentType("application/json");
        ctx.header("Cache-Control", "max-age=86400");
        ctx.json(jwkSet.toJSONObject(true));
    }
}
