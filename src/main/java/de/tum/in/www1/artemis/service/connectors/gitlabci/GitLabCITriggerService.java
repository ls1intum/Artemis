package de.tum.in.www1.artemis.service.connectors.gitlabci;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Trigger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.GitLabCIException;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;

@Profile("gitlabci")
@Service
public class GitLabCITriggerService implements ContinuousIntegrationTriggerService {

    private final GitLabApi gitlab;

    private final UrlService urlService;

    public GitLabCITriggerService(GitLabApi gitlab, UrlService urlService) {
        this.gitlab = gitlab;
        this.urlService = urlService;
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
        triggerBuild(participation.getVcsRepositoryUrl(), participation.getProgrammingExercise().getBranch());
    }

    private void triggerBuild(VcsRepositoryUrl vcsRepositoryUrl, String branch) {
        final String repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(vcsRepositoryUrl);
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
