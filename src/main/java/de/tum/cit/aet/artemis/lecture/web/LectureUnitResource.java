package de.tum.cit.aet.artemis.lecture.web;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastEditorInLecture;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastEditorInLectureUnit;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastInstructorInLectureUnit;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastStudentInLectureUnit;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitCombinedStatusDTO;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitForLearningPathNodeDetailsDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureContentProcessingService;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

@Conditional(LectureEnabled.class)
@Lazy
@RestController
@RequestMapping("api/lecture/")
public class LectureUnitResource {

    private static final Logger log = LoggerFactory.getLogger(LectureUnitResource.class);

    private static final String ENTITY_NAME = "lectureUnit";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserRepository userRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureRepository lectureRepository;

    private final LectureUnitService lectureUnitService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<LectureContentProcessingService> lectureContentProcessingService;

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureTranscriptionRepository transcriptionRepository;

    public LectureUnitResource(UserRepository userRepository, LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository,
            LectureUnitService lectureUnitService, Optional<CompetencyProgressApi> competencyProgressApi, Optional<LectureContentProcessingService> lectureContentProcessingService,
            LectureUnitProcessingStateRepository processingStateRepository, LectureTranscriptionRepository transcriptionRepository) {
        this.userRepository = userRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitService = lectureUnitService;
        this.competencyProgressApi = competencyProgressApi;
        this.lectureContentProcessingService = lectureContentProcessingService;
        this.processingStateRepository = processingStateRepository;
        this.transcriptionRepository = transcriptionRepository;
    }

    /**
     * PUT /lectures/:lectureId/lecture-units-order
     *
     * @param lectureId             the id of the lecture for which to update the lecture unit order
     * @param orderedLectureUnitIds ordered list of ids of lecture units
     * @return the ResponseEntity with status 200 (OK) and with body the ordered lecture units
     */
    @PutMapping("lectures/{lectureId}/lecture-units-order")
    @EnforceAtLeastEditorInLecture
    public ResponseEntity<List<LectureUnit>> updateLectureUnitsOrder(@PathVariable Long lectureId, @RequestBody List<Long> orderedLectureUnitIds) {
        log.debug("REST request to update the order of lecture units of lecture: {}", lectureId);
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureId);

        if (lecture.getCourse() == null) {
            throw new BadRequestAlertException("Specified lecture is not part of a course", ENTITY_NAME, "courseMissing");
        }

        List<LectureUnit> lectureUnits = lecture.getLectureUnits();

        // Ensure that exactly as many lecture unit ids have been received as are currently related to the lecture
        if (orderedLectureUnitIds.size() != lectureUnits.size()) {
            throw new BadRequestAlertException("Received wrong size of lecture unit ids", ENTITY_NAME, "lectureUnitsSizeMismatch");
        }

        // Ensure that all received lecture unit ids are already part of the lecture
        if (!lectureUnits.stream().map(LectureUnit::getId).collect(Collectors.toSet()).containsAll(orderedLectureUnitIds)) {
            throw new BadRequestAlertException("Received lecture unit is not part of the lecture", ENTITY_NAME, "lectureMismatch");
        }

        lecture.reorderLectureUnits(orderedLectureUnitIds);

