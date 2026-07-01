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

    @Test
    void shouldAcceptWellFormedS256Challenge() {
        assertThat(PkceUtil.isValidS256Challenge(RFC_CHALLENGE)).isTrue();
        assertThat(PkceUtil.isValidS256Challenge(PkceUtil.s256Challenge("some-high-entropy-verifier-value-1234567890"))).isTrue();
    }

    @Test
    void shouldRejectMalformedS256Challenge() {
        assertThat(PkceUtil.isValidS256Challenge(null)).isFalse();
        assertThat(PkceUtil.isValidS256Challenge("")).isFalse();
        assertThat(PkceUtil.isValidS256Challenge("   ")).isFalse();
        assertThat(PkceUtil.isValidS256Challenge("tooShort")).isFalse();
        assertThat(PkceUtil.isValidS256Challenge(RFC_CHALLENGE + "x")).isFalse(); // 44 characters
        assertThat(PkceUtil.isValidS256Challenge(RFC_CHALLENGE.substring(0, 42))).isFalse(); // 42 characters
        assertThat(PkceUtil.isValidS256Challenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw+cM")).isFalse(); // '+' is not base64url
    }

    @Test
    void shouldAcceptWellFormedCodeVerifier() {
        assertThat(PkceUtil.isValidCodeVerifier(RFC_VERIFIER)).isTrue(); // 43 characters (minimum)
        assertThat(PkceUtil.isValidCodeVerifier("A".repeat(128))).isTrue(); // 128 characters (maximum)
        assertThat(PkceUtil.isValidCodeVerifier("abcDEF123-._~" + "a".repeat(40))).isTrue(); // full unreserved set
    }

    @Test
    void shouldRejectMalformedCodeVerifier() {
        assertThat(PkceUtil.isValidCodeVerifier(null)).isFalse();
        assertThat(PkceUtil.isValidCodeVerifier("")).isFalse();
        assertThat(PkceUtil.isValidCodeVerifier("A".repeat(42))).isFalse(); // too short
        assertThat(PkceUtil.isValidCodeVerifier("A".repeat(129))).isFalse(); // too long
        assertThat(PkceUtil.isValidCodeVerifier("contains spaces but is otherwise long enough 0123")).isFalse(); // space is not unreserved
        assertThat(PkceUtil.isValidCodeVerifier("contains/slash/and/is/long/enough/0123456789xxxx")).isFalse(); // '/' is not unreserved
    }
}
