package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Pins how the complaint lists of the assessment dashboards select complaints by exercise.
 * <p>
 * The queries behind them filter the denormalized {@code complaint.exerciseId} instead of reaching the exercise
 * through the result, so complaints of other exercises must stay out of the result and the assessor filter has to keep
 * working alongside it.
 */
class ComplaintExerciseFilterTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "complaintexercisefilter";

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private TextExercise exerciseWithComplaints;

    private TextExercise exerciseWithoutComplaints;

    private Complaint complaintAssessedByTutor1;

    private Complaint complaintAssessedByTutor2;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 2, 0, 0);
        exerciseWithComplaints = releasedTextExercise("with-complaints");
        exerciseWithoutComplaints = releasedTextExercise("without-complaints");
        complaintAssessedByTutor1 = addComplaint(exerciseWithComplaints, TEST_PREFIX + "tutor1", ZonedDateTime.now().minusHours(2));
        complaintAssessedByTutor2 = addComplaint(exerciseWithComplaints, TEST_PREFIX + "tutor2", ZonedDateTime.now().minusHours(1));
    }

    @Test
    void shouldFindAllComplaintsOfTheGivenExercises() {
        var complaints = complaintRepository.findAllByExerciseIdIn(Set.of(exerciseWithComplaints.getId()));

        assertThat(complaints).extracting(Complaint::getId).containsExactlyInAnyOrder(complaintAssessedByTutor1.getId(), complaintAssessedByTutor2.getId());
    }

    @Test
    void shouldNotFindComplaintsOfOtherExercises() {
        var complaints = complaintRepository.findAllByExerciseIdIn(Set.of(exerciseWithoutComplaints.getId()));

        assertThat(complaints).isEmpty();
    }

    @Test
    void shouldFindOnlyTheComplaintsAssessedByTheGivenTutorInTheGivenExercises() {
        long tutor1Id = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1").getId();

        var complaints = complaintRepository.findAllByResult_Assessor_IdAndExerciseIdIn(tutor1Id, Set.of(exerciseWithComplaints.getId(), exerciseWithoutComplaints.getId()));

        assertThat(complaints).extracting(Complaint::getId).containsExactly(complaintAssessedByTutor1.getId());
    }

    @Test
    void shouldFindOnlyTheComplaintsAssessedByTheGivenTutorInOneExercise() {
        long tutor2Id = userUtilService.getUserByLogin(TEST_PREFIX + "tutor2").getId();

        var complaints = complaintRepository.findAllByResult_Assessor_IdAndExerciseId(tutor2Id, exerciseWithComplaints.getId());

        assertThat(complaints).extracting(Complaint::getId).containsExactly(complaintAssessedByTutor2.getId());
    }

    private TextExercise releasedTextExercise(String title) {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise(TEST_PREFIX + title);
        return (TextExercise) course.getExercises().iterator().next();
    }

    private Complaint addComplaint(TextExercise exercise, String assessorLogin, ZonedDateTime submissionDate) {
        var submission = new TextSubmission();
        submission.setSubmissionDate(submissionDate);
        submission.setSubmitted(true);
        var savedSubmission = participationUtilService.addSubmission(exercise, submission, TEST_PREFIX + "student1");
        var assessedSubmission = participationUtilService.addResultToSubmission(savedSubmission, AssessmentType.MANUAL, userUtilService.getUserByLogin(assessorLogin), 50.0, true,
                ZonedDateTime.now());
        var complaint = new Complaint().participant(userUtilService.getUserByLogin(TEST_PREFIX + "student1")).result(assessedSubmission.getLatestResult())
                .complaintType(ComplaintType.COMPLAINT);
        return complaintRepository.save(complaint);
    }
}
