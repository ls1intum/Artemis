package de.tum.cit.aet.artemis.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessmentMode;
import de.tum.cit.aet.artemis.presentation.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.presentation.dto.PresentationAssessmentInstanceDTO;
import de.tum.cit.aet.artemis.presentation.repository.PresentationAssessmentInstanceRepository;
import de.tum.cit.aet.artemis.presentation.repository.PresentationAssessmentRepository;
import de.tum.cit.aet.artemis.presentation.service.PresentationAssessmentService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class PresentationAssessmentIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "presentationassessment";

    private static final String BASE_URL = "/api/presentation/courses/";

    private static final ZonedDateTime FIXED_DATE = ZonedDateTime.parse("2026-07-31T13:26:00+02:00");

    @Autowired
    private PresentationAssessmentRepository presentationAssessmentRepository;

    @Autowired
    private PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository;

    @Autowired
    private PresentationAssessmentService presentationAssessmentService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private FeatureToggleService featureToggleService;

    private Course course;

    private Course otherCourse;

    private PresentationAssessment presentationAssessment;

    @BeforeEach
    void initTestCase() {
        featureToggleService.enableFeature(Feature.PresentationAssessments);
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        otherCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        course.setPresentationAssessmentsEnabled(true);
        otherCourse.setPresentationAssessmentsEnabled(true);
        courseRepository.saveAll(List.of(course, otherCourse));

        presentationAssessment = new PresentationAssessment();
        presentationAssessment.setCourse(course);
        presentationAssessment.setTitle("Initial presentation");
        presentationAssessment.setDescription("Initial description");
        presentationAssessment.setMaxPoints(20.0);
        presentationAssessment = presentationAssessmentRepository.save(presentationAssessment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_shouldCreatePresentationAssessment() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30.5, null, null, null, List.of());

        PresentationAssessmentDTO result = request.postWithResponseBody(getBaseUrl(course), dto, PresentationAssessmentDTO.class, HttpStatus.CREATED);

        assertThat(result.id()).isNotNull();
        assertThat(result.title()).isEqualTo(dto.title());
        assertThat(result.description()).isEqualTo(dto.description());
        assertThat(result.maxPoints()).isEqualTo(dto.maxPoints());
        assertThat(result.courseId()).isEqualTo(course.getId());
        assertThat(presentationAssessmentRepository.findByIdAndCourseId(result.id(), course.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_withCourseExercise_shouldLinkExercise() throws Exception {
        var exercise = textExerciseUtilService.createIndividualTextExercise(course, FIXED_DATE.minusDays(1), FIXED_DATE.plusDays(7), FIXED_DATE.plusDays(14));
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(null, "Exercise presentation", "Presentation for an exercise", 30.0, course.getId(), exercise.getId(), null,
                List.of());

        PresentationAssessmentDTO result = request.postWithResponseBody(getBaseUrl(course), dto, PresentationAssessmentDTO.class, HttpStatus.CREATED);

        assertThat(result.exerciseId()).isEqualTo(exercise.getId());
        assertThat(result.exerciseTitle()).isEqualTo(exercise.getTitle());
        PresentationAssessment storedAssessment = presentationAssessmentRepository.findByIdAndCourseId(result.id(), course.getId()).orElseThrow();
        assertThat(storedAssessment.getExercise().getId()).isEqualTo(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createPresentationAssessment_asStudent_shouldReturnForbidden() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30.0, null, null, null, List.of());

        request.postWithResponseBody(getBaseUrl(course), dto, PresentationAssessmentDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessment_withInvalidRequestData_shouldReturnBadRequest() throws Exception {
        request.postWithResponseBody(getBaseUrl(course), new PresentationAssessmentDTO(null, " ", "Course-level presentation assessment", 30.0, null, null, null, List.of()),
                PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(getBaseUrl(course),
                new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 0.0, null, null, null, List.of()),
                PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(getBaseUrl(course),
                new PresentationAssessmentDTO(null, "Final presentation", "Course-level presentation assessment", 30.0, otherCourse.getId(), null, null, List.of()),
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
        instance = presentationAssessmentInstanceRepository.save(instance);
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25.5, course.getId(),
                replacementExercise.getId(), null, List.of());

        request.put(getAssessmentUrl(course, presentationAssessment), dto, HttpStatus.OK);

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
                null, List.of());

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
        presentationAssessmentInstanceRepository.save(instance);
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 10.0, course.getId(), null,
                null, List.of());

        request.put(getAssessmentUrl(course, presentationAssessment), dto, HttpStatus.BAD_REQUEST);

        PresentationAssessment storedAssessment = presentationAssessmentRepository.findByIdElseThrow(presentationAssessment.getId());
        assertThat(storedAssessment.getMaxPoints()).isEqualTo(20.0);
        assertThat(storedAssessment.getTitle()).isEqualTo("Initial presentation");
    }

    @Test
    void concurrentMaximumAndResultUpdates_shouldPreservePointsInvariant() throws Exception {
        PresentationAssessmentInstance instance = new PresentationAssessmentInstance();
        instance.setPresentationAssessment(presentationAssessment);
        instance.setPresentationDate(FIXED_DATE.plusDays(7));
        instance.setLanguage("en");
        instance.setMode(PresentationAssessmentMode.IN_PERSON);
        instance = presentationAssessmentInstanceRepository.save(instance);
        long instanceId = instance.getId();
        PresentationAssessmentDTO assessmentDto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 10.0, course.getId(),
                null, null, List.of());
        PresentationAssessmentInstanceDTO instanceDto = new PresentationAssessmentInstanceDTO(instanceId, instance.getPresentationDate(), 18.0, List.of(), "en",
                PresentationAssessmentMode.IN_PERSON, "Room 1", null, null);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<RuntimeException> maximumUpdate = executor
                    .submit(() -> runAfterLatch(ready, start, () -> presentationAssessmentService.update(course, presentationAssessment.getId(), assessmentDto)));
            Future<RuntimeException> resultUpdate = executor
                    .submit(() -> runAfterLatch(ready, start, () -> presentationAssessmentService.updateInstance(course, presentationAssessment.getId(), instanceId, instanceDto)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RuntimeException> failures = java.util.stream.Stream.of(maximumUpdate.get(10, TimeUnit.SECONDS), resultUpdate.get(10, TimeUnit.SECONDS)).filter(Objects::nonNull)
                    .toList();
            assertThat(failures).singleElement().isInstanceOf(BadRequestAlertException.class);
        }

        PresentationAssessment storedAssessment = presentationAssessmentRepository.findByIdElseThrow(presentationAssessment.getId());
        PresentationAssessmentInstance storedInstance = presentationAssessmentInstanceRepository.findByIdElseThrow(instanceId);
        assertThat(storedInstance.getResultPoints() == null || storedInstance.getResultPoints() <= storedAssessment.getMaxPoints()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_withMismatchedCourseId_shouldReturnBadRequest() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25.0, otherCourse.getId(),
                null, null, List.of());

        request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updatePresentationAssessment_withWrongCourseId_shouldReturnNotFound() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25.0, otherCourse.getId(),
                null, null, List.of());

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

        assertThat(presentationAssessmentRepository.findById(presentationAssessment.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void presentationAssessmentWriteRequests_asStudent_shouldReturnForbidden() throws Exception {
        PresentationAssessmentDTO dto = new PresentationAssessmentDTO(presentationAssessment.getId(), "Updated presentation", "Updated description", 25.0, course.getId(), null,
                null, List.of());

        request.putWithResponseBody(getAssessmentUrl(course, presentationAssessment), dto, PresentationAssessmentDTO.class, HttpStatus.FORBIDDEN);
        request.delete(getAssessmentUrl(course, presentationAssessment), HttpStatus.FORBIDDEN);

        assertThat(presentationAssessmentRepository.findById(presentationAssessment.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessmentInstance_withoutStudentLogins_shouldReturnBadRequest() throws Exception {
        long instancesBeforeRequest = presentationAssessmentInstanceRepository.count();
        PresentationAssessmentInstanceDTO dto = new PresentationAssessmentInstanceDTO(null, FIXED_DATE.plusDays(14), null, List.of(), "en", PresentationAssessmentMode.IN_PERSON,
                "Room 1", null, null);

        request.postWithResponseBody(getInstancesUrl(course, presentationAssessment), dto, PresentationAssessmentInstanceDTO.class, HttpStatus.BAD_REQUEST);

        assertThat(presentationAssessmentInstanceRepository.count()).isEqualTo(instancesBeforeRequest);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createPresentationAssessmentInstance_withInvalidRequestData_shouldReturnBadRequest() throws Exception {
        long instancesBeforeRequest = presentationAssessmentInstanceRepository.count();
        request.postWithResponseBody(getInstancesUrl(course, presentationAssessment), new PresentationAssessmentInstanceDTO(null, FIXED_DATE.plusDays(14), null,
                List.of(TEST_PREFIX + "student1"), "", PresentationAssessmentMode.IN_PERSON, "Room 1", null, null), PresentationAssessmentInstanceDTO.class,
                HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(getInstancesUrl(course, presentationAssessment),
                new PresentationAssessmentInstanceDTO(null, FIXED_DATE.plusDays(14), null, List.of(TEST_PREFIX + "student1"), "en", null, "Room 1", null, null),
                PresentationAssessmentInstanceDTO.class, HttpStatus.BAD_REQUEST);

        assertThat(presentationAssessmentInstanceRepository.count()).isEqualTo(instancesBeforeRequest);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void savePresentationAssessmentInstances_shouldCreateOneInstancePerStudentAtomically() throws Exception {
        PresentationAssessmentInstanceDTO dto = new PresentationAssessmentInstanceDTO(null, FIXED_DATE.plusDays(14), 15.5,
                List.of(TEST_PREFIX + "student1", TEST_PREFIX + "student2"), "en", PresentationAssessmentMode.IN_PERSON, "Room 1", null, "Good presentation");

        List<PresentationAssessmentInstanceDTO> result = request.postListWithResponseBody(getInstancesUrl(course, presentationAssessment) + "/batch", dto,
                PresentationAssessmentInstanceDTO.class, HttpStatus.OK);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(instance -> {
            assertThat(instance.id()).isNotNull();
            assertThat(instance.resultPoints()).isEqualTo(15.5);
            assertThat(instance.studentLogins()).hasSize(1);
        });
        assertThat(result).flatExtracting(PresentationAssessmentInstanceDTO::studentLogins).containsExactlyInAnyOrder(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void savePresentationAssessmentInstances_withInvalidStudent_shouldNotPersistPartialResults() throws Exception {
        long instancesBeforeRequest = presentationAssessmentInstanceRepository.count();
        PresentationAssessmentInstanceDTO dto = new PresentationAssessmentInstanceDTO(null, FIXED_DATE.plusDays(14), 15.5, List.of(TEST_PREFIX + "student1", "unknown-student"),
                "en", PresentationAssessmentMode.IN_PERSON, "Room 1", null, null);

        request.post(getInstancesUrl(course, presentationAssessment) + "/batch", dto, HttpStatus.BAD_REQUEST);

        assertThat(presentationAssessmentInstanceRepository.count()).isEqualTo(instancesBeforeRequest);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void savePresentationAssessmentInstances_shouldSplitSharedInstanceAtomically() throws Exception {
        PresentationAssessmentInstanceDTO sharedDto = new PresentationAssessmentInstanceDTO(null, FIXED_DATE.plusDays(14), null,
                List.of(TEST_PREFIX + "student1", TEST_PREFIX + "student2"), "en", PresentationAssessmentMode.IN_PERSON, "Room 1", null, null);
        PresentationAssessmentInstanceDTO sharedInstance = request.postWithResponseBody(getInstancesUrl(course, presentationAssessment), sharedDto,
                PresentationAssessmentInstanceDTO.class, HttpStatus.CREATED);
        PresentationAssessmentInstanceDTO assessedStudent = new PresentationAssessmentInstanceDTO(sharedInstance.id(), sharedInstance.presentationDate(), 18.5,
                List.of(TEST_PREFIX + "student1"), sharedInstance.language(), sharedInstance.mode(), sharedInstance.location(), sharedInstance.meetingLink(), "Assessed");

        List<PresentationAssessmentInstanceDTO> result = request.postListWithResponseBody(getInstancesUrl(course, presentationAssessment) + "/batch", assessedStudent,
                PresentationAssessmentInstanceDTO.class, HttpStatus.OK);

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(instance -> {
            assertThat(instance.studentLogins()).containsExactly(TEST_PREFIX + "student1");
            assertThat(instance.resultPoints()).isEqualTo(18.5);
            assertThat(instance.remark()).isEqualTo("Assessed");
        });
        assertThat(result).anySatisfy(instance -> {
            assertThat(instance.studentLogins()).containsExactly(TEST_PREFIX + "student2");
            assertThat(instance.resultPoints()).isNull();
        });
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

    private RuntimeException runAfterLatch(CountDownLatch ready, CountDownLatch start, Runnable operation) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            operation.run();
            return null;
        }
        catch (RuntimeException exception) {
            return exception;
        }
    }
}
