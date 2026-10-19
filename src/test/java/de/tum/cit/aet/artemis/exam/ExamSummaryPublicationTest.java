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

    /**
     * The gate compares against {@code ZonedDateTime.now()}, so the fixtures only have to sit far enough on either side of it
     * to stay unambiguous. Fixed instants keep the assertions independent of the wall clock and of the machine's time zone.
     */
    private static final ZonedDateTime PAST = ZonedDateTime.parse("2000-01-01T00:00:00Z");

    private static final ZonedDateTime FUTURE = ZonedDateTime.parse("2999-01-01T00:00:00Z");

    private static final ZonedDateTime FURTHER_FUTURE = ZonedDateTime.parse("2999-06-01T00:00:00Z");

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
        assertThat(examWith(FUTURE, null).isExamSummaryPublished()).isFalse();
    }

    @Test
    void isExamSummaryPublished_publicationDateInThePast_available() {
        assertThat(examWith(PAST, null).isExamSummaryPublished()).isTrue();
    }

    @Test
    void isExamSummaryPublished_resultsAlreadyPublished_availableDespiteFuturePublicationDate() {
        // the safeguard: a misconfigured date must never hide the overview once the grades are out
        assertThat(examWith(FUTURE, PAST).isExamSummaryPublished()).isTrue();
    }

    @Test
    void isExamSummaryPublished_resultsNotYetPublished_staysWithheld() {
        // the results are published even later than the summary, which is the ordering ExamResource's validation enforces
        assertThat(examWith(FUTURE, FURTHER_FUTURE).isExamSummaryPublished()).isFalse();
    }

    @Test
    void isExamSummaryPublished_testExam_neverGated() {
        Exam testExam = examWith(FUTURE, null);
        testExam.setTestExam(true);

        assertThat(testExam.isExamSummaryPublished()).isTrue();
    }
}
