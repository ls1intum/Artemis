package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.Course;

class HyperionUtilsTest {

    // --- Constants ---

    @Test
    void constants_haveExpectedValues() {
        assertThat(HyperionUtils.MAX_PROBLEM_STATEMENT_LENGTH).isEqualTo(50_000);
        assertThat(HyperionUtils.MAX_USER_PROMPT_LENGTH).isEqualTo(1_000);
        assertThat(HyperionUtils.MAX_INSTRUCTION_LENGTH).isEqualTo(500);
        assertThat(HyperionUtils.DEFAULT_COURSE_TITLE).isEqualTo("Programming Course");
        assertThat(HyperionUtils.DEFAULT_COURSE_DESCRIPTION).isEqualTo("A programming course");
    }

    // --- sanitizeInput ---

    @Test
    void sanitizeInput_returnsEmptyStringForNull() {
        assertThat(HyperionUtils.sanitizeInput(null)).isEmpty();
    }

    @Test
    void sanitizeInput_returnsEmptyStringForEmpty() {
        assertThat(HyperionUtils.sanitizeInput("")).isEmpty();
    }

    @Test
    void sanitizeInput_trimsWhitespace() {
        assertThat(HyperionUtils.sanitizeInput("  hello world  ")).isEqualTo("hello world");
    }

    @Test
    void sanitizeInput_preservesNormalText() {
        assertThat(HyperionUtils.sanitizeInput("Hello World")).isEqualTo("Hello World");
    }

    @Test
    void sanitizeInput_preservesNewlinesAndTabs() {
        // cover both \r and \r\n variants seen in branches
        String input = "line1\nline2\r\nline3\tindented\rcarriage";
        assertThat(HyperionUtils.sanitizeInput(input)).isEqualTo(input);
    }

    @Test
    void sanitizeInput_stripsControlCharacters() {
        // \u0000 (null), \u0007 (bell), \u001B (escape) are control chars that should be removed
        String input = "hello\u0000world\u0007test\u001Bend";
        assertThat(HyperionUtils.sanitizeInput(input)).isEqualTo("helloworldtestend");
    }

    @Test
    void sanitizeInput_preservesUnicodeText() {
        String input = "Übungsblatt für Studierende 日本語テスト";
        assertThat(HyperionUtils.sanitizeInput(input)).isEqualTo(input);
    }

    @Test
    void sanitizeInput_handlesOnlyWhitespace() {
        assertThat(HyperionUtils.sanitizeInput("   ")).isEmpty();
    }

    @Test
    void sanitizeInput_handlesOnlyControlCharacters() {
        assertThat(HyperionUtils.sanitizeInput("\u0000\u0007\u001B")).isEmpty();
    }

    @Test
    void sanitizeInput_handlesMixedControlCharsAndWhitespace() {
        assertThat(HyperionUtils.sanitizeInput("  \u0000  ")).isEmpty();
    }

    @Test
    void sanitizeInput_stripsDelimiterLines() {
        String input = "Before\n--- BEGIN USER REQUIREMENTS ---\nContent\n--- END USER REQUIREMENTS ---\nAfter";
        String result = HyperionUtils.sanitizeInput(input);

        assertThat(result).doesNotContain("BEGIN USER REQUIREMENTS");
        assertThat(result).doesNotContain("END USER REQUIREMENTS");
        assertThat(result).contains("Before");
        assertThat(result).contains("Content");
        assertThat(result).contains("After");
    }

    @Test
    void sanitizeInput_stripsDelimiterLinesCaseInsensitive() {
        String input = "--- begin section name ---";
        assertThat(HyperionUtils.sanitizeInput(input)).isEmpty();
    }

    @Test
    void sanitizeInput_stripsTemplateVariables() {
        String input = "Inject {{userPrompt}} here and {{courseTitle}} there";
        String result = HyperionUtils.sanitizeInput(input);

        assertThat(result).doesNotContain("{{");
        assertThat(result).doesNotContain("}}");
        assertThat(result).isEqualTo("Inject  here and  there");
    }