        lecture = lectureRepository.save(lecture);
        return ResponseEntity.ok(lecture.getLectureUnits());
    }

    /**
     * POST lectures/:lectureId/lecture-units/:lectureUnitId/complete
     *
     * @param lectureId     the id of the lecture to which the unit belongs
     * @param lectureUnitId the id of the lecture unit to mark as completed for the logged-in user
     * @param completed     true if the lecture unit should be marked as completed, false for uncompleted
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("lectures/{lectureId}/lecture-units/{lectureUnitId}/completion")
    @EnforceAtLeastStudentInLectureUnit
    public ResponseEntity<Void> completeLectureUnit(@PathVariable Long lectureUnitId, @PathVariable Long lectureId, @RequestParam("completed") boolean completed) {
        log.info("REST request to mark lecture unit as completed: {}", lectureUnitId);
        LectureUnit lectureUnit = lectureUnitRepository.findById(lectureUnitId).orElseThrow(() -> new EntityNotFoundException(ENTITY_NAME));

        if (lectureUnit.getLecture() == null || lectureUnit.getLecture().getCourse() == null) {
            throw new BadRequestAlertException("Lecture unit must be associated to a lecture of a course", ENTITY_NAME, "lectureOrCourseMissing");
        }

        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Requested lecture unit is not part of the specified lecture", ENTITY_NAME, "lectureIdMismatch");
        }

        if (!lectureUnit.isVisibleToStudents()) {
            throw new BadRequestAlertException("Requested lecture unit is not yet visible for students", ENTITY_NAME, "lectureUnitNotReleased");
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();

        lectureUnitService.setLectureUnitCompletion(lectureUnit, user, completed);
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectForParticipantAsync(lectureUnit, user));

        return ResponseEntity.ok().build();
    }

    /**
     * DELETE lectures/:lectureId/lecture-units/:lectureUnitId
     *
     * @param lectureId     the id of the lecture to which the unit belongs
     * @param lectureUnitId the id of the lecture unit to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("lectures/{lectureId}/lecture-units/{lectureUnitId}")
    @EnforceAtLeastInstructorInLectureUnit
    public ResponseEntity<Void> deleteLectureUnit(@PathVariable long lectureUnitId, @PathVariable Long lectureId) {
        log.info("REST request to delete lecture unit: {}", lectureUnitId);
        LectureUnit lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);
        if (lectureUnit.getLecture() == null || lectureUnit.getLecture().getCourse() == null) {
            throw new BadRequestAlertException("Lecture unit must be associated to a lecture of a course", ENTITY_NAME, "lectureOrCourseMissing");
        }
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Requested lecture unit is not part of the specified lecture", ENTITY_NAME, "lectureIdMismatch");
        }

        String lectureUnitName = lectureUnit.getName();
        if (lectureUnitName == null) {
            lectureUnitName = "lectureUnitWithoutName";
        }
        lectureUnitService.removeLectureUnit(lectureUnit);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, lectureUnitName)).build();
    }

    /**
     * GET /lecture-units/:lectureUnitId/for-learning-path-node-details : Gets lecture unit for the details view of a learning path node.
     *
     * @param lectureUnitId the id of the lecture unit that should be fetched
     * @return the ResponseEntity with status 200 (OK)
     */
    @GetMapping("lecture-units/{lectureUnitId}/for-learning-path-node-details")
    @EnforceAtLeastStudentInLectureUnit
    public ResponseEntity<LectureUnitForLearningPathNodeDetailsDTO> getLectureUnitForLearningPathNodeDetails(@PathVariable long lectureUnitId) {
        log.info("REST request to get lecture unit for learning path node details with id: {}", lectureUnitId);
        LectureUnit lectureUnit = lectureUnitRepository.findById(lectureUnitId).orElseThrow();
        return ResponseEntity.ok(LectureUnitForLearningPathNodeDetailsDTO.of(lectureUnit));
    }

    /**
     * GET /lecture-units/:lectureUnitId : get the lecture unit with the given id.
     *
     * @param lectureUnitId the id of the lecture unit that should be fetched
     * @return the ResponseEntity with status 200 (OK) and the lecture unit in the body, or with status 404 (Not Found) if the lecture unit could not be found
     */
    @GetMapping("lecture-units/{lectureUnitId}")
    @EnforceAtLeastStudentInLectureUnit
    public ResponseEntity<LectureUnit> getLectureUnitById(@PathVariable @Valid long lectureUnitId) {
        log.debug("REST request to get lecture unit with id: {}", lectureUnitId);
        var lectureUnit = lectureUnitRepository.findByIdWithCompletedUsersElseThrow(lectureUnitId);
        lectureUnit.setCompleted(lectureUnit.isCompletedFor(userRepository.getUser()));
        return ResponseEntity.ok(lectureUnit);
    }

    /**
     * GET /lectures/:lectureId/lecture-units/statuses
     * Gets the combined processing and transcription status for all attachment video units in a lecture.
     * This bulk endpoint reduces the number of HTTP requests from 2N to 1 when loading the lecture unit management view.
     *
     * @param lectureId the id of the lecture
     * @return the ResponseEntity with status 200 (OK) and the list of combined statuses
     */
    @GetMapping("lectures/{lectureId}/lecture-units/statuses")
    @EnforceAtLeastEditorInLecture
    public ResponseEntity<List<LectureUnitCombinedStatusDTO>> getUnitStatuses(@PathVariable Long lectureId) {
        log.debug("REST request to get combined statuses for all lecture units in lecture: {}", lectureId);

        // If processing is not enabled (Iris/Nebula disabled), return empty list
        // This prevents the UI from showing "Awaiting Processing" when processing will never happen
        if (lectureContentProcessingService.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);

        // Bulk fetch processing states for all units in the lecture
        List<LectureUnitProcessingState> processingStates = processingStateRepository.findByLectureId(lectureId);
        Map<Long, LectureUnitProcessingState> stateByUnitId = processingStates.stream().collect(toMap(s -> s.getLectureUnit().getId(), identity(), (a, b) -> a));

        // Bulk fetch transcriptions for all units in the lecture
        List<LectureTranscription> transcriptions = transcriptionRepository.findByLectureId(lectureId);
        Map<Long, LectureTranscription> transcriptionByUnitId = transcriptions.stream().collect(toMap(t -> t.getLectureUnit().getId(), identity(), (a, b) -> a));

        // Build status list for attachment video units only
        List<LectureUnitCombinedStatusDTO> statuses = lecture.getLectureUnits().stream().filter(AttachmentVideoUnit.class::isInstance).map(unit -> {
            var processingState = stateByUnitId.get(unit.getId());
            var transcription = transcriptionByUnitId.get(unit.getId());
            var transcriptionStatus = transcription != null ? transcription.getTranscriptionStatus() : null;
            return LectureUnitCombinedStatusDTO.of(unit.getId(), processingState, transcriptionStatus);
        }).toList();

        return ResponseEntity.ok(statuses);
    }

    /**
     * POST /lectures/:lectureId/lecture-units/:lectureUnitId/retry-processing
     * Retries processing for a failed lecture unit.
     *
     * @param lectureId     the id of the lecture to which the unit belongs
     * @param lectureUnitId the id of the lecture unit to retry processing for
     * @return the ResponseEntity with status 200 (OK) and the updated combined status
     */
    @PostMapping("lectures/{lectureId}/lecture-units/{lectureUnitId}/retry-processing")
    @EnforceAtLeastEditorInLectureUnit
    public ResponseEntity<LectureUnitCombinedStatusDTO> retryProcessing(@PathVariable Long lectureId, @PathVariable Long lectureUnitId) {
        log.info("REST request to retry processing of lecture unit: {}", lectureUnitId);
        LectureUnit lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);

        if (lectureUnit.getLecture() == null || !lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Requested lecture unit is not part of the specified lecture", ENTITY_NAME, "lectureIdMismatch");
        }

        if (!(lectureUnit instanceof AttachmentVideoUnit attachmentVideoUnit)) {
            throw new BadRequestAlertException("Only attachment video units can have processing retried", ENTITY_NAME, "wrongUnitType");
        }

        if (lectureContentProcessingService.isEmpty()) {
            throw new AccessForbiddenException("Lecture content processing is not enabled");
        }

        // Check that the unit is in a failed state
        var currentState = lectureContentProcessingService.get().getProcessingState(lectureUnitId);
        if (currentState.isEmpty() || currentState.get().getPhase() != ProcessingPhase.FAILED) {
            throw new BadRequestAlertException("Cannot retry processing for a unit that is not in failed state", ENTITY_NAME, "notInFailedState");
        }

        // Retry processing - creates initial state synchronously
        var newState = lectureContentProcessingService.get().retryProcessing(attachmentVideoUnit);

        // Return the new state, or the existing failed state if preflight failed (services unavailable)
        var transcription = transcriptionRepository.findByLectureUnit_Id(lectureUnitId).orElse(null);
        var transcriptionStatus = transcription != null ? transcription.getTranscriptionStatus() : null;
        var stateToReturn = newState != null ? newState : currentState.get();
        return ResponseEntity.ok(LectureUnitCombinedStatusDTO.of(lectureUnitId, stateToReturn, transcriptionStatus));
    }

}
