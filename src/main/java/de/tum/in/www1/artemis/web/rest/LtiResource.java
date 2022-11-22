package de.tum.in.www1.artemis.web.rest;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.LtiDynamicRegistrationService;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseLtiConfigurationDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * REST controller to handle LTI10 launches.
 */
@RestController
@RequestMapping("api/")
public class LtiResource {

    private final LtiDynamicRegistrationService ltiDynamicRegistrationService;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public static final String LOGIN_REDIRECT_CLIENT_PATH = "/lti/launch";

    public LtiResource(LtiDynamicRegistrationService ltiDynamicRegistrationService, ExerciseRepository exerciseRepository, CourseRepository courseRepository,
            AuthorizationCheckService authCheckService) {
        this.ltiDynamicRegistrationService = ltiDynamicRegistrationService;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    @PostMapping("/lti13/dynamic-registration/{courseId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public void lti13DynamicRegistration(@PathVariable Long courseId, @RequestParam(name = "openid_configuration") String openIdConfiguration,
            @RequestParam(name = "registration_token", required = false) String registrationToken) {

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfiguration, registrationToken);
    }

    /**
     * GET lti/configuration/:exerciseId : Generates LTI configuration parameters for an exercise.
     *
     * @param exerciseId the id of the exercise for the wanted LTI configuration
     * @param request    HTTP request
     * @return the ResponseEntity with status 200 (OK) and with body the LTI configuration, or with status 404 (Not Found)
     */
    @GetMapping("lti/configuration/{exerciseId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ExerciseLtiConfigurationDTO> exerciseLtiConfiguration(@PathVariable("exerciseId") Long exerciseId, HttpServletRequest request) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        OnlineCourseConfiguration ocConfiguration = course.getOnlineCourseConfiguration();
        if (ocConfiguration == null) {
            throw new BadRequestAlertException("LTI is not configured for this course", "LTI", "ltiNotConfigured");
        }

        String launchUrl = request.getScheme() + "://" + // "https://"
                request.getServerName() +              // "localhost" // ":"
                (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "") + "/api/lti/launch/" + exercise.getId();

        return new ResponseEntity<>(new ExerciseLtiConfigurationDTO(launchUrl, ocConfiguration.getLtiKey(), ocConfiguration.getLtiSecret()), HttpStatus.OK);
    }
}