    @Test
    void sanitizeInput_stripsEmptyTemplateVariable() {
        assertThat(HyperionUtils.sanitizeInput("prefix{{}}suffix")).isEqualTo("prefixsuffix");
    }

    @Test
    void sanitizeInput_stripsNestedBraces() {
        // {{foo{bar}}} should strip everything from {{ to the first }}
        String input = "a{{foo{bar}}}b";
        String result = HyperionUtils.sanitizeInput(input);
        assertThat(result).doesNotContain("{{");
    }

    @Test
    void sanitizeInput_combinedSanitization() {
        String input = "\u0000Mal\u0007icious\n--- BEGIN PROMPT ---\n{{systemPrompt}}\nNormal text";
        String result = HyperionUtils.sanitizeInput(input);

        assertThat(result).doesNotContain("\u0000");
        assertThat(result).doesNotContain("\u0007");
        assertThat(result).doesNotContain("BEGIN PROMPT");
        assertThat(result).doesNotContain("{{");
        assertThat(result).contains("Malicious");
        assertThat(result).contains("Normal text");
    }

    // --- validateUserPrompt ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    void validateUserPrompt_rejectsBlankPrompts(String prompt) {
        assertThatThrownBy(() -> HyperionUtils.validateUserPrompt(prompt, "Test")).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void validateUserPrompt_acceptsValidPrompt() {
        HyperionUtils.validateUserPrompt("A valid prompt", "Test");
        // No exception expected
    }

