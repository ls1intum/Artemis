package de.tum.cit.aet.artemis.iris.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class IrisAssessmentRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "irisassessmentrepository";

    @Autowired
    private IrisAssessmentRepository irisAssessmentRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private ProgrammingExercise programmingExercise;

    private User student;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var course = courseUtilService.addEmptyCourse();
        programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false);
    }

    private IrisAssessment saveAssessment(IrisVerdict verdict, IrisVerdictReview review, List<String> reasoning) {
        var assessment = new IrisAssessment(student, programmingExercise);
        assessment.setVerdict(verdict);
        assessment.setVerdictReview(review);
        assessment.setReasoning(reasoning);
        return irisAssessmentRepository.save(assessment);
    }

    @Test
    void findWithReasoningByIdReturnsReasoningList() {
        var saved = saveAssessment(IrisVerdict.SUSPICIOUS, null, List.of("first", "second"));

        var loaded = irisAssessmentRepository.findWithReasoningById(saved.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getReasoning()).containsExactly("first", "second");
    }

    @Test
    void findWithReasoningByIdReturnsEmptyForUnknownId() {
        assertThat(irisAssessmentRepository.findWithReasoningById(-1L)).isEmpty();
    }

    @Test
    void findWithExerciseAndCourseByIdElseThrowReturnsAssessmentWithExerciseAndCourseLoaded() {
        var saved = saveAssessment(IrisVerdict.UNSUSPICIOUS, null, List.of());

        var loaded = irisAssessmentRepository.findWithExerciseAndCourseByIdElseThrow(saved.getId());

        assertThat(loaded.getExercise().getId()).isEqualTo(programmingExercise.getId());
        assertThat(loaded.getExercise().getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
    }

    @Test
    void findWithStudentByIdElseThrowReturnsAssessmentWithStudentAndExerciseLoaded() {
        var saved = saveAssessment(null, null, null);

        var loaded = irisAssessmentRepository.findWithStudentByIdElseThrow(saved.getId());

        assertThat(loaded.getStudent().getLogin()).isEqualTo(student.getLogin());
        assertThat(loaded.getExercise().getId()).isEqualTo(programmingExercise.getId());
    }

    @Test
    void findWithReasoningAndExerciseAndStudentByIdElseThrowReturnsAssessmentWithExerciseAndStudentAndReasoningLoaded() {
        var saved = saveAssessment(IrisVerdict.UNSUSPICIOUS, null, List.of("first", "second"));

        var loaded = irisAssessmentRepository.findWithReasoningAndExerciseAndStudentByIdElseThrow(saved.getId());

        assertThat(loaded.getExercise().getId()).isEqualTo(programmingExercise.getId());
        assertThat(loaded.getStudent().getLogin()).isEqualTo(student.getLogin());
        assertThat(loaded.getReasoning()).containsExactly("first", "second");
    }

    @Test
    void existsByCourseIdAndVerdictAndVerdictReviewIsNullReturnsTrueForUnreviewedSuspiciousAssessment() {
        saveAssessment(IrisVerdict.SUSPICIOUS, null, List.of());
        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        assertThat(irisAssessmentRepository.existsByCourseIdAndVerdictAndVerdictReviewIsNull(courseId, IrisVerdict.SUSPICIOUS)).isTrue();
    }

    @Test
    void existsByCourseIdAndVerdictAndVerdictReviewIsNullReturnsFalseWhenAlreadyReviewed() {
        saveAssessment(IrisVerdict.SUSPICIOUS, IrisVerdictReview.ACCEPTED, List.of());
        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        assertThat(irisAssessmentRepository.existsByCourseIdAndVerdictAndVerdictReviewIsNull(courseId, IrisVerdict.SUSPICIOUS)).isFalse();
    }

    @Test
    void existsByCourseIdAndVerdictAndVerdictReviewIsNullReturnsFalseForDifferentVerdict() {
        saveAssessment(IrisVerdict.UNSUSPICIOUS, null, List.of());
        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        assertThat(irisAssessmentRepository.existsByCourseIdAndVerdictAndVerdictReviewIsNull(courseId, IrisVerdict.SUSPICIOUS)).isFalse();
    }

    @Test
    void deleteAllByIdInBulkRemovesOnlyTheGivenAssessments() {
        var toDelete = saveAssessment(IrisVerdict.SUSPICIOUS, null, List.of("reason"));
        var toKeep = saveAssessment(IrisVerdict.UNSUSPICIOUS, null, List.of());

        irisAssessmentRepository.deleteAllByIdInBulk(Set.of(toDelete.getId()));

        assertThat(irisAssessmentRepository.findById(toDelete.getId())).isEmpty();
        assertThat(irisAssessmentRepository.findById(toKeep.getId())).isPresent();
    }
}
