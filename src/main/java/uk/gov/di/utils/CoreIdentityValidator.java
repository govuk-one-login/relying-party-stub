package uk.gov.di.utils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.SignedJWT;
import foundation.identity.did.DIDDocument;
import foundation.identity.did.VerificationMethod;
import uk.gov.di.config.RPConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Optional;

import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.HttpResponse.BodyHandlers.ofString;

public class CoreIdentityValidator {

    private final URI didKeyUri;
    private final HashMap<String, ECKey> keyCache = new HashMap<>();

    public enum Result {
        VALID,
        INVALID,
        NOT_VALIDATED
    }

    public static CoreIdentityValidator createValidator(RPConfig relyingPartyConfig) {
        return Optional.ofNullable(relyingPartyConfig.identitySigningKeyUrl())
                .map(CoreIdentityValidator::new)
                .orElseGet(NoopCoreIdentityValidator::new);
    }

    private CoreIdentityValidator(String didKeyUrl) {
        if (didKeyUrl != null) {
            this.didKeyUri = URI.create(didKeyUrl);
        } else {
            this.didKeyUri = null;
        }
    }

    public Result isValid(String jwt) {
        try {
            var signedJWT = SignedJWT.parse(jwt);
            var kid = getKeyID(signedJWT);
            var key = getKeyById(kid);
            return SignedJWT.parse(jwt).verify(new ECDSAVerifier(key))
                    ? Result.VALID
                    : Result.INVALID;
        } catch (JOSEException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getKeyID(SignedJWT signedJWT) {
        var kid = signedJWT.getHeader().getKeyID();
        if (kid == null) {
            throw new RuntimeException("No kid present in Core Identity");
        }
        return kid;
    }

    private ECKey getKeyById(String kid) {
        return keyCache.computeIfAbsent(kid, this::fetchKeyFromDid);
    }

    private ECKey fetchKeyFromDid(String kid) {
        var kidParts = kid.split("#");
        var controller = kidParts[0];
        var did = DIDDocument.fromJson(fetchDidDocument());
        var verificationMethod = getVerificationMethod(did, kid);
        verifyController(controller, verificationMethod);

        return getEcKeyFromVerificationMethod(verificationMethod);
    }

    private static VerificationMethod getVerificationMethod(DIDDocument did, String keyId) {
        var verificationMethodMaybe =
                did.getAssertionMethodVerificationMethodsInline().stream()
                        .filter(key -> keyId.equals(key.getId().toString()))
                        .findFirst();

        return verificationMethodMaybe.orElseThrow(
                () -> new RuntimeException("No key found in DID with ID " + keyId));
    }

    private void verifyController(String controller, VerificationMethod verificationMethod) {
        if (!controller.equals(verificationMethod.getController().toString())) {
            throw new RuntimeException(
                    "Controller in User Identity kid does not match DID key value: "
                            + controller
                            + " "
                            + verificationMethod.getController().toString());
        }
        var expectedControllerFromHost = "did:web:" + didKeyUri.getHost();
        if (!controller.equals(expectedControllerFromHost)) {
            throw new RuntimeException(
                    "Controller in User Identity kid does not match DID key URL: "
                            + controller
                            + " "
                            + didKeyUri);
        }
    }

    private static ECKey getEcKeyFromVerificationMethod(VerificationMethod verificationMethod) {
        var publicJwkString = verificationMethod.getPublicKeyJwk();
        try {
            var publicJwk = JWK.parse(publicJwkString);
            return publicJwk.toECKey();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private String fetchDidDocument() {
        try {
            HttpRequest request = HttpRequest.newBuilder(didKeyUri).build();
            var response = newHttpClient().send(request, ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "DID document could not be fetched. Status code: "
                                + response.statusCode()
                                + " - "
                                + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    static class NoopCoreIdentityValidator extends CoreIdentityValidator {
        private NoopCoreIdentityValidator() {
            super(null);
        }

        @Override
        public Result isValid(String jwt) {
            return Result.NOT_VALIDATED;
        }
    }
}
