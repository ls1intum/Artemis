package de.tum.cit.aet.artemis.lecture.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.TumLiveRecordingCleanup;
import de.tum.cit.aet.artemis.lecture.domain.TumLiveUnitUpload;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveUploadStartDTO;
import de.tum.cit.aet.artemis.lecture.dto.TumLiveUploadStateDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.TumLiveRecordingCleanupRepository;
import de.tum.cit.aet.artemis.lecture.repository.TumLiveUnitUploadRepository;
import de.tum.cit.aet.artemis.videosource.api.GocastBindingApi;
import de.tum.cit.aet.artemis.videosource.domain.event.GocastGrantRevokingEvent;
import de.tum.cit.aet.artemis.videosource.dto.GocastRecordingDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastUploadConnectionDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastUploadTargetDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastIntegrationException;

/**
 * Uploads the video of an attachment video unit to the connected TUM.Live course. The editor's browser sends the file straight to TUM.Live; Artemis attaches
 * the converted recording in the background (see {@link TumLiveUploadScheduler}), so nothing depends on the page staying open. Each upload goes into a new
 * TUM.Live lecture; a replaced, failed or discarded one is deleted there through the cleanup queue.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Service
public class TumLiveUploadService {

    private static final Logger log = LoggerFactory.getLogger(TumLiveUploadService.class);

    private static final String ENTITY_NAME = "tumLiveUpload";

    private static final Duration BLOCKED_RETRY = Duration.ofDays(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TumLiveUnitUploadRepository uploadRepository;

    private final TumLiveRecordingCleanupRepository cleanupRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final Optional<GocastBindingApi> gocastBindingApi;

    private final Optional<LectureContentProcessingService> contentProcessingService;

    private final Duration maxAge;

    public TumLiveUploadService(TumLiveUnitUploadRepository uploadRepository, TumLiveRecordingCleanupRepository cleanupRepository,
            AttachmentVideoUnitRepository attachmentVideoUnitRepository, Optional<GocastBindingApi> gocastBindingApi,
            Optional<LectureContentProcessingService> contentProcessingService, @Value("${artemis.tum-live.upload-max-age:PT6H}") Duration maxAge) {
        this.uploadRepository = uploadRepository;
        this.cleanupRepository = cleanupRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.gocastBindingApi = gocastBindingApi;
        this.contentProcessingService = contentProcessingService;
        this.maxAge = maxAge;
    }

    /**
     * Starts an upload for a unit, or hands out a new upload URL for the pending one if its transfer has not started yet.
     *
     * @param unit the unit, with its lecture and course
     * @return the upload's reference and where to send the file
     */
    public TumLiveUploadStartDTO start(AttachmentVideoUnit unit) {
        GocastBindingApi api = gocastBindingApi.orElseThrow(() -> notConnected());
        GocastUploadConnectionDTO connection = api.findUploadConnection(unit.getLecture().getCourse().getId()).orElseThrow(TumLiveUploadService::notConnected);
        TumLiveUnitUpload upload = uploadRepository.startPending(unit.getId(), connection.integrationId(), connection.grantId(), connection.courseSlug(), newReference(),
                ZonedDateTime.now());
        GocastUploadTargetDTO target;
        try {
            target = api.createUpload(upload.getPendingGrantId(), upload.getPendingRef(), title(unit));
        }
        catch (GocastIntegrationException exception) {
            if (exception.getUpstreamStatus().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw new ConflictException("The pending upload is already being transferred. Discard it to start another one.", ENTITY_NAME, "tumLiveUploadInProgress");
            }
            if (exception.getUpstreamStatus().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw notConnected();
            }
            throw exception;
        }
        uploadRepository.recordPendingStream(unit.getId(), upload.getPendingRef(), target.streamId());
        return new TumLiveUploadStartDTO(upload.getPendingRef(), target.uploadUrl());
    }

    /**
     * The upload state of a unit for its editors, with the conversion progress of a pending upload read from TUM.Live.
     *
     * @param unit the unit, with its lecture and course
     * @return the state
     */
    public TumLiveUploadStateDTO getState(AttachmentVideoUnit unit) {
        boolean connected = gocastBindingApi.flatMap(api -> api.findUploadConnection(unit.getLecture().getCourse().getId())).isPresent();
        Optional<TumLiveUnitUpload> upload = uploadRepository.findByLectureUnitId(unit.getId());
        if (upload.isEmpty()) {
            return new TumLiveUploadStateDTO(connected, null, null, false, 0, null, null);
        }
        TumLiveUnitUpload state = upload.get();
        Optional<GocastRecordingDTO> pending = Optional.empty();
        if (state.getPendingStreamId() != null && gocastBindingApi.isPresent()) {
            try {
                pending = Optional.of(gocastBindingApi.get().getRecording(state.getPendingGrantId(), state.getPendingStreamId()));
            }
            catch (GocastIntegrationException exception) {
                log.info("Could not read the conversion state of unit {}: {}", unit.getId(), exception.getUpstreamStatus());
            }
        }
        return new TumLiveUploadStateDTO(connected, state.getPendingRef(), state.getPendingSince(), pending.map(GocastRecordingDTO::converting).orElse(false),
                pending.map(GocastRecordingDTO::progress).orElse(0), state.getCurrentRef(), state.getLastError());
    }

    /**
     * Gives up the pending upload of a unit, for example after its transfer failed. Only that exact upload, and only while it is pending.
     *
     * @param unit the unit
     * @param ref  the upload's reference
     */
    public void discard(AttachmentVideoUnit unit, String ref) {
        uploadRepository.discardPending(unit.getId(), ref, null, ZonedDateTime.now());
    }

    /**
     * Prepares units for deletion: their pending uploads are deleted in TUM.Live, their attached recordings only if the instructor chose so.
     *
     * @param lectureUnitIds   the units about to be deleted
     * @param deleteRecordings whether to delete the attached TUM.Live recordings too
     */
    public void releaseForDeletion(Collection<Long> lectureUnitIds, boolean deleteRecordings) {
        ZonedDateTime now = ZonedDateTime.now();
        lectureUnitIds.forEach(id -> uploadRepository.releaseForDeletion(id, deleteRecordings, now));
    }

    /**
     * Attaches converted uploads, and gives up failed and overdue ones.
     */
    public void processPendingUploads() {
        if (gocastBindingApi.isEmpty()) {
            return;
        }
        for (TumLiveUnitUpload upload : uploadRepository.findTop50ByPendingRefIsNotNullOrderByPendingCheckedAtAsc()) {
            try {
                processPendingUpload(gocastBindingApi.get(), upload);
            }
            catch (RuntimeException exception) {
                log.warn("Could not process the TUM.Live upload of unit {}", upload.getLectureUnitId(), exception);
            }
        }
    }

    private void processPendingUpload(GocastBindingApi api, TumLiveUnitUpload upload) {
        long unitId = upload.getLectureUnitId();
        String ref = upload.getPendingRef();
        ZonedDateTime now = ZonedDateTime.now();
        if (upload.getPendingSince().plus(maxAge).isBefore(now)) {
            uploadRepository.discardPending(unitId, ref, "The upload did not finish in time.", now);
            return;
        }
        uploadRepository.markPendingChecked(upload.getId(), ref, now);
        if (upload.getPendingStreamId() == null) {
            // Starting it did not finish; the maximum age gives it up.
            return;
        }
        GocastRecordingDTO recording;
        try {
            recording = api.getRecording(upload.getPendingGrantId(), upload.getPendingStreamId());
        }
        catch (GocastIntegrationException exception) {
            if (exception.getUpstreamStatus().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                uploadRepository.discardPending(unitId, ref, "The lecture in TUM.Live no longer exists.", now);
            }
            return;
        }
        if (recording.failed()) {
            uploadRepository.discardPending(unitId, ref, "TUM.Live could not convert the video: " + recording.error(), now);
            return;
        }
        if (!recording.ready()) {
            return;
        }
        // The slug of the connection the upload was made through: the course may be connected to another TUM.Live course by now.
        String watchUrl = api.watchUrl(upload.getPendingCourseSlug(), upload.getPendingStreamId());
        if (uploadRepository.attachPending(unitId, ref, watchUrl, now)) {
            // Read again: the processing compares the stored video URL, which the attachment just changed.
            attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(unitId)
                    .ifPresent(attached -> contentProcessingService.ifPresent(service -> service.triggerProcessing(attached)));
        }
    }

    /**
     * Deletes uploaded lectures Artemis no longer uses in TUM.Live. An entry stays until TUM.Live confirms; a refused one is kept with the reason and retried daily.
     * When the grant it was uploaded through is revoked, the course's current grant of the same integration deletes it, if the course was connected again.
     */
    public void processCleanups() {
        if (gocastBindingApi.isEmpty()) {
            return;
        }
        GocastBindingApi api = gocastBindingApi.get();
        ZonedDateTime now = ZonedDateTime.now();
        for (TumLiveRecordingCleanup cleanup : cleanupRepository.findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(now)) {
            try {
                deleteUpload(api, cleanup);
                cleanupRepository.deleteConfirmed(cleanup.getId());
            }
            catch (GocastIntegrationException exception) {
                HttpStatus status = HttpStatus.resolve(exception.getUpstreamStatus().value());
                if (status == HttpStatus.NOT_FOUND || status == HttpStatus.CONFLICT) {
                    log.warn("TUM.Live refused to delete the uploaded lecture {} of grant {}: {}; retrying daily", cleanup.getExternalRef(), cleanup.getGrantId(), status);
                    cleanupRepository.postpone(cleanup.getId(), now.plus(BLOCKED_RETRY), "TUM.Live answered " + status);
                }
                // Anything else is TUM.Live being unavailable: the entry stays due and is retried on the next run.
            }
        }
        uploadRepository.deleteWithoutUnitAndPendingUpload();
    }

    private void deleteUpload(GocastBindingApi api, TumLiveRecordingCleanup cleanup) {
        try {
            api.deleteUpload(cleanup.getGrantId(), cleanup.getExternalRef());
        }
        catch (GocastIntegrationException exception) {
            Optional<Long> currentGrant = api.findUploadConnection(cleanup.getCourseId())
                    .filter(connection -> connection.integrationId() == cleanup.getIntegrationId() && connection.grantId() != cleanup.getGrantId())
                    .map(GocastUploadConnectionDTO::grantId);
            if (currentGrant.isEmpty() || !exception.getUpstreamStatus().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw exception;
            }
            api.deleteUpload(currentGrant.get(), cleanup.getExternalRef());
        }
    }

    /**
     * Before a course is disconnected from TUM.Live, deletes there what its uploads through this grant left unattached, while the grant still works: pending
     * uploads and the queued deletions. Attached recordings stay. A failure is logged and does not stop the disconnection; the queue keeps what is left.
     *
     * @param event the grant about to be revoked
     */
    @EventListener
    public void deleteUnattachedUploadsBeforeRevocation(GocastGrantRevokingEvent event) {
        if (gocastBindingApi.isEmpty()) {
            return;
        }
        try {
            ZonedDateTime now = ZonedDateTime.now();
            for (TumLiveUnitUpload upload : uploadRepository.findByCourseIdAndPendingGrantId(event.courseId(), event.grantId())) {
                uploadRepository.discardPending(upload.getLectureUnitId(), upload.getPendingRef(), "The course was disconnected from TUM.Live.", now);
            }
            for (TumLiveRecordingCleanup cleanup : cleanupRepository.findByGrantId(event.grantId())) {
                try {
                    gocastBindingApi.get().deleteUpload(event.grantId(), cleanup.getExternalRef());
                    cleanupRepository.deleteConfirmed(cleanup.getId());
                }
                catch (GocastIntegrationException exception) {
                    log.warn("Could not delete the uploaded lecture {} before the TUM.Live grant {} is revoked: {}", cleanup.getExternalRef(), event.grantId(),
                            exception.getUpstreamStatus());
                    HttpStatus status = HttpStatus.resolve(exception.getUpstreamStatus().value());
                    if (status != HttpStatus.NOT_FOUND && status != HttpStatus.CONFLICT) {
                        // TUM.Live is unavailable: the disconnection should not wait for every remaining entry to time out.
                        break;
                    }
                }
            }
        }
        catch (RuntimeException exception) {
            log.warn("Could not delete the unattached TUM.Live uploads of course {} before disconnecting it", event.courseId(), exception);
        }
    }

    private static String newReference() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String title(AttachmentVideoUnit unit) {
        String unitName = unit.getName() != null && !unit.getName().isBlank() ? unit.getName() : "Unit " + unit.getId();
        String lectureTitle = unit.getLecture().getTitle();
        String title = lectureTitle != null && !lectureTitle.isBlank() ? lectureTitle + ": " + unitName : unitName;
        return title.codePointCount(0, title.length()) > 255 ? title.substring(0, title.offsetByCodePoints(0, 255)) : title;
    }

    private static ConflictException notConnected() {
        return new ConflictException("The course is not connected to TUM.Live", ENTITY_NAME, "tumLiveNotConnected");
    }
}
