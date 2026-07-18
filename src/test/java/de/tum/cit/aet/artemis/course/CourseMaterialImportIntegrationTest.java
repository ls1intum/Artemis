package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseMaterialImportOptionsDTO;
import de.tum.cit.aet.artemis.course.dto.CourseMaterialImportResultDTO;
import de.tum.cit.aet.artemis.course.dto.CourseSummaryDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.util.ImportedExerciseAssertions;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class CourseMaterialImportIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "coursematerialimport";

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    private Course sourceCourse;

    private Course targetCourse;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        // Create source course with some content
        sourceCourse = courseUtilService.createCourse();
        sourceCourse = courseRepository.save(sourceCourse);

        // Create target course
        targetCourse = courseUtilService.createCourse();
        targetCourse = courseRepository.save(targetCourse);

        // Add FAQs to source course
        createFaq(sourceCourse, "FAQ 1", "Answer 1");
        createFaq(sourceCourse, "FAQ 2", "Answer 2");
    }

    private void createFaq(Course course, String title, String answer) {
        Faq faq = new Faq();
        faq.setQuestionTitle(title);
        faq.setQuestionAnswer(answer);
        faq.setCourse(course);
        faq.setFaqState(FaqState.ACCEPTED);
        faqRepository.save(faq);
    }

    // ==================== Authorization Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getImportSummary_asStudent_shouldReturnForbidden() throws Exception {
        request.get("/api/course/courses/" + targetCourse.getId() + "/import-summary?sourceCourseId=" + sourceCourse.getId(), HttpStatus.FORBIDDEN, CourseSummaryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getImportSummary_asTutor_shouldReturnForbidden() throws Exception {
        request.get("/api/course/courses/" + targetCourse.getId() + "/import-summary?sourceCourseId=" + sourceCourse.getId(), HttpStatus.FORBIDDEN, CourseSummaryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getImportSummary_asEditor_shouldReturnForbidden() throws Exception {
        // Editor can access source course but cannot access target course as instructor
        request.get("/api/course/courses/" + targetCourse.getId() + "/import-summary?sourceCourseId=" + sourceCourse.getId(), HttpStatus.FORBIDDEN, CourseSummaryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getImportSummary_asInstructor_shouldSucceed() throws Exception {
        CourseSummaryDTO summary = request.get("/api/course/courses/" + targetCourse.getId() + "/import-summary?sourceCourseId=" + sourceCourse.getId(), HttpStatus.OK,
                CourseSummaryDTO.class);

        assertThat(summary).isNotNull();
        assertThat(summary.numberOfFaqs()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void importMaterial_asStudent_shouldReturnForbidden() throws Exception {
        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), false, false, false, false, false, true);

        request.post("/api/course/courses/" + targetCourse.getId() + "/import-material", options, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMaterial_asInstructor_shouldSucceed() throws Exception {
        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), false, false, false, false, false, true);

        CourseMaterialImportResultDTO result = request.postWithResponseBody("/api/course/courses/" + targetCourse.getId() + "/import-material", options,
                CourseMaterialImportResultDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.faqsImported()).isEqualTo(2);
        assertThat(result.errors()).isNull();

        // Verify FAQs were actually imported
        List<Faq> targetFaqs = faqRepository.findAllByCourseIdOrderByCreatedDateDesc(targetCourse.getId());
        assertThat(targetFaqs).hasSize(2);
    }

    // ==================== Validation Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getImportSummary_sameCourse_shouldReturnBadRequest() throws Exception {
        request.get("/api/course/courses/" + sourceCourse.getId() + "/import-summary?sourceCourseId=" + sourceCourse.getId(), HttpStatus.BAD_REQUEST, CourseSummaryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMaterial_sameCourse_shouldReturnBadRequest() throws Exception {
        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), false, false, false, false, false, true);

        request.post("/api/course/courses/" + sourceCourse.getId() + "/import-material", options, HttpStatus.BAD_REQUEST);
    }

    // ==================== Functional Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMaterial_noOptionsSelected_shouldReturnEmptyResult() throws Exception {
        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), false, false, false, false, false, false);

        CourseMaterialImportResultDTO result = request.postWithResponseBody("/api/course/courses/" + targetCourse.getId() + "/import-material", options,
                CourseMaterialImportResultDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.exercisesImported()).isZero();
        assertThat(result.lecturesImported()).isZero();
        assertThat(result.examsImported()).isZero();
        assertThat(result.competenciesImported()).isZero();
        assertThat(result.tutorialGroupsImported()).isZero();
        assertThat(result.faqsImported()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMaterial_faqsWithCategories_shouldPreserveCategories() throws Exception {
        // Create FAQ with categories
        Faq faqWithCategories = new Faq();
        faqWithCategories.setQuestionTitle("FAQ with categories");
        faqWithCategories.setQuestionAnswer("Answer with categories");
        faqWithCategories.setCourse(sourceCourse);
        faqWithCategories.setFaqState(FaqState.ACCEPTED);
        faqWithCategories.setCategories(Set.of("Category1", "Category2"));
        faqRepository.save(faqWithCategories);

        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), false, false, false, false, false, true);

        CourseMaterialImportResultDTO result = request.postWithResponseBody("/api/course/courses/" + targetCourse.getId() + "/import-material", options,
                CourseMaterialImportResultDTO.class, HttpStatus.OK);

        assertThat(result.faqsImported()).isEqualTo(3);

        // Verify categories were preserved
        List<Faq> targetFaqs = faqRepository.findAllByCourseIdOrderByCreatedDateDesc(targetCourse.getId());
        Faq importedFaqWithCategories = targetFaqs.stream().filter(f -> f.getQuestionTitle().equals("FAQ with categories")).findFirst().orElseThrow();
        assertThat(importedFaqWithCategories.getCategories()).containsExactlyInAnyOrder("Category1", "Category2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMaterial_multipleTimes_shouldCreateDuplicates() throws Exception {
        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), false, false, false, false, false, true);

        // First import
        CourseMaterialImportResultDTO result1 = request.postWithResponseBody("/api/course/courses/" + targetCourse.getId() + "/import-material", options,
                CourseMaterialImportResultDTO.class, HttpStatus.OK);
        assertThat(result1.faqsImported()).isEqualTo(2);

        // Second import
        CourseMaterialImportResultDTO result2 = request.postWithResponseBody("/api/course/courses/" + targetCourse.getId() + "/import-material", options,
                CourseMaterialImportResultDTO.class, HttpStatus.OK);
        assertThat(result2.faqsImported()).isEqualTo(2);

        // Verify all FAQs exist (4 total from 2 imports)
        List<Faq> targetFaqs = faqRepository.findAllByCourseIdOrderByCreatedDateDesc(targetCourse.getId());
        assertThat(targetFaqs).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getImportSummary_emptySourceCourse_shouldReturnZeroCounts() throws Exception {
        // Create an empty source course
        Course emptyCourse = courseUtilService.createCourse();
        emptyCourse = courseRepository.save(emptyCourse);

        CourseSummaryDTO summary = request.get("/api/course/courses/" + targetCourse.getId() + "/import-summary?sourceCourseId=" + emptyCourse.getId(), HttpStatus.OK,
                CourseSummaryDTO.class);

        assertThat(summary).isNotNull();
        assertThat(summary.numberOfExercises()).isZero();
        assertThat(summary.numberOfLectures()).isZero();
        assertThat(summary.numberOfExams()).isZero();
        assertThat(summary.numberOfCompetencies()).isZero();
        assertThat(summary.numberOfTutorialGroups()).isZero();
        assertThat(summary.numberOfFaqs()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMaterial_fromEmptyCourse_shouldReturnZeroImported() throws Exception {
        // Create an empty source course
        Course emptyCourse = courseUtilService.createCourse();
        emptyCourse = courseRepository.save(emptyCourse);

        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(emptyCourse.getId(), true, true, true, true, true, true);

        CourseMaterialImportResultDTO result = request.postWithResponseBody("/api/course/courses/" + targetCourse.getId() + "/import-material", options,
                CourseMaterialImportResultDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.exercisesImported()).isZero();
        assertThat(result.lecturesImported()).isZero();
        assertThat(result.examsImported()).isZero();
        assertThat(result.competenciesImported()).isZero();
        assertThat(result.tutorialGroupsImported()).isZero();
        assertThat(result.faqsImported()).isZero();
    }

    // ==================== Exercise content preservation (regression guard for #13268) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMaterial_exercises_shouldPreserveAllContentFields() throws Exception {
        ZonedDateTime past = ZonedDateTime.now().minusDays(2);
        ZonedDateTime future = ZonedDateTime.now().plusDays(2);
        ZonedDateTime farFuture = ZonedDateTime.now().plusDays(4);

        TextExercise sourceText = TextExerciseFactory.generateTextExercise(past, future, farFuture, sourceCourse);
        sourceText.setTitle("Source Text");
        sourceText.setDifficulty(DifficultyLevel.HARD);
        sourceText.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        sourceText.setGradingInstructions("Text grading instructions");
        exerciseUtilService.addGradingInstructionsToExercise(sourceText);
        sourceText = exerciseRepository.save(sourceText);

        ModelingExercise sourceModeling = ModelingExerciseFactory.generateModelingExercise(past, future, farFuture, DiagramType.ClassDiagram, sourceCourse);
        sourceModeling.setTitle("Source Modeling");
        sourceModeling.setDifficulty(DifficultyLevel.HARD);
        sourceModeling.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exerciseUtilService.addGradingInstructionsToExercise(sourceModeling);
        sourceModeling = exerciseRepository.save(sourceModeling);

        FileUploadExercise sourceFileUpload = FileUploadExerciseFactory.generateFileUploadExercise(past, past, farFuture, "png,pdf", sourceCourse);
        sourceFileUpload.setTitle("Source FileUpload");
        sourceFileUpload.setDifficulty(DifficultyLevel.HARD);
        exerciseUtilService.addGradingInstructionsToExercise(sourceFileUpload);
        sourceFileUpload = exerciseRepository.save(sourceFileUpload);

        QuizExercise sourceQuiz = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(sourceCourse, past, future, farFuture, QuizMode.SYNCHRONIZED);

        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), true, false, false, false, false, false);
        CourseMaterialImportResultDTO result = request.postWithResponseBody("/api/course/courses/" + targetCourse.getId() + "/import-material", options,
                CourseMaterialImportResultDTO.class, HttpStatus.OK);

        assertThat(result.exercisesImported()).isEqualTo(4);

        Set<Exercise> importedExercises = exerciseRepository.findByCourseIdWithCategories(targetCourse.getId());
        assertThat(importedExercises).hasSize(4);

        ImportedExerciseAssertions.assertContentPreserved(reloadText(sourceText.getId()), reloadText(findByType(importedExercises, TextExercise.class).getId()));
        ImportedExerciseAssertions.assertContentPreserved(reloadModeling(sourceModeling.getId()), reloadModeling(findByType(importedExercises, ModelingExercise.class).getId()));
        ImportedExerciseAssertions.assertContentPreserved(reloadFileUpload(sourceFileUpload.getId()),
                reloadFileUpload(findByType(importedExercises, FileUploadExercise.class).getId()));
        ImportedExerciseAssertions.assertContentPreserved(reloadQuiz(sourceQuiz.getId()), reloadQuiz(findByType(importedExercises, QuizExercise.class).getId()));
    }

    private static <T extends Exercise> T findByType(Set<Exercise> exercises, Class<T> type) {
        return exercises.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private TextExercise reloadText(long id) {
        return textExerciseRepository.findByIdWithExampleSubmissionsAndResultsAndGradingCriteriaElseThrow(id);
    }

    private ModelingExercise reloadModeling(long id) {
        return modelingExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(id);
    }

    private FileUploadExercise reloadFileUpload(long id) {
        return fileUploadExerciseRepository.findWithGradingCriteriaByIdElseThrow(id);
    }

    private QuizExercise reloadQuiz(long id) {
        return quizExerciseRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(id);
    }
}
