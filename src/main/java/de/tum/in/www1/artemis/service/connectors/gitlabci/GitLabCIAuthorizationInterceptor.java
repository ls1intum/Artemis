package de.tum.in.www1.artemis.service.connectors.gitlabci;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.connectors.gitlab.AbstractGitLabAuthorizationInterceptor;

@Profile("gitlabci")
@Component
public class GitLabCIAuthorizationInterceptor extends AbstractGitLabAuthorizationInterceptor {
}
