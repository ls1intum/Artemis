package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.CampusOnlineEnabled;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseImportRequestDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineSyncResultDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineCourseImportService;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineEnrollmentSyncService;

@Profile(PROFILE_CORE)
@EnforceAdmin
@Conditional(CampusOnlineEnabled.class)
@Lazy
@RestController
@RequestMapping("api/core/admin/campus-online/")
public class AdminCampusOnlineResource {

    private final CampusOnlineCourseImportService courseImportService;

    private final CampusOnlineEnrollmentSyncService enrollmentSyncService;

    public AdminCampusOnlineResource(CampusOnlineCourseImportService courseImportService, CampusOnlineEnrollmentSyncService enrollmentSyncService) {
        this.courseImportService = courseImportService;
        this.enrollmentSyncService = enrollmentSyncService;
    }

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
        List<CampusOnlineCourseDTO> courses = courseImportService.searchCourses(orgUnitId, from, until);
        return ResponseEntity.ok(courses);
    }

    /**
     * POST /api/core/admin/campus-online/courses/import : Import a course from CAMPUSOnline.
     *
     * @param request the import request
     * @return the created Artemis course
     */
    @PostMapping("courses/import")
    public ResponseEntity<Course> importCourse(@RequestBody CampusOnlineCourseImportRequestDTO request) {
        Course course = courseImportService.importCourse(request);
        return ResponseEntity.ok(course);
    }

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
}
