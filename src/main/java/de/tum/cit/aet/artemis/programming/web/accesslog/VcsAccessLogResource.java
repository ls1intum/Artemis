package de.tum.cit.aet.artemis.programming.web.accesslog;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessLogDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
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

    private final VcsAccessLogService vcsAccessLogService;

    public VcsAccessLogResource(ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            ParticipationAuthorizationCheckService participationAuthCheckService, VcsAccessLogService vcsAccessLogService) {
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.participationAuthCheckService = participationAuthCheckService;
        this.vcsAccessLogService = vcsAccessLogService;
    }

    @GetMapping("programming-exercises/{exerciseId}/repository/{repositoryType}/vcs-access-log")
    @EnforceAtLeastInstructor
    public ResponseEntity<SearchResultPageDTO<VcsAccessLogDTO>> getVcsAccessLogForParticipationRepo(SearchTermPageableSearchDTO<String> search, @PathVariable long exerciseId,
            @PathVariable RepositoryType repositoryType, @RequestParam long repositoryId) {

        if (repositoryType == RepositoryType.USER && repositoryId != 0L) {
            ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(repositoryId);

            participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
            log.info("Fetching VCS access logs for participation ID: {}", repositoryId);
            var pageOfDTOs = vcsAccessLogService.getAllOnPageWithSize(search, repositoryId);
            return ResponseEntity.ok(pageOfDTOs);
        }

        if (repositoryType == RepositoryType.TEMPLATE || repositoryType == RepositoryType.SOLUTION) {
            ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);
            log.info("Fetching VCS access logs for exercise ID: {} and repository type: {}", exerciseId, repositoryType);

            var participation = repositoryType == RepositoryType.TEMPLATE ? programmingExercise.getTemplateParticipation() : programmingExercise.getSolutionParticipation();
            var pageOfDTOs = vcsAccessLogService.getAllOnPageWithSize(search, participation.getId());
            return ResponseEntity.ok(pageOfDTOs);
        }
        return ResponseEntity.badRequest().build();
    }
}
