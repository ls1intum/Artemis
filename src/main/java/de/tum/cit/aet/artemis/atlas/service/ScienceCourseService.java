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
import org.springframework.data.domain.PageRequest;
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
import de.tum.cit.aet.artemis.course.dto.CourseForRoleAssignmentDTO;
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

    private final AuthorizationCheckService authorizationCheckService;

    private final TempFileUtilService tempFileUtilService;

    public ScienceCourseService(CourseRepository courseRepository, UserRepository userRepository, ScienceEnabledCourseRepository scienceEnabledCourseRepository,
            ScienceCourseConsentRepository scienceCourseConsentRepository, ScienceEventRepository scienceEventRepository,
            ScienceResearchExportAuditRepository scienceResearchExportAuditRepository, AuthorizationCheckService authorizationCheckService,
            TempFileUtilService tempFileUtilService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.scienceEnabledCourseRepository = scienceEnabledCourseRepository;
        this.scienceCourseConsentRepository = scienceCourseConsentRepository;
        this.scienceEventRepository = scienceEventRepository;
        this.scienceResearchExportAuditRepository = scienceResearchExportAuditRepository;
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
     * Searches the courses an administrator can enable science data collection for. Shares the course search of the
     * role assignment, which ranks short-name matches first and escapes the wildcards of the term.
     *
     * @param searchTerm the text to look for in the title and the short name of the courses
     * @param size       the maximum number of courses to return
     * @return the matching courses, best match first, or none for a blank term
     */
    public List<CourseForRoleAssignmentDTO> searchSelectableCourses(String searchTerm, int size) {
        return courseRepository.searchForRoleAssignment(searchTerm, PageRequest.of(0, size));
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
        // The consent and the marker recording it are stored as one unit, one decision of the course after the other.
        ScienceCourseConsent savedConsent = scienceCourseConsentRepository.saveDecision(user, course, active, scienceEventRepository::save);
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
        scienceEventRepository.deleteInteractionEventsAndRecordDeletion(user.getLogin(), courseId);
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
                if (maxEventId.isPresent()) {
                    // One course at a time: the keyset order (timestamp, id) then matches the (course_id, timestamp, id)
                    // index, so every page is an index range. Across several courses each page would sort all remaining
                    // rows instead, which makes a large export quadratic.
                    for (long courseId : new TreeSet<>(request.courseIds())) {
                        ZonedDateTime lastTimestamp = null;
                        Long lastId = null;
                        List<ScienceEvent> scienceEvents;
                        do {
                            scienceEvents = scienceEventRepository.findNextPageForResearchExport(Set.of(courseId), request.from(), request.to(), eventTypes, maxEventId.get(),
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
