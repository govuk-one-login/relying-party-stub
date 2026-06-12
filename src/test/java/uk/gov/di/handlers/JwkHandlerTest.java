package uk.gov.di.handlers;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.di.helpers.keyHelper.generateRsaKeyPair;

public class JwkHandlerTest {
    private final RSAPublicKey TEST_PUBLIC_KEY = (RSAPublicKey) generateRsaKeyPair().getPublic();

    private final Map<String, Serializable> JWKS_CONFIG =
            new HashMap<>() {
                {
                    put("client_id", "some-client-id");
                    put("public_key", TEST_PUBLIC_KEY);
                    put("public_key_id", "12345");
                }
            };

    private JwkHandler jwkHandler;

    @BeforeEach
    void globalSetup() {
        jwkHandler = new JwkHandler(JWKS_CONFIG);
    }

    @Test
    void shouldReturnAJwkResponse() throws ParseException {
        var mockCtx = mock(Context.class);

        jwkHandler.handle(mockCtx);

        verify(mockCtx).header(eq("Cache-Control"), eq("max-age=86400"));
        verify(mockCtx).contentType("application/json");
        verify(mockCtx).json(eq(expectedJWK()));
    }

    private Map<String, Object> expectedJWK() throws ParseException {
        RSAKey key =
                new RSAKey.Builder((RSAPublicKey) JWKS_CONFIG.get("public_key"))
                        .keyID(JWKS_CONFIG.get("public_key_id").toString())
                        .keyUse(KeyUse.SIGNATURE)
                        .build();

        List<JWK> keys = new ArrayList<>();
        keys.add(JWK.parse(key.toString()));
        JWKSet jwkSet = new JWKSet(keys);
        return jwkSet.toJSONObject(true);
    }
}
