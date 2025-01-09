package uk.gov.di.utils;

import com.nimbusds.openid.connect.sdk.OIDCClaimsRequest;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSetRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.di.config.RPConfig;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.text.ParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.di.helpers.keyHelper.generateRsaKeyPair;

public class OidcTest {
    private RPConfig rpConfig = mock(RPConfig.class);
    private final KeyPair TEST_RSA_KEY_PAIR = generateRsaKeyPair();
    private final RSAPrivateKey testPrivateKey = (RSAPrivateKey) TEST_RSA_KEY_PAIR.getPrivate();
    private final String serializedPrivateKey =
            Base64.getMimeEncoder().encodeToString(testPrivateKey.getEncoded());
    private final String testClientId = "aClient";
    private final String testCallbackUri = "https://example.com/authentication-callback";
    private final String testVtr = "[\"Cl.Cm\"]";
    private final List<String> testScopes = List.of("openid", "phone");
    private final ClaimsSetRequest testClaimSetRequest =
            new ClaimsSetRequest()
                    .add(new ClaimsSetRequest.Entry("https://vocab.example.com/v1/permit"));
    private Oidc oidc;

    @BeforeEach
    void setup() {
        when(rpConfig.clientPrivateKey()).thenReturn(serializedPrivateKey);
        when(rpConfig.clientId()).thenReturn(testClientId);
        when(rpConfig.jwksConfiguration()).thenReturn(Map.of("public_key_id", "12345"));
        when(rpConfig.opBaseUrl()).thenReturn("https://oidc.sandpit.account.gov.uk/");
        oidc = new Oidc(rpConfig);
    }

    @Test
    void shouldCreateJARWithExpectedUserInfoClaims() throws ParseException {
        var authorizeRequest =
                oidc.buildJarAuthorizeRequest(
                        testCallbackUri,
                        testVtr,
                        testScopes,
                        testClaimSetRequest,
                        "en",
                        "login",
                        "",
                        "",
                        "123");

        var jarClaims = authorizeRequest.getRequestObject().getJWTClaimsSet();
        var expectedClaims = new OIDCClaimsRequest().withUserInfoClaimsRequest(testClaimSetRequest);
        assertEquals(expectedClaims.toJSONString(), jarClaims.getStringClaim("claims"));
        assertNull(jarClaims.getClaim("id_token_hint"));
        assertNull(jarClaims.getClaim("rp_sid"));
    }
}
