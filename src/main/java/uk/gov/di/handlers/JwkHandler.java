package uk.gov.di.handlers;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.Serializable;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JwkHandler implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(JwkHandler.class);
    private final JWKSet jwkSet;

    public JwkHandler(Map<String, Serializable> jwksConfig) {
        LOG.info("in jwkhandler");
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

    @Override
    public Object handle(Request request, Response response) {

        response.type("application/json");
        response.header("Cache-Control", "max-age=86400");
        LOG.info("returning jwk: " + jwkSet.toJSONObject(true));
        return jwkSet.toJSONObject(true);
    }
}
