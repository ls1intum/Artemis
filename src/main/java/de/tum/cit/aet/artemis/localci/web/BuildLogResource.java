package de.tum.cit.aet.artemis.localci.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;

@Profile(PROFILE_LOCALCI)
@Lazy
@FeatureUsage(UserFeature.BUILD_OVERVIEW)
@RestController
@RequestMapping("api/localci/")
public class BuildLogResource {

    private static final Logger log = LoggerFactory.getLogger(BuildLogResource.class);

    private final BuildLogEntryService buildLogEntryService;

    private final BuildJobRepository buildJobRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public BuildLogResource(BuildLogEntryService buildLogEntryService, BuildJobRepository buildJobRepository, AuthorizationCheckService authorizationCheckService) {
        this.buildLogEntryService = buildLogEntryService;
        this.buildJobRepository = buildJobRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET /build-jobs/{buildJobId}/build-log : get the build log for a given result
     *
     * @param buildJobId the id of the build job for which to retrieve the build log
     * @return the ResponseEntity with status 200 (OK) and the build log in the body, or with status 404 (Not Found) if the build job or its build log could not be found
     */
    @GetMapping("build-jobs/{buildJobId}/build-log")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<Resource> getBuildLogForBuildJob(@PathVariable String buildJobId) {
        log.debug("REST request to get the build log for build job {}", buildJobId);
        HttpHeaders responseHeaders = new HttpHeaders();

        BuildJob buildJob = buildJobRepository.findByBuildJobId(buildJobId).orElse(null);
        if (buildJob == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        checkIsAtLeastEditorForBuildJobElseThrow(buildJob);

        FileSystemResource buildLog = buildLogEntryService.retrieveBuildLogsFromFileForBuildJob(buildJobId);
        if (buildLog == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        responseHeaders.setContentType(MediaType.TEXT_PLAIN);
        responseHeaders.setContentDispositionFormData("attachment", "build-" + buildJobId + ".log");
        return new ResponseEntity<>(buildLog, responseHeaders, HttpStatus.OK);
    }

    /**
     * Build job ids are not scoped to a course, so the role is derived from the exercise (or, if it is unknown, the course) the build job was run for.
     * A build job without any of the two can only be read by an admin.
     *
     * @param buildJob the build job whose log is requested
     */
    private void checkIsAtLeastEditorForBuildJobElseThrow(BuildJob buildJob) {
        if (buildJob.getExerciseId() != null) {
            authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(Role.EDITOR, buildJob.getExerciseId());
        }
        else if (buildJob.getCourseId() != null) {
            authorizationCheckService.checkIsAtLeastRoleInCourseElseThrow(Role.EDITOR, buildJob.getCourseId());
        }
        else {
            authorizationCheckService.checkIsAdminElseThrow(null);
        }
    }
}
