package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.FeatureBonusAward;
import de.tum.cit.aet.artemis.assessment.dto.FeatureBonusAwardDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeatureBonusAwardRequestDTO;
import de.tum.cit.aet.artemis.assessment.dto.ParticipationFeatureBonusDTO;
import de.tum.cit.aet.artemis.assessment.dto.StudentFeatureBonusDTO;
import de.tum.cit.aet.artemis.assessment.repository.FeatureBonusAwardRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionForTutorAssessmentDTO;

class ProgrammingFeatureBonusIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "programmingfeaturebonus";

    @Autowired
    private FeatureBonusAwardRepository featureBonusAwardRepository;

    @Autowired
    private CourseConfigurationRepository courseConfigurationRepository;

    @Autowired
    private TeamUtilService teamUtilService;

    private ProgrammingExercise exercise;

    /** The participation of student1, whose assessment tutor1 holds. */
    private StudentParticipation participation;

    private ProgrammingSubmission submission;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 1);
        var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setDueDate(ZonedDateTime.now().minusDays(2));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        exercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(2));
        exercise.setFeatureBonusEnabled(true);
        exercise = programmingExerciseRepository.save(exercise);

        submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, ParticipationFactory.generateProgrammingSubmission(true), TEST_PREFIX + "student1");
        participation = (StudentParticipation) submission.getParticipation();
        // the draft manual result is the lock tutor1 holds while assessing
        participationUtilService.addResultToSubmission(AssessmentType.SEMI_AUTOMATIC, null, submission, TEST_PREFIX + "tutor1", new ArrayList<>());

        CourseConfiguration configuration = courseConfigurationRepository.findByCourseId(course.getId()).orElseThrow();
        configuration.setFeatureBonus(0.5, 5.0);
        courseConfigurationRepository.save(configuration);
    }

    private String awardsUrl() {
        return "/api/assessment/participations/" + participation.getId() + "/feature-bonus-awards";
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void assessingTutorAwardsChangesAndRemovesPoints() throws Exception {
        var created = request.postWithResponseBody(awardsUrl(), new FeatureBonusAwardRequestDTO(" Intelligent enemies ", "Simple algorithm", 1.5), FeatureBonusAwardDTO.class,
                HttpStatus.CREATED);
        assertThat(created.title()).isEqualTo("Intelligent enemies");
        assertThat(created.points()).isEqualTo(1.5);
        assertThat(created.createdBy()).isEqualTo(TEST_PREFIX + "tutor1");

        var updated = request.putWithResponseBody(awardsUrl() + "/" + created.id(), new FeatureBonusAwardRequestDTO("Intelligent enemies", "Path finding", 2.5),
                FeatureBonusAwardDTO.class, HttpStatus.OK);
        assertThat(updated.points()).isEqualTo(2.5);
        assertThat(request.getList(awardsUrl(), HttpStatus.OK, FeatureBonusAwardDTO.class)).singleElement()
                .satisfies(award -> assertThat(award.justification()).isEqualTo("Path finding"));

        request.delete(awardsUrl() + "/" + created.id(), HttpStatus.OK);
        assertThat(featureBonusAwardRepository.findAllByParticipationId(participation.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void assessmentPageLearnsThatTheExerciseAllowsFeatureBonus() throws Exception {
        var assessment = request.get("/api/programming/programming-submissions/" + submission.getId() + "/lock", HttpStatus.OK, ProgrammingSubmissionForTutorAssessmentDTO.class);
        assertThat(assessment.participation().exercise().featureBonusEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void otherTutorCannotAward() throws Exception {
        request.postWithResponseBody(awardsUrl(), new FeatureBonusAwardRequestDTO("Feature", null, 1.0), FeatureBonusAwardDTO.class, HttpStatus.FORBIDDEN);
        assertThat(featureBonusAwardRepository.findAllByParticipationId(participation.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void cannotAwardBeforeTheDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().plusDays(1));
        request.postWithResponseBody(awardsUrl(), new FeatureBonusAwardRequestDTO("Feature", null, 1.0), FeatureBonusAwardDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void cannotAwardWhenTheExerciseDoesNotAllowIt() throws Exception {
        exercise.setFeatureBonusEnabled(false);
        programmingExerciseRepository.save(exercise);
        request.postWithResponseBody(awardsUrl(), new FeatureBonusAwardRequestDTO("Feature", null, 1.0), FeatureBonusAwardDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void rejectsInvalidPoints() throws Exception {
        request.postWithResponseBody(awardsUrl(), new FeatureBonusAwardRequestDTO("Feature", null, 0.0), FeatureBonusAwardDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(awardsUrl(), new FeatureBonusAwardRequestDTO(" ", null, 1.0), FeatureBonusAwardDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void sumsPointsPerParticipationAndPerStudentIncludingTeams() throws Exception {
        award(participation, 3.0);
        award(participation, 9.0);

        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var student3 = userUtilService.getUserByLogin(TEST_PREFIX + "student3");
        Team team = teamUtilService.createTeam(Set.of(student2, student3), userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), exercise, TEST_PREFIX + "team");
        award(participationUtilService.addTeamParticipationForProgrammingExercise(exercise, team), 3.0);

        var perParticipation = request.getList("/api/assessment/exercises/" + exercise.getId() + "/feature-bonus", HttpStatus.OK, ParticipationFeatureBonusDTO.class);
        assertThat(perParticipation).filteredOn(bonus -> bonus.participationId() == participation.getId()).singleElement().satisfies(bonus -> {
            assertThat(bonus.points()).isEqualTo(12.0);
            // 12 points at 0.5% each would be 6%, capped at the maximum of 5%
            assertThat(bonus.bonusPercent()).isEqualTo(5.0);
        });

        long courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();
        var perStudent = request.getList("/api/assessment/courses/" + courseId + "/feature-bonus", HttpStatus.OK, StudentFeatureBonusDTO.class);
        assertThat(perStudent).hasSize(3);
        assertThat(perStudent).filteredOn(bonus -> bonus.studentId() == student2.getId()).singleElement().satisfies(bonus -> {
            assertThat(bonus.points()).isEqualTo(3.0);
            assertThat(bonus.bonusPercent()).isEqualTo(1.5);
        });
        assertThat(perStudent).filteredOn(bonus -> bonus.studentId() == student3.getId()).singleElement().satisfies(bonus -> assertThat(bonus.points()).isEqualTo(3.0));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void tutorCannotReadTheCourseBonus() throws Exception {
        long courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();
        request.getList("/api/assessment/courses/" + courseId + "/feature-bonus", HttpStatus.FORBIDDEN, StudentFeatureBonusDTO.class);
    }

    private void award(StudentParticipation awardedParticipation, double points) {
        var award = new FeatureBonusAward();
        award.setParticipation(awardedParticipation);
        award.setTitle("Feature");
        award.setPoints(points);
        featureBonusAwardRepository.save(award);
    }

    @Test
    void conversionNeedsBothValuesInRange() {
        var configuration = new CourseConfiguration();
        configuration.setFeatureBonus(null, null);
        assertThat(configuration.getFeatureBonusPercentPerPoint()).isNull();
        for (Double[] invalid : List.of(new Double[] { 0.5, null }, new Double[] { 0.0, 5.0 }, new Double[] { 0.5, 101.0 })) {
            assertThatThrownBy(() -> configuration.setFeatureBonus(invalid[0], invalid[1])).isInstanceOf(BadRequestAlertException.class);
        }
    }
}
