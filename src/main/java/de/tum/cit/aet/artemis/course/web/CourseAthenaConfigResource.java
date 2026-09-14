package de.tum.cit.aet.artemis.course.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigDTO;
import de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigUpdateDTO;
import de.tum.cit.aet.artemis.course.service.CourseAthenaConfigService;

/**
 * REST controller for the course-level Athena configuration shown on the course overview and in the onboarding wizard.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage("management/course-management")
@RestController
@RequestMapping("api/course/")
public class CourseAthenaConfigResource {

    private static final Logger log = LoggerFactory.getLogger(CourseAthenaConfigResource.class);

    private final CourseAthenaConfigService courseAthenaConfigService;

    public CourseAthenaConfigResource(CourseAthenaConfigService courseAthenaConfigService) {
        this.courseAthenaConfigService = courseAthenaConfigService;
    }

    /**
     * GET courses/:courseId/athena-configuration : Get the Athena configuration of a course.
     *
     * @param courseId the id of the course to read the configuration of
     * @return the ResponseEntity with status 200 (OK) and the Athena configuration in the body
     */
    @GetMapping("courses/{courseId}/athena-configuration")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<CourseAthenaConfigDTO> getAthenaConfig(@PathVariable long courseId) {
        log.debug("REST request to get the Athena configuration of course {}", courseId);
        return ResponseEntity.ok(courseAthenaConfigService.getConfig(courseId));
    }

    /**
     * PATCH courses/:courseId/athena-configuration : Change the Athena configuration of a course.
     * <p>
     * PATCH rather than PUT because the body names only the features that are actually being switched: the two toggles
     * save on click, and a request that also restated the other feature would carry whatever value the client last saw
     * for it back into the database, undoing a change someone else made in the meantime.
     *
     * @param courseId the id of the course to configure
     * @param update   the flags to change; a flag left out is not changed
     * @return the ResponseEntity with status 200 (OK) and the stored Athena configuration in the body
     */
    @PatchMapping("courses/{courseId}/athena-configuration")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<CourseAthenaConfigDTO> updateAthenaConfig(@PathVariable long courseId, @RequestBody CourseAthenaConfigUpdateDTO update) {
        log.debug("REST request to change the Athena configuration of course {} to {}", courseId, update);
        return ResponseEntity.ok(courseAthenaConfigService.updateConfig(courseId, update));
    }
}
