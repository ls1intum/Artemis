package de.tum.in.www1.artemis.service.connectors.gitlabci;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.connectors.gitlab.AbstractGitLabAuthorizationInterceptor;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

@Profile("gitlabci")
@Component
public class GitLabCIAuthorizationInterceptor extends AbstractGitLabAuthorizationInterceptor {
}
