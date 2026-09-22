package de.tum.cit.aet.artemis.atlas.service;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceCourseConsent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEnabledCourse;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceResearchExportAudit;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceResearchExportFilter;
import de.tum.cit.aet.artemis.atlas.dto.ScienceCourseConsentDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceEnabledCourseDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceResearchExportAuditDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceResearchExportRequestDTO;
import de.tum.cit.aet.artemis.atlas.repository.ScienceCourseConsentRepository;
import de.tum.cit.aet.artemis.atlas.repository.ScienceEnabledCourseRepository;
import de.tum.cit.aet.artemis.atlas.repository.ScienceEventRepository;
import de.tum.cit.aet.artemis.atlas.repository.ScienceResearchExportAuditRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseSelectionDTO;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class ScienceCourseService {

    private static final String ENTITY_NAME = "science";

    private static final int RESEARCH_EXPORT_PAGE_SIZE = 1000;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ScienceEnabledCourseRepository scienceEnabledCourseRepository;

    private final ScienceCourseConsentRepository scienceCourseConsentRepository;

    private final ScienceEventRepository scienceEventRepository;

    private final ScienceResearchExportAuditRepository scienceResearchExportAuditRepository;

    private final ScienceEventService scienceEventService;

    private final AuthorizationCheckService authorizationCheckService;

    private final TempFileUtilService tempFileUtilService;

    public ScienceCourseService(CourseRepository courseRepository, UserRepository userRepository, ScienceEnabledCourseRepository scienceEnabledCourseRepository,
            ScienceCourseConsentRepository scienceCourseConsentRepository, ScienceEventRepository scienceEventRepository,
            ScienceResearchExportAuditRepository scienceResearchExportAuditRepository, ScienceEventService scienceEventService, AuthorizationCheckService authorizationCheckService,
            TempFileUtilService tempFileUtilService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.scienceEnabledCourseRepository = scienceEnabledCourseRepository;
        this.scienceCourseConsentRepository = scienceCourseConsentRepository;
        this.scienceEventRepository = scienceEventRepository;
        this.scienceResearchExportAuditRepository = scienceResearchExportAuditRepository;
        this.scienceEventService = scienceEventService;
        this.authorizationCheckService = authorizationCheckService;
        this.tempFileUtilService = tempFileUtilService;
    }

    /**
     * Returns all courses that are or were configured for science data collection.
     *
     * @return the science-enabled course history
     */
    public List<ScienceEnabledCourseDTO> getEnabledCourseHistory() {
        return scienceEnabledCourseRepository.findAllByOrderByLastModifiedDateDesc().stream().map(ScienceEnabledCourseDTO::of).toList();
    }

    /**
     * Returns every course an administrator can enable science data collection for.
     *
     * @return all courses, most recent first
     */
    public List<CourseSelectionDTO> getSelectableCourses() {
        return courseRepository.findAllForSelection();
    }

    /**
     * Enables science data collection for a course.
     *
     * @param courseId the id of the course
     * @return the enabled-course entry
     */
    public ScienceEnabledCourseDTO enableCourse(long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        ScienceEnabledCourse enabledCourse = scienceEnabledCourseRepository.findByCourseId(courseId).orElseGet(ScienceEnabledCourse::new);
        enabledCourse.setCourse(course);
        enabledCourse.setActive(true);
        return ScienceEnabledCourseDTO.of(scienceEnabledCourseRepository.save(enabledCourse), course);
    }

    /**
     * Disables science data collection for a course while keeping the historical entry.
     *
     * @param courseId the id of the course
     * @return the updated enabled-course entry
     */
    public ScienceEnabledCourseDTO disableCourse(long courseId) {
        ScienceEnabledCourse enabledCourse = scienceEnabledCourseRepository.findByCourseId(courseId)
                .orElseThrow(() -> new BadRequestAlertException("Course was never enabled for science collection", ENTITY_NAME, "scienceCourseNotEnabled"));
        Course course = enabledCourse.getCourse();
        enabledCourse.setActive(false);
        return ScienceEnabledCourseDTO.of(scienceEnabledCourseRepository.save(enabledCourse), course);
    }

    /**
     * Returns science consent states for courses the current user may access or has historical consent for.
     *
     * @return the current user's course consent states
     */
    public List<ScienceCourseConsentDTO> getConsentsForCurrentUser() {
        User user = userRepository.getUserWithCourseRolesAndAuthorities();
        List<ScienceEnabledCourse> enabledCourses = scienceEnabledCourseRepository.findAllByOrderByLastModifiedDateDesc();
        Set<Long> courseIds = enabledCourses.stream().map(enabledCourse -> enabledCourse.getCourse().getId()).collect(Collectors.toSet());
        if (courseIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ScienceCourseConsent> consentsByCourseId = scienceCourseConsentRepository.findAllByUserIdAndCourseIdIn(user.getId(), courseIds).stream()
                .collect(Collectors.toMap(consent -> consent.getCourse().getId(), consent -> consent));
        return enabledCourses.stream().map(enabledCourse -> {
            Course course = enabledCourse.getCourse();
            ScienceCourseConsent consent = consentsByCourseId.get(course.getId());
            if (!mayAccessConsent(course, user, consent)) {
                return null;
            }
            return new ScienceCourseConsentDTO(course.getId(), course.getTitle(), course.getShortName(), consent == null ? null : consent.isActive(), enabledCourse.isActive());
        }).filter(Objects::nonNull).toList();
    }

    private boolean mayAccessConsent(Course course, User user, ScienceCourseConsent consent) {
        return consent != null || authorizationCheckService.isAtLeastStudentInCourse(course, user);
    }

    private void checkMayCreateOrActivateConsent(Course course, User user, ScienceCourseConsent consent, boolean active, boolean scienceEnabled) {
        if ((consent == null || active) && !authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
        if ((consent == null || active) && !scienceEnabled) {
            throw new BadRequestAlertException("Course is not enabled for science collection", ENTITY_NAME, "scienceCourseNotEnabled");
        }
    }

    private void checkMayDeleteScienceData(Course course, User user, ScienceCourseConsent consent) {
        if (consent == null && !authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
    }

    /**
     * Stores the current user's science consent decision for a course.
     *
     * @param courseId the id of the course
     * @param active   whether the user consents to science data collection
     * @return the updated consent state
     */
    public ScienceCourseConsentDTO saveConsentForCurrentUser(long courseId, boolean active) {
        User user = userRepository.getUser();
        Course course = courseRepository.findByIdElseThrow(courseId);
        ScienceCourseConsent consent = scienceCourseConsentRepository.findByUserIdAndCourseId(user.getId(), courseId).orElse(null);
        boolean scienceEnabled = scienceEnabledCourseRepository.existsByCourseIdAndActiveTrue(courseId);
        checkMayCreateOrActivateConsent(course, user, consent, active, scienceEnabled);
        if (consent == null) {
            consent = new ScienceCourseConsent();
        }
        consent.setUser(user);
        consent.setCourse(course);
        consent.setActive(active);

        // Whether a marker is needed is decided against the timeline rather than against the consent row this call is
        // about to overwrite. That matters because the two writes are ordered rather than atomic - a transaction would
        // have to be declared here, and boundaries belong in repositories. Reading the last recorded decision makes the
        // pair idempotent instead: saving the same decision twice records nothing the second time, and a marker lost to
        // a failed write is written by the next save, which comparing against the previous consent state could not do.
        ScienceEventType desiredMarker = active ? ScienceEventType.SCIENCE__OPT_IN : ScienceEventType.SCIENCE__OPT_OUT;
        boolean markerMissing = scienceEventService.latestConsentDecision(user.getLogin(), courseId).filter(desiredMarker::equals).isEmpty();

        // Of the two writes, the one that leaves the more privacy-preserving state on its own goes first: an opt-in
        // records the marker before collection can start, so a failed consent write leaves a marker for a window in
        // which nothing was collected; an opt-out stops collection before recording it, so a failed marker write still
        // stops collection and the marker is repaired by the next save.
        if (active) {
            if (markerMissing) {
                scienceEventService.logAuditEvent(user.getLogin(), desiredMarker, courseId);
            }
            return toConsentDTO(courseId, course, scienceCourseConsentRepository.save(consent), scienceEnabled);
        }
        ScienceCourseConsent savedConsent = scienceCourseConsentRepository.save(consent);
        if (markerMissing) {
            scienceEventService.logAuditEvent(user.getLogin(), desiredMarker, courseId);
        }
        return toConsentDTO(courseId, course, savedConsent, scienceEnabled);
    }

    private static ScienceCourseConsentDTO toConsentDTO(long courseId, Course course, ScienceCourseConsent consent, boolean scienceEnabled) {
        return new ScienceCourseConsentDTO(courseId, course.getTitle(), course.getShortName(), consent.isActive(), scienceEnabled);
    }

    /**
     * Deletes the current user's interaction science data for a course while retaining audit events.
     *
     * @param courseId the id of the course
     */
    public void deleteScienceDataForCurrentUser(long courseId) {
        User user = userRepository.getUser();
        Course course = courseRepository.findByIdElseThrow(courseId);
        ScienceCourseConsent consent = scienceCourseConsentRepository.findByUserIdAndCourseId(user.getId(), courseId).orElse(null);
        checkMayDeleteScienceData(course, user, consent);
        // The deletion commits before the marker is written, so a failure between them leaves data deleted without a
        // record of it rather than a record of a deletion that never happened.
        int deletedEvents = scienceEventRepository.deleteInteractionEventsByIdentityAndCourseId(user.getLogin(), courseId, ScienceEventType.AUDIT_EVENT_TYPES);
        if (deletedEvents > 0) {
            scienceEventService.logAuditEvent(user.getLogin(), ScienceEventType.SCIENCE__DATA_DELETED, courseId);
        }
    }

    /**
     * Creates a research CSV export for the selected courses, dates, and event types.
     *
     * @param request the export filter and purpose
     * @return the generated CSV export file
     */
    public ScienceResearchExport createResearchExport(ScienceResearchExportRequestDTO request) {
        validateResearchExportRequest(request);
        // An absent filter means every type; an explicitly empty one was rejected by the validation above.
        Set<ScienceEventType> eventTypes = request.eventTypes() == null ? EnumSet.allOf(ScienceEventType.class) : request.eventTypes();
        ScienceResearchExport export = createScienceEventCsv(request, eventTypes, UUID.randomUUID().toString());
        ScienceResearchExportAudit audit = new ScienceResearchExportAudit();
        audit.setPurpose(request.purpose().trim());
        audit.setFilter(new ScienceResearchExportFilter(new TreeSet<>(request.courseIds()), request.from() == null ? null : request.from().toString(),
                request.to() == null ? null : request.to().toString(), EnumSet.copyOf(eventTypes)));
        audit.setFileChecksum(export.fileChecksum());
        try {
            scienceResearchExportAuditRepository.save(audit);
        }
        catch (RuntimeException e) {
            deleteQuietly(export.path());
            throw e;
        }
        return export;
    }

    /**
     * Returns the audit history for research exports.
     *
     * @return the export audit history
     */
    public List<ScienceResearchExportAuditDTO> getResearchExportAudits() {
        return scienceResearchExportAuditRepository.findAllByOrderByCreatedDateDesc().stream().map(ScienceResearchExportAuditDTO::of).toList();
    }

    /**
     * Checks what bean validation on the request cannot: that the date range reads forwards. The presence of a course
     * and of a purpose is declared on {@link ScienceResearchExportRequestDTO} and enforced at the boundary, so that a
     * request which cannot be audited never reaches the point of generating a file.
     */
    private static void validateResearchExportRequest(ScienceResearchExportRequestDTO request) {
        ZonedDateTime from = request.from();
        ZonedDateTime to = request.to();
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestAlertException("The export start date must be before the end date", ENTITY_NAME, "scienceExportInvalidDateRange");
        }
        if (request.eventTypes() != null && request.eventTypes().isEmpty()) {
            // Omitting the field means every type; asking for none of them is a different request, and answering it
            // with the broadest possible export is the opposite of what was asked for.
            throw new BadRequestAlertException("Select at least one event type, or omit the filter for all of them", ENTITY_NAME, "scienceExportMissingEventTypes");
        }
    }

    private ScienceResearchExport createScienceEventCsv(ScienceResearchExportRequestDTO request, Set<ScienceEventType> eventTypes, String exportSalt) {
        String[] header = { "identity", "timestamp", "event_type", "course_id", "resource_id" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header).get();
        Path exportFile = null;
        try {
            exportFile = tempFileUtilService.createTempFile("science-research-export-", ".csv");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestOutputStream digestOutputStream = new DigestOutputStream(Files.newOutputStream(exportFile), digest);
                    OutputStreamWriter writer = new OutputStreamWriter(digestOutputStream, StandardCharsets.UTF_8);
                    CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
                Optional<Long> maxEventId = scienceEventRepository.findMaxIdForResearchExport(request.courseIds(), request.from(), request.to(), eventTypes);
                ZonedDateTime lastTimestamp = null;
                Long lastId = null;
                List<ScienceEvent> scienceEvents = List.of();
                if (maxEventId.isPresent()) {
                    do {
                        scienceEvents = scienceEventRepository.findNextPageForResearchExport(request.courseIds(), request.from(), request.to(), eventTypes, maxEventId.get(),
                                lastTimestamp, lastId, RESEARCH_EXPORT_PAGE_SIZE);
                        for (var scienceEvent : scienceEvents) {
                            lastTimestamp = scienceEvent.getTimestamp();
                            lastId = scienceEvent.getId();
                            printer.printRecord(pseudonymizeIdentity(scienceEvent.getIdentity(), exportSalt), scienceEvent.getTimestamp(), scienceEvent.getType(),
                                    scienceEvent.getCourseId(), scienceEvent.getResourceId());
                        }
                    }
                    while (scienceEvents.size() == RESEARCH_EXPORT_PAGE_SIZE);
                }
            }
            return new ScienceResearchExport(exportFile, HexFormat.of().formatHex(digest.digest()), Files.size(exportFile));
        }
        catch (IOException | NoSuchAlgorithmException e) {
            // Nothing streams a failed export, so nothing would ever close and delete it. Every attempt would otherwise
            // leave a partial CSV behind, which is the one case where the leftovers hold real interaction data.
            deleteQuietly(exportFile);
            throw new BadRequestAlertException("Could not create science export", ENTITY_NAME, "scienceExportFailed");
        }
        catch (RuntimeException e) {
            // Same reasoning, for a failure that is not ours to translate: a database error raised part-way through the
            // paged read leaves exactly the same half-written file.
            deleteQuietly(exportFile);
            throw e;
        }
    }

    /** Best-effort cleanup of a generated export; the caller's own failure stays the reported cause. */
    private static void deleteQuietly(@Nullable Path exportFile) {
        if (exportFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(exportFile);
        }
        catch (IOException ignored) {
            // Nothing useful to do here, and the original failure is the one worth reporting.
        }
    }

    /**
     * Replaces the login with a hash salted per export.
     * <p>
     * The salt is generated for one export and then discarded, so nobody - including this server - can map a row back
     * to an account afterwards. Two consequences follow, and both are deliberate: an export cannot be reproduced or
     * checked against the checksum recorded for it, and an erasure request cannot be honoured against a file already
     * handed to a researcher, because there is no way to say which rows are that person's.
     * <p>
     * The unlinkability this buys is partial. The exported timestamp, event type, course and resource are unchanged, so
     * two exports covering the same period can still be matched on those. Anyone widening what the export carries
     * should revisit whether the salt ought to be stored on the audit record instead.
     */
    private static String pseudonymizeIdentity(String identity, String exportSalt) {
        return sha256Hex((exportSalt + ":" + identity).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record ScienceResearchExport(Path path, String fileChecksum, long contentLength) {
    }
}
