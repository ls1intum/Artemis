package de.tum.cit.aet.artemis.exercise;

import static de.tum.cit.aet.artemis.core.util.QueryCountAssert.assertThatDb;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.PlagiarismDetectionConfigRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Pins what reading an exercise costs, for the plagiarism detection configuration.
 * <p>
 * The configuration holds the key to its exercise and the exercise carries no mapped association to it, so loading an
 * exercise - of any type, on its own, through its course or through its exam - reads the exercise rows and nothing
 * else. Before the key moved, about twenty entity graphs and fetch joins pulled the table in for every exercise they
 * returned, whether or not the caller looked at it. The tests look at the statements themselves rather than at their
 * number: no read of an exercise may mention the table, and the flows that do report the configuration read it exactly
 * once, however many exercises they return.
 */
class ExercisePlagiarismDetectionConfigLoadProfileTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "plagconfigloadprofile";

    private static final Pattern PLAGIARISM_DETECTION_CONFIG_TABLE = Pattern.compile("\\bplagiarism_detection_config\\b");

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private PlagiarismDetectionConfigRepository plagiarismDetectionConfigRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private long courseId;

    private long examId;

    private long textExerciseId;

    private long modelingExerciseId;

    private long fileUploadExerciseId;

    private long programmingExerciseId;

    private long examExerciseId;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        courseId = course.getId();

        textExerciseId = withConfig(textExerciseUtilService.createSampleTextExercise(course), 90).getId();
        modelingExerciseId = withConfig(modelingExerciseUtilService.addModelingExerciseToCourse(course), 80).getId();
        fileUploadExerciseId = withConfig(fileUploadExerciseUtilService.addFileUploadExercise(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(2),
                ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2)), 70).getId();
        programmingExerciseId = withConfig(programmingExerciseUtilService.addProgrammingExerciseToCourse(course), 60).getId();

        Exam exam = examUtilService.addExamWithExerciseGroup(courseRepository.findByIdElseThrow(courseId), true);
        examId = exam.getId();
        examExerciseId = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().getFirst())).getId();
    }

    private <E extends Exercise> E withConfig(E exercise, int similarityThreshold) {
        PlagiarismDetectionConfig config = PlagiarismDetectionConfig.createDefault();
        config.setSimilarityThreshold(similarityThreshold);
        plagiarismDetectionConfigRepository.replaceFor(exercise, config);
        return exercise;
    }

    @Test
    void readingAnExerciseOfAnyTypeDoesNotReadItsPlagiarismDetectionConfig() throws Exception {
        for (long id : List.of(textExerciseId, modelingExerciseId, fileUploadExerciseId, programmingExerciseId)) {
            assertThat(readsTheTable(() -> exerciseRepository.findByIdElseThrow(id))).as("reading exercise " + id).isFalse();
        }
    }

    @Test
    void theFindersThatUsedToFetchTheConfigurationNoLongerReadIt() throws Exception {
        assertThat(readsTheTable(() -> programmingExerciseRepository.findForUpdateByIdElseThrow(programmingExerciseId))).isFalse();
        assertThat(readsTheTable(() -> programmingExerciseRepository.findByIdForImportElseThrow(programmingExerciseId))).isFalse();
        assertThat(readsTheTable(() -> programmingExerciseRepository.findByIdWithGradingCriteriaAndCategoriesElseThrow(programmingExerciseId))).isFalse();
        assertThat(readsTheTable(() -> textExerciseRepository.findWithEagerCategoriesAndCompetenciesById(textExerciseId))).isFalse();
        assertThat(readsTheTable(() -> textExerciseRepository.findForVersioningById(textExerciseId))).isFalse();
    }

    @Test
    void readingTheExercisesOfACourseDoesNotReadThePlagiarismDetectionConfig() throws Exception {
        assertThat(readsTheTable(() -> courseRepository.findWithEagerExercisesById(courseId))).isFalse();
        assertThat(readsTheTable(() -> courseRepository.findWithEagerExercisesAndExerciseDetailsAndLecturesById(courseId))).isFalse();
    }

    @Test
    void readingTheExercisesOfAnExamDoesNotReadThePlagiarismDetectionConfig() throws Exception {
        assertThat(readsTheTable(() -> examRepository.findWithExerciseGroupsAndExercisesById(examId))).isFalse();
        assertThat(readsTheTable(() -> examRepository.findAllExercisesWithDetailsByExamId(examId))).isFalse();
        assertThat(readsTheTable(() -> exerciseRepository.findByIdElseThrow(examExerciseId))).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void readingAnExerciseThroughTheApiReadsTheConfigurationOnlyWhenAskedFor() throws Exception {
        String url = "/api/text/text-exercises/" + textExerciseId;

        List<String> withoutTheFlag = statementsOf(() -> request.get(url, HttpStatus.OK, TextExerciseResponseDTO.class));
        assertThat(withoutTheFlag.stream().filter(sql -> PLAGIARISM_DETECTION_CONFIG_TABLE.matcher(sql).find())).as("reads of plagiarism_detection_config without the flag")
                .isEmpty();

        List<String> withTheFlag = statementsOf(() -> request.get(url + "?withPlagiarismDetectionConfig=true", HttpStatus.OK, TextExerciseResponseDTO.class));
        assertThat(withTheFlag.stream().filter(sql -> PLAGIARISM_DETECTION_CONFIG_TABLE.matcher(sql).find())).as("reads of plagiarism_detection_config with the flag").hasSize(1);

        // and what it read reaches the response, which is what the client shows
        TextExerciseResponseDTO exercise = request.get(url + "?withPlagiarismDetectionConfig=true", HttpStatus.OK, TextExerciseResponseDTO.class);
        assertThat(exercise.plagiarismDetectionConfig()).isNotNull();
        assertThat(exercise.plagiarismDetectionConfig().similarityThreshold()).isEqualTo(90);
    }

    @Test
    void attachingTheConfigurationsOfSeveralExercisesCostsOneQuery() throws Exception {
        List<Exercise> exercises = List.of(exerciseRepository.findByIdElseThrow(textExerciseId), exerciseRepository.findByIdElseThrow(modelingExerciseId),
                exerciseRepository.findByIdElseThrow(fileUploadExerciseId), exerciseRepository.findByIdElseThrow(programmingExerciseId));

        assertThatDb(() -> {
            plagiarismDetectionConfigRepository.attachTo(exercises);
            return exercises;
        }).hasBeenCalledTimes(1);

        assertThat(exercises).allSatisfy(exercise -> assertThat(exercise.getPlagiarismDetectionConfig()).isNotNull());
        assertThat(exercises.getFirst().getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(90);
        assertThat(exercises.getLast().getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(60);
    }

    @Test
    void theSchedulerQueryFiltersOnTheConfigurationAndLeavesTheSlotsEmptyUntilTheyAreAttached() throws Exception {
        TextExercise exercise = textExerciseRepository.findById(textExerciseId).orElseThrow();
        exercise.setDueDate(ZonedDateTime.now().plusDays(5));
        textExerciseRepository.save(exercise);
        PlagiarismDetectionConfig config = plagiarismDetectionConfigRepository.findByExerciseId(textExerciseId).orElseThrow();
        config.setContinuousPlagiarismControlEnabled(true);
        plagiarismDetectionConfigRepository.save(config);

        var scheduled = exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue();

        // only the exercise whose configuration switches the control on is returned, although the query does not fetch it
        assertThat(scheduled).extracting(Exercise::getId).containsExactly(textExerciseId);
        assertThat(scheduled).allSatisfy(returned -> assertThat(returned.getPlagiarismDetectionConfig()).as("nothing fills the slot by itself").isNull());

        assertThatDb(() -> {
            plagiarismDetectionConfigRepository.attachTo(scheduled);
            return scheduled;
        }).hasBeenCalledTimes(1);
        assertThat(scheduled).allSatisfy(returned -> assertThat(returned.getPlagiarismDetectionConfig().isContinuousPlagiarismControlEnabled()).isTrue());
    }

    @Test
    void deletingTheExerciseDeletesItsConfiguration() {
        long configId = plagiarismDetectionConfigRepository.findByExerciseId(textExerciseId).orElseThrow().getId();

        // the configuration holds the key to its exercise, so the database removes it with the exercise
        exerciseRepository.deleteById(textExerciseId);

        assertThat(plagiarismDetectionConfigRepository.findById(configId)).isEmpty();
        assertThat(plagiarismDetectionConfigRepository.findByExerciseId(textExerciseId)).isEmpty();
    }

    @Test
    void replacingTheConfigurationLeavesExactlyOneRow() {
        Exercise exercise = exerciseRepository.findByIdElseThrow(textExerciseId);
        long idBefore = plagiarismDetectionConfigRepository.findByExerciseId(textExerciseId).orElseThrow().getId();

        PlagiarismDetectionConfig replacement = PlagiarismDetectionConfig.createDefault();
        replacement.setSimilarityThreshold(55);
        replacement.setMinimumSize(12);
        plagiarismDetectionConfigRepository.replaceFor(exercise, replacement);
        plagiarismDetectionConfigRepository.replaceFor(exercise, replacement);

        List<PlagiarismDetectionConfig> rows = plagiarismDetectionConfigRepository.findAllByExerciseIdIn(List.of(textExerciseId));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getId()).as("the stored row is updated in place, not replaced").isEqualTo(idBefore);
        assertThat(rows.getFirst().getSimilarityThreshold()).isEqualTo(55);
        assertThat(rows.getFirst().getMinimumSize()).isEqualTo(12);
        assertThat(exercise.getPlagiarismDetectionConfig().getId()).isEqualTo(idBefore);
    }

    @Test
    void anUpdateThatCarriesNoConfigurationLeavesTheStoredOneAlone() {
        Exercise exercise = exerciseRepository.findByIdElseThrow(textExerciseId);
        long idBefore = plagiarismDetectionConfigRepository.findByExerciseId(textExerciseId).orElseThrow().getId();

        var reported = plagiarismDetectionConfigRepository.replaceOrAttach(exercise, null);

        assertThat(reported).isNotNull();
        assertThat(reported.getId()).isEqualTo(idBefore);
        assertThat(exercise.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(90);
        assertThat(plagiarismDetectionConfigRepository.findByExerciseId(textExerciseId)).isPresent();
    }

    @Test
    void aCourseExerciseWithoutAConfigurationGetsTheDefaultOnce() {
        Exercise exercise = exerciseRepository.findByIdElseThrow(textExerciseId);
        plagiarismDetectionConfigRepository.deleteByExerciseId(textExerciseId);
        exercise.setPlagiarismDetectionConfig(null);

        PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(exercise, plagiarismDetectionConfigRepository);
        long filledId = plagiarismDetectionConfigRepository.findByExerciseId(textExerciseId).orElseThrow().getId();
        // the second call finds it on the slot and does nothing
        PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(exercise, plagiarismDetectionConfigRepository);

        assertThat(plagiarismDetectionConfigRepository.findAllByExerciseIdIn(List.of(textExerciseId))).hasSize(1).first().extracting(PlagiarismDetectionConfig::getId)
                .isEqualTo(filledId);
        assertThat(exercise.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(PlagiarismDetectionConfig.createDefault().getSimilarityThreshold());
    }

    @Test
    void anExamExerciseGetsNoDefaultConfiguration() {
        Exercise examExercise = exerciseRepository.findByIdElseThrow(examExerciseId);

        PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(examExercise, plagiarismDetectionConfigRepository);

        assertThat(plagiarismDetectionConfigRepository.findByExerciseId(examExerciseId)).isEmpty();
    }

    @Test
    void aConfigurationWithoutAnExerciseCannotBeWritten() {
        PlagiarismDetectionConfig config = PlagiarismDetectionConfig.createDefault();
        assertThatThrownBy(() -> plagiarismDetectionConfigRepository.saveAndFlush(config)).as("a plagiarism detection configuration without an exercise").isNotNull();
    }

    @Test
    void anExerciseCannotHaveTwoConfigurations() {
        Exercise exercise = exerciseRepository.findByIdElseThrow(textExerciseId);
        PlagiarismDetectionConfig second = PlagiarismDetectionConfig.createDefault();
        second.setExercise(exercise);

        assertThatThrownBy(() -> plagiarismDetectionConfigRepository.saveAndFlush(second)).as("a second configuration for the same exercise").isNotNull();
    }

    @Test
    void eachExerciseHasItsOwnConfigurationMatchedThroughTheKeyOnTheConfiguration() {
        assertThat(plagiarismDetectionConfigRepository.findAllByExerciseIdIn(List.of(textExerciseId, modelingExerciseId, fileUploadExerciseId, programmingExerciseId))).hasSize(4)
                .extracting(PlagiarismDetectionConfig::getExerciseId).containsExactlyInAnyOrder(textExerciseId, modelingExerciseId, fileUploadExerciseId, programmingExerciseId);
    }

    private boolean readsTheTable(ThrowingCall call) throws Exception {
        return statementsOf(call).stream().anyMatch(sql -> PLAGIARISM_DETECTION_CONFIG_TABLE.matcher(sql).find());
    }

    private List<String> statementsOf(ThrowingCall call) throws Exception {
        queryInterceptor.startQueryCount();
        call.run();
        return queryInterceptor.getStatements();
    }

    @FunctionalInterface
    private interface ThrowingCall {

        void run() throws Exception;
    }
}
