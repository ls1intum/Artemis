package de.tum.cit.aet.artemis.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.presentation.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.presentation.dto.PresentationAssessmentStudentDTO;
import de.tum.cit.aet.artemis.presentation.repository.PresentationAssessmentRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PresentationAssessmentIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "presentationassessment";

    private static final String BASE_URL = "/api/presentation/courses/";

    @Autowired
    private PresentationAssessmentRepository presentationAssessmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    private Course course;

    private Course otherCourse;

    private PresentationAssessment presentationAssessment;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        otherCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        course.setPresentationAssessmentsEnabled(true);
        otherCourse.setPresentationAssessmentsEnabled(true);
        courseRepository.saveAll(List.of(course, otherCourse));

        presentationAssessment = new PresentationAssessment();
        presentationAssessment.setCourse(course);
        presentationAssessment.setTitle("Initial presentation");
        presentationAssessment.setDescription("Initial description");
        presentationAssessment.setMaxPoints(20);
        presentationAssessment.setResultPoints(17);
        presentationAssessment.setPresentationDate(ZonedDateTime.now().plusDays(7));
        presentationAssessment = presentationAssessmentRepository.save(presentationAssessment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_shouldCreatePresentationAssessment() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30, 28, ZonedDateTime.now().plusDays(14),
                null, List.of(TEST_PREFIX + "student1"));

        PresentationAssessmentDTO result = request.postWithResponseBody(getBaseUrl(course), dto, PresentationAssessmentDTO.class, HttpStatus.CREATED);

        assertThat(result.id()).isNotNull();
        assertThat(result.title()).isEqualTo(dto.title());
        assertThat(result.description()).isEqualTo(dto.description());
        assertThat(result.maxPoints()).isEqualTo(dto.maxPoints());
        assertThat(result.resultPoints()).isEqualTo(dto.resultPoints());
        assertThat(result.courseId()).isEqualTo(course.getId());
        PresentationAssessment storedAssessment = presentationAssessmentRepository.findWithStudentsByIdAndCourseId(result.id(), course.getId()).orElseThrow();
        assertThat(storedAssessment.getStudents()).extracting(User::getLogin).containsExactly(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createPresentationAssessment_asStudent_shouldReturnForbidden() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30, 28, ZonedDateTime.now().plusDays(14),
                null, null);

        request.postWithResponseBody(getBaseUrl(course), dto, PresentationAssessmentDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_withMissingResultPoints_shouldCreateAssessment() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30, null,
                ZonedDateTime.now().plusDays(14), null, null);

        PresentationAssessmentDTO result = request.postWithResponseBody(getBaseUrl(course), dto, PresentationAssessmentDTO.class, HttpStatus.CREATED);

        assertThat(result.resultPoints()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_withInvalidRequestData_shouldReturnBadRequest() throws Exception {
        request.postWithResponseBody(getBaseUrl(course),
                new PresentationAssessmentDTO(null, " ", "Course-level presentation assessment", 30, 28, ZonedDateTime.now().plusDays(14), null, null),
                PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(getBaseUrl(course),
                new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 0, 0, ZonedDateTime.now().plusDays(14), null, null),
                PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(getBaseUrl(course),
                new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30, 31, ZonedDateTime.now().plusDays(14), null, null),
                PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(getBaseUrl(course),
                new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 10001, 9999, ZonedDateTime.now().plusDays(14), null, null),
                PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(getBaseUrl(course),
                new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30, 28, ZonedDateTime.parse("1969-12-31T23:59:00Z"), null, null),
                PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(getBaseUrl(course), new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30, 28,
                ZonedDateTime.now().plusDays(14), otherCourse.getId(), null), PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_withDecimalPointValues_shouldReturnBadRequest() throws Exception {
        String maxPointsRequestBody = """
                {
                    "title": "Final presentation",
                    "description": "Course-level presentation assessment",
                    "maxPoints": 30.5,
                    "resultPoints": 28
                }
                """;
        String resultPointsRequestBody = """
                {
                    "title": "Final presentation",
                    "description": "Course-level presentation assessment",
                    "maxPoints": 30,
                    "resultPoints": 28.5
                }
                """;

        request.performMvcRequest(post(getBaseUrl(course)).contentType(MediaType.APPLICATION_JSON).content(maxPointsRequestBody)).andExpect(status().isBadRequest());
        request.performMvcRequest(post(getBaseUrl(course)).contentType(MediaType.APPLICATION_JSON).content(resultPointsRequestBody)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_withUserNotInStudentGroup_shouldReturnBadRequestWithoutCreatingAssessment() throws Exception {
        long assessmentsBeforeRequest = presentationAssessmentRepository.count();
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(null, "Invalid student presentation", "Course-level presentation assessment", 30, 28,
                ZonedDateTime.now().plusDays(14), null, List.of(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1"));

        request.postWithResponseBody(getBaseUrl(course), dto, PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);

        assertThat(presentationAssessmentRepository.count()).isEqualTo(assessmentsBeforeRequest);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessments_shouldReturnCoursePresentationAssessments() throws Exception {
        PresentationAssessment otherPresentationAssessment = new PresentationAssessment();
        otherPresentationAssessment.setCourse(otherCourse);
        otherPresentationAssessment.setTitle("Other presentation");
        otherPresentationAssessment.setMaxPoints(10);
        presentationAssessmentRepository.save(otherPresentationAssessment);

        List<PresentationAssessmentDTO> result = request.getList(getBaseUrl(course), HttpStatus.OK, PresentationAssessmentDTO.class);

        assertThat(result).extracting(PresentationAssessmentDTO::id).containsExactly(presentationAssessment.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessment_shouldReturnPresentationAssessment() throws Exception {
        PresentationAssessmentDTO result = request.get(getAssessmentUrl(course, presentationAssessment), HttpStatus.OK, PresentationAssessmentDTO.class);

        assertThat(result.id()).isEqualTo(presentationAssessment.getId());
        assertThat(result.title()).isEqualTo(presentationAssessment.getTitle());
        assertThat(result.courseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_shouldUpdatePresentationAssessment() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25, 22,
                ZonedDateTime.now().plusDays(21), course.getId(), List.of(TEST_PREFIX + "student1"));

        PresentationAssessmentDTO result = request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.OK);

        assertThat(result.title()).isEqualTo(dto.title());
        assertThat(result.description()).isEqualTo(dto.description());
        assertThat(result.maxPoints()).isEqualTo(dto.maxPoints());
        assertThat(result.resultPoints()).isEqualTo(dto.resultPoints());
        PresentationAssessment updatedAssessment = presentationAssessmentRepository.findByIdElseThrow(presentationAssessment.getId());
        assertThat(updatedAssessment.getTitle()).isEqualTo(dto.title());
        assertThat(updatedAssessment.getMaxPoints()).isEqualTo(dto.maxPoints());
        assertThat(updatedAssessment.getResultPoints()).isEqualTo(dto.resultPoints());
        PresentationAssessment updatedAssessmentWithStudents = presentationAssessmentRepository.findWithStudentsByIdAndCourseId(presentationAssessment.getId(), course.getId())
                .orElseThrow();
        assertThat(updatedAssessmentWithStudents.getStudents()).extracting(User::getLogin).containsExactly(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_withMissingResultPoints_shouldUpdatePresentationAssessment() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25, null,
                ZonedDateTime.now().plusDays(21), course.getId(), null);

        PresentationAssessmentDTO result = request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.OK);

        assertThat(result.resultPoints()).isNull();
        PresentationAssessment updatedAssessment = presentationAssessmentRepository.findByIdElseThrow(presentationAssessment.getId());
        assertThat(updatedAssessment.getResultPoints()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_withMismatchedId_shouldReturnBadRequest() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId() + 1, "Updated presentation", "Updated description", 25, 22,
                ZonedDateTime.now().plusDays(21), course.getId(), null);

        request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_withMismatchedCourseId_shouldReturnBadRequest() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25, 22,
                ZonedDateTime.now().plusDays(21), otherCourse.getId(), null);

        request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_withWrongCourseId_shouldReturnNotFound() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25, 22,
                ZonedDateTime.now().plusDays(21), otherCourse.getId(), null);

        request.putWithResponseBody(getAssessmentUrl(otherCourse, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.NOT_FOUND);

        PresentationAssessment storedAssessment = presentationAssessmentRepository.findByIdElseThrow(presentationAssessment.getId());
        assertThat(storedAssessment.getTitle()).isEqualTo("Initial presentation");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_withUserNotInStudentGroup_shouldReturnBadRequestWithoutUpdatingAssessment() throws Exception {
        presentationAssessment.getStudents().add(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        presentationAssessment = presentationAssessmentRepository.save(presentationAssessment);
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Invalid student presentation", "Updated description", 25, 22,
                ZonedDateTime.now().plusDays(21), course.getId(), List.of(TEST_PREFIX + "tutor1"));

        request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);

        PresentationAssessment storedAssessment = presentationAssessmentRepository.findWithStudentsByIdAndCourseId(presentationAssessment.getId(), course.getId()).orElseThrow();
        assertThat(storedAssessment.getTitle()).isEqualTo("Initial presentation");
        assertThat(storedAssessment.getStudents()).extracting(User::getLogin).containsExactly(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deletePresentationAssessment_shouldDeletePresentationAssessment() throws Exception {
        request.delete(getAssessmentUrl(course, presentationAssessment), HttpStatus.NO_CONTENT);

        Optional<PresentationAssessment> deletedAssessment = presentationAssessmentRepository.findById(presentationAssessment.getId());
        assertThat(deletedAssessment).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessmentStudents_shouldReturnAssignedStudents() throws Exception {
        presentationAssessment.getStudents().add(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        presentationAssessmentRepository.save(presentationAssessment);

        List<PresentationAssessmentStudentDTO> result = request.getList(getStudentsUrl(course, presentationAssessment), HttpStatus.OK, PresentationAssessmentStudentDTO.class);

        assertThat(result).extracting(PresentationAssessmentStudentDTO::login).containsExactly(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessments_withFeatureDisabled_shouldReturnForbidden() throws Exception {
        course.setPresentationAssessmentsEnabled(false);
        courseRepository.save(course);

        request.getList(getBaseUrl(course), HttpStatus.FORBIDDEN, PresentationAssessmentDTO.class);
    }

    @Test
    void deleteCourse_withPresentationAssessment_shouldCascadeDeletePresentationAssessment() {
        Long presentationAssessmentId = presentationAssessment.getId();

        courseRepository.delete(course);
        courseRepository.flush();

        assertThat(courseRepository.findById(course.getId())).isEmpty();
        assertThat(presentationAssessmentRepository.findById(presentationAssessmentId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void presentationAssessmentRequests_withWrongCourseId_shouldReturnNotFound() throws Exception {
        request.get(getAssessmentUrl(otherCourse, presentationAssessment), HttpStatus.NOT_FOUND, PresentationAssessmentDTO.class);
        request.delete(getAssessmentUrl(otherCourse, presentationAssessment), HttpStatus.NOT_FOUND);
        request.getList(getStudentsUrl(otherCourse, presentationAssessment), HttpStatus.NOT_FOUND, PresentationAssessmentStudentDTO.class);

        assertThat(presentationAssessmentRepository.findById(presentationAssessment.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void presentationAssessmentWriteRequests_asStudent_shouldReturnForbidden() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25, 22,
                ZonedDateTime.now().plusDays(21), course.getId(), null);

        request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.FORBIDDEN);
        request.delete(getAssessmentUrl(course, presentationAssessment), HttpStatus.FORBIDDEN);

        assertThat(presentationAssessmentRepository.findById(presentationAssessment.getId())).isPresent();
    }

    private String getBaseUrl(Course course) {
        return BASE_URL + course.getId() + "/presentation-assessments";
    }

    private String getAssessmentUrl(Course course, PresentationAssessment presentationAssessment) {
        return getBaseUrl(course) + "/" + presentationAssessment.getId();
    }

    private String getStudentsUrl(Course course, PresentationAssessment presentationAssessment) {
        return getAssessmentUrl(course, presentationAssessment) + "/students";
    }
}
