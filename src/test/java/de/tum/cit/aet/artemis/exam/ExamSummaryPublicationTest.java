package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Unit tests for {@link Exam#isExamSummaryPublished()}, the server-side gate that decides whether a student may fetch the
 * submission overview.
 * <p>
 * The safeguard branch (a publication date still in the future, but the results are already out) cannot be reached through
 * the REST API: {@code ExamResource#checkExamForDatesConflictsElseThrow} rejects an exam whose summary date is after its
 * publish-results date, so neither an integration test nor
 * {@code ExamSummaryPublicationDate.spec.ts} can construct it. It is reachable for exams stored before that validation
 * existed and for direct database edits, which is exactly why it exists, so it is pinned here instead.
 */
class ExamSummaryPublicationTest {

    private static Exam examWith(ZonedDateTime summaryPublicationDate, ZonedDateTime publishResultsDate) {
        Exam exam = new Exam();
        exam.setExamSummaryPublicationDate(summaryPublicationDate);
        exam.setPublishResultsDate(publishResultsDate);
        return exam;
    }

    @Test
    void isExamSummaryPublished_noPublicationDate_availableImmediately() {
        assertThat(examWith(null, null).isExamSummaryPublished()).isTrue();
    }

    @Test
    void isExamSummaryPublished_publicationDateInTheFuture_withheld() {
        assertThat(examWith(ZonedDateTime.now().plusDays(1), null).isExamSummaryPublished()).isFalse();
    }

    @Test
    void isExamSummaryPublished_publicationDateInThePast_available() {
        assertThat(examWith(ZonedDateTime.now().minusMinutes(1), null).isExamSummaryPublished()).isTrue();
    }

    @Test
    void isExamSummaryPublished_resultsAlreadyPublished_availableDespiteFuturePublicationDate() {
        // the safeguard: a misconfigured date must never hide the overview once the grades are out
        assertThat(examWith(ZonedDateTime.now().plusDays(1), ZonedDateTime.now().minusMinutes(1)).isExamSummaryPublished()).isTrue();
    }

    @Test
    void isExamSummaryPublished_resultsNotYetPublished_staysWithheld() {
        assertThat(examWith(ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2)).isExamSummaryPublished()).isFalse();
    }

    @Test
    void isExamSummaryPublished_testExam_neverGated() {
        Exam testExam = examWith(ZonedDateTime.now().plusDays(1), null);
        testExam.setTestExam(true);

        assertThat(testExam.isExamSummaryPublished()).isTrue();
    }
}
