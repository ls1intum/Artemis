package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LectureTranscriptionServiceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "lecturetranscriptionservicetest";

    @Autowired
    @Qualifier("irisRequestMockProvider")
    private IrisRequestMockProvider irisRequestMockProvider;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitTestRepository;

    private Course course;

    private Lecture lecture;

    private AttachmentVideoUnit unit;

    @BeforeEach
    void initTestCase() throws Exception {
        irisRequestMockProvider.enableMockingOfRequests();
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        course = courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        lecture = course.getLectures().stream().findFirst().orElseThrow();
        lecture.setTitle("Service Test Lecture " + lecture.getId());
        lecture = lectureRepository.save(lecture);
        unit = lectureUtilService.createAttachmentVideoUnit(lecture, true);
        unit.setVideoSource("https://example.com/video.mp4");
        unit = attachmentVideoUnitTestRepository.save(unit);
        final Long unitId = unit.getId();
        lecture = lectureUtilService.addLectureUnitsToLecture(lecture, List.of(unit));
        lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture.getId());
        unit = (AttachmentVideoUnit) lecture.getLectureUnits().stream().filter(u -> u.getId().equals(unitId)).findFirst().orElseThrow();
    }

    @AfterEach
    void tearDownMocks() throws Exception {
        irisRequestMockProvider.reset();
    }

    @Test
    void startNebulaTranscription_success_createsTranscription() {
        irisRequestMockProvider.mockTranscriptionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());

        NebulaTranscriptionRequestDTO requestDTO = new NebulaTranscriptionRequestDTO("https://example.com/video.mp4", unit.getId(), lecture.getId(), course.getId(),
                course.getTitle(), lecture.getTitle(), unit.getName(), null);

        String jobToken = lectureTranscriptionService.startNebulaTranscription(lecture.getId(), unit.getId(), requestDTO);

        assertThat(jobToken).isNotNull();
        var saved = lectureTranscriptionRepository.findByJobId(jobToken);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
        assertThat(saved.get().getLectureUnit().getId()).isEqualTo(unit.getId());
    }

    @Test
    void startNebulaTranscription_deletesExistingBeforeStarting() {
        // Pre-persist a stale transcription for the unit
        LectureTranscription stale = new LectureTranscription();
        stale.setJobId("old-job-id");
        stale.setTranscriptionStatus(TranscriptionStatus.FAILED);
        stale.setLectureUnit(unit);
        LectureTranscription saved = lectureTranscriptionRepository.save(stale);
        Long staleId = saved.getId();

        irisRequestMockProvider.mockTranscriptionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());

        NebulaTranscriptionRequestDTO requestDTO = new NebulaTranscriptionRequestDTO("https://example.com/video.mp4", unit.getId(), lecture.getId(), course.getId(),
                course.getTitle(), lecture.getTitle(), unit.getName(), null);

        String jobToken = lectureTranscriptionService.startNebulaTranscription(lecture.getId(), unit.getId(), requestDTO);

        assertThat(jobToken).isNotNull();
        assertThat(lectureTranscriptionRepository.findById(staleId)).isEmpty();
        var newTranscription = lectureTranscriptionRepository.findByJobId(jobToken);
        assertThat(newTranscription).isPresent();
        assertThat(newTranscription.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
    }
}