    @Test
    void validateUserPrompt_rejectsOverlongPrompt() {
        String longPrompt = "a".repeat(HyperionUtils.MAX_USER_PROMPT_LENGTH + 1);
        assertThatThrownBy(() -> HyperionUtils.validateUserPrompt(longPrompt, "Test")).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void validateUserPrompt_acceptsExactlyMaxLength() {
        String maxPrompt = "a".repeat(HyperionUtils.MAX_USER_PROMPT_LENGTH);
        HyperionUtils.validateUserPrompt(maxPrompt, "Test");
        // No exception expected
    }

    // --- validateInstruction ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    void validateInstruction_rejectsBlankInstructions(String instruction) {
        assertThatThrownBy(() -> HyperionUtils.validateInstruction(instruction, "Test")).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void validateInstruction_acceptsValidInstruction() {
        HyperionUtils.validateInstruction("A valid instruction", "Test");
        // No exception expected
    }

    @Test
    void validateInstruction_rejectsOverlongInstruction() {
        String longInstruction = "a".repeat(HyperionUtils.MAX_INSTRUCTION_LENGTH + 1);
        assertThatThrownBy(() -> HyperionUtils.validateInstruction(longInstruction, "Test")).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void validateInstruction_acceptsExactlyMaxLength() {
        String maxInstruction = "a".repeat(HyperionUtils.MAX_INSTRUCTION_LENGTH);
        HyperionUtils.validateInstruction(maxInstruction, "Test");
        // No exception expected
    }

    // --- getSanitizedCourseTitle ---

    @Test
    void getSanitizedCourseTitle_returnsTitleWhenPresent() {
        Course course = new Course();
        course.setTitle("Advanced Java");
        assertThat(HyperionUtils.getSanitizedCourseTitle(course)).isEqualTo("Advanced Java");
    }

    @Test
    void getSanitizedCourseTitle_returnsDefaultForNullTitle() {
        Course course = new Course();
        course.setTitle(null);
        assertThat(HyperionUtils.getSanitizedCourseTitle(course)).isEqualTo(HyperionUtils.DEFAULT_COURSE_TITLE);
    }

    @Test
    void getSanitizedCourseTitle_returnsDefaultForBlankTitle() {
        Course course = new Course();
        course.setTitle("   ");
        assertThat(HyperionUtils.getSanitizedCourseTitle(course)).isEqualTo(HyperionUtils.DEFAULT_COURSE_TITLE);
    }

    @Test
    void getSanitizedCourseTitle_returnsDefaultForControlCharOnlyTitle() {
        Course course = new Course();
        course.setTitle("\u0000\u0007");
        assertThat(HyperionUtils.getSanitizedCourseTitle(course)).isEqualTo(HyperionUtils.DEFAULT_COURSE_TITLE);
    }

    @Test
    void getSanitizedCourseTitle_sanitizesControlCharsInTitle() {
        Course course = new Course();
        course.setTitle("CS\u0000101");
        assertThat(HyperionUtils.getSanitizedCourseTitle(course)).isEqualTo("CS101");
    }

    // --- getSanitizedCourseDescription ---

    @Test
    void getSanitizedCourseDescription_returnsDescriptionWhenPresent() {
        Course course = new Course();
        course.setDescription("Learn advanced programming concepts");
        assertThat(HyperionUtils.getSanitizedCourseDescription(course)).isEqualTo("Learn advanced programming concepts");
    }

    @Test
    void getSanitizedCourseDescription_returnsDefaultForNullDescription() {
        Course course = new Course();
        course.setDescription(null);
        assertThat(HyperionUtils.getSanitizedCourseDescription(course)).isEqualTo(HyperionUtils.DEFAULT_COURSE_DESCRIPTION);
    }

    @Test
    void getSanitizedCourseDescription_returnsDefaultForBlankDescription() {
        Course course = new Course();
        course.setDescription("");
        assertThat(HyperionUtils.getSanitizedCourseDescription(course)).isEqualTo(HyperionUtils.DEFAULT_COURSE_DESCRIPTION);
    }

    @Test
    void getSanitizedCourseDescription_returnsDefaultForControlCharOnlyDescription() {
        Course course = new Course();
        course.setDescription("\u001B\u0007");
        assertThat(HyperionUtils.getSanitizedCourseDescription(course)).isEqualTo(HyperionUtils.DEFAULT_COURSE_DESCRIPTION);
    }

    @Test
    void getSanitizedCourseDescription_sanitizesControlCharsInDescription() {
        Course course = new Course();
        course.setDescription("A great\u0000course");
        assertThat(HyperionUtils.getSanitizedCourseDescription(course)).isEqualTo("A greatcourse");
    }

    @Test
    void getSanitizedCourseDescription_stripsTemplateVarsInDescription() {
        Course course = new Course();
        course.setDescription("A course about {{topic}}");
        String result = HyperionUtils.getSanitizedCourseDescription(course);

        assertThat(result).doesNotContain("{{");
        // after stripping, trailing whitespace may or may not remain depending on implementation; be robust:
        assertThat(result.trim()).isEqualTo("A course about");
    }

    @Test
    void getSanitizedCourseDescription_stripsHtmlTags() {
        Course course = new Course();
        course.setDescription("<p>Learn <strong>programming</strong> with <em>Java</em></p>");
        assertThat(HyperionUtils.getSanitizedCourseDescription(course)).isEqualTo("Learn programming with Java");
    }

    @Test
    void getSanitizedCourseDescription_stripsHtmlAndFallsBackWhenOnlyTags() {
        Course course = new Course();
        course.setDescription("<br/><hr/>");
        assertThat(HyperionUtils.getSanitizedCourseDescription(course)).isEqualTo(HyperionUtils.DEFAULT_COURSE_DESCRIPTION);
    }

    // --- stripLineNumbers ---

    @Test
    void stripLineNumbers_removesSequentialPrefixes() {
        String input = "1: First line\n2: Second line\n3: Third line";
        assertThat(HyperionUtils.stripLineNumbers(input)).isEqualTo("First line\nSecond line\nThird line");
    }

    @Test
    void stripLineNumbers_preservesNonSequentialPrefixes() {
        // Numbers start at 3 instead of 1 — should not be stripped
        String input = "3: First item\n5: Second item";
        assertThat(HyperionUtils.stripLineNumbers(input)).isEqualTo(input);
    }

    @Test
    void stripLineNumbers_preservesTextWithoutPrefixes() {
        String input = "Normal text\nAnother line";
        assertThat(HyperionUtils.stripLineNumbers(input)).isEqualTo(input);
    }

    @Test
    void stripLineNumbers_preservesMixedLines() {
        // First line has prefix, second doesn't — should not be stripped
        String input = "1: Introduction\nNo prefix here";
        assertThat(HyperionUtils.stripLineNumbers(input)).isEqualTo(input);
    }

    @Test
    void stripLineNumbers_handlesBlankLinesInMiddle() {
        // Blank lines between numbered lines should be preserved
        String input = "1: First\n\n2: Third";
        assertThat(HyperionUtils.stripLineNumbers(input)).isEqualTo("First\n\nThird");
    }

    @Test
    void stripLineNumbers_handlesSingleLine() {
        String input = "1: Only line";
        assertThat(HyperionUtils.stripLineNumbers(input)).isEqualTo("Only line");
    }

    @Test
    void stripLineNumbers_preservesSingleLineWithoutPrefix() {
        String input = "Just text";
        assertThat(HyperionUtils.stripLineNumbers(input)).isEqualTo(input);
    }

    @Test
    void stripLineNumbers_preservesNumberedListContent() {
        // Content that looks like "1. First" should not be stripped (colon-space pattern, not dot)
        String input = "1. First item\n2. Second item";
        assertThat(HyperionUtils.stripLineNumbers(input)).isEqualTo(input);
    }

    @Test
    void stripLineNumbers_handlesEmptyString() {
        assertThat(HyperionUtils.stripLineNumbers("")).isEqualTo("");
    }

    @Test
    void stripWrapperMarkers_removesBeginEndProblemStatement() {
        String input = "--- BEGIN PROBLEM STATEMENT ---\nHello World\n--- END PROBLEM STATEMENT ---";
        assertThat(HyperionUtils.stripWrapperMarkers(input)).isEqualTo("Hello World");
    }

    @Test
    void stripWrapperMarkers_removesBeginEndWithBlankSurrounding() {
        String input = "\n--- BEGIN PROBLEM STATEMENT ---\nLine 1\nLine 2\n--- END PROBLEM STATEMENT ---\n";
        assertThat(HyperionUtils.stripWrapperMarkers(input)).isEqualTo("Line 1\nLine 2");
    }

    @Test
    void stripWrapperMarkers_removesOnlyLeadingMarker() {
        String input = "--- BEGIN PROBLEM STATEMENT ---\nContent here";
        assertThat(HyperionUtils.stripWrapperMarkers(input)).isEqualTo("Content here");
    }

    @Test
    void stripWrapperMarkers_removesOnlyTrailingMarker() {
        String input = "Content here\n--- END PROBLEM STATEMENT ---";
        assertThat(HyperionUtils.stripWrapperMarkers(input)).isEqualTo("Content here");
    }

    @Test
    void stripWrapperMarkers_preservesContentWithoutMarkers() {
        String input = "Just normal content\nwith multiple lines";
        assertThat(HyperionUtils.stripWrapperMarkers(input)).isEqualTo(input);
    }

    @Test
    void stripWrapperMarkers_preservesInteriorMarkerLines() {
        String input = "Intro\n--- BEGIN SECTION ---\nMiddle\n--- END SECTION ---\nOutro";
        assertThat(HyperionUtils.stripWrapperMarkers(input)).isEqualTo(input);
    }

    @Test
    void stripWrapperMarkers_handlesEmptyString() {
        assertThat(HyperionUtils.stripWrapperMarkers("")).isEqualTo("");
    }

    @Test
    void stripWrapperMarkers_handlesCaseInsensitiveMarkers() {
        String input = "--- begin Problem Statement ---\nContent\n--- end Problem Statement ---";
        assertThat(HyperionUtils.stripWrapperMarkers(input)).isEqualTo("Content");
    }

    @Test
    void stripWrapperMarkers_handlesTargetedInstructionMarkers() {
        String input = "--- BEGIN TARGETED INSTRUCTIONS ---\nContent\n--- END TARGETED INSTRUCTIONS ---";
        assertThat(HyperionUtils.stripWrapperMarkers(input)).isEqualTo("Content");
    }
}
