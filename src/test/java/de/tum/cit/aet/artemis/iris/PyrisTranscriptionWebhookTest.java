package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcription.PyrisTranscriptionResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcription.PyrisTranscriptionStatusUpdateDTO;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

class PyrisTranscriptionWebhookTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyristranscriptionwebhooktest";

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

    @Autowired
    protected PyrisJobService pyrisJobService;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @Autowired
    private ObjectMapper mapper;

    private Course course;

    private Lecture lecture;

    private AttachmentVideoUnit unit;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        course = courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        lecture = course.getLectures().stream().findFirst().orElseThrow();
        lecture.setTitle("Transcription Test Lecture " + lecture.getId());
        lecture = lectureRepository.save(lecture);
        unit = lectureUtilService.createAttachmentVideoUnit(lecture, true);
        unit.setVideoSource("https://example.com/video.mp4");
        unit = attachmentVideoUnitTestRepository.save(unit);
        final Long unitId = unit.getId();
        lecture = lectureUtilService.addLectureUnitsToLecture(lecture, List.of(unit));
        lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture.getId());
        unit = (AttachmentVideoUnit) lecture.getLectureUnits().stream().filter(u -> u.getId().equals(unitId)).findFirst().orElseThrow();
        activateIrisFor(course);
    }

    @Test
    void testTranscriptionJobDone_savesCompletedTranscription() throws Exception {
        String jobToken = pyrisJobService.addTranscriptionWebhookJob(course.getId(), lecture.getId(), unit.getId());
        LectureTranscription transcription = new LectureTranscription();
        transcription.setJobId(jobToken);
        transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);
        transcription.setLectureUnit(unit);
        lectureTranscriptionRepository.save(transcription);

        PyrisTranscriptionResultDTO result = new PyrisTranscriptionResultDTO(unit.getId(), "en", List.of(new LectureTranscriptionSegment(0.0, 5.0, "Hello world", 1)));
        String resultJson = mapper.writeValueAsString(result);
        PyrisTranscriptionStatusUpdateDTO statusUpdate = new PyrisTranscriptionStatusUpdateDTO(List.of(new PyrisStageDTO("done", 1, PyrisStageState.DONE, "complete", false)),
                resultJson, unit.getId());

        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/transcription/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);

        var saved = lectureTranscriptionRepository.findByJobId(jobToken);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(saved.get().getLanguage()).isEqualTo("en");
        assertThat(saved.get().getSegments()).hasSize(1);
        assertThat(pyrisJobService.getJob(jobToken)).isNull();
    }

    @Test
    void testTranscriptionJobError_savesFailedTranscription() throws Exception {
        String jobToken = pyrisJobService.addTranscriptionWebhookJob(course.getId(), lecture.getId(), unit.getId());
        LectureTranscription transcription = new LectureTranscription();
        transcription.setJobId(jobToken);
        transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);
        transcription.setLectureUnit(unit);
        lectureTranscriptionRepository.save(transcription);

        PyrisTranscriptionStatusUpdateDTO statusUpdate = new PyrisTranscriptionStatusUpdateDTO(List.of(new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "failed", false)), null,
                unit.getId());

        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/transcription/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);

        var saved = lectureTranscriptionRepository.findByJobId(jobToken);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(pyrisJobService.getJob(jobToken)).isNull();
    }

    @Test
    void testTranscriptionJobInProgress_keepsJob() throws Exception {
        String jobToken = pyrisJobService.addTranscriptionWebhookJob(course.getId(), lecture.getId(), unit.getId());
        LectureTranscription transcription = new LectureTranscription();
        transcription.setJobId(jobToken);
        transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);
        transcription.setLectureUnit(unit);
        lectureTranscriptionRepository.save(transcription);

        PyrisTranscriptionStatusUpdateDTO statusUpdate = new PyrisTranscriptionStatusUpdateDTO(
                List.of(new PyrisStageDTO("processing", 1, PyrisStageState.IN_PROGRESS, "running", false)), null, unit.getId());

        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/transcription/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);

        assertThat(pyrisJobService.getJob(jobToken)).isNotNull();
        var saved = lectureTranscriptionRepository.findByJobId(jobToken);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
    }

    @Test
    void testRunIdMismatch_returns409() throws Exception {
        String tokenA = pyrisJobService.addTranscriptionWebhookJob(course.getId(), lecture.getId(), unit.getId());
        String tokenB = pyrisJobService.addTranscriptionWebhookJob(course.getId(), lecture.getId(), unit.getId());
        PyrisTranscriptionStatusUpdateDTO statusUpdate = new PyrisTranscriptionStatusUpdateDTO(List.of(new PyrisStageDTO("done", 1, PyrisStageState.DONE, "complete", false)), null,
                unit.getId());

        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + tokenB))));
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/webhooks/transcription/runs/" + tokenA + "/status", statusUpdate,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match run ID in request body");
    }

    @Test
    void testWrongJobType_returns409() throws Exception {
        String chatJobToken = pyrisJobService.addCourseChatJob(course.getId(), 123L, 123L);
        PyrisTranscriptionStatusUpdateDTO statusUpdate = new PyrisTranscriptionStatusUpdateDTO(List.of(new PyrisStageDTO("done", 1, PyrisStageState.DONE, "complete", false)), null,
                unit.getId());

        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + chatJobToken))));
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/webhooks/transcription/runs/" + chatJobToken + "/status", statusUpdate,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID is not a transcription job");
    }

    @Test
    void testAllDoneNoResult_savesNoChange() throws Exception {
        String jobToken = pyrisJobService.addTranscriptionWebhookJob(course.getId(), lecture.getId(), unit.getId());
        LectureTranscription transcription = new LectureTranscription();
        transcription.setJobId(jobToken);
        transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);
        transcription.setLectureUnit(unit);
        lectureTranscriptionRepository.save(transcription);

        PyrisTranscriptionStatusUpdateDTO statusUpdate = new PyrisTranscriptionStatusUpdateDTO(List.of(new PyrisStageDTO("done", 1, PyrisStageState.DONE, "complete", false)), null,
                unit.getId());

        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/transcription/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);

        var saved = lectureTranscriptionRepository.findByJobId(jobToken);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
        assertThat(pyrisJobService.getJob(jobToken)).isNull();
    }

    @Test
    void testTranscriptionJobDone_noTranscriptionRow_doesNotCrash() throws Exception {
        // Job token exists in Hazelcast but no LectureTranscription row in DB (e.g. persistence failed earlier)
        String jobToken = pyrisJobService.addTranscriptionWebhookJob(course.getId(), lecture.getId(), unit.getId());

        PyrisTranscriptionResultDTO result = new PyrisTranscriptionResultDTO(unit.getId(), "en", List.of(new LectureTranscriptionSegment(0.0, 5.0, "Hello world", 1)));
        String resultJson = mapper.writeValueAsString(result);
        PyrisTranscriptionStatusUpdateDTO statusUpdate = new PyrisTranscriptionStatusUpdateDTO(List.of(new PyrisStageDTO("done", 1, PyrisStageState.DONE, "complete", false)),
                resultJson, unit.getId());

        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/transcription/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);

        // No crash: request returns 200 and job is cleaned up even though there was nothing to save
        assertThat(pyrisJobService.getJob(jobToken)).isNull();
        assertThat(lectureTranscriptionRepository.findByJobId(jobToken)).isEmpty();
    }

    @Test
    void testTranscriptionJobDone_malformedResult_savesFailedTranscription() throws Exception {
        String jobToken = pyrisJobService.addTranscriptionWebhookJob(course.getId(), lecture.getId(), unit.getId());
        LectureTranscription transcription = new LectureTranscription();
        transcription.setJobId(jobToken);
        transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);
        transcription.setLectureUnit(unit);
        lectureTranscriptionRepository.save(transcription);

        PyrisTranscriptionStatusUpdateDTO statusUpdate = new PyrisTranscriptionStatusUpdateDTO(List.of(new PyrisStageDTO("done", 1, PyrisStageState.DONE, "complete", false)),
                "not-valid-json{{{", unit.getId());

        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/transcription/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);

        var saved = lectureTranscriptionRepository.findByJobId(jobToken);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(pyrisJobService.getJob(jobToken)).isNull();
    }
}
