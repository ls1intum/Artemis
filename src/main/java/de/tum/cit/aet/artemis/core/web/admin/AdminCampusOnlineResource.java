package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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

import de.tum.cit.aet.artemis.core.config.CampusOnlineEnabled;
import de.tum.cit.aet.artemis.core.domain.CampusOnlineOrgUnit;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseImportRequestDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineLinkRequestDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineOrgUnitDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineOrgUnitImportDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineSyncResultDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CampusOnlineOrgUnitRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineCourseImportService;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineEnrollmentSyncService;

/**
 * REST controller for CAMPUSOnline administration endpoints.
 * <p>
 * Provides admin-only endpoints for:
 * <ul>
 * <li>CRUD operations on organizational units (faculties/departments)</li>
 * <li>Bulk CSV import of organizational units</li>
 * <li>Searching, importing, linking, and unlinking CAMPUSOnline courses</li>
 * <li>Triggering enrollment synchronization (all courses or a single course)</li>
 * </ul>
 * All endpoints require the ADMIN role and are only available when the CAMPUSOnline module is enabled.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Conditional(CampusOnlineEnabled.class)
@Lazy
@RestController
@RequestMapping("api/core/admin/campus-online/")
public class AdminCampusOnlineResource {

    /** Minimum length for course name search queries to prevent overly broad API calls. */
    private static final int MIN_SEARCH_QUERY_LENGTH = 3;

    /** Maximum number of org units that can be imported in a single bulk request. */
    private static final int MAX_IMPORT_SIZE = 10_000;

    private final CampusOnlineCourseImportService courseImportService;

    private final CampusOnlineEnrollmentSyncService enrollmentSyncService;

    private final CampusOnlineOrgUnitRepository orgUnitRepository;

    /**
     * Constructs the admin resource with the required dependencies.
     *
     * @param courseImportService   the service for searching and importing CAMPUSOnline courses
     * @param enrollmentSyncService the service for syncing student enrollments
     * @param orgUnitRepository     the repository for managing organizational units
     */
    public AdminCampusOnlineResource(CampusOnlineCourseImportService courseImportService, CampusOnlineEnrollmentSyncService enrollmentSyncService,
            CampusOnlineOrgUnitRepository orgUnitRepository) {
        this.courseImportService = courseImportService;
        this.enrollmentSyncService = enrollmentSyncService;
        this.orgUnitRepository = orgUnitRepository;
    }

    // ==================== Org Unit CRUD ====================

    /**
     * GET /api/core/admin/campus-online/org-units : Get all organizational units.
     *
     * @return list of all org units
     */
    @GetMapping("org-units")
    public ResponseEntity<List<CampusOnlineOrgUnitDTO>> getAllOrgUnits() {
        List<CampusOnlineOrgUnitDTO> orgUnits = orgUnitRepository.findAll().stream().map(CampusOnlineOrgUnitDTO::fromEntity).toList();
        return ResponseEntity.ok(orgUnits);
    }

    /**
     * GET /api/core/admin/campus-online/org-units/{orgUnitId} : Get a single organizational unit.
     *
     * @param orgUnitId the ID of the org unit
     * @return the org unit
     */
    @GetMapping("org-units/{orgUnitId}")
    public ResponseEntity<CampusOnlineOrgUnitDTO> getOrgUnit(@PathVariable long orgUnitId) {
        CampusOnlineOrgUnit orgUnit = orgUnitRepository.findByIdElseThrow(orgUnitId);
        return ResponseEntity.ok(CampusOnlineOrgUnitDTO.fromEntity(orgUnit));
    }

    /**
     * POST /api/core/admin/campus-online/org-units : Create a new organizational unit.
     *
     * @param orgUnitDTO the org unit to create
     * @return the created org unit
     */
    @PostMapping("org-units")
    public ResponseEntity<CampusOnlineOrgUnitDTO> createOrgUnit(@RequestBody @Valid CampusOnlineOrgUnitDTO orgUnitDTO) {
        if (orgUnitDTO.id() != null) {
            throw new BadRequestAlertException("A new org unit cannot already have an ID", "campusOnline", "idExists");
        }
        if (orgUnitRepository.existsByExternalId(orgUnitDTO.externalId())) {
            throw new BadRequestAlertException("An org unit with this external ID already exists", "campusOnline", "externalIdExists");
        }
        CampusOnlineOrgUnit orgUnit = new CampusOnlineOrgUnit();
        orgUnit.setExternalId(orgUnitDTO.externalId());
        orgUnit.setName(orgUnitDTO.name());
        try {
            CampusOnlineOrgUnit saved = orgUnitRepository.save(orgUnit);
            return ResponseEntity.status(HttpStatus.CREATED).body(CampusOnlineOrgUnitDTO.fromEntity(saved));
        }
        catch (DataIntegrityViolationException e) {
            throw new BadRequestAlertException("An org unit with this external ID already exists", "campusOnline", "externalIdExists");
        }
    }

    /**
     * POST /api/core/admin/campus-online/org-units/import : Bulk import organizational units.
     * Skips entries with duplicate external IDs (both within the import and against existing data).
     *
     * @param importDTOs the list of org units to import
     * @return list of newly created org units (duplicates are skipped)
     */
    @PostMapping("org-units/import")
    public ResponseEntity<List<CampusOnlineOrgUnitDTO>> importOrgUnits(@RequestBody @Valid List<CampusOnlineOrgUnitImportDTO> importDTOs) {
        if (importDTOs.size() > MAX_IMPORT_SIZE) {
            throw new BadRequestAlertException("Import size exceeds maximum of " + MAX_IMPORT_SIZE, "campusOnline", "importTooLarge");
        }

        Set<String> existingExternalIds = orgUnitRepository.findAll().stream().map(CampusOnlineOrgUnit::getExternalId).collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        List<CampusOnlineOrgUnit> toSave = new ArrayList<>();

        for (CampusOnlineOrgUnitImportDTO dto : importDTOs) {
            if (existingExternalIds.contains(dto.externalId()) || !seen.add(dto.externalId())) {
                continue; // skip duplicates (existing or within batch)
            }
            CampusOnlineOrgUnit orgUnit = new CampusOnlineOrgUnit();
            orgUnit.setExternalId(dto.externalId());
            orgUnit.setName(dto.name());
            toSave.add(orgUnit);
        }

        try {
            List<CampusOnlineOrgUnit> saved = orgUnitRepository.saveAll(toSave);
            List<CampusOnlineOrgUnitDTO> created = saved.stream().map(CampusOnlineOrgUnitDTO::fromEntity).toList();
            return ResponseEntity.ok(created);
        }
        catch (DataIntegrityViolationException e) {
            throw new BadRequestAlertException("Duplicate external ID detected during import", "campusOnline", "externalIdExists");
        }
    }

    /**
     * PUT /api/core/admin/campus-online/org-units/{orgUnitId} : Update an existing organizational unit.
     *
     * @param orgUnitId  the ID of the org unit to update
     * @param orgUnitDTO the updated org unit data
     * @return the updated org unit
     */
    @PutMapping("org-units/{orgUnitId}")
    public ResponseEntity<CampusOnlineOrgUnitDTO> updateOrgUnit(@PathVariable long orgUnitId, @RequestBody @Valid CampusOnlineOrgUnitDTO orgUnitDTO) {
        CampusOnlineOrgUnit existing = orgUnitRepository.findByIdElseThrow(orgUnitId);
        // Check for duplicate externalId (only if it changed)
        if (!existing.getExternalId().equals(orgUnitDTO.externalId()) && orgUnitRepository.existsByExternalId(orgUnitDTO.externalId())) {
            throw new BadRequestAlertException("An org unit with this external ID already exists", "campusOnline", "externalIdExists");
        }
        existing.setExternalId(orgUnitDTO.externalId());
        existing.setName(orgUnitDTO.name());
        try {
            CampusOnlineOrgUnit saved = orgUnitRepository.save(existing);
            return ResponseEntity.ok(CampusOnlineOrgUnitDTO.fromEntity(saved));
        }
        catch (DataIntegrityViolationException e) {
            throw new BadRequestAlertException("An org unit with this external ID already exists", "campusOnline", "externalIdExists");
        }
    }

    /**
     * DELETE /api/core/admin/campus-online/org-units/{orgUnitId} : Delete an organizational unit.
     *
     * @param orgUnitId the ID of the org unit to delete
     * @return empty response
     */
    @DeleteMapping("org-units/{orgUnitId}")
    public ResponseEntity<Void> deleteOrgUnit(@PathVariable long orgUnitId) {
        CampusOnlineOrgUnit orgUnit = orgUnitRepository.findByIdElseThrow(orgUnitId);
        orgUnitRepository.delete(orgUnit);
        return ResponseEntity.noContent().build();
    }

    // ==================== Course Search ====================

    /**
     * GET /api/core/admin/campus-online/courses : Search for courses in a CAMPUSOnline organizational unit.
     *
     * @param orgUnitId the organizational unit ID
     * @param from      the start date (format: YYYY-MM-DD)
     * @param until     the end date (format: YYYY-MM-DD)
     * @return list of matching courses
     */
    @GetMapping("courses")
    public ResponseEntity<List<CampusOnlineCourseDTO>> searchCourses(@RequestParam String orgUnitId, @RequestParam String from, @RequestParam String until) {
        validateOrgUnitId(orgUnitId);
        validateDateParam(from, "from");
        validateDateParam(until, "until");
        if (LocalDate.parse(from).isAfter(LocalDate.parse(until))) {
            throw new BadRequestAlertException("'from' date must not be after 'until' date", "campusOnline", "invalidDateRange");
        }
        List<CampusOnlineCourseDTO> courses = courseImportService.searchCourses(orgUnitId, from, until);
        return ResponseEntity.ok(courses);
    }

    /**
     * GET /api/core/admin/campus-online/courses/search : Search for courses by name for typeahead.
     *
     * @param query     the course name query
     * @param orgUnitId the organizational unit ID to search in
     * @param semester  optional semester filter
     * @return list of matching courses with metadata
     */
    @GetMapping("courses/search")
    public ResponseEntity<List<CampusOnlineCourseDTO>> searchCoursesByName(@RequestParam String query, @RequestParam String orgUnitId,
            @RequestParam(required = false) String semester) {
        validateOrgUnitId(orgUnitId);
        if (query == null || query.length() < MIN_SEARCH_QUERY_LENGTH) {
            throw new BadRequestAlertException("Search query must be at least " + MIN_SEARCH_QUERY_LENGTH + " characters", "campusOnline", "queryTooShort");
        }
        List<CampusOnlineCourseDTO> courses = courseImportService.searchCoursesByName(query, orgUnitId, semester);
        return ResponseEntity.ok(courses);
    }

    // ==================== Course Link/Unlink/Import ====================

    /**
     * PUT /api/core/admin/campus-online/courses/{courseId}/link : Link a CAMPUSOnline course to an Artemis course.
     *
     * @param courseId the Artemis course ID
     * @param request  the link request containing CAMPUSOnline course details
     * @return the updated course with campus online configuration
     */
    @PutMapping("courses/{courseId}/link")
    public ResponseEntity<CampusOnlineCourseDTO> linkCourse(@PathVariable long courseId, @RequestBody @Valid CampusOnlineLinkRequestDTO request) {
        CampusOnlineCourseDTO result = courseImportService.linkCourse(courseId, request.campusOnlineCourseId(), request.responsibleInstructor(), request.department(),
                request.studyProgram());
        return ResponseEntity.ok(result);
    }

    /**
     * DELETE /api/core/admin/campus-online/courses/{courseId}/link : Unlink a CAMPUSOnline course from an Artemis course.
     *
     * @param courseId the Artemis course ID
     * @return empty response
     */
    @DeleteMapping("courses/{courseId}/link")
    public ResponseEntity<Void> unlinkCourse(@PathVariable long courseId) {
        courseImportService.unlinkCourse(courseId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/core/admin/campus-online/courses/import : Import a course from CAMPUSOnline.
     *
     * @param request the import request
     * @return the created course DTO
     */
    @PostMapping("courses/import")
    public ResponseEntity<CampusOnlineCourseDTO> importCourse(@RequestBody @Valid CampusOnlineCourseImportRequestDTO request) {
        CampusOnlineCourseDTO course = courseImportService.importCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(course);
    }

    // ==================== Enrollment Sync ====================

    /**
     * POST /api/core/admin/campus-online/sync : Manually trigger enrollment sync for all courses.
     *
     * @return the sync result
     */
    @PostMapping("sync")
    public ResponseEntity<CampusOnlineSyncResultDTO> syncAllCourses() {
        CampusOnlineSyncResultDTO result = enrollmentSyncService.performEnrollmentSync();
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/core/admin/campus-online/courses/{courseId}/sync : Sync enrollment for a single course.
     *
     * @param courseId the Artemis course ID
     * @return the sync result
     */
    @PostMapping("courses/{courseId}/sync")
    public ResponseEntity<CampusOnlineSyncResultDTO> syncSingleCourse(@PathVariable long courseId) {
        CampusOnlineSyncResultDTO result = enrollmentSyncService.performSingleCourseSync(courseId);
        return ResponseEntity.ok(result);
    }

    /**
     * Validates that the org unit ID is a non-blank numeric string.
     *
     * @param orgUnitId the org unit ID to validate
     * @throws BadRequestAlertException if the ID is null, blank, or non-numeric
     */
    private void validateOrgUnitId(String orgUnitId) {
        if (orgUnitId == null || orgUnitId.isBlank() || !orgUnitId.matches("\\d+")) {
            throw new BadRequestAlertException("orgUnitId must be a numeric value", "campusOnline", "invalidOrgUnitId");
        }
    }

    /**
     * Validates that a date parameter is in the ISO-8601 format (YYYY-MM-DD).
     *
     * @param date      the date string to validate
     * @param paramName the parameter name for error messages
     * @throws BadRequestAlertException if the date is null or not parseable
     */
    private void validateDateParam(String date, String paramName) {
        if (date == null) {
            throw new BadRequestAlertException("Date parameter '" + paramName + "' is required", "campusOnline", "invalidDateFormat");
        }
        try {
            LocalDate.parse(date);
        }
        catch (DateTimeParseException e) {
            throw new BadRequestAlertException("Invalid date format for '" + paramName + "', expected YYYY-MM-DD", "campusOnline", "invalidDateFormat");
        }
    }
}
