package uk.gov.di.utils;

import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.OIDCClaimsRequest;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSetRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.di.config.RPConfig;

import java.net.URISyntaxException;
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
    void shouldCreateJARWithExpectedUserInfoClaimsWhenNoPkceAttributesProvided()
            throws ParseException {
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
                        "123",
                        null,
                        null,
                        null,
                        "web");

        var jarClaims = authorizeRequest.getRequestObject().getJWTClaimsSet();
        var expectedClaims = new OIDCClaimsRequest().withUserInfoClaimsRequest(testClaimSetRequest);
        assertEquals(expectedClaims.toJSONString(), jarClaims.getStringClaim("claims"));
        assertNull(jarClaims.getClaim("id_token_hint"));
        assertNull(jarClaims.getClaim("rp_sid"));

        assertNull(jarClaims.getClaim("code_challenge"));
        assertNull(jarClaims.getClaim("code_challenge_method"));
    }

    @Test
    void shouldCreateJARWithExpectedUserInfoClaimsWhenPkceAttributesProvided()
            throws ParseException {
        var codeChallengeMethod = CodeChallengeMethod.S256;
        var codeVerifier = new CodeVerifier();

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
                        "123",
                        codeChallengeMethod,
                        codeVerifier,
                        null,
                        "web");

        var jarClaims = authorizeRequest.getRequestObject().getJWTClaimsSet();
        var expectedClaims = new OIDCClaimsRequest().withUserInfoClaimsRequest(testClaimSetRequest);
        assertEquals(expectedClaims.toJSONString(), jarClaims.getStringClaim("claims"));
        assertNull(jarClaims.getClaim("id_token_hint"));
        assertNull(jarClaims.getClaim("rp_sid"));

        var expectedCodeChallengeClaim =
                CodeChallenge.compute(codeChallengeMethod, codeVerifier).getValue();

        assertEquals(expectedCodeChallengeClaim, jarClaims.getClaim("code_challenge"));
        assertEquals(codeChallengeMethod.getValue(), jarClaims.getClaim("code_challenge_method"));
    }

    @Test
    void
            shouldCreateQueryParamAuthorizeRequestWithExpectedUserInfoClaimsWhenNoPkceAttributesProvided()
                    throws ParseException, URISyntaxException {
        var authorizeRequest =
                oidc.buildQueryParamAuthorizeRequest(
                        testCallbackUri,
                        testVtr,
                        testScopes,
                        testClaimSetRequest,
                        "en",
                        "login",
                        "",
                        "123",
                        null,
                        null,
                        "web");

        var jarClaims = authorizeRequest.toJWTClaimsSet();
        var expectedClaims = new OIDCClaimsRequest().withUserInfoClaimsRequest(testClaimSetRequest);
        assertEquals(expectedClaims.toJSONString(), jarClaims.getStringClaim("claims"));
        assertNull(jarClaims.getClaim("id_token_hint"));
        assertNull(jarClaims.getClaim("rp_sid"));

        assertNull(jarClaims.getClaim("code_challenge"));
        assertNull(jarClaims.getClaim("code_challenge_method"));
    }

    @Test
    void
            shouldCreateQueryParamAuthorizeRequestWithExpectedUserInfoClaimsWhenPkceAttributesProvided()
                    throws ParseException, URISyntaxException {
        var codeChallengeMethod = CodeChallengeMethod.S256;
        var codeVerifier = new CodeVerifier();

        var authorizeRequest =
                oidc.buildQueryParamAuthorizeRequest(
                        testCallbackUri,
                        testVtr,
                        testScopes,
                        testClaimSetRequest,
                        "en",
                        "login",
                        "",
                        "123",
                        codeChallengeMethod,
                        codeVerifier,
                        "web");

        var jarClaims = authorizeRequest.toJWTClaimsSet();
        var expectedClaims = new OIDCClaimsRequest().withUserInfoClaimsRequest(testClaimSetRequest);
        assertEquals(expectedClaims.toJSONString(), jarClaims.getStringClaim("claims"));
        assertNull(jarClaims.getClaim("id_token_hint"));
        assertNull(jarClaims.getClaim("rp_sid"));

        var expectedCodeChallengeClaim =
                CodeChallenge.compute(codeChallengeMethod, codeVerifier).getValue();

        assertEquals(expectedCodeChallengeClaim, jarClaims.getClaim("code_challenge"));
        assertEquals(codeChallengeMethod.getValue(), jarClaims.getClaim("code_challenge_method"));
    }
}
