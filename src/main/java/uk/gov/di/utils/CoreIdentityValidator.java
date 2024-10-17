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
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.HttpResponse.BodyHandlers.ofString;

public class CoreIdentityValidator {

    private final URI didKeyUri;
    private final HashMap<String, ECKeyWithExpiry> keyCache = new HashMap<>();
    private final Clock clock;

    public enum Result {
        VALID,
        INVALID,
        NOT_VALIDATED
    }

    public static CoreIdentityValidator createValidator(RPConfig relyingPartyConfig) {
        return createValidator(relyingPartyConfig, Clock.systemUTC());
    }

    public static CoreIdentityValidator createValidator(RPConfig relyingPartyConfig, Clock clock) {
        return Optional.ofNullable(relyingPartyConfig.identitySigningKeyUrl())
                .map(didKeyUrl -> new CoreIdentityValidator(didKeyUrl, clock))
                .orElseGet(NoopCoreIdentityValidator::new);
    }

    private CoreIdentityValidator(String didKeyUrl, Clock clock) {
        this.clock = clock;
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
        var keyFromCache = keyCache.get(kid);
        if (keyFromCache != null && keyFromCache.expiry.isAfter(Instant.now(clock))) {
            return keyFromCache.key;
        }

        var refreshedKey = fetchKeyFromDid(kid);
        keyCache.put(kid, refreshedKey);
        return refreshedKey.key;
    }

    private ECKeyWithExpiry fetchKeyFromDid(String kid) {
        var kidParts = kid.split("#");
        var controller = kidParts[0];
        var didResponseWithLifetime = fetchDidDocument();
        var did = DIDDocument.fromJson(didResponseWithLifetime.did);
        var verificationMethod = getVerificationMethod(did, kid);
        verifyController(controller, verificationMethod);
        var ecKey = getEcKeyFromVerificationMethod(verificationMethod);
        return new ECKeyWithExpiry(ecKey, didResponseWithLifetime.expiry);
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
        var expectedControllerFromHost = "did:web:" + didKeyUri.getAuthority();
        if (!controller.replace("%3A", ":").equals(expectedControllerFromHost)) {
            throw new RuntimeException(
                    "Controller in User Identity kid does not match DID key URL: "
                            + controller.replace("%3A", ":")
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

    private DidResponseWithExpiry fetchDidDocument() {
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
            var expiry = calculateExpiry(response);
            return new DidResponseWithExpiry(response.body(), expiry);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private Instant calculateExpiry(HttpResponse<String> response) {
        Integer responseLifetime =
                Optional.ofNullable(response.headers())
                        .flatMap(httpHeaders -> httpHeaders.firstValue("cache-control"))
                        .map(CoreIdentityValidator::lifetimeFromCacheControlHeader)
                        .orElse(0);
        return Instant.now(clock).plus(responseLifetime, ChronoUnit.SECONDS);
    }

    private static Integer lifetimeFromCacheControlHeader(String header) {
        // This is a very simplistic parser that doesn't honour freshness or other expiry
        var matcher = Pattern.compile("max-age=(\\d+)", Pattern.CASE_INSENSITIVE).matcher(header);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private record DidResponseWithExpiry(String did, Instant expiry) {}

    private record ECKeyWithExpiry(ECKey key, Instant expiry) {}

    static class NoopCoreIdentityValidator extends CoreIdentityValidator {
        private NoopCoreIdentityValidator() {
            super(null, null);
        }

        @Override
        public Result isValid(String jwt) {
            return Result.NOT_VALIDATED;
        }
    }
}
