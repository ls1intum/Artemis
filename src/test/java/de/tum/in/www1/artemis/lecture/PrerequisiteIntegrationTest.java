package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.competency.PrerequisiteUtilService;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.PrerequisiteRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.competency.PrerequisiteRequestDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.PrerequisiteResponseDTO;

public class PrerequisiteIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "prerequisiteintegrationtest";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private PrerequisiteUtilService prerequisiteUtilService;

    @Autowired
    private PrerequisiteRepository prerequisiteRepository;

    @Autowired
    private CourseRepository courseRepository;

    private Course course;

    private Course course2;

    @BeforeEach
    void setupTestScenario() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        // users not in courses
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        // creating courses
        course = courseUtilService.createCourse();
        course2 = courseUtilService.createCourse();
    }

    private static String baseUrl(long courseId) {
        return "/api/courses/" + courseId + "/competencies/prerequisites";
    }

    @Nested
    class GetPrerequisites {

        private static String url(long courseId) {
            return PrerequisiteIntegrationTest.baseUrl(courseId);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnPrerequisites() throws Exception {
            prerequisiteUtilService.createPrerequisites(course, 5);
            var prerequisites = prerequisiteRepository.findByCourseIdOrderByTitle(course.getId());
            var expectedPrerequisites = prerequisites.stream().map(PrerequisiteResponseDTO::of).toList();

            List<PrerequisiteResponseDTO> actualPrerequisites = request.getList(url(course.getId()), HttpStatus.OK, PrerequisiteResponseDTO.class);

            assertThat(actualPrerequisites).containsAll(expectedPrerequisites);
            assertThat(actualPrerequisites).hasSameSizeAs(expectedPrerequisites);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
        void shouldReturnPrerequisitesForStudentNotInCourseIfEnrollmentIsEnabled() throws Exception {
            course.setEnrollmentEnabled(true);
            courseRepository.save(course);
            prerequisiteUtilService.createPrerequisites(course, 5);
            var prerequisites = prerequisiteRepository.findByCourseIdOrderByTitle(course.getId());
            var expectedPrerequisites = prerequisites.stream().map(PrerequisiteResponseDTO::of).toList();

            List<PrerequisiteResponseDTO> actualPrerequisites = request.getList(url(course.getId()), HttpStatus.OK, PrerequisiteResponseDTO.class);

            assertThat(actualPrerequisites).containsAll(expectedPrerequisites);
            assertThat(actualPrerequisites).hasSameSizeAs(expectedPrerequisites);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
        void shouldNotReturnPrerequisitesForStudentNotInCourse() throws Exception {
            request.get(url(course2.getId()), HttpStatus.FORBIDDEN, PrerequisiteResponseDTO.class);
        }
    }

    @Nested
    class CreatePrerequisite {

        private static String url(long courseId) {
            return PrerequisiteIntegrationTest.baseUrl(courseId);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldCreatePrerequisite() throws Exception {
            var prerequisite = new Prerequisite("title", "description", null, 66, CompetencyTaxonomy.ANALYZE, false);
            var requestBody = prerequisiteUtilService.prerequisiteToRequestDTO(prerequisite);

            var actualPrerequisite = request.postWithResponseBody(url(course.getId()), requestBody, PrerequisiteResponseDTO.class, HttpStatus.CREATED);

            var expectedPrerequisite = new PrerequisiteResponseDTO(actualPrerequisite.id(), prerequisite.getTitle(), prerequisite.getDescription(), prerequisite.getTaxonomy(),
                    prerequisite.getSoftDueDate(), prerequisite.getMasteryThreshold(), prerequisite.isOptional(), null);
            assertThat(actualPrerequisite).usingRecursiveComparison().ignoringFields("id").isEqualTo(expectedPrerequisite);
        }

    }

    @Nested
    class UpdatePrerequisite {

        private static String url(long courseId, long prerequisiteId) {
            return PrerequisiteIntegrationTest.baseUrl(courseId) + "/" + prerequisiteId;
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldUpdatePrerequisite() throws Exception {
            var existingPrerequisite = prerequisiteUtilService.createPrerequisite(course);
            var updateDTO = new PrerequisiteRequestDTO("new title", "new description", CompetencyTaxonomy.ANALYZE, null, 1, true);

            var updatedPrerequisite = request.putWithResponseBody(url(course.getId(), existingPrerequisite.getId()), updateDTO, PrerequisiteResponseDTO.class, HttpStatus.OK);

            assertThat(updatedPrerequisite).usingRecursiveComparison().comparingOnlyFields("title", "description", "taxonomy", "softDueDate", "masteryThreshold", "optional")
                    .isEqualTo(updateDTO);
            assertThat(updatedPrerequisite.id()).isEqualTo(existingPrerequisite.getId());
        }

    }

    @Nested
    class DeletePrerequisite {

        private static String url(long courseId, long prerequisiteId) {
            return PrerequisiteIntegrationTest.baseUrl(courseId) + "/" + prerequisiteId;
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldDeletePrerequisite() throws Exception {
            var prerequisite = prerequisiteUtilService.createPrerequisite(course);

            request.delete(url(course.getId(), prerequisite.getId()), HttpStatus.OK);

            boolean exists = prerequisiteRepository.existsById(prerequisite.getId());
            assertThat(exists).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldNotDeletePrerequisiteNotInCourse() throws Exception {
            var prerequisite = prerequisiteUtilService.createPrerequisite(course);

            // try to delete prerequisite in course2
            request.delete(url(course2.getId(), prerequisite.getId()), HttpStatus.NOT_FOUND);

            boolean exists = prerequisiteRepository.existsById(prerequisite.getId());
            assertThat(exists).isTrue();
        }
    }

    @Nested
    class ImportPrerequisites {

        private static String url(long courseId) {
            return PrerequisiteIntegrationTest.baseUrl(courseId) + "/import";
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldImportPrerequisites() throws Exception {
            var prerequisites = prerequisiteUtilService.createPrerequisites(course, 5);
            var prerequisiteIds = prerequisites.stream().map(DomainObject::getId).toList();

            var expectedPrerequisites = prerequisites.stream().map(prerequisite -> {
                prerequisite.setLinkedCourseCompetency(prerequisite);
                return PrerequisiteResponseDTO.of(prerequisite);
            }).toList();

            var actualPrerequisites = request.postListWithResponseBody(url(course2.getId()), prerequisiteIds, PrerequisiteResponseDTO.class, HttpStatus.CREATED);

            assertThat(actualPrerequisites).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id").containsAll(expectedPrerequisites);
            assertThat(actualPrerequisites).hasSameSizeAs(expectedPrerequisites);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldReturnBadRequestWhenImportingFromSameCourse() throws Exception {
            var prerequisites = prerequisiteUtilService.createPrerequisites(course, 4);
            // one of the competencies is in the same course we import into (course2)
            prerequisites.add(prerequisiteUtilService.createPrerequisite(course2));
            var prerequisiteIds = prerequisites.stream().map(DomainObject::getId).toList();

            request.post(url(course2.getId()), prerequisiteIds, HttpStatus.BAD_REQUEST);
        }

    }
}
