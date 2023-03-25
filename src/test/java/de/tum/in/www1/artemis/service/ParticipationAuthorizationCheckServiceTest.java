package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;

class ParticipationAuthorizationCheckServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "participationauthservice";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ParticipationAuthorizationCheckService participationAuthCheckService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation participation;

    @BeforeEach
    void setup() throws Exception {
        database.addUsers(TEST_PREFIX, 2, 1, 1, 1);

        var course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        programmingExercise = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipation_asInstructor() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipation_asInstructor_edgeCase_exercise_null() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

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
    void testCanAccessParticipation_asInstructor_edgeCase_programmingExercise_null() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        // Check with programmingExercise only null
        participation.setProgrammingExercise(null);
        programmingExercise.getSolutionParticipation().setProgrammingExercise(null);
        programmingExercise.getTemplateParticipation().setProgrammingExercise(null);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipation_asInstructor_edgeCase_programmingExercise_unknownId() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

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
    void testCanAccessParticipation_asStudent() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCanAccessParticipation_asTutor() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCanAccessParticipation_asEditor() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    void checkCanAccessParticipation(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation, boolean shouldBeAllowed,
            boolean shouldBeAllowedTemplateSolution) {
        var isAllowed = participationAuthCheckService.canAccessParticipation(participation);
        assertThat(isAllowed).isEqualTo(shouldBeAllowed);

        var isAllowedSolution = participationAuthCheckService.canAccessParticipation(programmingExercise.getSolutionParticipation());
        assertThat(isAllowedSolution).isEqualTo(shouldBeAllowedTemplateSolution);

        var isAllowedTemplate = participationAuthCheckService.canAccessParticipation(programmingExercise.getTemplateParticipation());
        assertThat(isAllowedTemplate).isEqualTo(shouldBeAllowedTemplateSolution);

        var responseOther = participationAuthCheckService.canAccessParticipation((ProgrammingExerciseParticipation) null);
        assertThat(responseOther).isFalse();
    }
}
