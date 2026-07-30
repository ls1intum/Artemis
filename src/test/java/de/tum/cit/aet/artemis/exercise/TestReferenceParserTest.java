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
}
