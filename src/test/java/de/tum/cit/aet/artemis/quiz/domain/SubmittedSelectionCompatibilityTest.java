package de.tum.cit.aet.artemis.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.quiz.domain.compare.DnDMapping;
import de.tum.cit.aet.artemis.quiz.domain.compare.SAMapping;

/** The object-shaped quiz API must keep normalized selections intact and discard removed references. */
class SubmittedSelectionCompatibilityTest {

    @Test
    void multipleChoiceIgnoresIncompleteOptionsDeduplicatesAddsAndReturnsIndependentViews() {
        var answer = new MultipleChoiceSubmittedAnswer();
        var first = option(1L);
        var second = option(2L);
        assertThat(answer.isSelected(null)).isFalse();
        assertThat(answer.isSelected(new AnswerOption())).isFalse();
        assertThat(answer.toSelectedIds()).isEmpty();
        assertThat(answer.getSelectedOptions()).isEmpty();
        answer.setSelection(new ShortAnswerSubmittedAnswerSelection());
        assertThat(answer.isSelected(first)).isFalse();
        answer.setSelectedOptions(new LinkedHashSet<>(Arrays.asList(null, new AnswerOption(), first)));
        answer.addSelectedOptions(null);
        answer.addSelectedOptions(new AnswerOption());
        answer.addSelectedOptions(first);
        answer.addSelectedOptions(second);
        assertThat(answer.toSelectedIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(answer.getSelectedOptions()).isEmpty();
        var question = new MultipleChoiceQuestion();
        question.setId(7L);
        question.setAnswerOptions(List.of(first, second));
        answer.setQuizQuestion(question);
        assertThat(answer.getSelectedOptions()).containsExactlyInAnyOrder(first, second);
        answer.getSelectedOptions().clear();
        answer.toSelectedIds().clear();
        assertThat(answer.isSelected(first)).isTrue();
        assertThat(answer.isSelected(option(99L))).isFalse();
        assertThat(answer.toSelectedIds()).hasSize(2);
        answer.setSelectedOptions(null);
        assertThat(answer.toSelectedIds()).isEmpty();
    }

    @Test
    void multipleChoiceReevaluationRemovesDeletedOptionsAndDeletedQuestions() {
        var question = new MultipleChoiceQuestion();
        question.setId(7L);
        question.setAnswerOptions(List.of(option(1L)));
        var answer = new MultipleChoiceSubmittedAnswer();
        answer.setQuizQuestion(question);
        answer.addSelectedOptions(option(1L));
        answer.addSelectedOptions(option(99L));
        assertThat(answer.getSelectedOptions()).extracting(AnswerOption::getId).containsExactly(1L);
        answer.checkAndDeleteReferences(quiz(question));
        assertThat(answer.toSelectedIds()).containsExactly(1L);
        answer.setSelection(new ShortAnswerSubmittedAnswerSelection());
        answer.checkAndDeleteReferences(quiz(question));
        assertThat(answer.toSelectedIds()).isEmpty();
        answer.checkAndDeleteReferences(new QuizExercise());
        assertThat(answer.getQuizQuestion()).isNull();
        assertThat(answer.getSelection()).isNull();
        answer.checkAndDeleteReferences(new QuizExercise());
        assertThat(answer.getSelection()).isNull();
    }

    @Test
    void dragAndDropSkipsIncompleteMappingsAndResolvesOnlyExistingQuestionChildren() {
        var answer = new DragAndDropSubmittedAnswer();
        var item = new DragItem();
        item.setId(1L);
        var location = new DropLocation();
        location.setId(2L);
        var valid = mapping(item, location);
        assertThat(answer.getMappings()).isEmpty();
        assertThat(answer.toDnDMapping()).isEmpty();
        assertThat(answer.getSelectedDragItemForDropLocation(null)).isNull();
        assertThat(answer.getSelectedDragItemForDropLocation(new DropLocation())).isNull();
        assertThat(answer.getSelectedDragItemForDropLocation(location)).isNull();
        assertThat(answer.removeMappings(null)).isSameAs(answer);
        answer.setMappings(new LinkedHashSet<>(Arrays.asList(null, mapping(null, location), mapping(item, null), mapping(new DragItem(), location), valid)));
        assertThat(answer.toDnDMapping()).containsExactly(new DnDMapping(1L, 2L));
        assertThat(answer.getMappings()).isEmpty();
        assertThat(answer.getSelectedDragItemForDropLocation(location)).isNull();
        var question = new DragAndDropQuestion();
        question.setId(7L);
        question.setDragItems(List.of(item));
        question.setDropLocations(List.of(location));
        answer.setQuizQuestion(question);
        var selection = (DragAndDropSubmittedAnswerSelection) answer.getSelection();
        selection.getMappings().addAll(List.of(new DragAndDropMappingSelection(null, 2L), new DragAndDropMappingSelection(1L, null), new DragAndDropMappingSelection(99L, 2L),
                new DragAndDropMappingSelection(1L, 99L)));
        assertThat(answer.getMappings()).singleElement().satisfies(mapped -> {
            assertThat(mapped.getDragItem()).isSameAs(item);
            assertThat(mapped.getDropLocation()).isSameAs(location);
            assertThat(mapped.getDragItemIndex()).isZero();
            assertThat(mapped.getDropLocationIndex()).isZero();
        });
        assertThat(answer.getSelectedDragItemForDropLocation(location)).isSameAs(item);
        var absent = new DropLocation();
        absent.setId(3L);
        assertThat(answer.getSelectedDragItemForDropLocation(absent)).isNull();
        answer.checkAndDeleteReferences(quiz(question));
        assertThat(selection.getMappings()).containsExactly(new DragAndDropMappingSelection(1L, 2L));
        assertThat(answer.removeMappings(mapping(item, absent))).isSameAs(answer);
        assertThat(answer.toDnDMapping()).containsExactly(new DnDMapping(1L, 2L));
        answer.removeMappings(valid);
        assertThat(answer.getMappings()).isEmpty();
        answer.addMappings(valid).addMappings(null);
        assertThat(answer.toDnDMapping()).hasSize(1);
        answer.getMappings().clear();
        assertThat(answer.toDnDMapping()).hasSize(1);
        answer.setMappings(null);
        assertThat(answer.getMappings()).isEmpty();
        answer.checkAndDeleteReferences(new QuizExercise());
        assertThat(answer.getQuizQuestion()).isNull();
        assertThat(answer.getSelection()).isNull();
    }

    @Test
    void shortAnswerWrappersWriteThroughWhileOrphanedSpotsAreRemovedFromTheWireView() {
        var answer = new ShortAnswerSubmittedAnswer();
        var spot = new ShortAnswerSpot();
        spot.setId(1L);
        var entry = new ShortAnswerTextSelection(1L, "first", false);
        var text = new ShortAnswerSubmittedText(entry);
        var question = new ShortAnswerQuestion();
        question.setId(7L);
        question.setSpots(List.of(spot));
        assertThat(answer.getSubmittedTexts()).isEmpty();
        assertThat(answer.toSAMappings()).isEmpty();
        assertThat(answer.getSubmittedTextForSpot(null)).isNull();
        assertThat(answer.getSubmittedTextForSpot(new ShortAnswerSpot())).isNull();
        assertThat(answer.getSubmittedTextForSpot(spot)).isNull();
        assertThat(answer.addSubmittedTexts(null)).isSameAs(answer);
        assertThat(answer.removeSubmittedTexts(null)).isSameAs(answer);
        answer.setSubmittedTexts(new LinkedHashSet<>(Arrays.asList(text, null)));
        assertThat(answer.getSubmittedTexts()).isEmpty();
        assertThat(answer.getSubmittedTextForSpot(spot).getSpot()).isNull();
        answer.setQuizQuestion(question);
        var wrapper = answer.getSubmittedTextForSpot(spot);
        assertThat(wrapper.getSpot()).isSameAs(spot);
        assertThat(wrapper.getSubmittedAnswer()).isSameAs(answer);
        wrapper.setText("changed");
        wrapper.setIsCorrect(true);
        assertThat(entry.getText()).isEqualTo("changed");
        assertThat(entry.getIsCorrect()).isTrue();
        assertThat(answer.toSAMappings()).containsExactly(new SAMapping(1L, "changed"));
        var orphan = new ShortAnswerSubmittedText(new ShortAnswerTextSelection(99L, "orphan", true));
        answer.addSubmittedTexts(orphan);
        assertThat(orphan.getSubmittedAnswer()).isSameAs(answer);
        assertThat(answer.getSubmittedTexts()).singleElement().satisfies(value -> assertThat(value.getText()).isEqualTo("changed"));
        answer.checkAndDeleteReferences(quiz(question));
        assertThat(answer.toSAMappings()).containsExactly(new SAMapping(1L, "changed"));
        answer.removeSubmittedTexts(new ShortAnswerSubmittedText(new ShortAnswerTextSelection(99L, "unrelated", false)));
        assertThat(answer.toSAMappings()).hasSize(1);
        answer.removeSubmittedTexts(wrapper);
        assertThat(wrapper.getSubmittedAnswer()).isNull();
        assertThat(answer.toSAMappings()).isEmpty();
        answer.addSubmittedTexts(text);
        answer.removeSubmittedTexts(text);
        assertThat(answer.getSubmittedTexts()).isEmpty();
        answer.setSubmittedTexts(null);
        assertThat(answer.toSAMappings()).isEmpty();
        answer.checkAndDeleteReferences(new QuizExercise());
        assertThat(answer.getQuizQuestion()).isNull();
        assertThat(answer.getSelection()).isNull();
    }

    private static QuizExercise quiz(QuizQuestion question) {
        var quiz = new QuizExercise();
        quiz.setQuizQuestions(List.of(question));
        return quiz;
    }

    private static AnswerOption option(long id) {
        var option = new AnswerOption();
        option.setId(id);
        return option;
    }

    private static DragAndDropMapping mapping(DragItem item, DropLocation location) {
        var mapping = new DragAndDropMapping();
        mapping.setDragItem(item);
        mapping.setDropLocation(location);
        return mapping;
    }
}
