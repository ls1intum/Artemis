package de.tum.cit.aet.artemis.competency;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportResponseDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

class CompetencyIntegrationTest extends AbstractCompetencyPrerequisiteIntegrationTest {

    private static final String TEST_PREFIX = "competencyintegrationtest";

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @BeforeEach
    void setupTestScenario() {
        super.setupTestScenario(TEST_PREFIX, competencyUtilService::createCompetency);
    }

    @Nested
    class PreAuthorize {

        private void testAllPreAuthorizeEditor() throws Exception {
            request.post("/api/courses/" + course.getId() + "/competencies/import/bulk", new CompetencyImportOptionsDTO(null, null, false, false, false, null, false),
                    HttpStatus.FORBIDDEN);
            request.post("/api/courses/" + course.getId() + "/competencies/import-standardized", Collections.emptyList(), HttpStatus.FORBIDDEN);
        }

        private void testAllPreAuthorizeInstructor() throws Exception {
            request.put("/api/courses/" + course.getId() + "/competencies", new Competency(), HttpStatus.FORBIDDEN);
            request.post("/api/courses/" + course.getId() + "/competencies", new Competency(), HttpStatus.FORBIDDEN);
            request.delete("/api/courses/" + course.getId() + "/competencies/" + courseCompetency.getId(), HttpStatus.FORBIDDEN);
            request.post("/api/courses/" + course.getId() + "/competencies/bulk", Collections.emptyList(), HttpStatus.FORBIDDEN);
            // import
            request.post("/api/courses/" + course.getId() + "/competencies/import-all", new CompetencyImportOptionsDTO(null, null, false, false, false, null, false),
                    HttpStatus.FORBIDDEN);
            request.post("/api/courses/" + course.getId() + "/competencies/import", new CompetencyImportOptionsDTO(null, null, false, false, false, null, false),
                    HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldFailAsTutor() throws Exception {
            this.testAllPreAuthorizeInstructor();
            this.testAllPreAuthorizeEditor();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldFailAsStudent() throws Exception {
            this.testAllPreAuthorizeInstructor();
            this.testAllPreAuthorizeEditor();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldFailAsEditor() throws Exception {
            this.testAllPreAuthorizeInstructor();
            // do not call testAllPreAuthorizeEditor, as these methods should succeed
        }
    }

    Competency getCall(long courseId, long competencyId, HttpStatus expectedStatus) throws Exception {
        return request.get("/api/courses/" + courseId + "/competencies/" + competencyId, expectedStatus, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCompetencyForStudent() throws Exception {
        super.shouldReturnCompetencyForStudent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldOnlySendUserSpecificData() throws Exception {
        super.testShouldOnlySendUserSpecificData(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void shouldReturnForbiddenForUserNotInCourse() throws Exception {
        super.shouldReturnForbiddenForUserNotInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestForWrongCourse() throws Exception {
        super.shouldReturnBadRequestForWrongCourse();
    }

    @Override
    List<? extends CourseCompetency> getAllCall(long courseId, HttpStatus expectedStatus) throws Exception {
        return request.getList("/api/courses/" + courseId + "/competencies", expectedStatus, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCompetenciesForStudentOfCourse() throws Exception {
        super.shouldReturnCompetenciesForStudentOfCourse(new Competency());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testShouldReturnForbiddenForStudentNotInCourse() throws Exception {
        super.testShouldReturnForbiddenForStudentNotInCourse();
    }

    void deleteCall(long courseId, long competencyId, HttpStatus expectedStatus) throws Exception {
        request.delete("/api/courses/" + courseId + "/competencies/" + competencyId, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldDeleteCompetencyWhenInstructor() throws Exception {
        super.shouldDeleteCompetencyWhenInstructor();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldDeleteCompetencyAndRelations() throws Exception {
        super.shouldDeleteCompetencyAndRelations(competencyUtilService.createCompetency(course));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void shouldReturnForbiddenForInstructorOfOtherCourseForDelete() throws Exception {
        super.shouldReturnForbiddenForInstructorOfOtherCourseForDelete();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteCourseShouldAlsoDeleteCompetencyAndRelations() throws Exception {
        super.deleteCourseShouldAlsoDeleteCompetencyAndRelations(competencyUtilService.createCompetency(course));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureShouldUpdateCompetency() throws Exception {
        super.deleteLectureShouldUpdateCompetency();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnitShouldUpdateCompetency() throws Exception {
        super.deleteLectureUnitShouldUpdateCompetency();
    }

    CourseCompetency updateCall(long courseId, CourseCompetency competency, HttpStatus expectedStatus) throws Exception {
        return request.putWithResponseBody("/api/courses/" + courseId + "/competencies", competency, Competency.class, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateCompetency() throws Exception {
        super.shouldUpdateCompetency();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnBadRequestForCompetencyWithoutId() throws Exception {
        super.shouldReturnBadRequestForCompetencyWithoutId();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(IncludedInOverallScore.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateCompetencyToOptionalWhenSettingOptional(IncludedInOverallScore includedInOverallScore) throws Exception {
        super.shouldUpdateCompetencyToOptionalWhenSettingOptional(new Competency(), includedInOverallScore);
    }

    CourseCompetency createCall(long courseId, CourseCompetency competency, HttpStatus expectedStatus) throws Exception {
        return request.postWithResponseBody("/api/courses/" + courseId + "/competencies", competency, Competency.class, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateValidCompetency() throws Exception {
        super.shouldCreateValidCompetency(new Competency());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void forCompetencyWithNoTitleForCreate() throws Exception {
        super.forCompetencyWithNoTitleForCreate(new Competency());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void forCompetencyWithEmptyTitleForCreate() throws Exception {
        super.forCompetencyWithEmptyTitleForCreate(new Competency());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void forCompetencyWithIdForCreate() throws Exception {
        super.forCompetencyWithIdForCreate(new Competency());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void shouldReturnForbiddenForInstructorOfOtherCourseForCreate() throws Exception {
        super.shouldReturnForbiddenForInstructorOfOtherCourseForCreate(new Competency());
    }

    CourseCompetency importCall(long courseId, CompetencyImportOptionsDTO importOptions, HttpStatus expectedStatus) throws Exception {
        return request.postWithResponseBody("/api/courses/" + courseId + "/competencies/import", importOptions, Competency.class, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldImportCompetency() throws Exception {
        super.shouldImportCompetency();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldImportExerciseAndLectureWithCompetency() throws Exception {
        super.shouldImportExerciseAndLectureWithCompetency();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldImportExerciseAndLectureWithCompetencyAndChangeDates() throws Exception {
        super.shouldImportExerciseAndLectureWithCompetencyAndChangeDates();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void shouldReturnForbiddenForInstructorOfOtherCourseForImport() throws Exception {
        super.shouldReturnForbiddenForInstructorOfOtherCourseForImport();
    }

    List<? extends CourseCompetency> createBulkCall(long courseId, List<? extends CourseCompetency> competencies, HttpStatus expectedStatus) throws Exception {
        return request.postListWithResponseBody("/api/courses/" + courseId + "/competencies/bulk", competencies, Competency.class, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateCompetencies() throws Exception {
        super.shouldCreateCompetencies(new Competency(), new Competency());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void forCompetencyWithNoTitle() throws Exception {
        super.forCompetencyWithNoTitle(new Competency());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void forCompetencyWithEmptyTitle() throws Exception {
        super.forCompetencyWithEmptyTitle(new Competency());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void forCompetencyWithId() throws Exception {
        super.forCompetencyWithId(new Competency());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void shouldReturnForbiddenForInstructorOfOtherCourseForCreateBulk() throws Exception {
        super.shouldReturnForbiddenForInstructorOfOtherCourseForCreateBulk();
    }

    List<CompetencyWithTailRelationDTO> importAllCall(long courseId, CompetencyImportOptionsDTO importOptions, HttpStatus expectedStatus) throws Exception {
        return request.postListWithResponseBody("/api/courses/" + courseId + "/competencies/import-all", importOptions, CompetencyWithTailRelationDTO.class, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldImportAllCompetencies() throws Exception {
        super.shouldImportAllCompetencies(competencyUtilService::createCompetency);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldImportAllExerciseAndLectureWithCompetency() throws Exception {
        super.shouldImportAllExerciseAndLectureWithCompetency();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldImportAllExerciseAndLectureWithCompetencyAndChangeDates() throws Exception {
        super.shouldImportAllExerciseAndLectureWithCompetencyAndChangeDates();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void shouldReturnForbiddenForInstructorNotInCourse() throws Exception {
        super.shouldReturnForbiddenForInstructorNotInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnBadRequestForImportFromSameCourse() throws Exception {
        super.shouldReturnBadRequestForImportFromSameCourse();
    }

    List<CompetencyImportResponseDTO> importStandardizedCall(long courseId, List<Long> idList, HttpStatus expectedStatus) throws Exception {
        return request.postListWithResponseBody("/api/courses/" + courseId + "/competencies/import-standardized", idList, CompetencyImportResponseDTO.class, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldImportStandardizedCompetencies() throws Exception {
        super.shouldImportStandardizedCompetencies();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReturnNotFoundForNotExistingIds() throws Exception {
        super.shouldReturnNotFoundForNotExistingIds();
    }

    List<CompetencyWithTailRelationDTO> importBulkCall(long courseId, CompetencyImportOptionsDTO importOptions, HttpStatus expectedStatus) throws Exception {
        return request.postListWithResponseBody("/api/courses/" + courseId + "/competencies/import/bulk", importOptions, CompetencyWithTailRelationDTO.class, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void shouldReturnForbiddenForInstructorOfOtherCourseForBulkImport() throws Exception {
        super.shouldReturnForbiddenForInstructorOfOtherCourseForBulkImport();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldImportCompetencies() throws Exception {
        super.shouldImportCompetencies(competencyUtilService::createCompetency);
    }
}
