package de.tum.in.www1.artemis.service.connectors;

import org.eclipse.jgit.lib.Repository;

public interface ContinuousIntegrationPushService {

    /**
     * Trigger a new build and process the results. This is implemented only for local VC + local CI.
     * For Bitbucket + Bamboo and GitLab + Jenkins, webhooks were added when creating the repository, that notify the CI system and thus trigger the build.
     *
     * @param commitHash the hash of the commit that was pushed.
     * @param repository the repository that was pushed to.
     */
    void processNewPush(String commitHash, Repository repository);
}
