package uk.gov.di.utils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.gov.di.config.RPConfig;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CoreIdentityValidatorTest {
    private static ECKey SIGNING_KEY;
    private static final String SIGNING_KEY_ID = "signing_key_kid";
    private static final String CONTROLLER = "did:web:identity.test.account.gov.uk";
    private static final String DID_URL =
            "https://identity.test.account.gov.uk/.well-known/did.json";
    private static CoreIdentityValidator underTest;
    private static final HttpClient httpClient = mock(HttpClient.class);
    private static MockedStatic<HttpClient> httpClientMockedStatic;

    @BeforeAll
    static void beforeAll() {
        httpClientMockedStatic = mockStatic(HttpClient.class);
        httpClientMockedStatic.when(HttpClient::newHttpClient).thenReturn(httpClient);
    }

    @BeforeEach
    void setUp() throws JOSEException {
        SIGNING_KEY =
                new ECKeyGenerator(Curve.P_256)
                        .keyUse(KeyUse.SIGNATURE)
                        .keyID(SIGNING_KEY_ID)
                        .generate();

        var rpConfig = mock(RPConfig.class);
        when(rpConfig.identitySigningKeyUrl()).thenReturn(DID_URL);
        underTest = CoreIdentityValidator.createValidator(rpConfig);
    }

    @AfterAll
    static void tearDown() {
        httpClientMockedStatic.close();
    }

    @Test
    void createValidatorReturnsNoopValidatorWhenDidUrlIsNull() {
        // given
        var rpConfig = mock(RPConfig.class);
        when(rpConfig.identitySigningKeyUrl()).thenReturn(null);

        // when
        var validator = CoreIdentityValidator.createValidator(rpConfig);

        // then
        assertInstanceOf(CoreIdentityValidator.NoopCoreIdentityValidator.class, validator);
    }

    @Test
    void noopValidatorReturnsNotValidated() {
        // given
        var rpConfig = mock(RPConfig.class);
        when(rpConfig.identitySigningKeyUrl()).thenReturn(null);
        var validator = CoreIdentityValidator.createValidator(rpConfig);

        // when
        var result = validator.isValid(createJws("kid"));

        // then
        assertEquals(CoreIdentityValidator.Result.NOT_VALIDATED, result);
    }

    @Test
    void createValidatorReturnsValidatorWhenDidUrlIsPresent() {
        // given
        var rpConfig = mock(RPConfig.class);
        when(rpConfig.identitySigningKeyUrl()).thenReturn("http://example.com");

        // when
        var validator = CoreIdentityValidator.createValidator(rpConfig);

        // then
        assertInstanceOf(CoreIdentityValidator.class, validator);
    }

    @Test
    void succeedsWhenSignatureIsValid() {
        configureDidDocumentResponse(SIGNING_KEY, CONTROLLER);
        var result = underTest.isValid(createJws(CONTROLLER + "#" + SIGNING_KEY_ID));
        assertEquals(CoreIdentityValidator.Result.VALID, result);
    }

    @Test
    void failsWhenSignatureIsInvalid() throws JOSEException {
        var didSigningKey =
                new ECKeyGenerator(Curve.P_256)
                        .keyUse(KeyUse.SIGNATURE)
                        .keyID(SIGNING_KEY_ID)
                        .generate();
        configureDidDocumentResponse(didSigningKey, CONTROLLER);
        var result = underTest.isValid(createJws(CONTROLLER + "#" + SIGNING_KEY_ID));

        assertEquals(CoreIdentityValidator.Result.INVALID, result);
    }

    @Test
    void throwsExceptionWhenDidFetchErrors() throws IOException, InterruptedException {
        var httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("Bad Request");
        when(httpClient.send(any(), any())).thenReturn(httpResponse);

        var thrownException =
                assertThrows(
                        RuntimeException.class,
                        () -> underTest.isValid(createJws("controller#id")));
        assertEquals(
                "DID document could not be fetched. Status code: 400 - Bad Request",
                thrownException.getMessage());
    }

    @Test
    void throwsExceptionIfKidNotPresentInJws() {
        assertThrows(
                RuntimeException.class,
                () -> underTest.isValid(createJws(null)),
                "No kid present in Core Identity");
    }

    @Test
    void throwsExceptionWhenKidNotPresentInDidDocument() {
        configureDidDocumentResponse(SIGNING_KEY, CONTROLLER);

        var thrownException =
                assertThrows(
                        RuntimeException.class,
                        () -> underTest.isValid(createJws("controller#mismatched-kid")));
        assertEquals(
                "No key found in DID with ID controller#mismatched-kid",
                thrownException.getMessage());
    }

    @Test
    void throwsExceptionWhenKidControllerDoesNotMatchDidDocument() {
        configureDidDocumentResponse(SIGNING_KEY, CONTROLLER, "mismatched-controller");

        var thrownException =
                assertThrows(
                        RuntimeException.class,
                        () -> underTest.isValid(createJws(CONTROLLER + "#" + SIGNING_KEY_ID)));
        assertEquals(
                "Controller in User Identity kid does not match DID key value: did:web:identity.test.account.gov.uk mismatched-controller",
                thrownException.getMessage());
    }

    @Test
    void throwsExceptionWhenDidControllerDoesNotMatchDidEndpoint() {
        String controller = "did:web:not-identity.test.account.gov.uk";
        configureDidDocumentResponse(SIGNING_KEY, controller);

        var thrownException =
                assertThrows(
                        RuntimeException.class,
                        () -> underTest.isValid(createJws(controller + "#" + SIGNING_KEY_ID)));
        assertEquals(
                "Controller in User Identity kid does not match DID key URL: did:web:not-identity.test.account.gov.uk https://identity.test.account.gov.uk/.well-known/did.json",
                thrownException.getMessage());
    }

    private String createJws(String kid) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("example").build();
        try {
            JWSSigner signer = new ECDSASigner(SIGNING_KEY);
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(kid).build();
            SignedJWT signedJwt = new SignedJWT(header, claims);
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    private void configureDidDocumentResponse(ECKey ecKey, String controller) {
        configureDidDocumentResponse(ecKey, controller, controller);
    }

    private void configureDidDocumentResponse(ECKey ecKey, String keyIdPrefix, String controller) {
        var assertion = buildDidAssertion(ecKey, controller, keyIdPrefix);
        var documentResponse = buildDidDocumentResponse(assertion, controller);
        var httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(documentResponse);
        try {
            when(httpClient.send(any(), any())).thenReturn(httpResponse);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildDidAssertion(ECKey ecKey, String controller, String keyIdPrefix) {
        var jwk = ecKey.toPublicJWK();
        return String.format(
                """
                        {
                              "id": "%s#%s",
                              "type": "JsonWebKey",
                              "controller": "%s",
                              "publicKeyJwk": {
                                "kty": "EC",
                                "crv": "P-256",
                                "x": "%s",
                                "y": "%s"
                              }
                            }
                        """,
                keyIdPrefix, jwk.getKeyID(), controller, jwk.getX(), jwk.getY());
    }

    private String buildDidDocumentResponse(String assertionMethod, String controller) {
        return String.format(
                """
                        {
                          "@context": [
                            "https://www.w3.org/ns/did/v1",
                            "https://w3id.org/security/jwk/v1"
                          ],
                          "id": "%s",
                          "assertionMethod": [
                            %s
                          ]
                        }
                        """,
                controller, assertionMethod);
    }
}
