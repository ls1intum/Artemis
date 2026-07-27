package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.dto.AuxiliaryRepositoryDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseListItemDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionWithResultsDTO;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;
import de.tum.cit.aet.artemis.programming.dto.TemplateSolutionParticipationDTO;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * Wire-contract tests for the programming exercise retrieval endpoints.
 * <p>
 * These endpoints returned entities before the DTO migration; the client types every response as the full entity
 * model, so a dropped or relocated field is invisible to the compiler and to a status-code assertion. Every test here
 * pins the field set an enumerated client consumer reads, at the location it reads it, and the read endpoints
 * additionally assert that they wrote nothing.
 */
class ProgrammingExerciseRetrievalIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexretrieval";

    private static final String EXERCISE_BASE = "/api/programming/programming-exercises/";

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionTestRepository;

    private Course course;

    private ProgrammingExercise exercise;

    private ProgrammingExercise examExercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(exercise);
        examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // R11 — GET programming-exercises/{exerciseId}
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExercise_courseExercise_carriesTheTracedFieldSet() throws Exception {
        ProgrammingExerciseResponseDTO response = assertThatDb(() -> request.get(EXERCISE_BASE + exercise.getId(), HttpStatus.OK, ProgrammingExerciseResponseDTO.class))
                .hasBeenCalledAtMostTimes(10);

        assertThat(response.id()).isEqualTo(exercise.getId());
        // The discriminator the client switches on; a record emits no Jackson subtype id for free.
        assertThat(response.type()).isEqualTo("programming");
        assertThat(response.title()).isEqualTo(exercise.getTitle());
        assertThat(response.shortName()).isEqualTo(exercise.getShortName());
        assertThat(response.projectKey()).isEqualTo(exercise.getProjectKey());
        assertThat(response.programmingLanguage()).isEqualTo(exercise.getProgrammingLanguage());
        assertThat(response.maxPoints()).isEqualTo(exercise.getMaxPoints());
        assertThat(response.bonusPoints()).isEqualTo(exercise.getBonusPoints());
        assertThat(response.includedInOverallScore()).isEqualTo(exercise.getIncludedInOverallScore());
        assertThat(response.mode()).isEqualTo(exercise.getMode());
        assertThat(response.teamMode()).isEqualTo(exercise.isTeamMode());
        assertThat(response.assessmentType()).isEqualTo(exercise.getAssessmentType());
        assertThat(response.difficulty()).isEqualTo(exercise.getDifficulty());
        assertThat(response.allowOnlineEditor()).isEqualTo(exercise.isAllowOnlineEditor());
        assertThat(response.allowOfflineIde()).isEqualTo(exercise.isAllowOfflineIde());
        assertThat(response.allowOnlineIde()).isEqualTo(exercise.isAllowOnlineIde());
        assertThat(response.staticCodeAnalysisEnabled()).isEqualTo(exercise.isStaticCodeAnalysisEnabled());
        assertThat(response.showTestNamesToStudents()).isEqualTo(exercise.getShowTestNamesToStudents());
        assertThat(response.releaseTestsWithExampleSolution()).isEqualTo(exercise.isReleaseTestsWithExampleSolution());
        assertThat(response.testCasesChanged()).isEqualTo(exercise.getTestCasesChanged());
        assertThat(response.presentationScoreEnabled()).isEqualTo(exercise.getPresentationScoreEnabled());
        assertThat(response.secondCorrectionEnabled()).isEqualTo(exercise.getSecondCorrectionEnabled());
        // The Playwright helper that pushes the timeline into the past re-sends all six date fields.
        assertThat(response.releaseDate()).isEqualTo(exercise.getReleaseDate());
        assertThat(response.startDate()).isEqualTo(exercise.getStartDate());
        assertThat(response.dueDate()).isEqualTo(exercise.getDueDate());
        assertThat(response.assessmentDueDate()).isEqualTo(exercise.getAssessmentDueDate());
        assertThat(response.exampleSolutionPublicationDate()).isEqualTo(exercise.getExampleSolutionPublicationDate());
        assertThat(response.buildAndTestStudentSubmissionsAfterDueDate()).isEqualTo(exercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        // gradingInstructionFeedbackUsed is a transient the grading-instruction editor branches on.
        assertThat(response.gradingInstructionFeedbackUsed()).isNotNull();
        assertThat(response.buildConfig()).isNotNull();
        assertThat(response.buildConfig().id()).isEqualTo(exercise.getBuildConfig().getId());
        assertThat(response.templateParticipation()).isNotNull();
        assertThat(response.templateParticipation().type()).isEqualTo("template");
        assertThat(response.solutionParticipation()).isNotNull();
        assertThat(response.solutionParticipation().type()).isEqualTo("solution");

        // The nested course must not be flattened to an id: client access rights are computed from the group names.
        assertThat(response.course()).isNotNull();
        assertThat(response.course().id()).isEqualTo(course.getId());
        assertThat(response.course().title()).isEqualTo(course.getTitle());
        assertThat(response.course().shortName()).isEqualTo(course.getShortName());
        assertThat(response.course().studentGroupName()).isEqualTo(course.getStudentGroupName());
        assertThat(response.course().teachingAssistantGroupName()).isEqualTo(course.getTeachingAssistantGroupName());
        assertThat(response.course().editorGroupName()).isEqualTo(course.getEditorGroupName());
        assertThat(response.course().instructorGroupName()).isEqualTo(course.getInstructorGroupName());
        // Read by the presentation-score control on the programming grading form.
        assertThat(response.course().presentationScore()).isEqualTo(course.getPresentationScore());
        assertThat(response.exerciseGroup()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExercise_examExercise_carriesTheExamChain() throws Exception {
        var exerciseGroup = examExercise.getExerciseGroup();
        var exam = exerciseGroup.getExam();

        ProgrammingExerciseResponseDTO response = request.get(EXERCISE_BASE + examExercise.getId(), HttpStatus.OK, ProgrammingExerciseResponseDTO.class);

        assertThat(response.id()).isEqualTo(examExercise.getId());
        assertThat(response.type()).isEqualTo("programming");
        // Exam mode is detected from the presence of exerciseGroup; the course member stays empty, as on the entity.
        assertThat(response.course()).isNull();
        assertThat(response.exerciseGroup()).isNotNull();
        assertThat(response.exerciseGroup().id()).isEqualTo(exerciseGroup.getId());
        assertThat(response.exerciseGroup().exam()).isNotNull();
        assertThat(response.exerciseGroup().exam().id()).isEqualTo(exam.getId());
        assertThat(response.exerciseGroup().exam().title()).isEqualTo(exam.getTitle());
        assertThat(response.exerciseGroup().exam().testExam()).isEqualTo(exam.isTestExam());
        assertThat(response.exerciseGroup().exam().numberOfCorrectionRoundsInExam()).isEqualTo(exam.getNumberOfCorrectionRoundsInExam());
        assertThat(response.exerciseGroup().exam().publishResultsDate()).isEqualTo(exam.getPublishResultsDate());
        assertThat(response.exerciseGroup().exam().exampleSolutionPublicationDate()).isEqualTo(exam.getExampleSolutionPublicationDate());
        // Exam navigation and access rights walk exerciseGroup.exam.course; a flat exam id kills both.
        assertThat(response.exerciseGroup().exam().course()).isNotNull();
        assertThat(response.exerciseGroup().exam().course().id()).isEqualTo(exam.getCourse().getId());
        assertThat(response.exerciseGroup().exam().course().instructorGroupName()).isEqualTo(exam.getCourse().getInstructorGroupName());
        assertThat(response.exerciseGroup().exam().course().editorGroupName()).isEqualTo(exam.getCourse().getEditorGroupName());
        assertThat(response.exerciseGroup().exam().course().teachingAssistantGroupName()).isEqualTo(exam.getCourse().getTeachingAssistantGroupName());
        assertThat(response.exerciseGroup().exam().course().studentGroupName()).isEqualTo(exam.getCourse().getStudentGroupName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExercise_emitsTheDiscriminatorsTheClientSwitchesOn() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get(new URI(EXERCISE_BASE + exercise.getId()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("programming")).andExpect(jsonPath("$.templateParticipation.type").value("template"))
                .andExpect(jsonPath("$.solutionParticipation.type").value("solution")).andExpect(jsonPath("$.id").value(exercise.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExercise_withPlagiarismDetectionConfig_createsExactlyOneDefaultRow() throws Exception {
        String path = EXERCISE_BASE + exercise.getId() + "?withPlagiarismDetectionConfig=true";

        ProgrammingExerciseResponseDTO first = request.get(path, HttpStatus.OK, ProgrammingExerciseResponseDTO.class);
        ProgrammingExerciseResponseDTO second = request.get(path, HttpStatus.OK, ProgrammingExerciseResponseDTO.class);
        ProgrammingExerciseResponseDTO third = request.get(path, HttpStatus.OK, ProgrammingExerciseResponseDTO.class);

        // The flagged GET writes a default config row when the exercise has none; the config must be on the wire.
        assertThat(first.plagiarismDetectionConfig()).isNotNull();
        assertThat(second.plagiarismDetectionConfig()).isNotNull();
        // Every later GET must reuse the row written by the first one instead of creating another.
        assertThat(second.plagiarismDetectionConfig().id()).isNotNull().isEqualTo(third.plagiarismDetectionConfig().id());

        var reloaded = programmingExerciseRepository
                .findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesAndPlagiarismDetectionConfigAndBuildConfigElseThrow(exercise.getId());
        assertThat(reloaded.getPlagiarismDetectionConfig()).isNotNull();
        assertThat(reloaded.getPlagiarismDetectionConfig().getId()).isEqualTo(second.plagiarismDetectionConfig().id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExercise_withoutPlagiarismDetectionConfigFlag_omitsTheConfig() throws Exception {
        ProgrammingExerciseResponseDTO response = request.get(EXERCISE_BASE + exercise.getId(), HttpStatus.OK, ProgrammingExerciseResponseDTO.class);
        assertThat(response.plagiarismDetectionConfig()).isNull();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // R8 — GET programming-exercises/{exerciseId}/with-participations
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExerciseWithSetupParticipations_carriesAllThreeParticipationSlots() throws Exception {
        AuxiliaryRepository auxiliaryRepository = programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(exercise);
        participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "instructor1");

        ProgrammingExerciseResponseDTO response = request.get(EXERCISE_BASE + exercise.getId() + "/with-participations", HttpStatus.OK, ProgrammingExerciseResponseDTO.class);

        // The instructor code-editor container reads {id, repositoryUri} off all three participation slots.
        assertThat(response.templateParticipation()).isNotNull();
        assertThat(response.templateParticipation().id()).isNotNull();
        assertThat(response.templateParticipation().repositoryUri()).isEqualTo(exercise.getTemplateParticipation().getRepositoryUri());
        assertThat(response.solutionParticipation()).isNotNull();
        assertThat(response.solutionParticipation().id()).isNotNull();
        assertThat(response.studentParticipations()).hasSize(1);
        assertThat(response.studentParticipations().getFirst().id()).isNotNull();
        assertThat(response.studentParticipations().getFirst().type()).isEqualTo("programming");
        // The nested exercise is the cycle break: it must stay empty when the participation is embedded.
        assertThat(response.studentParticipations().getFirst().exercise()).isNull();
        assertThat(response.auxiliaryRepositories()).extracting(AuxiliaryRepositoryDTO::id).containsExactly(auxiliaryRepository.getId());
        assertThat(response.problemStatement()).isEqualTo(exercise.getProblemStatement());
        assertThat(response.title()).isEqualTo(exercise.getTitle());
        assertThat(response.maxPoints()).isEqualTo(exercise.getMaxPoints());
        assertThat(response.course()).isNotNull();
        assertThat(response.course().instructorGroupName()).isEqualTo(course.getInstructorGroupName());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // R10 — GET programming-exercises/{exerciseId}/with-template-and-solution-participation
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExerciseWithTemplateAndSolutionParticipation_nestsResultsUnderSubmissions() throws Exception {
        ProgrammingSubmission solutionSubmission = programmingExerciseUtilService.createProgrammingSubmission(exercise.getSolutionParticipation(), false, "solution-hash");
        var solutionResult = participationUtilService.addResultToSubmission(exercise.getSolutionParticipation(), solutionSubmission);

        String path = EXERCISE_BASE + exercise.getId() + "/with-template-and-solution-participation?withSubmissionResults=true&withGradingCriteria=true";
        ProgrammingExerciseResponseDTO response = assertThatDb(() -> request.get(path, HttpStatus.OK, ProgrammingExerciseResponseDTO.class)).hasBeenCalledAtMostTimes(12);

        TemplateSolutionParticipationDTO solutionParticipation = response.solutionParticipation();
        assertThat(solutionParticipation).isNotNull();
        assertThat(solutionParticipation.type()).isEqualTo("solution");
        assertThat(solutionParticipation.submissions()).isNotNull();
        ProgrammingSubmissionWithResultsDTO submission = solutionParticipation.submissions().stream().filter(s -> solutionSubmission.getId().equals(s.id())).findFirst()
                .orElseThrow();
        assertThat(submission.submissionExerciseType()).isEqualTo("programming");
        assertThat(submission.commitHash()).isEqualTo("solution-hash");
        assertThat(submission.submissionDate()).isNotNull();
        // Results stay nested under their submission: the client sorts submissions and reads last().results.
        assertThat(submission.results()).extracting(ResultDTO::id).containsExactly(solutionResult.getId());
        // A nested result must not re-emit its submission/participation subtree.
        assertThat(submission.results().getFirst().submission()).isNull();
        assertThat(submission.results().getFirst().participation()).isNull();
        assertThat(response.testRepositoryUri()).isEqualTo(exercise.getTestRepositoryUri());
        assertThat(response.course()).isNotNull();
        assertThat(response.course().instructorGroupName()).isEqualTo(course.getInstructorGroupName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExerciseWithTemplateAndSolutionParticipation_withoutSubmissionResults_omitsResults() throws Exception {
        ProgrammingSubmission solutionSubmission = programmingExerciseUtilService.createProgrammingSubmission(exercise.getSolutionParticipation(), false, "solution-hash");
        participationUtilService.addResultToSubmission(exercise.getSolutionParticipation(), solutionSubmission);

        String path = EXERCISE_BASE + exercise.getId() + "/with-template-and-solution-participation?withSubmissionResults=false";
        ProgrammingExerciseResponseDTO response = request.get(path, HttpStatus.OK, ProgrammingExerciseResponseDTO.class);

        ProgrammingSubmissionWithResultsDTO submission = response.solutionParticipation().submissions().stream().filter(s -> solutionSubmission.getId().equals(s.id())).findFirst()
                .orElseThrow();
        assertThat(submission.results()).isNull();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // R15 — GET programming-exercises/{exerciseId}/with-auxiliary-repository (no server test existed before)
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExerciseWithAuxiliaryRepository_carriesAuxiliaryRepositoriesAndCourseGroups() throws Exception {
        AuxiliaryRepository auxiliaryRepository = programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(exercise);

        ProgrammingExerciseResponseDTO response = request.get(EXERCISE_BASE + exercise.getId() + "/with-auxiliary-repository", HttpStatus.OK, ProgrammingExerciseResponseDTO.class);

        assertThat(response.id()).isEqualTo(exercise.getId());
        assertThat(response.title()).isEqualTo(exercise.getTitle());
        assertThat(response.auxiliaryRepositories()).hasSize(1);
        AuxiliaryRepositoryDTO returnedAuxiliaryRepository = response.auxiliaryRepositories().getFirst();
        assertThat(returnedAuxiliaryRepository.id()).isEqualTo(auxiliaryRepository.getId());
        assertThat(returnedAuxiliaryRepository.name()).isEqualTo(auxiliaryRepository.getName());
        assertThat(returnedAuxiliaryRepository.checkoutDirectory()).isEqualTo(auxiliaryRepository.getCheckoutDirectory());
        assertThat(returnedAuxiliaryRepository.description()).isEqualTo(auxiliaryRepository.getDescription());
        // The VCS access log button is gated on the nested course group names.
        assertThat(response.course()).isNotNull();
        assertThat(response.course().instructorGroupName()).isEqualTo(course.getInstructorGroupName());
        assertThat(response.course().editorGroupName()).isEqualTo(course.getEditorGroupName());

        // The auxiliary repository ids must survive a second read: the client sends them back on the next update.
        ProgrammingExerciseResponseDTO secondResponse = request.get(EXERCISE_BASE + exercise.getId() + "/with-auxiliary-repository", HttpStatus.OK,
                ProgrammingExerciseResponseDTO.class);
        assertThat(secondResponse.auxiliaryRepositories()).extracting(AuxiliaryRepositoryDTO::id).containsExactly(auxiliaryRepository.getId());
        assertThat(auxiliaryRepositoryRepository.findByProgrammingExerciseId(exercise.getId())).extracting(DomainObject::getId).containsExactly(auxiliaryRepository.getId());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // R12 — GET programming-exercises/{exerciseId}/auxiliary-repository
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAuxiliaryRepositories_carriesTheFullFieldSet() throws Exception {
        AuxiliaryRepository auxiliaryRepository = programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(exercise);

        List<AuxiliaryRepositoryDTO> returned = request.getList(EXERCISE_BASE + exercise.getId() + "/auxiliary-repository", HttpStatus.OK, AuxiliaryRepositoryDTO.class);

        assertThat(returned).hasSize(1);
        assertThat(returned.getFirst().id()).isEqualTo(auxiliaryRepository.getId());
        assertThat(returned.getFirst().name()).isEqualTo(auxiliaryRepository.getName());
        assertThat(returned.getFirst().checkoutDirectory()).isEqualTo(auxiliaryRepository.getCheckoutDirectory());
        assertThat(returned.getFirst().description()).isEqualTo(auxiliaryRepository.getDescription());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // R9 — GET courses/{courseId}/programming-exercises
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getProgrammingExercisesForCourse_carriesTheTableFieldSetAndWritesNothing() throws Exception {
        ProgrammingSubmission solutionSubmission = programmingExerciseUtilService.createProgrammingSubmission(exercise.getSolutionParticipation(), false, "solution-hash");
        var firstResult = participationUtilService.addResultToSubmission(exercise.getSolutionParticipation(), solutionSubmission);
        var secondResult = participationUtilService.addResultToSubmission(exercise.getSolutionParticipation(), solutionSubmission);
        ProgrammingSubmission templateSubmission = programmingExerciseUtilService.createProgrammingSubmission(exercise.getTemplateParticipation(), false, "template-hash");
        var templateResult = participationUtilService.addResultToSubmission(exercise.getTemplateParticipation(), templateSubmission);

        // Capture the persisted result ids AND their @OrderColumn order before the read.
        List<Long> solutionResultIdsBefore = resultIdsOf(solutionSubmission.getId());
        List<Long> templateResultIdsBefore = resultIdsOf(templateSubmission.getId());
        assertThat(solutionResultIdsBefore).containsExactly(firstResult.getId(), secondResult.getId());
        assertThat(templateResultIdsBefore).containsExactly(templateResult.getId());

        String path = "/api/programming/courses/" + course.getId() + "/programming-exercises";
        List<ProgrammingExerciseListItemDTO> exercises = assertThatDb(() -> request.getList(path, HttpStatus.OK, ProgrammingExerciseListItemDTO.class))
                .hasBeenCalledAtMostTimes(18);

        ProgrammingExerciseListItemDTO listItem = exercises.stream().filter(item -> exercise.getId().equals(item.id())).findFirst().orElseThrow();
        assertThat(listItem.type()).isEqualTo("programming");
        assertThat(listItem.title()).isEqualTo(exercise.getTitle());
        assertThat(listItem.shortName()).isEqualTo(exercise.getShortName());
        assertThat(listItem.projectKey()).isEqualTo(exercise.getProjectKey());
        assertThat(listItem.programmingLanguage()).isEqualTo(exercise.getProgrammingLanguage());
        assertThat(listItem.maxPoints()).isEqualTo(exercise.getMaxPoints());
        assertThat(listItem.bonusPoints()).isEqualTo(exercise.getBonusPoints());
        assertThat(listItem.includedInOverallScore()).isEqualTo(exercise.getIncludedInOverallScore());
        assertThat(listItem.assessmentType()).isEqualTo(exercise.getAssessmentType());
        assertThat(listItem.mode()).isEqualTo(exercise.getMode());
        assertThat(listItem.teamMode()).isEqualTo(exercise.isTeamMode());
        assertThat(listItem.presentationScoreEnabled()).isEqualTo(exercise.getPresentationScoreEnabled());
        assertThat(listItem.testCasesChanged()).isEqualTo(exercise.getTestCasesChanged());
        assertThat(listItem.allowOfflineIde()).isEqualTo(exercise.isAllowOfflineIde());
        assertThat(listItem.allowOnlineEditor()).isEqualTo(exercise.isAllowOnlineEditor());
        assertThat(listItem.allowOnlineIde()).isEqualTo(exercise.isAllowOnlineIde());
        // The bulk "Edit selected" timeline modal rebuilds its request body from this very list.
        assertThat(listItem.releaseDate()).isEqualTo(exercise.getReleaseDate());
        assertThat(listItem.startDate()).isEqualTo(exercise.getStartDate());
        assertThat(listItem.dueDate()).isEqualTo(exercise.getDueDate());
        assertThat(listItem.assessmentDueDate()).isEqualTo(exercise.getAssessmentDueDate());
        assertThat(listItem.exampleSolutionPublicationDate()).isEqualTo(exercise.getExampleSolutionPublicationDate());
        assertThat(listItem.buildAndTestStudentSubmissionsAfterDueDate()).isEqualTo(exercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        // The course is deliberately absent; the client re-attaches the course it already holds.
        assertThat(listItem.course()).isNull();
        assertThat(listItem.exerciseGroup()).isNull();
        // The table counts participation.submissions[*].results and links templateParticipation.id.
        assertThat(listItem.templateParticipation()).isNotNull();
        assertThat(listItem.templateParticipation().id()).isEqualTo(exercise.getTemplateParticipation().getId());
        assertThat(listItem.solutionParticipation()).isNotNull();
        assertThat(listItem.solutionParticipation().id()).isEqualTo(exercise.getSolutionParticipation().getId());
        assertThat(allResultIds(listItem.solutionParticipation())).containsExactly(secondResult.getId());
        assertThat(allResultIds(listItem.templateParticipation())).containsExactly(templateResult.getId());

        // The read must not have reshaped the persisted graph: same rows, same @OrderColumn order.
        assertThat(resultIdsOf(solutionSubmission.getId())).containsExactlyElementsOf(solutionResultIdsBefore);
        assertThat(resultIdsOf(templateSubmission.getId())).containsExactlyElementsOf(templateResultIdsBefore);
        assertThat(resultRepository.existsById(firstResult.getId())).isTrue();
        assertThat(resultRepository.existsById(secondResult.getId())).isTrue();
        assertThat(resultRepository.existsById(templateResult.getId())).isTrue();
    }

    /**
     * Reads the results of a submission from a fresh session, preserving the {@code @OrderColumn} order.
     */
    private List<Long> resultIdsOf(long submissionId) {
        return programmingSubmissionTestRepository.findProgrammingSubmissionWithResultsById(submissionId).orElseThrow().getResults().stream().map(DomainObject::getId).toList();
    }

    private static List<Long> allResultIds(TemplateSolutionParticipationDTO participation) {
        return participation.submissions().stream().filter(submission -> submission.results() != null).flatMap(submission -> submission.results().stream()).map(ResultDTO::id)
                .toList();
    }
}
