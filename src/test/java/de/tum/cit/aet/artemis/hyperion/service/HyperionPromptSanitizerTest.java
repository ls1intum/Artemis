package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.Course;

class HyperionPromptSanitizerTest {

    // --- Constants ---

    @Test
    void constants_haveExpectedValues() {
        assertThat(HyperionPromptSanitizer.MAX_PROBLEM_STATEMENT_LENGTH).isEqualTo(50_000);
        assertThat(HyperionPromptSanitizer.MAX_USER_PROMPT_LENGTH).isEqualTo(1_000);
        assertThat(HyperionPromptSanitizer.DEFAULT_COURSE_TITLE).isEqualTo("Programming Course");
        assertThat(HyperionPromptSanitizer.DEFAULT_COURSE_DESCRIPTION).isEqualTo("A programming course");
    }

    // --- sanitizeInput ---

    @Test
    void sanitizeInput_returnsEmptyStringForNull() {
        assertThat(HyperionPromptSanitizer.sanitizeInput(null)).isEmpty();
    }

    @Test
    void sanitizeInput_returnsEmptyStringForEmpty() {
        assertThat(HyperionPromptSanitizer.sanitizeInput("")).isEmpty();
    }

    @Test
    void sanitizeInput_trimsWhitespace() {
        assertThat(HyperionPromptSanitizer.sanitizeInput("  hello world  ")).isEqualTo("hello world");
    }

    @Test
    void sanitizeInput_preservesNewlinesAndTabs() {
        String input = "line1\nline2\tindented\rcarriage";
        assertThat(HyperionPromptSanitizer.sanitizeInput(input)).isEqualTo(input);
    }

    @Test
    void sanitizeInput_stripsControlCharacters() {
        // \u0000 (null), \u0007 (bell), \u001B (escape) are control chars that should be removed
        String input = "hello\u0000world\u0007test\u001Bend";
        assertThat(HyperionPromptSanitizer.sanitizeInput(input)).isEqualTo("helloworldtestend");
    }

    @Test
    void sanitizeInput_preservesUnicodeText() {
        String input = "Übungsblatt für Studierende 日本語テスト";
        assertThat(HyperionPromptSanitizer.sanitizeInput(input)).isEqualTo(input);
    }

    @Test
    void sanitizeInput_handlesOnlyWhitespace() {
        assertThat(HyperionPromptSanitizer.sanitizeInput("   ")).isEmpty();
    }

    @Test
    void sanitizeInput_handlesOnlyControlCharacters() {
        assertThat(HyperionPromptSanitizer.sanitizeInput("\u0000\u0007\u001B")).isEmpty();
    }

    @Test
    void sanitizeInput_handlesMixedControlCharsAndWhitespace() {
        assertThat(HyperionPromptSanitizer.sanitizeInput("  \u0000  ")).isEmpty();
    }

    // --- getSanitizedCourseTitle ---

    @Test
    void getSanitizedCourseTitle_returnsTitleWhenPresent() {
        Course course = new Course();
        course.setTitle("Advanced Java");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseTitle(course)).isEqualTo("Advanced Java");
    }

    @Test
    void getSanitizedCourseTitle_returnsDefaultForNullTitle() {
        Course course = new Course();
        course.setTitle(null);
        assertThat(HyperionPromptSanitizer.getSanitizedCourseTitle(course)).isEqualTo("Programming Course");
    }

    @Test
    void getSanitizedCourseTitle_returnsDefaultForBlankTitle() {
        Course course = new Course();
        course.setTitle("   ");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseTitle(course)).isEqualTo("Programming Course");
    }

    @Test
    void getSanitizedCourseTitle_returnsDefaultForControlCharOnlyTitle() {
        Course course = new Course();
        course.setTitle("\u0000\u0007");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseTitle(course)).isEqualTo("Programming Course");
    }

    @Test
    void getSanitizedCourseTitle_sanitizesControlCharsInTitle() {
        Course course = new Course();
        course.setTitle("Java\u0000Course");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseTitle(course)).isEqualTo("JavaCourse");
    }

    // --- getSanitizedCourseDescription ---

    @Test
    void getSanitizedCourseDescription_returnsDescriptionWhenPresent() {
        Course course = new Course();
        course.setDescription("Learn advanced programming concepts");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseDescription(course)).isEqualTo("Learn advanced programming concepts");
    }

    @Test
    void getSanitizedCourseDescription_returnsDefaultForNullDescription() {
        Course course = new Course();
        course.setDescription(null);
        assertThat(HyperionPromptSanitizer.getSanitizedCourseDescription(course)).isEqualTo("A programming course");
    }

    @Test
    void getSanitizedCourseDescription_returnsDefaultForBlankDescription() {
        Course course = new Course();
        course.setDescription("");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseDescription(course)).isEqualTo("A programming course");
    }

    @Test
    void getSanitizedCourseDescription_returnsDefaultForControlCharOnlyDescription() {
        Course course = new Course();
        course.setDescription("\u001B\u0007");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseDescription(course)).isEqualTo("A programming course");
    }

    @Test
    void getSanitizedCourseDescription_sanitizesControlCharsInDescription() {
        Course course = new Course();
        course.setDescription("A great\u0000course");
        assertThat(HyperionPromptSanitizer.getSanitizedCourseDescription(course)).isEqualTo("A greatcourse");
    }
}
