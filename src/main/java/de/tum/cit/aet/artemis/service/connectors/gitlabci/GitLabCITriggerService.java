package de.tum.cit.aet.artemis.service.connectors.gitlabci;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Trigger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.GitLabCIException;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.service.UriService;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

@Profile("gitlabci")
@Service
public class GitLabCITriggerService implements ContinuousIntegrationTriggerService {

    private final GitLabApi gitlab;

    private final UriService uriService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public GitLabCITriggerService(GitLabApi gitlab, UriService uriService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.gitlab = gitlab;
        this.uriService = uriService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, boolean triggerAll) throws ContinuousIntegrationException {
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        programmingExerciseBuildConfigRepository.loadAndSetBuildConfig(programmingExercise);
        triggerBuild(participation.getVcsRepositoryUri(), programmingExercise.getBuildConfig().getBranch());
    }

    private void triggerBuild(VcsRepositoryUri vcsRepositoryUri, String branch) {
        final String repositoryPath = uriService.getRepositoryPathFromRepositoryUri(vcsRepositoryUri);
        try {
            Trigger trigger = gitlab.getPipelineApi().createPipelineTrigger(repositoryPath, "Trigger build");
            gitlab.getPipelineApi().triggerPipeline(repositoryPath, trigger, branch, null);
            gitlab.getPipelineApi().deletePipelineTrigger(repositoryPath, trigger.getId());
        }
        catch (GitLabApiException e) {
            throw new GitLabCIException("Error triggering the build for " + repositoryPath, e);
        }
    }
}
