package de.tum.cit.aet.artemis.programming.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

class VcsAnalyticsLogRepositoryIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    @Autowired
    private VcsAnalyticsLogRepository vcsAnalyticsLogRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.createAndSaveUser("student1");
    }

    @Test
    void testFindCourseIdByParticipationId_NormalCourse() {
        Course testCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise testExercise = (ProgrammingExercise) testCourse.getExercises().iterator().next();
        ProgrammingSubmission submission = programmingExerciseUtilService.addProgrammingSubmission(testExercise, new ProgrammingSubmission(), "student1");
        Long participationId = submission.getParticipation().getId();

        // Test the functionality of findCourseIdByParticipationId
        Optional<Long> courseIdOpt = vcsAnalyticsLogRepository.findCourseIdByParticipationId(participationId);

        assertThat(courseIdOpt).isPresent();
        assertThat(courseIdOpt.get()).isEqualTo(testCourse.getId());
    }

    @Test
    void testFindCourseIdByParticipationId_ExamCourse() {
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        Long expectedCourseId = examExercise.getExerciseGroup().getExam().getCourse().getId();
        ProgrammingSubmission submission = programmingExerciseUtilService.addProgrammingSubmission(examExercise, new ProgrammingSubmission(), "student1");
        Long participationId = submission.getParticipation().getId();

        // Test the functionality of findCourseIdByParticipationId
        Optional<Long> courseIdOpt = vcsAnalyticsLogRepository.findCourseIdByParticipationId(participationId);

        assertThat(courseIdOpt).isPresent();
        assertThat(courseIdOpt.get()).isEqualTo(expectedCourseId);
    }

    @Test
    void testFindCourseIdByParticipationId_NonExistentParticipation() {
        // Test the functionality of findCourseIdByParticipationId
        Optional<Long> courseIdOpt = vcsAnalyticsLogRepository.findCourseIdByParticipationId(999999L);
        assertThat(courseIdOpt).isEmpty();
    }
}
