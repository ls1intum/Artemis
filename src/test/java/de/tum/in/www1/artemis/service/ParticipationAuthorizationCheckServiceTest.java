package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.participation.ParticipationInterface;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

class ParticipationAuthorizationCheckServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "participationauthservice";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ParticipationAuthorizationCheckService participationAuthCheckService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation participation;

    @BeforeEach
    void setup() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);

        final var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        programmingExercise = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanAccessOwnTextParticipation() {
        final var course = exerciseUtilService.addCourseWithOneExerciseAndSubmissions(TEST_PREFIX, "text", 1);
        final var exercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        final var participation = studentParticipationRepository.findByExerciseId(exercise.getId()).stream().findFirst().orElseThrow();

        checkParticipationAccess(participation, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStudentCannotAccessOtherTextParticipation() {
        final var course = exerciseUtilService.addCourseWithOneExerciseAndSubmissions(TEST_PREFIX, "text", 2);
        final var exercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        final var participation = studentParticipationRepository.findByExerciseId(exercise.getId()).stream()
                .filter(studentParticipation -> !studentParticipation.isOwnedBy(TEST_PREFIX + "student1")).findFirst().orElseThrow();

        checkParticipationAccess(participation, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipationAsInstructor() {
        // Set solution and template participation
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipationAsInstructorEdgeCaseExerciseNull() {
        // Set solution and template participation
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        // Check with exercise null
        participation.setExercise(null);
        programmingExercise.getSolutionParticipation().setExercise(null);
        programmingExercise.getTemplateParticipation().setExercise(null);

        checkCanAccessParticipation(programmingExercise, participation, true, true);

        // Check with exercise and programmingExercise null (and set everything again)
        participation.setExercise(null);
        programmingExercise.getSolutionParticipation().setExercise(null);
        programmingExercise.getTemplateParticipation().setExercise(null);
        // Note that in the current implementation, setProgrammingExercise is equivalent to setExercise only for the ProgrammingExerciseStudentParticipation
        participation.setProgrammingExercise(null);
        programmingExercise.getSolutionParticipation().setProgrammingExercise(null);
        programmingExercise.getTemplateParticipation().setProgrammingExercise(null);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipationAsInstructorEdgeCaseProgrammingExerciseNull() {
        // Set solution and template participation
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        // Check with programmingExercise only null
        participation.setProgrammingExercise(null);
        programmingExercise.getSolutionParticipation().setProgrammingExercise(null);
        programmingExercise.getTemplateParticipation().setProgrammingExercise(null);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipationAsInstructorEdgeCaseProgrammingExerciseUnknownId() {
        // Set solution and template participation
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        // Check with programmingExercise null and a non-existent participation id
        participation.setProgrammingExercise(null);
        participation.setId(123456L);
        programmingExercise.getSolutionParticipation().setProgrammingExercise(null);
        programmingExercise.getSolutionParticipation().setId(123456L);
        programmingExercise.getTemplateParticipation().setProgrammingExercise(null);
        programmingExercise.getTemplateParticipation().setId(123456L);

        checkCanAccessParticipation(programmingExercise, participation, false, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanAccessParticipationAsStudent() {
        // Set solution and template participation
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCanAccessParticipationAsTutor() {
        // Set solution and template participation
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCanAccessParticipationAsEditor() {
        // Set solution and template participation
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    void checkCanAccessParticipation(final ProgrammingExercise programmingExercise, final ProgrammingExerciseStudentParticipation participation, final boolean shouldBeAllowed,
            final boolean shouldBeAllowedTemplateSolution) {
        checkParticipationAccess(participation, shouldBeAllowed);
        checkParticipationAccess(programmingExercise.getSolutionParticipation(), shouldBeAllowedTemplateSolution);
        checkParticipationAccess(programmingExercise.getTemplateParticipation(), shouldBeAllowedTemplateSolution);
        checkParticipationAccess(null, false);
    }

    private <T extends ParticipationInterface> void checkParticipationAccess(final T participation, final boolean shouldBeAllowed) {
        var isAllowed = participationAuthCheckService.canAccessParticipation(participation);
        assertThat(isAllowed).isEqualTo(shouldBeAllowed);

        if (shouldBeAllowed) {
            assertThatCode(() -> participationAuthCheckService.checkCanAccessParticipationElseThrow(participation)).doesNotThrowAnyException();
        }
        else {
            assertThatThrownBy(() -> participationAuthCheckService.checkCanAccessParticipationElseThrow(participation)).isInstanceOf(AccessForbiddenException.class);
        }
    }
}
