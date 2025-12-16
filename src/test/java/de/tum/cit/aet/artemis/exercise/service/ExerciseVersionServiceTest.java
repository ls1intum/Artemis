package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService.zonedDateTimeBiPredicate;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseVersionServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "exerciseversiontest";

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionServiceTest.class);

    @Autowired
    private ExerciseVersionTestRepository exerciseVersionRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private SubmissionPolicyRepository submissionPolicyRepository;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionUtilService exerciseVersionUtilService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @AfterEach
    void tearDown() {
        exerciseVersionRepository.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_OnCreate(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        exerciseVersionService.createExerciseVersion(exercise);
        exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_OnUpdate(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        exerciseVersionService.createExerciseVersion(exercise);
        ExerciseVersion previousVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);

        ExerciseVersionUtilService.updateExercise(exercise);
        Exercise updatedExercise = updateExerciseByType(exercise);
        saveExerciseByType(updatedExercise);
        exerciseVersionService.createExerciseVersion(updatedExercise);

        var versions = exerciseVersionRepository.findAllByExerciseId(updatedExercise.getId());
        assertThat(versions).hasSizeGreaterThan(1);

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);
        assertThat(newVersion.getId()).isNotEqualTo(previousVersion.getId());

        ExerciseSnapshotDTO snapshot = newVersion.getExerciseSnapshot();
        assertThat(snapshot).isNotNull();

        Exercise fetchedExercise = fetchExerciseForComparison(exercise);
        ExerciseSnapshotDTO expectedSnapshot = ExerciseSnapshotDTO.of(fetchedExercise, gitService);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(expectedSnapshot);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isNotEqualTo(previousVersion.getExerciseSnapshot());
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_OnInvalidUpdate(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        exerciseVersionService.createExerciseVersion(exercise);
        ExerciseVersion previousVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);

        // save again to db without changing versionable data
        Course newCourse = courseUtilService.addEmptyCourse();
        exercise.setCourse(newCourse);
        saveExerciseByType(exercise);
        exerciseVersionService.createExerciseVersion(exercise);

        var versions = exerciseVersionRepository.findAllByExerciseId(exercise.getId());
        assertThat(versions).isNotEmpty();

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);
        assertThat(newVersion.getId()).isEqualTo(previousVersion.getId());

        ExerciseSnapshotDTO snapshot = newVersion.getExerciseSnapshot();
        assertThat(snapshot).isNotNull();

        Exercise fetchedExercise = fetchExerciseForComparison(exercise);
        ExerciseSnapshotDTO expectedSnapshot = ExerciseSnapshotDTO.of(fetchedExercise, gitService);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(expectedSnapshot);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_OnNullExericise() {
        var previousCount = exerciseVersionRepository.count();
        exerciseVersionService.createExerciseVersion(null);
        var afterCount = exerciseVersionRepository.count();
        assertThat(afterCount).isEqualTo(previousCount);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_OnNullExericiseId() {
        Exercise exercise = createExerciseByType(ExerciseType.TEXT);
        exercise.setId(null);
        var previousCount = exerciseVersionRepository.count();
        exerciseVersionService.createExerciseVersion(exercise);
        var afterCount = exerciseVersionRepository.count();
        assertThat(afterCount).isEqualTo(previousCount);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_OnNullUser() {
        Exercise exercise = createExerciseByType(ExerciseType.TEXT);
        var previousCount = exerciseVersionRepository.count();
        exerciseVersionService.createExerciseVersion(exercise, null);
        var afterCount = exerciseVersionRepository.count();
        assertThat(afterCount).isEqualTo(previousCount);
    }

    private Exercise createExerciseByType(ExerciseType exerciseType) {
        return switch (exerciseType) {
            case TEXT -> createTextExercise();
            case PROGRAMMING -> createProgrammingExercise();
            case QUIZ -> createQuizExercise();
            case MODELING -> createModelingExercise();
            case FILE_UPLOAD -> createFileUploadExercise();
        };
    }

    private TextExercise createTextExercise() {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        return (TextExercise) course.getExercises().iterator().next();
    }

    private ProgrammingExercise createProgrammingExercise() {

        ProgrammingExercise newProgrammingExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        newProgrammingExercise = programmingExerciseRepository.findForVersioningById(newProgrammingExercise.getId()).orElseThrow();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(newProgrammingExercise);

        var penaltyPolicy = new SubmissionPenaltyPolicy();
        penaltyPolicy.setSubmissionLimit(7);
        penaltyPolicy.setExceedingPenalty(1.2);
        penaltyPolicy.setActive(true);
        penaltyPolicy.setProgrammingExercise(newProgrammingExercise);
        penaltyPolicy = submissionPolicyRepository.saveAndFlush(penaltyPolicy);
        newProgrammingExercise.setSubmissionPolicy(penaltyPolicy);
        programmingExerciseRepository.saveAndFlush(newProgrammingExercise);

        String projectKey = newProgrammingExercise.getProjectKey();
        try {
            newProgrammingExercise = programmingExerciseRepository.findForVersioningById(newProgrammingExercise.getId()).orElseThrow();

            newProgrammingExercise.setAuxiliaryRepositories(new ArrayList<>());

            RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, newProgrammingExercise);
            templateProgrammingExerciseParticipationRepository.save(newProgrammingExercise.getTemplateParticipation());
            solutionProgrammingExerciseParticipationRepository.save(newProgrammingExercise.getSolutionParticipation());

            newProgrammingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
            programmingExerciseRepository.saveAndFlush(newProgrammingExercise);

        }
        catch (Exception e) {
            log.error("Failed to create programming exercise", e);
        }

        // Check that the repository folders were created in the file system for all
        // base repositories.
        localVCLocalCITestService.verifyRepositoryFoldersExist(newProgrammingExercise, localVCBasePath);

        newProgrammingExercise = programmingExerciseRepository.findForVersioningById(newProgrammingExercise.getId()).orElseThrow();
        return newProgrammingExercise;
    }

    private ModelingExercise createModelingExercise() {
        Course course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        // Create a modeling exercise
        Exercise exercise = course.getExercises().iterator().next();
        return modelingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();
    }

    private QuizExercise createQuizExercise() {
        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        quizExerciseRepository.flush();
        return (QuizExercise) course.getExercises().iterator().next();
    }

    private FileUploadExercise createFileUploadExercise() {
        Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        fileUploadExerciseRepository.flush();
        return (FileUploadExercise) course.getExercises().iterator().next();
    }

    private Exercise fetchExerciseForComparison(Exercise exercise) {
        return switch (exercise) {
            case ProgrammingExercise pExercise -> programmingExerciseRepository.findForVersioningById(exercise.getId()).orElse(pExercise);
            case QuizExercise qExercise -> quizExerciseRepository.findForVersioningById(exercise.getId()).orElse(qExercise);
            case TextExercise tExercise -> textExerciseRepository.findForVersioningById(exercise.getId()).orElse(tExercise);
            case ModelingExercise mExercise -> modelingExerciseRepository.findForVersioningById(exercise.getId()).orElse(mExercise);
            case FileUploadExercise fExercise -> fileUploadExerciseRepository.findForVersioningById(exercise.getId()).orElse(fExercise);
            default -> exercise;
        };
    }

    private void saveExerciseByType(Exercise exercise) {
        switch (exercise) {
            case TextExercise textExercise -> textExerciseRepository.saveAndFlush(textExercise);
            case ProgrammingExercise newProgrammingExercise -> programmingExerciseRepository.saveAndFlush(newProgrammingExercise);
            case QuizExercise quizExercise -> quizExerciseRepository.saveAndFlush(quizExercise);
            case ModelingExercise modelingExercise -> modelingExerciseRepository.saveAndFlush(modelingExercise);
            case FileUploadExercise fileUploadExercise -> fileUploadExerciseRepository.saveAndFlush(fileUploadExercise);
            default -> throw new IllegalArgumentException("Unsupported exercise type");
        }
    }

    private Exercise updateExerciseByType(Exercise exercise) {
        return switch (exercise) {
            case TextExercise textExercise:
                textExercise.setExampleSolution("Updated example solution");
                yield textExercise;
            case ProgrammingExercise newProgrammingExercise:
                ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(newProgrammingExercise, exercise.getShortName(), "Updated Title", true, ProgrammingLanguage.SWIFT);
                yield newProgrammingExercise;
            case QuizExercise quizExercise:
                quizExerciseUtilService.emptyOutQuizExercise(quizExercise);
                yield quizExercise;
            case ModelingExercise modelingExercise:
                modelingExercise.setExampleSolutionModel("Updated example solution");
                modelingExercise.setExampleSolutionExplanation("Updated example explanation");
                modelingExercise.setDiagramType(DiagramType.CommunicationDiagram);
                yield modelingExercise;
            case FileUploadExercise fileUploadExercise:
                fileUploadExercise.setExampleSolution("Updated example solution");
                fileUploadExercise.setFilePattern("Updated file pattern");
                yield fileUploadExercise;
            default:
                throw new IllegalArgumentException("Unsupported exercise type");
        };
    }

}
