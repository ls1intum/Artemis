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
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    private final TransactionTemplate transactionTemplate;

    public ScienceCourseService(CourseRepository courseRepository, UserRepository userRepository, ScienceEnabledCourseRepository scienceEnabledCourseRepository,
            ScienceCourseConsentRepository scienceCourseConsentRepository, ScienceEventRepository scienceEventRepository,
            ScienceResearchExportAuditRepository scienceResearchExportAuditRepository, ScienceEventService scienceEventService, AuthorizationCheckService authorizationCheckService,
            TempFileUtilService tempFileUtilService, PlatformTransactionManager transactionManager) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.scienceEnabledCourseRepository = scienceEnabledCourseRepository;
        this.scienceCourseConsentRepository = scienceCourseConsentRepository;
        this.scienceEventRepository = scienceEventRepository;
        this.scienceResearchExportAuditRepository = scienceResearchExportAuditRepository;
        this.scienceEventService = scienceEventService;
        this.authorizationCheckService = authorizationCheckService;
        this.tempFileUtilService = tempFileUtilService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
        return ScienceEnabledCourseDTO.of(scienceEnabledCourseRepository.save(enabledCourse));
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
        enabledCourse.setActive(false);
        return ScienceEnabledCourseDTO.of(scienceEnabledCourseRepository.save(enabledCourse));
    }

    /**
     * Returns the current user's science consent state for a course.
     *
     * @param courseId the id of the course
     * @return the consent state for the course
     */
    public ScienceCourseConsentDTO getConsentForCurrentUser(long courseId) {
        User user = userRepository.getUser();
        Course course = courseRepository.findByIdElseThrow(courseId);
        ScienceCourseConsent consent = scienceCourseConsentRepository.findByUserIdAndCourseId(user.getId(), courseId).orElse(null);
        checkMayAccessConsent(course, user, consent);
        boolean scienceEnabled = scienceEnabledCourseRepository.existsByCourseIdAndActiveTrue(courseId);
        return consent == null ? new ScienceCourseConsentDTO(courseId, course.getTitle(), course.getShortName(), null, null, scienceEnabled)
                : new ScienceCourseConsentDTO(courseId, course.getTitle(), course.getShortName(), consent.isActive(), consent.getLastModifiedDate(), scienceEnabled);
    }

    /**
     * Returns science consent states for courses the current user may access or has historical consent for.
     *
     * @return the current user's course consent states
     */
    public List<ScienceCourseConsentDTO> getConsentsForCurrentUser() {
        User user = userRepository.getUser();
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
            return consent == null ? new ScienceCourseConsentDTO(course.getId(), course.getTitle(), course.getShortName(), null, null, enabledCourse.isActive())
                    : new ScienceCourseConsentDTO(course.getId(), course.getTitle(), course.getShortName(), consent.isActive(), consent.getLastModifiedDate(),
                            enabledCourse.isActive());
        }).filter(java.util.Objects::nonNull).toList();
    }

    private boolean mayAccessConsent(Course course, User user, ScienceCourseConsent consent) {
        return consent != null || authorizationCheckService.isAtLeastStudentInCourse(course, user);
    }

    private void checkMayAccessConsent(Course course, User user, ScienceCourseConsent consent) {
        if (!mayAccessConsent(course, user, consent)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
    }

    private void checkMayCreateOrActivateConsent(Course course, User user, ScienceCourseConsent consent, boolean active) {
        if ((consent == null || active) && !authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
        if ((consent == null || active) && !scienceEnabledCourseRepository.existsByCourseIdAndActiveTrue(course.getId())) {
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
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            User user = userRepository.getUser();
            Course course = courseRepository.findByIdElseThrow(courseId);
            ScienceCourseConsent consent = scienceCourseConsentRepository.findByUserIdAndCourseId(user.getId(), courseId).orElse(null);
            checkMayCreateOrActivateConsent(course, user, consent, active);
            boolean scienceEnabled = scienceEnabledCourseRepository.existsByCourseIdAndActiveTrue(courseId);
            boolean isNewConsent = consent == null;
            boolean previousActive = consent != null && consent.isActive();
            if (consent == null) {
                consent = new ScienceCourseConsent();
            }
            consent.setUser(user);
            consent.setCourse(course);
            consent.setActive(active);
            ScienceCourseConsent savedConsent = scienceCourseConsentRepository.save(consent);
            if (active && (isNewConsent || !previousActive)) {
                scienceEventService.logAuditEvent(user.getLogin(), ScienceEventType.SCIENCE__OPT_IN, courseId);
            }
            if (!active && (isNewConsent || previousActive)) {
                scienceEventService.logAuditEvent(user.getLogin(), ScienceEventType.SCIENCE__OPT_OUT, courseId);
            }
            return new ScienceCourseConsentDTO(courseId, course.getTitle(), course.getShortName(), savedConsent.isActive(), savedConsent.getLastModifiedDate(), scienceEnabled);
        }));
    }

    /**
     * Deletes the current user's interaction science data for a course while retaining audit events.
     *
     * @param courseId the id of the course
     */
    public void deleteScienceDataForCurrentUser(long courseId) {
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.getUser();
            Course course = courseRepository.findByIdElseThrow(courseId);
            ScienceCourseConsent consent = scienceCourseConsentRepository.findByUserIdAndCourseId(user.getId(), courseId).orElse(null);
            checkMayDeleteScienceData(course, user, consent);
            scienceEventRepository.deleteInteractionEventsByIdentityAndCourseId(user.getLogin(), courseId, ScienceEventService.SCIENCE_AUDIT_EVENT_TYPES);
            scienceEventService.logAuditEvent(user.getLogin(), ScienceEventType.SCIENCE__DATA_DELETED, courseId);
        });
    }

    /**
     * Creates a research CSV export for the selected courses, dates, and event types.
     *
     * @param request the export filter and purpose
     * @return the generated CSV export file
     */
    public ScienceResearchExport createResearchExport(ScienceResearchExportRequestDTO request) {
        validateResearchExportRequest(request);
        Set<ScienceEventType> eventTypes = request.eventTypes() == null || request.eventTypes().isEmpty() ? EnumSet.allOf(ScienceEventType.class) : request.eventTypes();
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
            try {
                Files.deleteIfExists(export.path());
            }
            catch (IOException ignored) {
                // Best-effort cleanup. Keep the original persistence exception as the failure cause.
            }
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

    private static void validateResearchExportRequest(ScienceResearchExportRequestDTO request) {
        if (request == null || request.courseIds() == null || request.courseIds().isEmpty()) {
            throw new BadRequestAlertException("At least one course must be selected", ENTITY_NAME, "scienceExportMissingCourses");
        }
        if (request.purpose() == null || request.purpose().trim().isBlank()) {
            throw new BadRequestAlertException("A research purpose must be provided", ENTITY_NAME, "scienceExportMissingPurpose");
        }
        ZonedDateTime from = request.from();
        ZonedDateTime to = request.to();
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestAlertException("The export start date must be before the end date", ENTITY_NAME, "scienceExportInvalidDateRange");
        }
    }

    private ScienceResearchExport createScienceEventCsv(ScienceResearchExportRequestDTO request, Set<ScienceEventType> eventTypes, String exportSalt) {
        String[] header = { "identity", "timestamp", "event_type", "course_id", "resource_id" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header).get();
        try {
            Path exportFile = tempFileUtilService.createTempFile("science-research-export-", ".csv");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestOutputStream digestOutputStream = new DigestOutputStream(Files.newOutputStream(exportFile), digest);
                    OutputStreamWriter writer = new OutputStreamWriter(digestOutputStream, StandardCharsets.UTF_8);
                    CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
                Long maxEventId = scienceEventRepository.findMaxIdForResearchExport(request.courseIds(), request.from(), request.to(), eventTypes);
                ZonedDateTime lastTimestamp = null;
                Long lastId = null;
                List<ScienceEvent> scienceEvents = List.of();
                if (maxEventId != null) {
                    do {
                        scienceEvents = scienceEventRepository.findNextPageForResearchExport(request.courseIds(), request.from(), request.to(), eventTypes, maxEventId,
                                lastTimestamp, lastId, PageRequest.ofSize(RESEARCH_EXPORT_PAGE_SIZE));
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
            throw new BadRequestAlertException("Could not create science export", ENTITY_NAME, "scienceExportFailed");
        }
    }

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
