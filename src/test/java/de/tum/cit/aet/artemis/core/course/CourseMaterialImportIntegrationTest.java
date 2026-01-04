package de.tum.cit.aet.artemis.core.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CourseMaterialImportOptionsDTO;
import de.tum.cit.aet.artemis.core.dto.CourseMaterialImportResultDTO;
import de.tum.cit.aet.artemis.core.dto.CourseSummaryDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseMaterialImportIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "coursematerialimport";

    @Autowired
    private FaqRepository faqRepository;

    private Course sourceCourse;

    private Course targetCourse;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        // Create source course with some content
        sourceCourse = courseUtilService.createCourse();
        sourceCourse.setFaqEnabled(true);
        sourceCourse = courseRepository.save(sourceCourse);

        // Create target course
        targetCourse = courseUtilService.createCourse();
        targetCourse.setFaqEnabled(true);
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
        request.get("/api/core/courses/" + targetCourse.getId() + "/import-summary/" + sourceCourse.getId(), HttpStatus.FORBIDDEN, CourseSummaryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getImportSummary_asTutor_shouldReturnForbidden() throws Exception {
        request.get("/api/core/courses/" + targetCourse.getId() + "/import-summary/" + sourceCourse.getId(), HttpStatus.FORBIDDEN, CourseSummaryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getImportSummary_asEditor_shouldReturnForbidden() throws Exception {
        // Editor can access source course but cannot access target course as instructor
        request.get("/api/core/courses/" + targetCourse.getId() + "/import-summary/" + sourceCourse.getId(), HttpStatus.FORBIDDEN, CourseSummaryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getImportSummary_asInstructor_shouldSucceed() throws Exception {
        CourseSummaryDTO summary = request.get("/api/core/courses/" + targetCourse.getId() + "/import-summary/" + sourceCourse.getId(), HttpStatus.OK, CourseSummaryDTO.class);

        assertThat(summary).isNotNull();
        assertThat(summary.numberOfFaqs()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void importMaterial_asStudent_shouldReturnForbidden() throws Exception {
        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), false, false, false, false, false, true);

        request.post("/api/core/courses/" + targetCourse.getId() + "/import-material", options, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMaterial_asInstructor_shouldSucceed() throws Exception {
        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), false, false, false, false, false, true);

        CourseMaterialImportResultDTO result = request.postWithResponseBody("/api/core/courses/" + targetCourse.getId() + "/import-material", options,
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
        request.get("/api/core/courses/" + sourceCourse.getId() + "/import-summary/" + sourceCourse.getId(), HttpStatus.BAD_REQUEST, CourseSummaryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMaterial_sameCourse_shouldReturnBadRequest() throws Exception {
        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), false, false, false, false, false, true);

        request.post("/api/core/courses/" + sourceCourse.getId() + "/import-material", options, HttpStatus.BAD_REQUEST);
    }

    // ==================== Functional Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMaterial_noOptionsSelected_shouldReturnEmptyResult() throws Exception {
        CourseMaterialImportOptionsDTO options = new CourseMaterialImportOptionsDTO(sourceCourse.getId(), false, false, false, false, false, false);

        CourseMaterialImportResultDTO result = request.postWithResponseBody("/api/core/courses/" + targetCourse.getId() + "/import-material", options,
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

        CourseMaterialImportResultDTO result = request.postWithResponseBody("/api/core/courses/" + targetCourse.getId() + "/import-material", options,
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
        CourseMaterialImportResultDTO result1 = request.postWithResponseBody("/api/core/courses/" + targetCourse.getId() + "/import-material", options,
                CourseMaterialImportResultDTO.class, HttpStatus.OK);
        assertThat(result1.faqsImported()).isEqualTo(2);

        // Second import
        CourseMaterialImportResultDTO result2 = request.postWithResponseBody("/api/core/courses/" + targetCourse.getId() + "/import-material", options,
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

        CourseSummaryDTO summary = request.get("/api/core/courses/" + targetCourse.getId() + "/import-summary/" + emptyCourse.getId(), HttpStatus.OK, CourseSummaryDTO.class);

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

        CourseMaterialImportResultDTO result = request.postWithResponseBody("/api/core/courses/" + targetCourse.getId() + "/import-material", options,
                CourseMaterialImportResultDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.exercisesImported()).isZero();
        assertThat(result.lecturesImported()).isZero();
        assertThat(result.examsImported()).isZero();
        assertThat(result.competenciesImported()).isZero();
        assertThat(result.tutorialGroupsImported()).isZero();
        assertThat(result.faqsImported()).isZero();
    }
}
