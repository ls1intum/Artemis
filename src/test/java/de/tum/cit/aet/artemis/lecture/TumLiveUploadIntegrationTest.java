package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentUpdateIntent;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TumLiveRecordingCleanup;
import de.tum.cit.aet.artemis.lecture.repository.TumLiveRecordingCleanupRepository;
import de.tum.cit.aet.artemis.lecture.repository.TumLiveUnitUploadRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TumLiveUploadIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "tumliveupload";

    private static final String WATCH_PAGE = "https://tum.live/w/algo/55";

    private static final String YOUTUBE = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Autowired
    private TumLiveUnitUploadRepository uploadRepository;

    @Autowired
    private TumLiveRecordingCleanupRepository cleanupRepository;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private LectureTestRepository lectureRepository;

    private Lecture lecture;

    private AttachmentVideoUnit unit;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 1, 1);
        lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);
        unit = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture);
        unit.setVideoSource(null);
        unit = attachmentVideoUnitRepository.saveWithVideoSource(unit);
    }

    private String videoSource() {
        return attachmentVideoUnitRepository.findById(unit.getId()).orElseThrow().getVideoSource();
    }

    private boolean queued(String ref) {
        return cleanupRepository.findAll().stream().map(TumLiveRecordingCleanup::getExternalRef).anyMatch(ref::equals);
    }

    private String ref(String name) {
        return name + "-" + unit.getId() + "-reference";
    }

    @Test
    void firstUploadIsAttachedToAUnitWithoutVideo() {
        var now = ZonedDateTime.now();
        var started = uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        var repeated = uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("other"), now);
        assertThat(repeated.getPendingRef()).as("one pending upload per unit").isEqualTo(started.getPendingRef());
        uploadRepository.recordPendingStream(unit.getId(), ref("first"), 55);

        assertThat(uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now)).isTrue();

        assertThat(videoSource()).isEqualTo(WATCH_PAGE);
        var upload = uploadRepository.findByLectureUnitId(unit.getId()).orElseThrow();
        assertThat(upload.getCurrentRef()).isEqualTo(ref("first"));
        assertThat(upload.getCurrentStreamId()).isEqualTo(55);
        assertThat(upload.getPendingRef()).isNull();
        assertThat(queued(ref("first"))).isFalse();
    }

    @Test
    void replacementQueuesTheOldLectureWhenTheNewOneIsAttached() {
        var now = ZonedDateTime.now();
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now);
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("second"), now);
        assertThat(queued(ref("first"))).as("the old video stays until the new one is ready").isFalse();

        assertThat(uploadRepository.attachPending(unit.getId(), ref("second"), "https://tum.live/w/algo/56", now)).isTrue();

        assertThat(videoSource()).isEqualTo("https://tum.live/w/algo/56");
        assertThat(queued(ref("first"))).isTrue();
        assertThat(cleanupRepository.findAll()).allSatisfy(cleanup -> assertThat(cleanup.getCourseId()).as("for a later grant of the course").isEqualTo(courseId()));
    }

    @Test
    void anUploadPendingThroughAnEarlierGrantIsReplacedByANewOne() {
        var now = ZonedDateTime.now();
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);

        // The course was disconnected from TUM.Live and connected again.
        var started = uploadRepository.startPending(unit.getId(), 17, 24, "algo", ref("second"), now);

        assertThat(started.getPendingRef()).isEqualTo(ref("second"));
        assertThat(started.getPendingGrantId()).isEqualTo(24);
        assertThat(queued(ref("first"))).isTrue();
    }

    @Test
    void aLongReasonIsCutToItsColumn() {
        var now = ZonedDateTime.now();
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);

        uploadRepository.discardPending(unit.getId(), ref("first"), "TUM.Live could not convert the video: " + "x".repeat(255), now);

        assertThat(uploadRepository.findByLectureUnitId(unit.getId()).orElseThrow().getLastError()).hasSize(255).startsWith("TUM.Live could not convert the video: ");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void anEditFormOpenedBeforeTheUploadDoesNotTakeTheUploadedVideoAway() throws Exception {
        var now = ZonedDateTime.now();
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now);

        // The form was loaded before the upload, without a video.
        updateUnit("Renamed", null, null);
        assertThat(videoSource()).isEqualTo(WATCH_PAGE);

        // A form that shows the uploaded video changes and removes it as usual.
        updateUnit("Renamed", YOUTUBE, WATCH_PAGE);
        assertThat(videoSource()).isEqualTo(YOUTUBE);
        updateUnit("Renamed", null, YOUTUBE);
        assertThat(videoSource()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void anUpdateWithoutTheLoadedLinkEditsAgainstTheCurrentOne() throws Exception {
        unit.setVideoSource(WATCH_PAGE);
        unit = attachmentVideoUnitRepository.saveWithVideoSource(unit);

        // A client from before loadedVideoSource does not send it.
        sendUpdate(mapper.createObjectNode().put("name", "Renamed").put("videoSource", YOUTUBE).put("attachmentUpdateIntent", AttachmentUpdateIntent.NO_FILE_CHANGE.name()));

        assertThat(videoSource()).isEqualTo(YOUTUBE);
    }

    @Test
    void anUploadIsForgottenOnlyWhenTheLinkShowsAnotherVideo() {
        var now = ZonedDateTime.now();
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        uploadRepository.recordPendingStream(unit.getId(), ref("first"), 55);
        uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now);

        uploadRepository.forgetCurrentUnlessShown(unit.getId(), 55L);
        assertThat(uploadRepository.findUploadedUnitsByCourseId(courseId())).as("the link still shows the upload").hasSize(1);

        uploadRepository.forgetCurrentUnlessShown(unit.getId(), null);
        assertThat(uploadRepository.findUploadedUnitsByCourseId(courseId())).isEmpty();
        assertThat(uploadRepository.findByLectureUnitId(unit.getId()).orElseThrow().getCurrentRef()).isNull();
        assertThat(queued(ref("first"))).as("the recording stays in TUM.Live").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void aRemovedVideoLinkCanBeReplacedLater() throws Exception {
        updateUnit("Renamed", YOUTUBE, null);
        assertThat(videoSource()).isEqualTo(YOUTUBE);

        // The form sends an emptied link field as an empty string.
        updateUnit("Renamed", "", YOUTUBE);
        assertThat(videoSource()).isNull();

        updateUnit("Renamed", WATCH_PAGE, null);
        assertThat(videoSource()).isEqualTo(WATCH_PAGE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void aBlankLinkStoredByAnOlderSaveCountsAsNone() throws Exception {
        unit.setVideoSource("");
        unit = attachmentVideoUnitRepository.saveWithVideoSource(unit);

        updateUnit("Renamed", YOUTUBE, null);

        assertThat(videoSource()).isEqualTo(YOUTUBE);
    }

    @Test
    void savingALectureWithItsUnitsWritesNoVideoUrl() {
        var now = ZonedDateTime.now();
        Lecture loadedLecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lecture.getId());
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now);

        // As reordering the units does.
        lectureRepository.save(loadedLecture);

        assertThat(videoSource()).isEqualTo(WATCH_PAGE);
    }

    /** An update from a form loaded with {@code loadedVideoSource}; the client sends an empty one as "". */
    private void updateUnit(String name, String videoSource, String loadedVideoSource) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("name", name).put("videoSource", videoSource).put("loadedVideoSource", loadedVideoSource == null ? "" : loadedVideoSource)
                .put("attachmentUpdateIntent", AttachmentUpdateIntent.NO_FILE_CHANGE.name());
        sendUpdate(body);
    }

    private void sendUpdate(ObjectNode body) throws Exception {
        var part = new MockMultipartFile("attachmentVideoUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsBytes(body));
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/lecture/lectures/" + lecture.getId() + "/attachment-video-units/" + unit.getId()).file(part)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)).andExpect(status().isOk());
    }

    private long courseId() {
        return lecture.getCourse().getId();
    }

    @Test
    void manualEditWinsOverAConvertingUpload() {
        var now = ZonedDateTime.now();
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        unit.setVideoSource("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        attachmentVideoUnitRepository.saveWithVideoSource(unit);

        assertThat(uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now)).isFalse();

        assertThat(videoSource()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(queued(ref("first"))).isTrue();
        var upload = uploadRepository.findByLectureUnitId(unit.getId()).orElseThrow();
        assertThat(upload.getPendingRef()).isNull();
        assertThat(upload.getLastError()).isEqualTo(TumLiveUnitUploadRepository.REPLACED_BY_EDIT);
    }

    @Test
    void discardTakesExactlyThePendingReference() {
        var now = ZonedDateTime.now();
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now);
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("second"), now);

        assertThat(uploadRepository.discardPending(unit.getId(), ref("first"), null, now)).as("not pending").isFalse();
        assertThat(uploadRepository.discardPending(unit.getId(), ref("second"), null, now)).isTrue();
        assertThat(uploadRepository.discardPending(unit.getId(), ref("second"), null, now)).as("repeated").isFalse();

        assertThat(queued(ref("second"))).isTrue();
        assertThat(queued(ref("first"))).as("the attached video is never discarded").isFalse();
        assertThat(videoSource()).isEqualTo(WATCH_PAGE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void deletingAUnitQueuesItsUploadsAsChosen() throws Exception {
        var now = ZonedDateTime.now();
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now);
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("second"), now);

        mockMvc.perform(delete("/api/lecture/lectures/" + lecture.getId() + "/lecture-units/" + unit.getId()).param("deleteTumLiveRecordings", "true")).andExpect(status().isOk());

        assertThat(queued(ref("first"))).isTrue();
        assertThat(queued(ref("second"))).isTrue();
        assertThat(uploadRepository.findByLectureUnitId(unit.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void deletingALectureKeepsAttachedRecordingsByDefault() throws Exception {
        var now = ZonedDateTime.now();
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now);
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("second"), now);

        mockMvc.perform(delete("/api/lecture/lectures/" + lecture.getId())).andExpect(status().isOk());

        assertThat(queued(ref("first"))).as("kept in TUM.Live").isFalse();
        assertThat(queued(ref("second"))).as("a pending upload is never kept").isTrue();
    }

    @Test
    void rowsOfUnitsDeletedWithoutAskingArePurged() {
        var now = ZonedDateTime.now();
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now);
        attachmentVideoUnitRepository.deleteById(unit.getId());

        uploadRepository.deleteWithoutUnitAndPendingUpload();

        assertThat(uploadRepository.findByLectureUnitId(unit.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void editorsSeeTheStateAndCannotUploadWithoutConnection() throws Exception {
        mockMvc.perform(get("/api/lecture/lecture-units/" + unit.getId() + "/tumlive-upload")).andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(false));
        mockMvc.perform(post("/api/lecture/lecture-units/" + unit.getId() + "/tumlive-upload")).andExpect(status().isConflict());
        mockMvc.perform(delete("/api/lecture/lecture-units/" + unit.getId() + "/tumlive-upload").param("ref", ref("none"))).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void editorsSeeWhichUnitsHaveAnUploadedVideo() throws Exception {
        var now = ZonedDateTime.now();
        long courseId = lecture.getCourse().getId();
        mockMvc.perform(get("/api/lecture/courses/" + courseId + "/tumlive-uploads")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        // Also while it converts: it may be attached before the unit is deleted.
        uploadRepository.startPending(unit.getId(), 17, 23, "algo", ref("first"), now);
        mockMvc.perform(get("/api/lecture/courses/" + courseId + "/tumlive-uploads")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        uploadRepository.attachPending(unit.getId(), ref("first"), WATCH_PAGE, now);

        mockMvc.perform(get("/api/lecture/courses/" + courseId + "/tumlive-uploads")).andExpect(status().isOk()).andExpect(jsonPath("$[0].lectureId").value(lecture.getId()))
                .andExpect(jsonPath("$[0].lectureUnitId").value(unit.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentsCannotUpload() throws Exception {
        mockMvc.perform(get("/api/lecture/lecture-units/" + unit.getId() + "/tumlive-upload")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/lecture/lecture-units/" + unit.getId() + "/tumlive-upload")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/lecture/lecture-units/" + unit.getId() + "/tumlive-upload").param("ref", ref("none"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/lecture/courses/" + lecture.getCourse().getId() + "/tumlive-uploads")).andExpect(status().isForbidden());
    }
}
