package de.tum.cit.aet.artemis.atlas.service;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceCourseConsent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEnabledCourse;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceResearchExportAudit;
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
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class ScienceCourseService {

    private static final String ENTITY_NAME = "science";

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ScienceEnabledCourseRepository scienceEnabledCourseRepository;

    private final ScienceCourseConsentRepository scienceCourseConsentRepository;

    private final ScienceEventRepository scienceEventRepository;

    private final ScienceResearchExportAuditRepository scienceResearchExportAuditRepository;

    private final ScienceEventService scienceEventService;

    private final AuthorizationCheckService authorizationCheckService;

    public ScienceCourseService(CourseRepository courseRepository, UserRepository userRepository, ScienceEnabledCourseRepository scienceEnabledCourseRepository,
            ScienceCourseConsentRepository scienceCourseConsentRepository, ScienceEventRepository scienceEventRepository,
            ScienceResearchExportAuditRepository scienceResearchExportAuditRepository, ScienceEventService scienceEventService,
            AuthorizationCheckService authorizationCheckService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.scienceEnabledCourseRepository = scienceEnabledCourseRepository;
        this.scienceCourseConsentRepository = scienceCourseConsentRepository;
        this.scienceEventRepository = scienceEventRepository;
        this.scienceResearchExportAuditRepository = scienceResearchExportAuditRepository;
        this.scienceEventService = scienceEventService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Returns all courses that are or were configured for science data collection.
     *
     * @return the science-enabled course history
     */
    @Transactional(readOnly = true)
    public List<ScienceEnabledCourseDTO> getEnabledCourseHistory() {
        return scienceEnabledCourseRepository.findAllByOrderByLastModifiedDateDesc().stream().map(ScienceEnabledCourseDTO::of).toList();
    }

    /**
     * Enables science data collection for a course.
     *
     * @param courseId the id of the course
     * @return the enabled-course entry
     */
    @Transactional
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
    @Transactional
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
        if (consent != null) {
            return;
        }
        if (!scienceEnabledCourseRepository.existsByCourseIdAndActiveTrue(course.getId())) {
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
    @Transactional
    public ScienceCourseConsentDTO saveConsentForCurrentUser(long courseId, boolean active) {
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
    }

    /**
     * Deletes the current user's interaction science data for a course while retaining audit events.
     *
     * @param courseId the id of the course
     */
    @Transactional
    public void deleteScienceDataForCurrentUser(long courseId) {
        User user = userRepository.getUser();
        Course course = courseRepository.findByIdElseThrow(courseId);
        ScienceCourseConsent consent = scienceCourseConsentRepository.findByUserIdAndCourseId(user.getId(), courseId).orElse(null);
        checkMayDeleteScienceData(course, user, consent);
        scienceEventRepository.deleteInteractionEventsByIdentityAndCourseId(user.getLogin(), courseId, ScienceEventService.SCIENCE_AUDIT_EVENT_TYPES);
        scienceEventService.logAuditEvent(user.getLogin(), ScienceEventType.SCIENCE__DATA_DELETED, courseId);
    }

    /**
     * Creates a research CSV export for the selected courses, dates, and event types.
     *
     * @param request the export filter and purpose
     * @return the generated CSV file bytes
     */
    @Transactional
    public byte[] createResearchExport(ScienceResearchExportRequestDTO request) {
        validateResearchExportRequest(request);
        Set<ScienceEventType> eventTypes = request.eventTypes() == null || request.eventTypes().isEmpty() ? EnumSet.allOf(ScienceEventType.class) : request.eventTypes();
        var scienceEvents = scienceEventRepository.findForResearchExport(request.courseIds(), request.from(), request.to(), eventTypes);
        byte[] csvBytes = createScienceEventCsv(scienceEvents, UUID.randomUUID().toString());
        ScienceResearchExportAudit audit = new ScienceResearchExportAudit();
        audit.setPurpose(request.purpose().trim());
        audit.setCourseFilter(request.courseIds().stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
        audit.setDateFrom(request.from() == null ? null : request.from().toString());
        audit.setDateTo(request.to() == null ? null : request.to().toString());
        audit.setEventTypes(eventTypes.stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        audit.setFileChecksum(sha256Hex(csvBytes));
        scienceResearchExportAuditRepository.save(audit);
        return csvBytes;
    }

    /**
     * Returns the audit history for research exports.
     *
     * @return the export audit history
     */
    @Transactional(readOnly = true)
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

    private static byte[] createScienceEventCsv(List<de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent> scienceEvents, String exportSalt) {
        String[] header = { "identity", "timestamp", "event_type", "course_id", "resource_id" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header).get();
        try (StringWriter writer = new StringWriter(); CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            for (var scienceEvent : scienceEvents) {
                printer.printRecord(pseudonymizeIdentity(scienceEvent.getIdentity(), exportSalt), scienceEvent.getTimestamp(), scienceEvent.getType(), scienceEvent.getCourseId(),
                        scienceEvent.getResourceId());
            }
            printer.flush();
            return writer.toString().getBytes(StandardCharsets.UTF_8);
        }
        catch (IOException e) {
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
}
