package de.tum.cit.aet.artemis.core.security.externallogin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PkceUtilTest {

    // RFC 7636 Appendix B reference vector.
    private static final String RFC_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    private static final String RFC_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    void shouldComputeS256ChallengeMatchingRfcReferenceVector() {
        assertThat(PkceUtil.s256Challenge(RFC_VERIFIER)).isEqualTo(RFC_CHALLENGE);
    }

    @Test
    void shouldProduceBase64UrlWithoutPadding() {
        String challenge = PkceUtil.s256Challenge("some-verifier-value");
        assertThat(challenge).doesNotContain("=", "+", "/");
    }

    @Test
    void shouldMatchVerifierAgainstItsOwnChallenge() {
        String verifier = "another-high-entropy-verifier-value-1234567890";
        assertThat(PkceUtil.matches(verifier, PkceUtil.s256Challenge(verifier))).isTrue();
    }

    @Test
    void shouldMatchRfcReferenceVector() {
        assertThat(PkceUtil.matches(RFC_VERIFIER, RFC_CHALLENGE)).isTrue();
    }

    @Test
    void shouldNotMatchWrongVerifier() {
        String challenge = PkceUtil.s256Challenge("the-real-verifier-value-0987654321");
        assertThat(PkceUtil.matches("a-different-verifier-value", challenge)).isFalse();
    }

    @Test
    void shouldNotMatchWhenArgumentsAreNull() {
        assertThat(PkceUtil.matches(null, RFC_CHALLENGE)).isFalse();
        assertThat(PkceUtil.matches(RFC_VERIFIER, null)).isFalse();
        assertThat(PkceUtil.matches(null, null)).isFalse();
    }
}
