package de.tum.in.www1.artemis.service.connectors.gitlab;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("gitlab")
@Component
public class GitLabAuthorizationInterceptor extends AbstractGitLabAuthorizationInterceptor {
}
