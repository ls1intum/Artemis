package de.tum.cit.aet.artemis.athena.service.connectors;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.athena.service.AthenaModuleService;
import de.tum.cit.aet.artemis.athena.service.AthenaRepositoryExportService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.util.LocalVCRepositoryTestService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class AthenaRepositoryExportServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "athenarepositoryexport";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private AthenaRepositoryExportService athenaRepositoryExportService;

    @Autowired
    private AthenaModuleService athenaModuleService;

    @Autowired
    private LocalVCRepositoryTestService localVCRepositoryTestService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    /**
     * Writes a file into the real LocalVC repository of the given type, so that the export has something to return.
     */
    private void seedRepository(ProgrammingExercise exercise, RepositoryType repositoryType, String fileName) {
        var repositoryUri = localVCRepositoryTestService.repositoryUri(exercise.getProjectKey(), exercise.generateRepositoryName(repositoryType));
        localVCRepositoryTestService.writeFilesAndPush(repositoryUri, Map.of(fileName, "Test"), "Add " + fileName);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1")
    void shouldExportRepository() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        var programmingExercise = programmingExerciseRepository.findAllByCourseId(course.getId()).getFirst();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        seedRepository(programmingExerciseWithId, RepositoryType.SOLUTION, "Solution.java");

        // The student participation gets a real LocalVC repository, which is seeded with a file of its own.
        var studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExerciseWithId, TEST_PREFIX + "student1");
        localVCRepositoryTestService.writeFilesAndPush(new LocalVCRepositoryUri(studentParticipation.getRepositoryUri()), Map.of("Student.java", "Test"), "Add Student.java");
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(studentParticipation);
        var programmingSubmissionWithId = programmingExerciseUtilService.addProgrammingSubmission(programmingExerciseWithId, submission, TEST_PREFIX + "student1");

        Map<String, String> resultStudentRepo = athenaRepositoryExportService.getStudentRepositoryFilesContent(programmingExerciseWithId.getId(),
                programmingSubmissionWithId.getId());
        Map<String, String> resultSolutionRepo = athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), RepositoryType.SOLUTION);

        assertThat(resultStudentRepo).as("the student repository export contains the pushed file").containsEntry("Student.java", "Test");
        assertThat(resultSolutionRepo).as("the solution repository export contains the pushed file").containsEntry("Solution.java", "Test");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1")
    void shouldExportAllValidInstructorRepositoryTypes() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        var programmingExercise = programmingExerciseRepository.findAllByCourseId(course.getId()).getFirst();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        seedRepository(programmingExerciseWithId, RepositoryType.TEMPLATE, "Template.java");
        seedRepository(programmingExerciseWithId, RepositoryType.SOLUTION, "Solution.java");
        seedRepository(programmingExerciseWithId, RepositoryType.TESTS, "Tests.java");

        Map<String, String> templateRepo = athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), RepositoryType.TEMPLATE);
        Map<String, String> solutionRepo = athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), RepositoryType.SOLUTION);
        Map<String, String> testsRepo = athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), RepositoryType.TESTS);

        assertThat(templateRepo).as("the template repository export contains the pushed file").containsEntry("Template.java", "Test");
        assertThat(solutionRepo).as("the solution repository export contains the pushed file").containsEntry("Solution.java", "Test");
        assertThat(testsRepo).as("the tests repository export contains the pushed file").containsEntry("Tests.java", "Test");
    }

    @Test
    void shouldThrowServiceUnavailableWhenFeedbackSuggestionsNotEnabled() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(null);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        assertThatExceptionOfType(ServiceUnavailableException.class).as("Should throw ServiceUnavailableException when feedback suggestions are not enabled")
                .isThrownBy(() -> athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), RepositoryType.TEMPLATE))
                .withMessageContaining("Feedback suggestions are not enabled");
    }

    @Test
    void shouldThrowBadRequestAlertExceptionForInvalidRepositoryType() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        var invalidRepositoryTypes = Set.of(RepositoryType.USER, RepositoryType.AUXILIARY);
        for (var invalidRepositoryType : invalidRepositoryTypes) {
            assertThatExceptionOfType(BadRequestAlertException.class).as("Should throw BadRequestAlertException for invalid repository type: " + invalidRepositoryType)
                    .isThrownBy(() -> athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), invalidRepositoryType))
                    .withMessageContaining("Invalid instructor repository type")
                    .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("invalid.instructor.repository.type"));
        }
    }

    @Test
    void shouldThrowBadRequestAlertExceptionWhenFeedbackSuggestionModuleIsNull() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(null);

        assertThatExceptionOfType(BadRequestAlertException.class).as("Should throw BadRequestAlertException when feedback suggestion module is null")
                .isThrownBy(() -> athenaModuleService.getAthenaModuleUrl(programmingExercise))
                .withMessageContaining("Exercise does not have a feedback suggestion module configured");
    }
}
