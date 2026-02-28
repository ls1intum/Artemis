package de.tum.cit.aet.artemis.core.web.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseMaterialImportOptionsDTO;
import de.tum.cit.aet.artemis.core.dto.CourseMaterialImportResultDTO;
import de.tum.cit.aet.artemis.core.dto.CourseSummaryDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy.EnforceAccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.PolicyEngine;
import de.tum.cit.aet.artemis.core.service.course.CourseAdminService;
import de.tum.cit.aet.artemis.core.service.course.CourseMaterialImportService;

/**
 * REST controller for importing course material from one course to another.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/")
@Lazy
public class CourseMaterialImportResource {

    private static final String ENTITY_NAME = "course";

    private static final Logger log = LoggerFactory.getLogger(CourseMaterialImportResource.class);

    private final CourseMaterialImportService courseMaterialImportService;

    private final CourseAdminService courseAdminService;

    private final PolicyEngine policyEngine;

    private final AccessPolicy<Course> courseEditorAccessPolicy;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    public CourseMaterialImportResource(CourseMaterialImportService courseMaterialImportService, CourseAdminService courseAdminService, PolicyEngine policyEngine,
            @Qualifier("courseEditorAccessPolicy") AccessPolicy<Course> courseEditorAccessPolicy, UserRepository userRepository, CourseRepository courseRepository) {
        this.courseMaterialImportService = courseMaterialImportService;
        this.courseAdminService = courseAdminService;
        this.policyEngine = policyEngine;
        this.courseEditorAccessPolicy = courseEditorAccessPolicy;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * GET /courses/{courseId}/import-summary/{sourceCourseId} : Get summary of what can be imported from the source course.
     * This returns the counts of exercises, lectures, exams, competencies, tutorial groups, and FAQs.
     *
     * @param courseId       the ID of the target course (for authorization)
     * @param sourceCourseId the ID of the source course to get summary from
     * @return the ResponseEntity with status 200 (OK) and the course summary in the body
     */
    @GetMapping("courses/{courseId}/import-summary/{sourceCourseId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @EnforceAccessPolicy(value = "courseInstructorAccessPolicy", resourceIdFieldName = "courseId")
    public ResponseEntity<CourseSummaryDTO> getImportSummary(@PathVariable long courseId, @PathVariable long sourceCourseId) {
        log.debug("REST request to get import summary for source course {}", sourceCourseId);

        if (courseId == sourceCourseId) {
            throw new BadRequestAlertException("Cannot import from the same course", ENTITY_NAME, "sameCourse");
        }

        // Verify user has at least editor access to source course (programmatic check for second course)
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course sourceCourse = courseRepository.findByIdElseThrow(sourceCourseId);
        policyEngine.checkAllowedElseThrow(courseEditorAccessPolicy, user, sourceCourse);

        CourseSummaryDTO summary = courseAdminService.getCourseSummary(sourceCourseId);
        return ResponseEntity.ok(summary);
    }

    /**
     * POST /courses/{courseId}/import-material : Import material from another course.
     * This imports selected content (exercises, lectures, exams, competencies, tutorial groups, FAQs)
     * from the source course into the target course.
     *
     * @param courseId the ID of the target course
     * @param options  the import options specifying what to import
     * @return the ResponseEntity with status 200 (OK) and the import result in the body
     */
    @PostMapping("courses/{courseId}/import-material")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @EnforceAccessPolicy(value = "courseInstructorAccessPolicy", resourceIdFieldName = "courseId")
    public ResponseEntity<CourseMaterialImportResultDTO> importCourseMaterial(@PathVariable long courseId, @RequestBody CourseMaterialImportOptionsDTO options) {
        log.info("REST request to import course material from course {} to course {}", options.sourceCourseId(), courseId);

        if (courseId == options.sourceCourseId()) {
            throw new BadRequestAlertException("Cannot import from the same course", ENTITY_NAME, "sameCourse");
        }

        // Verify user has at least editor access to source course (programmatic check for second course)
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course sourceCourse = courseRepository.findByIdElseThrow(options.sourceCourseId());
        policyEngine.checkAllowedElseThrow(courseEditorAccessPolicy, user, sourceCourse);

        // Perform the import
        CourseMaterialImportResultDTO result = courseMaterialImportService.importCourseMaterial(courseId, options, user);

        return ResponseEntity.ok(result);
    }
}
