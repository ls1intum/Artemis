package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.service.TestReferenceParser;

class TestReferenceParserTest {

    @Test
    void shouldSplitSimpleRefs() {
        assertThat(TestReferenceParser.splitTestReferences("testA,testB")).containsExactly("testA", "testB");
    }

    @Test
    void shouldKeepCommasInsideParentheses() {
        assertThat(TestReferenceParser.splitTestReferences("testInsert(InsertMock, 1),testClass[SortStrategy],testWithBraces()")).containsExactly("testInsert(InsertMock, 1)",
                "testClass[SortStrategy]", "testWithBraces()");
    }

    @Test
    void shouldTrimAndDropEmptyRefs() {
        assertThat(TestReferenceParser.splitTestReferences("  testA , , testB  ")).containsExactly("testA", "testB");
    }

    @Test
    void shouldReturnEmptyListForBlankInput() {
        assertThat(TestReferenceParser.splitTestReferences("")).isEmpty();
        assertThat(TestReferenceParser.splitTestReferences(null)).isEmpty();
    }

    @Test
    void shouldPreserveTestIdWrappers() {
        assertThat(TestReferenceParser.splitTestReferences("<testid>1</testid>,<testid>2</testid>")).containsExactly("<testid>1</testid>", "<testid>2</testid>");
    }

    @Test
    void shouldDropTrailingEmptyRef() {
        assertThat(TestReferenceParser.splitTestReferences("testA,")).containsExactly("testA");
    }

    @Test
    void shouldReturnEmptyListForWhitespaceOnly() {
        assertThat(TestReferenceParser.splitTestReferences("   ")).isEmpty();
    }

    @Test
    void shouldDropAllEmptyRefs() {
        assertThat(TestReferenceParser.splitTestReferences("testA,,testB")).containsExactly("testA", "testB");
    }

    @Test
    void shouldExtractWrappedTestId() {
        assertThat(TestReferenceParser.extractTestId("<testid>17</testid>")).isEqualTo(17L);
        assertThat(TestReferenceParser.extractTestId("testBubbleSort<testid>5</testid>")).isEqualTo(5L);
    }

    @Test
    void shouldReturnNullForReferenceWithoutWrapper() {
        assertThat(TestReferenceParser.extractTestId("testBubbleSort")).isNull();
    }

    @Test
    void shouldReturnNullForOutOfRangeTestId() {
        // Problem statements are author-controlled, so a digit sequence that overflows a long is ordinary input.
        // It must resolve to "unresolved reference", never throw: a NumberFormatException here would turn a render
        // request into an internal server error.
        assertThat(TestReferenceParser.extractTestId("<testid>999999999999999999999999</testid>")).isNull();
    }
}
