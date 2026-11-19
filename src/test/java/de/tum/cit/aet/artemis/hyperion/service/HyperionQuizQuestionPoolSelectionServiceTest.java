package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolSelectionRequestDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;

class HyperionQuizQuestionPoolSelectionServiceTest {

    @Mock
    private QuizQuestionPoolItemRepository itemRepository;

    private HyperionQuizQuestionPoolSelectionService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new HyperionQuizQuestionPoolSelectionService(itemRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void select_spreadsTheDrawOverCompetenciesUnitsAndCells() {
        List<QuizQuestionPoolItem> eligible = List.of(item(1L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE), item(2L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE),
                item(3L, 9L, 90L, QuizQuestionGenerationType.MULTIPLE_CHOICE), item(4L, 9L, 91L, QuizQuestionGenerationType.SINGLE_CHOICE),
                item(5L, 10L, 100L, QuizQuestionGenerationType.SINGLE_CHOICE), item(6L, 10L, 100L, QuizQuestionGenerationType.SINGLE_CHOICE));
        stubEligible(eligible);

        HyperionQuizQuestionPoolSelectionService.Selection selection = service.select(5L, request(List.of(9L, 10L), 5));

        assertThat(selection.items()).extracting(QuizQuestionPoolItem::getId).containsExactly(1L, 5L, 4L, 6L, 3L);
        assertThat(selection.competencyIdsWithoutQuestions()).isEmpty();
    }

    @Test
    void select_movesOnToOtherCellsWhenEveryCellHoldsOneQuestion() {
        QuizQuestionPoolItem drawnFirst = item(1L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE);
        QuizQuestionPoolItem drawnSecond = item(2L, 10L, 100L, QuizQuestionGenerationType.SINGLE_CHOICE);
        QuizQuestionPoolItem otherCell = item(3L, 9L, 90L, QuizQuestionGenerationType.MULTIPLE_CHOICE);
        QuizQuestionPoolItem otherUnit = item(4L, 10L, 101L, QuizQuestionGenerationType.SINGLE_CHOICE);
        stubEligible(List.of(drawnFirst, drawnSecond, otherCell, otherUnit));
        HyperionQuizQuestionPoolSelectionService.Selection first = service.select(5L, request(List.of(9L, 10L), 2));
        assertThat(first.items()).containsExactly(drawnFirst, drawnSecond);

        stubEligible(List.of(otherCell, otherUnit, drawnFirst, drawnSecond));
        HyperionQuizQuestionPoolSelectionService.Selection second = service.select(5L, request(List.of(9L, 10L), 2));

        assertThat(second.items()).containsExactly(otherCell, otherUnit);
    }

    @Test
    void select_prefersQuestionsDrawnLeastOftenAndLongestAgo() {
        QuizQuestionPoolItem drawnTwice = item(1L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE);
        drawnTwice.setSelectionCount(2);
        drawnTwice.setLastSelectedAt(ZonedDateTime.parse("2026-10-01T10:00:00Z"));
        QuizQuestionPoolItem drawnOnceLongAgo = item(2L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE);
        drawnOnceLongAgo.setSelectionCount(1);
        drawnOnceLongAgo.setLastSelectedAt(ZonedDateTime.parse("2026-09-01T10:00:00Z"));
        QuizQuestionPoolItem drawnOnceRecently = item(3L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE);
        drawnOnceRecently.setSelectionCount(1);
        drawnOnceRecently.setLastSelectedAt(ZonedDateTime.parse("2026-10-10T10:00:00Z"));
        QuizQuestionPoolItem neverDrawn = item(4L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE);
        stubEligible(List.of(drawnTwice, drawnOnceLongAgo, drawnOnceRecently, neverDrawn));

        HyperionQuizQuestionPoolSelectionService.Selection selection = service.select(5L, request(List.of(9L), 3));

        assertThat(selection.items()).extracting(QuizQuestionPoolItem::getId).containsExactly(4L, 2L, 3L);
    }

    @Test
    void select_filtersByUnitTypeAndDifficultyAndSkipsItemsWithoutQuestion() {
        QuizQuestionPoolItem wrongUnit = item(1L, 9L, 91L, QuizQuestionGenerationType.SINGLE_CHOICE);
        QuizQuestionPoolItem wrongType = item(2L, 9L, 90L, QuizQuestionGenerationType.MULTIPLE_CHOICE);
        QuizQuestionPoolItem wrongDifficulty = item(3L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE);
        wrongDifficulty.setDifficulty(QuizQuestionDifficulty.HARD);
        QuizQuestionPoolItem withoutQuestion = item(4L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE);
        withoutQuestion.setQuestion(null);
        QuizQuestionPoolItem matching = item(5L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE);
        stubEligible(List.of(wrongUnit, wrongType, wrongDifficulty, withoutQuestion, matching));

        HyperionQuizQuestionPoolSelectionService.Selection selection = service.select(5L,
                new QuizQuestionPoolSelectionRequestDTO(List.of(9L), List.of(90L), 10, List.of(QuizQuestionGenerationType.SINGLE_CHOICE), List.of(QuizQuestionDifficulty.MEDIUM)));

        assertThat(selection.items()).extracting(QuizQuestionPoolItem::getId).containsExactly(5L);
    }

    @Test
    void select_recordsTheDrawAndNamesCompetenciesWithoutQuestions() {
        QuizQuestionPoolItem first = item(1L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE);
        QuizQuestionPoolItem second = item(2L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE);
        stubEligible(List.of(first, second));

        HyperionQuizQuestionPoolSelectionService.Selection selection = service.select(5L, request(List.of(9L, 10L), 1));

        assertThat(selection.items()).containsExactly(first);
        assertThat(selection.competencyIdsWithoutQuestions()).containsExactly(10L);
        assertThat(first.getSelectionCount()).isEqualTo(1);
        assertThat(first.getLastSelectedAt()).isNotNull();
        assertThat(second.getSelectionCount()).isZero();
        assertThat(second.getLastSelectedAt()).isNull();
        ArgumentCaptor<List<QuizQuestionPoolItem>> saved = ArgumentCaptor.captor();
        verify(itemRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).containsExactly(first);
    }

    @Test
    void select_returnsFewerItemsWhenThePoolHoldsFewer() {
        stubEligible(List.of(item(1L, 9L, 90L, QuizQuestionGenerationType.SINGLE_CHOICE)));

        HyperionQuizQuestionPoolSelectionService.Selection selection = service.select(5L, request(List.of(9L), 8));

        assertThat(selection.items()).hasSize(1);
    }

    private void stubEligible(List<QuizQuestionPoolItem> items) {
        when(itemRepository.findAllForSelection(anyLong(), any(), eq(QuizQuestionPoolItemState.FILTERED))).thenReturn(items);
    }

    private static QuizQuestionPoolSelectionRequestDTO request(List<Long> competencyIds, int count) {
        return new QuizQuestionPoolSelectionRequestDTO(competencyIds, null, count, null, null);
    }

    private static QuizQuestionPoolItem item(long id, long competencyId, long lectureUnitId, QuizQuestionGenerationType type) {
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(id);
        item.setCompetencyId(competencyId);
        item.setLectureUnitId(lectureUnitId);
        item.setLanguage(QuizQuestionGenerationLanguage.EN);
        item.setQuestionType(type);
        item.setDifficulty(QuizQuestionDifficulty.MEDIUM);
        item.setState(QuizQuestionPoolItemState.FILTERED);
        item.setReviewState(QuizQuestionReviewState.APPROVED);
        item.setQuestion(new GeneratedQuizQuestionDTO(type, "Question " + id, "What about " + id + "?",
                List.of(new GeneratedQuizAnswerOptionDTO("A", true, null, null), new GeneratedQuizAnswerOptionDTO("B", false, null, null)), null, "Because."));
        return item;
    }
}
