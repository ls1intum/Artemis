package de.tum.cit.aet.artemis.lecture;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitCombinedStatusDTO;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitForLearningPathNodeDetailsDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.repository.TextUnitRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LectureUnitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "lectureunitintegration";

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private LectureUnitCompletionRepository lectureUnitCompletionRepository;

    @Autowired
    private LectureUnitProcessingStateRepository lectureUnitProcessingStateRepository;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    private Lecture lecture1;

    private TextUnit textUnit;

    private TextUnit textUnit2;

    private TextUnit textUnit3;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, 1);
        Course course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        var sortedLectures = course1.getLectures().stream().sorted(Comparator.comparing(Lecture::getId)).toList();
        this.lecture1 = sortedLectures.getFirst();
        var lecture2 = sortedLectures.get(1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        this.textUnit = lectureUtilService.createTextUnit(lecture1);
        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(lecture1, false);
        OnlineUnit onlineUnit = lectureUtilService.createOnlineUnit(lecture1);
        this.textUnit2 = lectureUtilService.createTextUnit(lecture2);
        // textUnit3 belongs to a different lecture to test invalid lecture-unit combinations
        this.textUnit3 = lectureUtilService.createTextUnit(lecture2);

        lectureUtilService.addLectureUnitsToLecture(lecture2, List.of(textUnit2, textUnit3));
        this.lecture1 = lectureUtilService.addLectureUnitsToLecture(this.lecture1, List.of(this.textUnit, onlineUnit, attachmentVideoUnit));
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
        this.textUnit = textUnitRepository.findById(this.textUnit.getId()).orElseThrow();
        this.textUnit2 = textUnitRepository.findById(textUnit2.getId()).orElseThrow();
        this.textUnit3 = textUnitRepository.findById(textUnit3.getId()).orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units-order", List.of(), HttpStatus.FORBIDDEN);
        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/0", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit() throws Exception {
        var lectureUnitId = lecture1.getLectureUnits().getFirst().getId();
        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnitId, HttpStatus.OK);
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
        assertThat(this.lecture1.getLectureUnits().stream().map(DomainObject::getId)).doesNotContain(lectureUnitId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_shouldUnlinkCompetency() throws Exception {
        var lectureUnit = lecture1.getLectureUnits().getFirst();
        var competency = competencyUtilService.createCompetency(lecture1.getCourse());
        lectureUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, lectureUnit, 1)));
        lectureRepository.save(lecture1);

        var lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture1.getId());
        assertThat(lecture.getLectureUnits().getFirst().getCompetencyLinks()).isNotEmpty();

        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnit.getId(), HttpStatus.OK);
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
        assertThat(this.lecture1.getLectureUnits().stream().map(DomainObject::getId)).doesNotContain(lectureUnit.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_shouldRemoveCompletions() throws Exception {
        var lectureUnit = lecture1.getLectureUnits().getFirst();
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        LectureUnitCompletion completion = new LectureUnitCompletion();
        completion.setLectureUnit(lectureUnit);
        completion.setUser(user);
        completion.setCompletedAt(ZonedDateTime.now().minusDays(1));
        lectureUnitCompletionRepository.save(completion);

        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lectureUnit.getId(), user.getId())).isPresent();

        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnit.getId(), HttpStatus.OK);

        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
        assertThat(this.lecture1.getLectureUnits().stream().map(DomainObject::getId)).doesNotContain(lectureUnit.getId());
        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lectureUnit.getId(), user.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void deleteLectureUnit_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        var lectureUnitId = lecture1.getLectureUnits().getFirst().getId();
        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnitId, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_notPartOfLecture_shouldReturnBadRequest() throws Exception {
        var lectureUnitId = lecture1.getLectureUnits().getFirst().getId();
        request.delete("/api/lecture/lectures/" + Integer.MAX_VALUE + "/lecture-units/" + lectureUnitId, HttpStatus.BAD_REQUEST);
    }

    /**
     * We have to make sure to reorder the list of lecture units when we delete a lecture unit to prevent hibernate
     * from entering nulls into the list to keep the order of lecture units
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_FirstLectureUnit_ShouldReorderList() throws Exception {
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture1.getId());
        assertThat(lecture.getLectureUnits()).hasSize(3);
        LectureUnit firstLectureUnit = lecture.getLectureUnits().stream().findFirst().orElseThrow();
        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + firstLectureUnit.getId(), HttpStatus.OK);
        lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture1.getId());
        assertThat(lecture.getLectureUnits()).hasSize(2).noneMatch(Objects::isNull);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLectureUnitOrder_asInstructor_shouldUpdateLectureUnitOrder() throws Exception {
        List<Long> newlyOrderedList = lecture1.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toCollection(ArrayList::new));
        Collections.swap(newlyOrderedList, 0, 1);
        List<LectureUnit> returnedList = request.putWithResponseBodyList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, LectureUnit.class,
                HttpStatus.OK);
        assertThat(returnedList.getFirst().getId()).isEqualTo(newlyOrderedList.getFirst());
        assertThat(returnedList.get(1).getId()).isEqualTo(newlyOrderedList.get(1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLectureUnitOrder_wrongSizeOfIds_shouldReturnBadRequest() throws Exception {
        List<Long> newlyOrderedList = lecture1.getLectureUnits().stream().map(DomainObject::getId).skip(1).toList();
        request.put("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLectureUnitOrder_newTextUnitInOrderedList_shouldReturnBadRequest() throws Exception {
        List<Long> newlyOrderedList = lecture1.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toCollection(ArrayList::new));
        // textUnit3 is not in specified lecture
        newlyOrderedList.set(1, this.textUnit3.getId());
        request.put("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLectureUnitOrder_asInstructorWithWrongLectureId_shouldReturnForbidden() throws Exception {
        request.put("/api/lecture/lectures/" + 0L + "/lecture-units-order", List.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void updateLectureUnitOrder_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        request.put("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units-order", List.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void setLectureUnitCompletion() throws Exception {
        // Set lecture unit as completed for current user
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().getFirst().getId() + "/completion?completed=true",
                null, HttpStatus.OK, null);

        this.lecture1 = lectureRepository.findByIdWithAttachmentsAndLectureUnitsAndCompletionsElseThrow(lecture1.getId());
        LectureUnit lectureUnit = this.lecture1.getLectureUnits().getFirst();

        assertThat(lectureUnit.getCompletedUsers()).isNotEmpty();
        assertThat(lectureUnit.isCompletedFor(userTestRepository.getUser())).isTrue();

        // Set lecture unit as uncompleted for user
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().getFirst().getId() + "/completion?completed=false",
                null, HttpStatus.OK, null);

        this.lecture1 = lectureRepository.findByIdWithAttachmentsAndLectureUnitsAndCompletionsElseThrow(lecture1.getId());
        lectureUnit = this.lecture1.getLectureUnits().getFirst();

        assertThat(lectureUnit.getCompletedUsers()).isEmpty();
        assertThat(lectureUnit.isCompletedFor(userTestRepository.getUser())).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void setLectureUnitCompletion_lectureUnitNotPartOfLecture_shouldReturnBadRequest() throws Exception {
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + this.textUnit2.getId() + "/completion?completed=true", null,
                HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void setLectureUnitCompletion_withoutLecture_shouldReturnBadRequest() throws Exception {
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + this.textUnit3.getId() + "/completion?completed=true", null,
                HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void setLectureUnitCompletion_lectureUnitNotVisible_shouldReturnBadRequest() throws Exception {
        this.textUnit.setReleaseDate(ZonedDateTime.now().plusDays(1));
        textUnitRepository.save(this.textUnit);
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + this.textUnit.getId() + "/completion?completed=true", null,
                HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void setLectureUnitCompletion_shouldReturnForbidden() throws Exception {
        // User is not in same course as lecture unit
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().getFirst().getId() + "/completion?completed=true",
                null, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLectureUnitForLearningPathNodeDetailsAsStudentOfCourse() throws Exception {
        final var result = request.get("/api/lecture/lecture-units/" + textUnit.getId() + "/for-learning-path-node-details", HttpStatus.OK,
                LectureUnitForLearningPathNodeDetailsDTO.class);
        assertThat(result).isEqualTo(LectureUnitForLearningPathNodeDetailsDTO.of(textUnit));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testGetLectureUnitForLearningPathNodeDetailsAsStudentNotInCourse() throws Exception {
        request.get("/api/lecture/lecture-units/" + textUnit.getId() + "/for-learning-path-node-details", HttpStatus.FORBIDDEN, LectureUnitForLearningPathNodeDetailsDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getProcessingStatus_asEditor_shouldSucceed() throws Exception {
        // Get processing status for an attachment video unit
        var attachmentVideoUnit = lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).findFirst().orElseThrow();
        // The endpoint exists but may return 403 if processing service is not available
        // This tests the endpoint is accessible to editors
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + attachmentVideoUnit.getId() + "/processing-status", HttpStatus.OK, Object.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getProcessingStatus_asStudent_shouldBeForbidden() throws Exception {
        var attachmentVideoUnit = lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).findFirst().orElseThrow();
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + attachmentVideoUnit.getId() + "/processing-status", HttpStatus.FORBIDDEN, Object.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void retryProcessing_wrongUnitType_shouldReturnBadRequest() throws Exception {
        // Try to retry processing for a text unit (not attachment video unit)
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + textUnit.getId() + "/retry-processing", null, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void retryProcessing_asStudent_shouldBeForbidden() throws Exception {
        var attachmentVideoUnit = lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).findFirst().orElseThrow();
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + attachmentVideoUnit.getId() + "/retry-processing", null, HttpStatus.FORBIDDEN,
                null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void retryProcessing_whenInFailedState_shouldSucceed() throws Exception {
        // Get the attachment video unit
        var attachmentVideoUnit = (AttachmentVideoUnit) lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).findFirst().orElseThrow();

        // Create a processing state with FAILED phase
        LectureUnitProcessingState processingState = new LectureUnitProcessingState(attachmentVideoUnit);
        processingState.setPhase(ProcessingPhase.FAILED);
        processingState.setRetryCount(3);
        processingState.setErrorKey("artemisApp.processing.error.transcriptionFailed");
        lectureUnitProcessingStateRepository.save(processingState);

        // Call the retry processing endpoint - it should succeed (return 200)
        // Note: The processing itself may not actually change the state if the unit has no video/PDF to process
        // but the endpoint should still return OK for a unit in FAILED state
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + attachmentVideoUnit.getId() + "/retry-processing", null, HttpStatus.OK, null);

        // The endpoint returns OK if the unit was in FAILED state
        // The actual processing behavior depends on the unit's content (video/PDF)
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void retryProcessing_whenNotInFailedState_shouldReturnBadRequest() throws Exception {
        // Get the attachment video unit
        var attachmentVideoUnit = lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).findFirst().orElseThrow();

        // Create a processing state with TRANSCRIBING phase (not FAILED)
        LectureUnitProcessingState processingState = new LectureUnitProcessingState(attachmentVideoUnit);
        processingState.setPhase(ProcessingPhase.TRANSCRIBING);
        processingState.setRetryCount(0);
        lectureUnitProcessingStateRepository.save(processingState);

        // Call the retry processing endpoint - should return BAD_REQUEST
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + attachmentVideoUnit.getId() + "/retry-processing", null,
                HttpStatus.BAD_REQUEST, null);

        // Verify the processing state was not changed
        var unchangedState = lectureUnitProcessingStateRepository.findByLectureUnit_Id(attachmentVideoUnit.getId()).orElseThrow();
        assertThat(unchangedState.getPhase()).isEqualTo(ProcessingPhase.TRANSCRIBING);
    }

    // --- Tests for the bulk status endpoint: GET /lectures/{lectureId}/lecture-units/statuses ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getUnitStatuses_asEditor_shouldReturnStatusesForVideoUnits() throws Exception {
        // Get the attachment video unit from the lecture
        var attachmentVideoUnit = (AttachmentVideoUnit) lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).findFirst().orElseThrow();

        // Create a processing state for the video unit
        LectureUnitProcessingState processingState = new LectureUnitProcessingState(attachmentVideoUnit);
        processingState.setPhase(ProcessingPhase.TRANSCRIBING);
        processingState.setRetryCount(1);
        processingState.setStartedAt(ZonedDateTime.now().minusMinutes(5));
        lectureUnitProcessingStateRepository.save(processingState);

        // Create a transcription for the video unit
        LectureTranscription transcription = new LectureTranscription();
        transcription.setLectureUnit(attachmentVideoUnit);
        transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
        transcription.setLanguage("en");
        lectureTranscriptionRepository.save(transcription);

        // Call the bulk endpoint
        List<LectureUnitCombinedStatusDTO> statuses = request.getList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/statuses", HttpStatus.OK,
                LectureUnitCombinedStatusDTO.class);

        // Verify the response contains only the video unit
        assertThat(statuses).hasSize(1);
        var status = statuses.getFirst();
        assertThat(status.lectureUnitId()).isEqualTo(attachmentVideoUnit.getId());
        assertThat(status.processingPhase()).isEqualTo(ProcessingPhase.TRANSCRIBING);
        assertThat(status.retryCount()).isEqualTo(1);
        assertThat(status.startedAt()).isNotNull();
        assertThat(status.transcriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getUnitStatuses_asStudent_shouldBeForbidden() throws Exception {
        request.getList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/statuses", HttpStatus.FORBIDDEN, LectureUnitCombinedStatusDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getUnitStatuses_asTutor_shouldBeForbidden() throws Exception {
        request.getList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/statuses", HttpStatus.FORBIDDEN, LectureUnitCombinedStatusDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void getUnitStatuses_asInstructorNotInCourse_shouldBeForbidden() throws Exception {
        request.getList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/statuses", HttpStatus.FORBIDDEN, LectureUnitCombinedStatusDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getUnitStatuses_withNoProcessingStateOrTranscription_shouldReturnIdleStatus() throws Exception {
        // Get the attachment video unit from the lecture - no processing state or transcription exists
        var attachmentVideoUnit = (AttachmentVideoUnit) lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).findFirst().orElseThrow();

        // Clean up any existing processing states and transcriptions for this unit
        lectureUnitProcessingStateRepository.findByLectureUnit_Id(attachmentVideoUnit.getId()).ifPresent(lectureUnitProcessingStateRepository::delete);
        lectureTranscriptionRepository.findByLectureUnit_Id(attachmentVideoUnit.getId()).ifPresent(lectureTranscriptionRepository::delete);

        // Call the bulk endpoint
        List<LectureUnitCombinedStatusDTO> statuses = request.getList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/statuses", HttpStatus.OK,
                LectureUnitCombinedStatusDTO.class);

        // Verify the response contains the video unit with IDLE status
        assertThat(statuses).hasSize(1);
        var status = statuses.getFirst();
        assertThat(status.lectureUnitId()).isEqualTo(attachmentVideoUnit.getId());
        assertThat(status.processingPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(status.retryCount()).isZero();
        assertThat(status.startedAt()).isNull();
        assertThat(status.processingErrorKey()).isNull();
        assertThat(status.transcriptionStatus()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getUnitStatuses_withFailedProcessingState_shouldReturnErrorKey() throws Exception {
        // Get the attachment video unit from the lecture
        var attachmentVideoUnit = (AttachmentVideoUnit) lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).findFirst().orElseThrow();

        // Create a failed processing state with error key
        LectureUnitProcessingState processingState = new LectureUnitProcessingState(attachmentVideoUnit);
        processingState.setPhase(ProcessingPhase.FAILED);
        processingState.setRetryCount(3);
        processingState.setErrorKey("artemisApp.processing.error.transcriptionFailed");
        lectureUnitProcessingStateRepository.save(processingState);

        // Call the bulk endpoint
        List<LectureUnitCombinedStatusDTO> statuses = request.getList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/statuses", HttpStatus.OK,
                LectureUnitCombinedStatusDTO.class);

        // Verify the response contains the error information
        assertThat(statuses).hasSize(1);
        var status = statuses.getFirst();
        assertThat(status.lectureUnitId()).isEqualTo(attachmentVideoUnit.getId());
        assertThat(status.processingPhase()).isEqualTo(ProcessingPhase.FAILED);
        assertThat(status.retryCount()).isEqualTo(3);
        assertThat(status.processingErrorKey()).isEqualTo("artemisApp.processing.error.transcriptionFailed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getUnitStatuses_withOnlyTranscription_shouldReturnIdleProcessingWithTranscriptionStatus() throws Exception {
        // Get the attachment video unit from the lecture
        var attachmentVideoUnit = (AttachmentVideoUnit) lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).findFirst().orElseThrow();

        // Clean up any existing processing states
        lectureUnitProcessingStateRepository.findByLectureUnit_Id(attachmentVideoUnit.getId()).ifPresent(lectureUnitProcessingStateRepository::delete);

        // Create only a transcription (no processing state)
        LectureTranscription transcription = new LectureTranscription();
        transcription.setLectureUnit(attachmentVideoUnit);
        transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);
        transcription.setLanguage("de");
        lectureTranscriptionRepository.save(transcription);

        // Call the bulk endpoint
        List<LectureUnitCombinedStatusDTO> statuses = request.getList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/statuses", HttpStatus.OK,
                LectureUnitCombinedStatusDTO.class);

        // Verify the response shows IDLE processing but with transcription status
        assertThat(statuses).hasSize(1);
        var status = statuses.getFirst();
        assertThat(status.lectureUnitId()).isEqualTo(attachmentVideoUnit.getId());
        assertThat(status.processingPhase()).isEqualTo(ProcessingPhase.IDLE);
        assertThat(status.transcriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getUnitStatuses_shouldFilterOutNonVideoUnits() throws Exception {
        // The lecture has text unit, online unit, and attachment video unit
        // Only the attachment video unit should be returned

        List<LectureUnitCombinedStatusDTO> statuses = request.getList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/statuses", HttpStatus.OK,
                LectureUnitCombinedStatusDTO.class);

        // Verify only video units are returned (not text or online units)
        assertThat(statuses).hasSize(1);

        // Verify the returned ID is the attachment video unit's ID
        var attachmentVideoUnit = lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).findFirst().orElseThrow();
        assertThat(statuses.getFirst().lectureUnitId()).isEqualTo(attachmentVideoUnit.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getUnitStatuses_forNonExistentLecture_shouldBeForbidden() throws Exception {
        // Note: Returns 403 (not 404) to avoid leaking information about resource existence
        // This is the expected behavior from @EnforceAtLeastEditorInLecture annotation
        request.getList("/api/lecture/lectures/" + 999999L + "/lecture-units/statuses", HttpStatus.FORBIDDEN, LectureUnitCombinedStatusDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getUnitStatuses_withMultipleVideoUnits_shouldReturnAllStatuses() throws Exception {
        // Add a second attachment video unit to the lecture
        AttachmentVideoUnit attachmentVideoUnit2 = lectureUtilService.createAttachmentVideoUnit(lecture1, false);
        this.lecture1 = lectureUtilService.addLectureUnitsToLecture(this.lecture1, List.of(attachmentVideoUnit2));

        // Get all video units
        var videoUnits = lecture1.getLectureUnits().stream().filter(lu -> lu instanceof AttachmentVideoUnit).map(lu -> (AttachmentVideoUnit) lu).toList();
        assertThat(videoUnits).hasSize(2);

        // Create different states for each
        LectureUnitProcessingState state1 = new LectureUnitProcessingState(videoUnits.get(0));
        state1.setPhase(ProcessingPhase.INGESTING);
        lectureUnitProcessingStateRepository.save(state1);

        LectureUnitProcessingState state2 = new LectureUnitProcessingState(videoUnits.get(1));
        state2.setPhase(ProcessingPhase.DONE);
        lectureUnitProcessingStateRepository.save(state2);

        // Call the bulk endpoint
        List<LectureUnitCombinedStatusDTO> statuses = request.getList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/statuses", HttpStatus.OK,
                LectureUnitCombinedStatusDTO.class);

        // Verify both video units are returned
        assertThat(statuses).hasSize(2);

        var statusIds = statuses.stream().map(LectureUnitCombinedStatusDTO::lectureUnitId).collect(Collectors.toSet());
        assertThat(statusIds).containsExactlyInAnyOrder(videoUnits.get(0).getId(), videoUnits.get(1).getId());

        // Verify each has the correct phase
        var statusMap = statuses.stream().collect(toMap(LectureUnitCombinedStatusDTO::lectureUnitId, s -> s));
        assertThat(statusMap.get(videoUnits.get(0).getId()).processingPhase()).isEqualTo(ProcessingPhase.INGESTING);
        assertThat(statusMap.get(videoUnits.get(1).getId()).processingPhase()).isEqualTo(ProcessingPhase.DONE);
    }
}
