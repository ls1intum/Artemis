package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.course.CourseAdminService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class CourseAdminServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "courseadminservice";

    @Autowired
    private CourseAdminService courseAdminService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private BuildJobRepository buildJobRepository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
    }

    @Test
    void testCourseSummary() {
        SecurityUtils.setAuthorizationObject();
        Course course = courseUtilService.addEmptyCourse();

        var programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now(), ZonedDateTime.now().plusDays(7), course);
        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));
        programmingExerciseRepository.save(programmingExercise);

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        conversationUtilService.addMessageInChannelOfCourseForUser(student1.getLogin(), course, TEST_PREFIX + "message");

        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, student1.getLogin());
        Result result = participationUtilService.createSubmissionAndResult(participation, 1L, true);
        createBuildJob(programmingExercise, course, participation, result);

        var summary = courseAdminService.getCourseSummary(course.getId());
        assertThat(summary.numberOfPosts()).isEqualTo(1L);
        assertThat(summary.numberOfAnswerPosts()).isEqualTo(1L);
        assertThat(summary.numberOfBuilds()).isEqualTo(1L);
    }

    private void createBuildJob(ProgrammingExercise programmingExercise, Course course, Participation participation, Result result) {
        var buildJob = new BuildJob();
        buildJob.setExerciseId(programmingExercise.getId());
        buildJob.setCourseId(course.getId());
        buildJob.setParticipationId(participation.getId());
        buildJob.setResult(result);

        buildJobRepository.save(buildJob);
    }
}
