package de.tum.cit.aet.artemis.core.security.externallogin;

import java.io.Serializable;

/**
 * Data bound to a one-time external-login code, stored server-side (distributed) until the external
 * client exchanges the code together with its PKCE verifier for the JWT.
 *
 * @param jwt           the full Artemis JWT minted at issue time (already capped to the originating
 *                          session's remaining validity)
 * @param codeChallenge the PKCE S256 code challenge (base64url, no padding)
 * @param callback      the validated extension callback URI
 */
public record ExternalLoginCodeData(String jwt, String codeChallenge, String callback) implements Serializable {
}
