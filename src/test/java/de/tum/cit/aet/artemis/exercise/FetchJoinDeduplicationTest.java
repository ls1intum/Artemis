package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Pins the reason the participation lookups carry no {@code SELECT DISTINCT}.
 * <p>
 * Hibernate 6 de-duplicates the roots of a fetch join itself, so a participation with several submissions comes back
 * once. Writing {@code DISTINCT} would not change that result - Hibernate passes it through to SQL, where it becomes a
 * sort over every selected column, and those queries select the whole exercise and course including the problem
 * statement. If a future Hibernate stops de-duplicating, this test fails rather than the callers silently seeing
 * repeated rows.
 */
class FetchJoinDeduplicationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "fetchjoindedup";

    private static final ZonedDateTime RELEASE_DATE = ZonedDateTime.parse("2024-01-01T00:00:00Z");

    private static final ZonedDateTime DUE_DATE = RELEASE_DATE.plusDays(1);

    private static final ZonedDateTime ASSESSMENT_DUE_DATE = RELEASE_DATE.plusDays(3);

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Test
    void aParticipationWithSeveralSubmissionsIsReturnedOnce() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        var course = courseUtilService.createCourse();
        var exercise = textExerciseUtilService.createIndividualTextExercise(course, RELEASE_DATE, DUE_DATE, ASSESSMENT_DUE_DATE);
        var participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        for (int i = 0; i < 3; i++) {
            participationUtilService.addSubmission(participation, ParticipationFactory.generateTextSubmission("submission " + i, Language.ENGLISH, true));
        }
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var found = studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentIdAndTestRun(exercise.getId(), student.getId(), false);

        assertThat(found).as("the fetch join must not make the Optional ambiguous").isPresent();
        assertThat(found.get().getId()).isEqualTo(participation.getId());
        assertThat(found.get().getSubmissions()).as("all three submissions still arrive with it").hasSize(3);
    }
}
