package de.tum.cit.aet.artemis.metis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;

/**
 * Unit tests of the helpers over the questions of one comprehension check. The questions are built in memory with fixed ids; the helpers never touch the
 * database.
 */
class MetisQuestionThreadsTest {

    private static final ZonedDateTime SHOWN = ZonedDateTime.parse("2026-03-01T10:00:00Z");

    private static final long CHECK_ID = 7L;

    @Test
    void shouldListEveryFollowUpRightAfterItsMainQuestionWhenFollowUpsWereAskedAfterLaterMainQuestions() {
        MetisQuestion firstMain = mainQuestion(1L, 1);
        MetisQuestion secondMain = mainQuestion(2L, 2);
        MetisQuestion thirdMain = mainQuestion(3L, 3);
        MetisQuestion firstFollowUpOfFirst = followUp(4L, 4, 1L);
        MetisQuestion followUpOfSecond = followUp(5L, 5, 2L);
        MetisQuestion secondFollowUpOfFirst = followUp(6L, 6, 1L);

        List<MetisQuestion> shuffled = List.of(secondFollowUpOfFirst, thirdMain, followUpOfSecond, secondMain, firstFollowUpOfFirst, firstMain);

        assertThat(MetisQuestionThreads.inThreadOrder(shuffled)).containsExactly(firstMain, firstFollowUpOfFirst, secondFollowUpOfFirst, secondMain, followUpOfSecond, thirdMain);
    }

    @Test
    void shouldPlaceAFollowUpWithoutItsMainQuestionByItsOwnOrder() {
        MetisQuestion firstMain = mainQuestion(1L, 1);
        MetisQuestion thirdMain = mainQuestion(3L, 3);
        MetisQuestion followUpOfMissingMain = followUp(4L, 2, 2L);

        assertThat(MetisQuestionThreads.inThreadOrder(List.of(thirdMain, followUpOfMissingMain, firstMain))).containsExactly(firstMain, followUpOfMissingMain, thirdMain);
    }

    @Test
    void shouldReturnNoThreadsForACheckWithoutQuestions() {
        assertThat(MetisQuestionThreads.inThreadOrder(List.of())).isEmpty();
    }

    @Test
    void shouldReturnOnlyTheMainQuestionsInTheirOrder() {
        MetisQuestion firstMain = mainQuestion(1L, 1);
        MetisQuestion secondMain = mainQuestion(2L, 2);
        MetisQuestion followUp = followUp(3L, 3, 1L);

        assertThat(MetisQuestionThreads.mainQuestions(List.of(followUp, secondMain, firstMain))).containsExactly(firstMain, secondMain);
    }

    @Test
    void shouldReturnTheShownUnansweredQuestionAsTheCurrentQuestion() {
        MetisQuestion answeredMain = answered(shown(mainQuestion(1L, 1)));
        MetisQuestion openFollowUp = shown(followUp(3L, 3, 1L));
        MetisQuestion unshownMain = mainQuestion(2L, 2);

        assertThat(MetisQuestionThreads.currentQuestion(List.of(unshownMain, openFollowUp, answeredMain))).contains(openFollowUp);
    }

    @Test
    void shouldReturnNoCurrentQuestionWhenNothingIsOpen() {
        MetisQuestion answeredMain = answered(shown(mainQuestion(1L, 1)));
        MetisQuestion unshownMain = mainQuestion(2L, 2);

        assertThat(MetisQuestionThreads.currentQuestion(List.of(answeredMain, unshownMain))).isEmpty();
        assertThat(MetisQuestionThreads.currentQuestion(List.of())).isEmpty();
    }

    @Test
    void shouldPickTheLowestOrderWhenSeveralQuestionsAreOpen() {
        MetisQuestion openSecond = shown(mainQuestion(2L, 2));
        MetisQuestion openFirst = shown(mainQuestion(1L, 1));
        MetisQuestion openFollowUp = shown(followUp(3L, 3, 1L));

        assertThat(MetisQuestionThreads.currentQuestion(List.of(openFollowUp, openSecond, openFirst))).contains(openFirst);
    }

    @Test
    void shouldReturnTheUnshownMainQuestionWithTheLowestOrderAsTheNextOne() {
        MetisQuestion shownMain = answered(shown(mainQuestion(1L, 1)));
        MetisQuestion secondMain = mainQuestion(2L, 2);
        MetisQuestion thirdMain = mainQuestion(3L, 3);
        MetisQuestion followUp = followUp(4L, 4, 1L);

        assertThat(MetisQuestionThreads.nextUnshownMain(List.of(thirdMain, followUp, secondMain, shownMain))).contains(secondMain);
    }

    @Test
    void shouldReturnNoNextMainQuestionWhenEveryMainQuestionWasShown() {
        MetisQuestion firstMain = answered(shown(mainQuestion(1L, 1)));
        MetisQuestion secondMain = shown(mainQuestion(2L, 2));
        MetisQuestion followUp = followUp(3L, 3, 1L);

        assertThat(MetisQuestionThreads.nextUnshownMain(List.of(firstMain, secondMain, followUp))).isEmpty();
    }

    @Test
    void shouldCountOnlyTheFollowUpsOfTheGivenMainQuestion() {
        MetisQuestion firstMain = mainQuestion(1L, 1);
        MetisQuestion secondMain = mainQuestion(2L, 2);
        List<MetisQuestion> questions = List.of(firstMain, secondMain, followUp(3L, 3, 1L), followUp(4L, 4, 2L), followUp(5L, 5, 1L));

        assertThat(MetisQuestionThreads.followUpCount(questions, 1L)).isEqualTo(2);
        assertThat(MetisQuestionThreads.followUpCount(questions, 2L)).isEqualTo(1);
        assertThat(MetisQuestionThreads.followUpCount(List.of(firstMain, secondMain), 1L)).isZero();
    }

    @Test
    void shouldGiveANewQuestionTheOrderAfterTheHighestOne() {
        assertThat(MetisQuestionThreads.nextOrder(List.of(mainQuestion(1L, 1), followUp(4L, 5, 1L), mainQuestion(2L, 2)))).isEqualTo(6);
        assertThat(MetisQuestionThreads.nextOrder(List.of())).isEqualTo(1);
    }

    private static MetisQuestion mainQuestion(long id, int order) {
        return question(id, order, null);
    }

    private static MetisQuestion followUp(long id, int order, long parentQuestionId) {
        return question(id, order, parentQuestionId);
    }

    private static MetisQuestion question(long id, int order, @Nullable Long parentQuestionId) {
        MetisQuestion question = new MetisQuestion();
        question.setId(id);
        question.setCheckId(CHECK_ID);
        question.setQuestionOrder(order);
        question.setParentQuestionId(parentQuestionId);
        question.setQuestionText("Question " + order);
        question.setTimeLimitSeconds(120);
        return question;
    }

    private static MetisQuestion shown(MetisQuestion question) {
        question.setShownAt(SHOWN.plusMinutes(question.getQuestionOrder()));
        return question;
    }

    private static MetisQuestion answered(MetisQuestion question) {
        question.setAnswerText("An answer");
        question.setAnsweredAt(SHOWN.plusMinutes(question.getQuestionOrder()).plusSeconds(30));
        return question;
    }
}
