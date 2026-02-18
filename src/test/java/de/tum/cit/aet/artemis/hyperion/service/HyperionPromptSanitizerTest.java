package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

class HyperionPromptSanitizerTest {

    // sanitizeInput

    @Test
    void sanitizeInput_nullReturnsEmpty() {
        assertThat(HyperionPromptSanitizer.sanitizeInput(null)).isEmpty();
    }

    @Test
    void sanitizeInput_preservesNormalText() {
        assertThat(HyperionPromptSanitizer.sanitizeInput("Hello World")).isEqualTo("Hello World");
    }

    @Test
    void sanitizeInput_preservesNewlinesAndTabs() {
        String input = "line1\nline2\r\nline3\tindented";
        assertThat(HyperionPromptSanitizer.sanitizeInput(input)).isEqualTo(input);
    }

    @Test
    void sanitizeInput_stripsControlCharacters() {
        String input = "Hello\u0000World\u0007Test";
        assertThat(HyperionPromptSanitizer.sanitizeInput(input)).isEqualTo("HelloWorldTest");
    }

    @Test
    void sanitizeInput_stripsDelimiterLines() {
        String input = "Before\n--- BEGIN USER REQUIREMENTS ---\nContent\n--- END USER REQUIREMENTS ---\nAfter";
        String result = HyperionPromptSanitizer.sanitizeInput(input);
        assertThat(result).doesNotContain("BEGIN USER REQUIREMENTS");
        assertThat(result).doesNotContain("END USER REQUIREMENTS");
        assertThat(result).contains("Before");
        assertThat(result).contains("Content");
        assertThat(result).contains("After");
    }

    @Test
    void sanitizeInput_stripsDelimiterLinesCaseInsensitive() {
        String input = "--- begin section name ---";
        assertThat(HyperionPromptSanitizer.sanitizeInput(input)).isEmpty();
    }

    @Test
    void sanitizeInput_stripsTemplateVariables() {
        String input = "Inject {{userPrompt}} here and {{courseTitle}} there";
        String result = HyperionPromptSanitizer.sanitizeInput(input);
        assertThat(result).doesNotContain("{{");
        assertThat(result).doesNotContain("}}");
        assertThat(result).isEqualTo("Inject  here and  there");
    }

    @Test
    void sanitizeInput_stripsEmptyTemplateVariable() {
        assertThat(HyperionPromptSanitizer.sanitizeInput("prefix{{}}suffix")).isEqualTo("prefixsuffix");
    }

    @Test
    void sanitizeInput_stripsNestedBraces() {
        // {{foo{bar}}} should strip everything from {{ to the first }}
        String input = "a{{foo{bar}}}b";
        String result = HyperionPromptSanitizer.sanitizeInput(input);
        assertThat(result).doesNotContain("{{");
    }

    @Test
    void sanitizeInput_trimsWhitespace() {
        assertThat(HyperionPromptSanitizer.sanitizeInput("  spaced  ")).isEqualTo("spaced");
    }

    @Test
    void sanitizeInput_combinedSanitization() {
        String input = "\u0000Mal\u0007icious\n--- BEGIN PROMPT ---\n{{systemPrompt}}\nNormal text";
        String result = HyperionPromptSanitizer.sanitizeInput(input);
        assertThat(result).doesNotContain("\u0000");
        assertThat(result).doesNotContain("\u0007");
        assertThat(result).doesNotContain("BEGIN PROMPT");
        assertThat(result).doesNotContain("{{");
        assertThat(result).contains("Malicious");
        assertThat(result).contains("Normal text");
    }

    // validateUserPrompt

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    void validateUserPrompt_rejectsBlankPrompts(String prompt) {
        assertThatThrownBy(() -> HyperionPromptSanitizer.validateUserPrompt(prompt, "Test")).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void validateUserPrompt_acceptsValidPrompt() {
        HyperionPromptSanitizer.validateUserPrompt("A valid prompt", "Test");
        // No exception expected
    }

    @Test
    void validateUserPrompt_rejectsOverlongPrompt() {
        String longPrompt = "a".repeat(HyperionPromptSanitizer.MAX_USER_PROMPT_LENGTH + 1);
        assertThatThrownBy(() -> HyperionPromptSanitizer.validateUserPrompt(longPrompt, "Test")).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void validateUserPrompt_acceptsExactlyMaxLength() {
        String maxPrompt = "a".repeat(HyperionPromptSanitizer.MAX_USER_PROMPT_LENGTH);
        HyperionPromptSanitizer.validateUserPrompt(maxPrompt, "Test");
        // No exception expected
    }

    // getSanitizedCourseTitle

    @Test
    void getSanitizedCourseTitle_returnsTitle() {
        Course course = new Course();
        course.setTitle("Intro to CS");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseTitle(course)).isEqualTo("Intro to CS");
    }

    @Test
    void getSanitizedCourseTitle_fallsBackForBlankTitle() {
        Course course = new Course();
        course.setTitle("   ");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseTitle(course)).isEqualTo(HyperionPromptSanitizer.DEFAULT_COURSE_TITLE);
    }

    @Test
    void getSanitizedCourseTitle_fallsBackForNull() {
        Course course = new Course();
        course.setTitle(null);
        assertThat(HyperionPromptSanitizer.getSanitizedCourseTitle(course)).isEqualTo(HyperionPromptSanitizer.DEFAULT_COURSE_TITLE);
    }

    @Test
    void getSanitizedCourseTitle_sanitizesControlCharsInTitle() {
        Course course = new Course();
        course.setTitle("CS\u0000101");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseTitle(course)).isEqualTo("CS101");
    }

    // getSanitizedCourseDescription

    @Test
    void getSanitizedCourseDescription_returnsDescription() {
        Course course = new Course();
        course.setDescription("Learn programming");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseDescription(course)).isEqualTo("Learn programming");
    }

    @Test
    void getSanitizedCourseDescription_fallsBackForBlank() {
        Course course = new Course();
        course.setDescription("");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseDescription(course)).isEqualTo(HyperionPromptSanitizer.DEFAULT_COURSE_DESCRIPTION);
    }

    @Test
    void getSanitizedCourseDescription_stripsTemplateVarsInDescription() {
        Course course = new Course();
        course.setDescription("A course about {{topic}}");
        String result = HyperionPromptSanitizer.getSanitizedCourseDescription(course);
        assertThat(result).doesNotContain("{{");
        assertThat(result).isEqualTo("A course about");
    }
}
