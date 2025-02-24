package de.tum.cit.aet.artemis.programming.web.accesslog;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessLogDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.VcsAccessLogService;

@Profile(PROFILE_LOCALVC)
@RestController
@RequestMapping("api/")
public class VcsAccessLogResource {

    private static final Logger log = LoggerFactory.getLogger(VcsAccessLogResource.class);

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private final VcsAccessLogRepository vcsAccessLogRepository;

    private final VcsAccessLogService vcsAccessLogService;

    public VcsAccessLogResource(ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            ParticipationAuthorizationCheckService participationAuthCheckService, VcsAccessLogRepository vcsAccessLogRepository, VcsAccessLogService vcsAccessLogService) {
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.participationAuthCheckService = participationAuthCheckService;
        this.vcsAccessLogRepository = vcsAccessLogRepository;
        this.vcsAccessLogService = vcsAccessLogService;
    }

    /**
     * Retrieves the VCS access logs for the specified programming exercise's template or solution participation
     *
     * @param exerciseId     the ID of the programming exercise
     * @param repositoryType the type of repository (either TEMPLATE or SOLUTION) for which to retrieve the logs.
     * @return the ResponseEntity with status 200 (OK) and with body containing a list of vcsAccessLogDTOs of the participation, or 400 (Bad request) if localVC is not enabled.
     * @throws BadRequestAlertException if the repository type is invalid
     */
    @GetMapping("programming-exercise/{exerciseId}/vcs-access-log/{repositoryType}")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<List<VcsAccessLogDTO>> getVcsAccessLogForExerciseRepository(@PathVariable long exerciseId, @PathVariable RepositoryType repositoryType) {
        if (repositoryType != RepositoryType.TEMPLATE && repositoryType != RepositoryType.SOLUTION) {
            throw new BadRequestAlertException("Can only get vcs access log from template and assignment repositories", "programmingExerciseParticipation",
                    "incorrect repositoryType");
        }
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);
        log.info("Fetching VCS access logs for exercise ID: {} and repository type: {}", exerciseId, repositoryType);

        var participation = repositoryType == RepositoryType.TEMPLATE ? programmingExercise.getTemplateParticipation() : programmingExercise.getSolutionParticipation();

        List<VcsAccessLog> vcsAccessLogs = vcsAccessLogRepository.findAllByParticipationId(participation.getId());
        var vcsAccessLogDTOs = vcsAccessLogs.stream().map(VcsAccessLogDTO::of).toList();
        return ResponseEntity.ok(vcsAccessLogDTOs);
    }

    /**
     * GET /programming-exercise-participations/{participationId}/vcs-access-log :
     * Here we check if the user is least an instructor for the exercise. If true the user can have access to the vcs access log of any participation of the exercise.
     *
     * @param participationId the id of the participation for which to retrieve the vcs access log
     * @return the ResponseEntity with status 200 (OK) and with body containing a list of vcsAccessLogDTOs of the participation, or 400 (Bad request) if localVC is not enabled.
     */
    @GetMapping("programming-exercise-participations/{participationId}/vcs-access-log")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<VcsAccessLogDTO>> getVcsAccessLogForParticipationRepo(@PathVariable long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
        log.info("Fetching VCS access logs for participation ID: {}", participationId);
        List<VcsAccessLog> vcsAccessLogs = vcsAccessLogRepository.findAllByParticipationId(participationId);
        var vcsAccessLogDTOs = vcsAccessLogs.stream().map(VcsAccessLogDTO::of).toList();
        return ResponseEntity.ok(vcsAccessLogDTOs);
    }

    @GetMapping("vcs-access-log/participation/{participationId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<SearchResultPageDTO<VcsAccessLogDTO>> getVcsAccessLogForParticipationRepo(SearchTermPageableSearchDTO<String> search,
            @PathVariable long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);

        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
        log.info("Fetching VCS access logs for participation ID: {}", participationId);
        var pageOfDTOs = vcsAccessLogService.getAllOnPageWithSize(search, participationId);
        return ResponseEntity.ok(pageOfDTOs);
    }

}
