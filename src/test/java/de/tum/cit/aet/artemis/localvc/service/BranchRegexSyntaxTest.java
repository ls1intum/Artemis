package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the subset of regular expressions that is supported for the names of branches.
 */
class BranchRegexSyntaxTest {

    @ParameterizedTest
    @ValueSource(strings = { ".*", "feature/.*", "^(feature|bugfix)/[a-z0-9-]+$", "[a-z]{3,10}-\\d{2}", "main", "feature\\.x", "a]b", "a}b", "\\/\\-\\_", "[^/]+", "[\\w.-]*",
            "(?:ab){2,5}", "(a|b)c", "((ab))c", "^a|b$", "(^a|b$)", "x{0}", "x{1000}", "x{2,}", "a?", "a*", "\\d+\\s*\\w?\\D\\W\\S", "[a-z&]", "[\\]\\[]", "((((a))))",
            "(?:(?:(?:(?:(?:a)))))", "[a-]", "[-a]", "[\\w-]", "\\(\\)" })
    void supportedExpressionsAreAccepted(String regex) {
        assertThat(BranchRegexSyntax.isSupported(regex)).as(regex).isTrue();
        // every supported expression is also a valid expression
        Pattern.compile(regex);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(?:^){2147483647}", "(?x)(?:^){2147483647 }",
            "^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?^?", "^^^a", "a^b", "(?=a)b", "(?<=a)b", "(?!a)b",
            "(?<n>a)", "(?>a)", "(?i)a", "(a)\\1", "\\k<n>", "\\bfoo", "\\Bfoo", "\\Afoo", "\\Zfoo", "\\zfoo", "\\Gfoo", "(a+)+", "(a|aa)+", "(a?)*", "(^)*", "a{1001}", "a{3,2}",
            "a*?", "a++", "a??", "a+?", "a{2}?", "a{2}{3}", "a**", "\\Qa\\E", "\\p{Alpha}", "\\P{Alpha}", "\\x41", "\\u0041", "\\0", "\\R", "\\X", "\\h", "\\a", "[a&&b]", "[[a]]",
            "[a\\Qb\\E]", "[\\p{Alpha}]", "[]", "[^]", "(((((((a)))))))", "(", "[", "a{", "a{1", "a{1,", "a{,2}", "{2}", "()", "(?:)", "a||b", "|a", "a|", "(a|)", "a)", "(a", "$a",
            "a$b", "$*", "^*", "*a", "+a", "?a", "a\\", "[z-a]", "[\\d-z]", "(a$)*", "(a|b)*{2}", "(a|b)+c", "((ab)+c)?", "(?:a|b){2}", "(ab?){2}", "((a)){2}x|(b+){3}", "",
            "a{12345}" })
    void unsupportedExpressionsAreRejected(String regex) {
        assertThat(BranchRegexSyntax.isSupported(regex)).as(regex).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void anAbsentExpressionIsNotSupported(String regex) {
        assertThat(BranchRegexSyntax.isSupported(regex)).isFalse();
    }

    @Test
    void anExpressionLongerThanTheLimitIsNotSupported() {
        assertThat(BranchRegexSyntax.isSupported("a".repeat(BranchRegexSyntax.MAX_LENGTH))).isTrue();
        assertThat(BranchRegexSyntax.isSupported("a".repeat(BranchRegexSyntax.MAX_LENGTH + 1))).isFalse();
    }

    @Test
    void anAlternativeGroupMayBeRepeatedOnlyWhenItIsPlain() {
        assertThat(BranchRegexSyntax.isSupported("(feature|bugfix)/[a-z0-9-]+")).isTrue();
        assertThat(BranchRegexSyntax.isSupported("(feature|bugfix)+")).isFalse();
        assertThat(BranchRegexSyntax.isSupported("(?:feature){2}")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "(^|^)", "(a?|b*)", "(x{0}|y{0})", "((a?))", "(a{0,1})", "(a{0,})", "(a{0,0})", "(😀?)", "(😀*)", "(😀{0})" })
    void groupsWhoseAlternativesCanMatchWithoutConsumingInputAreRejected(String regex) {
        assertThat(BranchRegexSyntax.isSupported(regex)).as(regex).isFalse();
    }

    @Test
    void sequentialZeroWidthAlternativesAreRejectedWithoutMatchingThem() {
        // 3^18 zero-width paths before '$' fails on a name of at least three characters. Never evaluate this expression.
        String regex = "(^|^|^)".repeat(18) + "$";
        assertThat(regex).hasSize(127);
        assertThat(BranchRegexSyntax.isSupported(regex)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "(a?b|c+)", "(a{0}b|c)", "(a{0,1}b)", "(a{1,})", "(^a|b$)", "^(feature|bugfix)/.*$", "(😀+)", "(😀?b)", "([😀-😁])" })
    void groupsWithARequiredConsumingElementInEveryAlternativeAreAccepted(String regex) {
        assertThat(BranchRegexSyntax.isSupported(regex)).as(regex).isTrue();
    }
}
