package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstanceDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstanceRequestDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstancesBatchCreateDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStudentRowDTO;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentInstanceRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class PresentationAssessmentIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "presentationassessment";

    private static final String BASE_URL = "/api/assessment/courses/";

    private static final ZonedDateTime FIXED_DATE = ZonedDateTime.parse("2026-07-31T13:26:00+02:00");

    @Autowired
    private PresentationAssessmentRepository presentationAssessmentRepository;

    @Autowired
    private PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private UserTestRepository searchUserRepository;

    private Course course;

    private Course otherCourse;

    private PresentationAssessment presentationAssessment;

    @BeforeEach
    void initTestCase() {
        featureToggleService.enableFeature(Feature.PresentationAssessments);
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        otherCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        setPresentationAssessmentsEnabled(course, true);
        setPresentationAssessmentsEnabled(otherCourse, true);
        courseRepository.saveAll(List.of(course, otherCourse));

        presentationAssessment = new PresentationAssessment();
        presentationAssessment.setCourse(course);
        presentationAssessment.setTitle("Initial presentation");
        presentationAssessment.setDescription("Initial description");
        presentationAssessment.setMaxPoints(20.0);
        presentationAssessment = presentationAssessmentRepository.save(presentationAssessment);
    }

    @AfterEach
    void resetPresentationAssessmentFeature() {
        featureToggleService.disableFeature(Feature.PresentationAssessments);
    }

    @Test
    void studentSearch_shouldMatchCaseInsensitiveLoginPrefixesWithConsistentCounts() {
        var pageable = PageRequest.of(0, 10);
        var roles = Set.of(CourseRole.STUDENT);
        long firstStudentId = userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();
        long secondStudentId = userUtilService.getUserByLogin(TEST_PREFIX + "student2").getId();
        String prefix = "PRESENTATIONASSESSMENTstudent";

        assertThat(searchUserRepository.findUserIdsByLoginOrNameInCourseWithRoles(prefix, course.getId(), roles, pageable)).containsExactlyInAnyOrder(firstStudentId,
                secondStudentId);
        assertThat(searchUserRepository.countUsersByLoginOrNameInCourseWithRoles(prefix, course.getId(), roles)).isEqualTo(2);
        assertThat(searchUserRepository.findUserIdsByLoginOrNameInCourseWithRolesNotUserId(prefix, course.getId(), roles, firstStudentId, pageable))
                .containsExactly(secondStudentId);
        assertThat(searchUserRepository.countUsersByLoginOrNameInCourseWithRolesNotUserId(prefix, course.getId(), roles, firstStudentId)).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_shouldCreatePresentationAssessment() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30.125, null, null, null);

        PresentationAssessmentDTO result = request.postWithResponseBody(getBaseUrl(course), dto, PresentationAssessmentDTO.class, HttpStatus.CREATED);

        assertThat(result.id()).isNotNull();
        assertThat(result.title()).isEqualTo(dto.title());
        assertThat(result.description()).isEqualTo(dto.description());
        assertThat(result.maxPoints()).isEqualTo(dto.maxPoints());
        assertThat(result.courseId()).isEqualTo(course.getId());
        assertThat(presentationAssessmentRepository.findOneByIdAndCourseId(result.id(), course.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_withCourseExercise_shouldLinkExercise() throws Exception {
        var exercise = textExerciseUtilService.createIndividualTextExercise(course, FIXED_DATE.minusDays(1), FIXED_DATE.plusDays(7), FIXED_DATE.plusDays(14));
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(null, "Exercise presentation", "Presentation for an exercise", 30.0, course.getId(), exercise.getId(), null);

        PresentationAssessmentDTO result = request.postWithResponseBody(getBaseUrl(course), dto, PresentationAssessmentDTO.class, HttpStatus.CREATED);

        assertThat(result.exerciseId()).isEqualTo(exercise.getId());
        assertThat(result.exerciseTitle()).isEqualTo(exercise.getTitle());
        PresentationAssessment storedAssessment = presentationAssessmentRepository.findOneByIdAndCourseId(result.id(), course.getId()).orElseThrow();
        assertThat(storedAssessment.getExercise().getId()).isEqualTo(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createPresentationAssessment_asStudent_shouldReturnForbidden() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30.0, null, null, null);

        request.postWithResponseBody(getBaseUrl(course), dto, PresentationAssessmentDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_withInvalidRequestData_shouldReturnBadRequest() throws Exception {
        request.postWithResponseBody(getBaseUrl(course), new PresentationAssessmentDTO(null, " ", "Course-level presentation assessment", 30.0, null, null, null),
                PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(getBaseUrl(course), new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 0.0, null, null, null),
                PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(getBaseUrl(course),
                new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30.0, otherCourse.getId(), null, null),
                PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessments_shouldReturnCoursePresentationAssessments() throws Exception {
        PresentationAssessment otherPresentationAssessment = new PresentationAssessment();
        otherPresentationAssessment.setCourse(otherCourse);
        otherPresentationAssessment.setTitle("Other presentation");
        otherPresentationAssessment.setMaxPoints(10.0);
        presentationAssessmentRepository.save(otherPresentationAssessment);

        var exercise = textExerciseUtilService.createIndividualTextExercise(course, FIXED_DATE.minusDays(1), FIXED_DATE.plusDays(7), FIXED_DATE.plusDays(14));
        presentationAssessment.setExercise(exercise);
        presentationAssessmentRepository.save(presentationAssessment);

        PresentationAssessmentInstance instance = new PresentationAssessmentInstance();
        instance.setPresentationAssessment(presentationAssessment);
        instance.setStudent(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        instance.setPresentationDate(FIXED_DATE.plusDays(14));
        instance.setLanguage("en");
        instance.setMode(PresentationAssessmentMode.IN_PERSON);
        presentationAssessmentInstanceRepository.save(instance);

        List<PresentationAssessmentDTO> result = request.getList(getBaseUrl(course), HttpStatus.OK, PresentationAssessmentDTO.class);

        assertThat(result).extracting(PresentationAssessmentDTO::id).containsExactly(presentationAssessment.getId());
        PresentationAssessmentDTO definition = result.getFirst();
        assertThat(definition.title()).isEqualTo(presentationAssessment.getTitle());
        assertThat(definition.description()).isEqualTo(presentationAssessment.getDescription());
        assertThat(definition.maxPoints()).isEqualTo(presentationAssessment.getMaxPoints());
        assertThat(definition.courseId()).isEqualTo(course.getId());
        assertThat(definition.exerciseId()).isEqualTo(exercise.getId());
        assertThat(definition.exerciseTitle()).isEqualTo(exercise.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessmentStudentRows_shouldReturnDistinctPagesWithTotalCount() throws Exception {
        PresentationAssessmentInstancesBatchCreateDTO dto = new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE.plusDays(14), null,
                List.of(TEST_PREFIX + "student1", TEST_PREFIX + "student2"), "en", PresentationAssessmentMode.IN_PERSON, "Room 1", null, null);
        request.postListWithResponseBody(getInstancesUrl(course, presentationAssessment), dto, PresentationAssessmentInstanceDTO.class, HttpStatus.OK);

        var firstPageResult = request
                .performMvcRequest(MockMvcRequestBuilders.get(getBaseUrl(course) + "/student-rows").param("assessmentId", presentationAssessment.getId().toString())
                        .param("page", "0").param("size", "1").param("sortField", "studentLogin").param("direction", "ASC"))
                .andExpect(status().isOk()).andReturn();
        List<PresentationAssessmentStudentRowDTO> firstPage = request.getObjectMapper().readValue(firstPageResult.getResponse().getContentAsString(),
                request.getObjectMapper().getTypeFactory().constructCollectionType(List.class, PresentationAssessmentStudentRowDTO.class));

        var secondPageResult = request
                .performMvcRequest(MockMvcRequestBuilders.get(getBaseUrl(course) + "/student-rows").param("assessmentId", presentationAssessment.getId().toString())
                        .param("page", "1").param("size", "1").param("sortField", "studentLogin").param("direction", "ASC"))
                .andExpect(status().isOk()).andReturn();
        List<PresentationAssessmentStudentRowDTO> secondPage = request.getObjectMapper().readValue(secondPageResult.getResponse().getContentAsString(),
                request.getObjectMapper().getTypeFactory().constructCollectionType(List.class, PresentationAssessmentStudentRowDTO.class));

        assertThat(firstPageResult.getResponse().getHeader("X-Total-Count")).isEqualTo("2");
        assertThat(firstPage).singleElement().satisfies(row -> assertThat(row.instance().student().login()).isEqualTo(TEST_PREFIX + "student1"));
        assertThat(secondPageResult.getResponse().getHeader("X-Total-Count")).isEqualTo("2");
        assertThat(secondPage).singleElement().satisfies(row -> assertThat(row.instance().student().login()).isEqualTo(TEST_PREFIX + "student2"));
        assertThat(firstPage.getFirst().instance().id()).isNotEqualTo(secondPage.getFirst().instance().id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_shouldUpdatePresentationAssessment() throws Exception {
        var originalExercise = textExerciseUtilService.createIndividualTextExercise(course, FIXED_DATE.minusDays(1), FIXED_DATE.plusDays(7), FIXED_DATE.plusDays(14));
        var replacementExercise = textExerciseUtilService.createIndividualTextExercise(course, FIXED_DATE.minusDays(1), FIXED_DATE.plusDays(8), FIXED_DATE.plusDays(15));
        presentationAssessment.setExercise(originalExercise);
        presentationAssessment = presentationAssessmentRepository.save(presentationAssessment);
        PresentationAssessmentInstance instance = new PresentationAssessmentInstance();
        instance.setPresentationAssessment(presentationAssessment);
        instance.setPresentationDate(FIXED_DATE.plusDays(7));
        instance.setResultPoints(17.5);
        instance.setLanguage("en");
        instance.setMode(PresentationAssessmentMode.IN_PERSON);
        instance.setStudent(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        instance = presentationAssessmentInstanceRepository.save(instance);
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25.5, course.getId(),
                replacementExercise.getId(), null);

        PresentationAssessmentDTO response = request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.OK);

        assertThat(response.id()).isEqualTo(presentationAssessment.getId());
        assertThat(response.title()).isEqualTo(dto.title());
        assertThat(response.description()).isEqualTo(dto.description());
        assertThat(response.maxPoints()).isEqualTo(dto.maxPoints());
        assertThat(response.courseId()).isEqualTo(course.getId());
        assertThat(response.exerciseId()).isEqualTo(replacementExercise.getId());
        assertThat(response.exerciseTitle()).isEqualTo(replacementExercise.getTitle());

        PresentationAssessment updatedAssessment = presentationAssessmentRepository.findByIdElseThrow(presentationAssessment.getId());
        assertThat(updatedAssessment.getTitle()).isEqualTo(dto.title());
        assertThat(updatedAssessment.getDescription()).isEqualTo(dto.description());
        assertThat(updatedAssessment.getMaxPoints()).isEqualTo(dto.maxPoints());
        assertThat(updatedAssessment.getExercise().getId()).isEqualTo(replacementExercise.getId());
        assertThat(presentationAssessmentInstanceRepository.findByIdElseThrow(instance.getId()).getPresentationAssessment().getId()).isEqualTo(presentationAssessment.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_withMismatchedId_shouldReturnBadRequest() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId() + 1, "Updated presentation", "Updated description", 25.0, course.getId(), null,
                null);

        request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_belowExistingResult_shouldReturnBadRequest() throws Exception {
        PresentationAssessmentInstance instance = new PresentationAssessmentInstance();
        instance.setPresentationAssessment(presentationAssessment);
        instance.setPresentationDate(FIXED_DATE.plusDays(7));
        instance.setResultPoints(18.0);
        instance.setLanguage("en");
        instance.setMode(PresentationAssessmentMode.IN_PERSON);
        instance.setStudent(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        presentationAssessmentInstanceRepository.save(instance);
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 10.0, course.getId(), null,
                null);

        request.put(getAssessmentUrl(course, presentationAssessment), dto, HttpStatus.BAD_REQUEST);

        PresentationAssessment storedAssessment = presentationAssessmentRepository.findByIdElseThrow(presentationAssessment.getId());
        assertThat(storedAssessment.getMaxPoints()).isEqualTo(20.0);
        assertThat(storedAssessment.getTitle()).isEqualTo("Initial presentation");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_withMismatchedCourseId_shouldReturnBadRequest() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25.0, otherCourse.getId(),
                null, null);

        request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_withWrongCourseId_shouldReturnNotFound() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25.0, otherCourse.getId(),
                null, null);

        request.putWithResponseBody(getAssessmentUrl(otherCourse, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.NOT_FOUND);

        PresentationAssessment storedAssessment = presentationAssessmentRepository.findByIdElseThrow(presentationAssessment.getId());
        assertThat(storedAssessment.getTitle()).isEqualTo("Initial presentation");
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
    void getPresentationAssessments_withFeatureDisabled_shouldReturnForbidden() throws Exception {
        featureToggleService.disableFeature(Feature.PresentationAssessments);

        request.getList(getBaseUrl(course), HttpStatus.FORBIDDEN, PresentationAssessmentDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessments_withCourseSettingDisabled_shouldReturnForbidden() throws Exception {
        setPresentationAssessmentsEnabled(course, false);
        courseRepository.save(course);

        request.getList(getBaseUrl(course), HttpStatus.FORBIDDEN, PresentationAssessmentDTO.class);
    }

    private static void setPresentationAssessmentsEnabled(Course course, boolean enabled) {
        CourseConfiguration courseConfiguration = course.getCourseConfiguration();
        if (courseConfiguration == null) {
            courseConfiguration = new CourseConfiguration();
            courseConfiguration.setCourse(course);
            course.setCourseConfiguration(courseConfiguration);
        }
        courseConfiguration.setPresentationAssessmentsEnabled(enabled);
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
    void deletePresentationAssessment_withWrongCourseId_shouldReturnNotFound() throws Exception {
        request.delete(getAssessmentUrl(otherCourse, presentationAssessment), HttpStatus.NOT_FOUND);

        assertThat(presentationAssessmentRepository.findById(presentationAssessment.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void presentationAssessmentWriteRequests_asStudent_shouldReturnForbidden() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25.0, course.getId(), null,
                null);

        request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.FORBIDDEN);
        request.delete(getAssessmentUrl(course, presentationAssessment), HttpStatus.FORBIDDEN);

        assertThat(presentationAssessmentRepository.findById(presentationAssessment.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessmentInstance_withoutStudentLogin_shouldReturnBadRequest() throws Exception {
        long instancesBeforeRequest = presentationAssessmentInstanceRepository.count();
        PresentationAssessmentInstancesBatchCreateDTO dto = new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE.plusDays(14), null, List.of(" "), "en",
                PresentationAssessmentMode.IN_PERSON, "Room 1", null, null);

        request.post(getInstancesUrl(course, presentationAssessment), dto, HttpStatus.BAD_REQUEST);

        assertThat(presentationAssessmentInstanceRepository.count()).isEqualTo(instancesBeforeRequest);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessmentInstance_withInvalidRequestData_shouldReturnBadRequest() throws Exception {
        long instancesBeforeRequest = presentationAssessmentInstanceRepository.count();
        request.post(getInstancesUrl(course, presentationAssessment), new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE.plusDays(14), null,
                List.of(TEST_PREFIX + "student1"), "", PresentationAssessmentMode.IN_PERSON, "Room 1", null, null), HttpStatus.BAD_REQUEST);
        request.post(getInstancesUrl(course, presentationAssessment),
                new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE.plusDays(14), null, List.of(TEST_PREFIX + "student1"), "en", null, "Room 1", null, null),
                HttpStatus.BAD_REQUEST);

        assertThat(presentationAssessmentInstanceRepository.count()).isEqualTo(instancesBeforeRequest);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessmentInstance_withDuplicateTrimmedLoginAndResult_shouldCreateSingleAssignment() throws Exception {
        List<PresentationAssessmentInstanceDTO> instancesBeforeRequest = request
                .getList(getBaseUrl(course) + "/student-rows?assessmentId=" + presentationAssessment.getId(), HttpStatus.OK, PresentationAssessmentStudentRowDTO.class).stream()
                .map(PresentationAssessmentStudentRowDTO::instance).toList();
        PresentationAssessmentInstancesBatchCreateDTO dto = new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE.plusDays(14), 15.5,
                List.of(TEST_PREFIX + "student1", " " + TEST_PREFIX + "student1 "), "en", PresentationAssessmentMode.IN_PERSON, "Room 1", null, null);

        List<PresentationAssessmentInstanceDTO> results = request.postListWithResponseBody(getInstancesUrl(course, presentationAssessment), dto,
                PresentationAssessmentInstanceDTO.class, HttpStatus.OK);
        assertThat(results).hasSize(1);
        PresentationAssessmentInstanceDTO result = results.getFirst();

        assertThat(result.student().login()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(result.resultPoints()).isEqualTo(15.5);
        assertThat(result.student()).isNotNull().satisfies(student -> {
            assertThat(student.login()).isEqualTo(TEST_PREFIX + "student1");
            assertThat(student.email()).isNotBlank();
        });
        List<PresentationAssessmentInstanceDTO> persistedInstances = request
                .getList(getBaseUrl(course) + "/student-rows?assessmentId=" + presentationAssessment.getId(), HttpStatus.OK, PresentationAssessmentStudentRowDTO.class).stream()
                .map(PresentationAssessmentStudentRowDTO::instance).toList();
        assertThat(persistedInstances).hasSize(instancesBeforeRequest.size() + 1);
        assertThat(persistedInstances).filteredOn(instance -> instance.id().equals(result.id())).singleElement()
                .satisfies(instance -> assertThat(instance.student().login()).isEqualTo(TEST_PREFIX + "student1"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessmentInstance_shouldUpdateIndividualResult() throws Exception {
        PresentationAssessmentInstanceDTO individual = createSearchInstance(presentationAssessment, "student1", null);
        PresentationAssessmentInstanceRequestDTO updateDto = new PresentationAssessmentInstanceRequestDTO(individual.id(), individual.presentationDate(), 12.5,
                individual.student().login(), individual.language(), PresentationAssessmentMode.ONLINE, individual.location(), "https://example.org/presentation",
                "Updated result");

        PresentationAssessmentInstanceDTO updated = request.putWithResponseBody(getInstancesUrl(course, presentationAssessment) + "/" + individual.id(), updateDto,
                PresentationAssessmentInstanceDTO.class, HttpStatus.OK);

        assertThat(updated.id()).isEqualTo(individual.id());
        assertThat(updated.student().login()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(updated.resultPoints()).isEqualTo(12.5);
        assertThat(updated.remark()).isEqualTo("Updated result");
        assertThat(updated.mode()).isEqualTo(PresentationAssessmentMode.ONLINE);
        assertThat(updated.location()).isNull();
        assertThat(updated.meetingLink()).isEqualTo("https://example.org/presentation");

        PresentationAssessmentInstance stored = presentationAssessmentInstanceRepository.findByIdElseThrow(individual.id());
        assertThat(stored.getResultPoints()).isEqualTo(12.5);
        assertThat(stored.getRemark()).isEqualTo("Updated result");
        assertThat(stored.getMode()).isEqualTo(PresentationAssessmentMode.ONLINE);
        assertThat(stored.getLocation()).isNull();
        assertThat(stored.getMeetingLink()).isEqualTo("https://example.org/presentation");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void savePresentationAssessmentInstances_shouldCreateOneInstancePerStudent() throws Exception {
        PresentationAssessmentInstancesBatchCreateDTO dto = new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE.plusDays(14), 15.125,
                List.of(TEST_PREFIX + "student1", TEST_PREFIX + "student2"), "en", PresentationAssessmentMode.IN_PERSON, "Room 1", null, "Good presentation");

        List<PresentationAssessmentInstanceDTO> result = request.postListWithResponseBody(getInstancesUrl(course, presentationAssessment), dto,
                PresentationAssessmentInstanceDTO.class, HttpStatus.OK);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(instance -> {
            assertThat(instance.id()).isNotNull();
            assertThat(instance.resultPoints()).isEqualTo(15.125);
            assertThat(instance.student().login()).isNotBlank();
        });
        assertThat(result).extracting(instance -> instance.student().login()).containsExactlyInAnyOrder(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
        assertThat(result).extracting(PresentationAssessmentInstanceDTO::id).doesNotHaveDuplicates();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void savePresentationAssessmentInstances_withInvalidStudent_shouldNotPersistPartialResults() throws Exception {
        long instancesBeforeRequest = presentationAssessmentInstanceRepository.count();
        PresentationAssessmentInstancesBatchCreateDTO dto = new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE.plusDays(14), 15.5,
                List.of(TEST_PREFIX + "student1", "unknown-student"), "en", PresentationAssessmentMode.IN_PERSON, "Room 1", null, null);

        request.post(getInstancesUrl(course, presentationAssessment), dto, HttpStatus.BAD_REQUEST);

        assertThat(presentationAssessmentInstanceRepository.count()).isEqualTo(instancesBeforeRequest);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void savePresentationAssessmentInstances_withEmptyStudentList_shouldReturnBadRequest() throws Exception {
        long instancesBeforeRequest = presentationAssessmentInstanceRepository.count();
        PresentationAssessmentInstancesBatchCreateDTO dto = new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE.plusDays(14), 15.5, List.of(), "en",
                PresentationAssessmentMode.IN_PERSON, "Room 1", null, null);

        request.post(getInstancesUrl(course, presentationAssessment), dto, HttpStatus.BAD_REQUEST);

        assertThat(presentationAssessmentInstanceRepository.count()).isEqualTo(instancesBeforeRequest);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessmentInstance_shouldNotChangeAnotherStudentsResult() throws Exception {
        PresentationAssessmentInstancesBatchCreateDTO createDto = new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE.plusDays(14), null,
                List.of(TEST_PREFIX + "student1", TEST_PREFIX + "student2"), "en", PresentationAssessmentMode.IN_PERSON, "Room 1", null, null);
        List<PresentationAssessmentInstanceDTO> created = request.postListWithResponseBody(getInstancesUrl(course, presentationAssessment), createDto,
                PresentationAssessmentInstanceDTO.class, HttpStatus.OK);
        PresentationAssessmentInstanceDTO individual = created.stream().filter(instance -> (TEST_PREFIX + "student1").equals(instance.student().login())).findFirst().orElseThrow();

        PresentationAssessmentInstanceRequestDTO updateDto = new PresentationAssessmentInstanceRequestDTO(individual.id(), individual.presentationDate(), 18.5,
                individual.student().login(), individual.language(), individual.mode(), individual.location(), individual.meetingLink(), "Assessed");
        request.putWithResponseBody(getInstancesUrl(course, presentationAssessment) + "/" + individual.id(), updateDto, PresentationAssessmentInstanceDTO.class, HttpStatus.OK);

        List<PresentationAssessmentInstanceDTO> result = request
                .getList(getBaseUrl(course) + "/student-rows?assessmentId=" + presentationAssessment.getId(), HttpStatus.OK, PresentationAssessmentStudentRowDTO.class).stream()
                .map(PresentationAssessmentStudentRowDTO::instance).toList();

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(instance -> {
            assertThat(instance.student().login()).isEqualTo(TEST_PREFIX + "student1");
            assertThat(instance.resultPoints()).isEqualTo(18.5);
            assertThat(instance.remark()).isEqualTo("Assessed");
        });
        assertThat(result).anySatisfy(instance -> {
            assertThat(instance.student().login()).isEqualTo(TEST_PREFIX + "student2");
            assertThat(instance.resultPoints()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "STUDENT1", "ADA LOVELACE", "ALPHA TALK" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessmentStudentRows_shouldSearchLoginNameAndTitle(String searchTerm) throws Exception {
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student.setFirstName("Ada");
        student.setLastName("Lovelace");
        searchUserRepository.save(student);
        presentationAssessment.setTitle("Alpha talk");
        presentationAssessmentRepository.save(presentationAssessment);
        var otherAssessment = createSearchAssessment("Beta talk", false);
        var matchingInstance = createSearchInstance(presentationAssessment, "student1", null);
        createSearchInstance(otherAssessment, "student2", null);

        assertStudentRowSearch(Map.of("searchTerm", "  " + searchTerm + "  "), List.of(matchingInstance.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "%", "_", "\\" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessmentStudentRows_shouldSearchSpecialCharactersLiterally(String character) throws Exception {
        presentationAssessment.setTitle("Topic " + character + " special");
        presentationAssessmentRepository.save(presentationAssessment);
        var otherAssessment = createSearchAssessment("Topic X special", false);
        var matchingInstance = createSearchInstance(presentationAssessment, "student1", null);
        createSearchInstance(otherAssessment, "student2", null);

        assertStudentRowSearch(Map.of("searchTerm", character), List.of(matchingInstance.id()));
    }

    @ParameterizedTest
    @CsvSource({ "assessed,true,zero|linked", "assessed,false,pending", "linkedToExercise,true,linked", "linkedToExercise,false,pending|zero",
            "assessmentId,standalone,pending|zero" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessmentStudentRows_shouldApplyIndividualFilters(String filter, String value, String expectedRows) throws Exception {
        var linkedAssessment = createSearchAssessment("Linked presentation", true);
        var pending = createSearchInstance(presentationAssessment, "student1", null);
        var zero = createSearchInstance(presentationAssessment, "student2", 0.0);
        var linked = createSearchInstance(linkedAssessment, "student1", 15.0);
        var ids = Map.of("pending", pending.id(), "zero", zero.id(), "linked", linked.id());
        var expectedIds = Arrays.stream(expectedRows.split("\\|")).map(ids::get).toList();
        String filterValue = value.equals("standalone") ? presentationAssessment.getId().toString() : value;

        assertStudentRowSearch(Map.of(filter, filterValue), expectedIds);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPresentationAssessmentStudentRows_shouldCombineSearchAndFilters() throws Exception {
        var linkedAssessment = createSearchAssessment("Shared presentation", true);
        var otherLinkedAssessment = createSearchAssessment("Shared other presentation", true);
        presentationAssessment.setTitle("Shared standalone presentation");
        presentationAssessmentRepository.save(presentationAssessment);
        var matchingInstance = createSearchInstance(linkedAssessment, "student1", 0.0);
        createSearchInstance(linkedAssessment, "student2", null);
        createSearchInstance(otherLinkedAssessment, "student1", 15.0);
        createSearchInstance(presentationAssessment, "student2", 10.0);

        assertStudentRowSearch(Map.of("searchTerm", "SHARED", "assessmentId", linkedAssessment.getId().toString(), "assessed", "true", "linkedToExercise", "true"),
                List.of(matchingInstance.id()));
        assertStudentRowSearch(Map.of("searchTerm", "missing", "assessmentId", linkedAssessment.getId().toString(), "assessed", "true", "linkedToExercise", "true"), List.of());
    }

    private PresentationAssessment createSearchAssessment(String title, boolean linkedToExercise) {
        var assessment = new PresentationAssessment();
        assessment.setCourse(course);
        assessment.setTitle(title);
        assessment.setMaxPoints(20.0);
        if (linkedToExercise) {
            assessment.setExercise(textExerciseUtilService.createIndividualTextExercise(course, FIXED_DATE.minusDays(1), FIXED_DATE.plusDays(7), FIXED_DATE.plusDays(14)));
        }
        return presentationAssessmentRepository.save(assessment);
    }

    private PresentationAssessmentInstanceDTO createSearchInstance(PresentationAssessment assessment, String studentSuffix, Double points) throws Exception {
        var dto = new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE, points, List.of(TEST_PREFIX + studentSuffix), "en", PresentationAssessmentMode.IN_PERSON, "Room 1",
                null, null);
        return request.postListWithResponseBody(getInstancesUrl(course, assessment), dto, PresentationAssessmentInstanceDTO.class, HttpStatus.OK).getFirst();
    }

    private void assertStudentRowSearch(Map<String, String> parameters, List<Long> expectedIds) throws Exception {
        var query = MockMvcRequestBuilders.get(getBaseUrl(course) + "/student-rows").param("size", "20");
        parameters.forEach(query::param);
        var result = request.performMvcRequest(query).andExpect(status().isOk()).andReturn();
        List<PresentationAssessmentStudentRowDTO> rows = request.getObjectMapper().readValue(result.getResponse().getContentAsString(),
                request.getObjectMapper().getTypeFactory().constructCollectionType(List.class, PresentationAssessmentStudentRowDTO.class));

        assertThat(rows).extracting(row -> row.instance().id()).containsExactlyInAnyOrderElementsOf(expectedIds);
        assertThat(result.getResponse().getHeader("X-Total-Count")).isEqualTo(Integer.toString(expectedIds.size()));
    }

    private String getBaseUrl(Course course) {
        return BASE_URL + course.getId() + "/presentation-assessments";
    }

    private String getAssessmentUrl(Course course, PresentationAssessment presentationAssessment) {
        return getBaseUrl(course) + "/" + presentationAssessment.getId();
    }

    private String getInstancesUrl(Course course, PresentationAssessment presentationAssessment) {
        return getAssessmentUrl(course, presentationAssessment) + "/instances";
    }

}
