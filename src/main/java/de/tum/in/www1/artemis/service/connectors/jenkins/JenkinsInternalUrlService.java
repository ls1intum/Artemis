package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.net.URL;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.InternalUrlService;

@Profile("jenkins")
@Service
public class JenkinsInternalUrlService extends InternalUrlService {

    public JenkinsInternalUrlService(@Value("${jenkins.internal-urls.ci-url:#{null}}") Optional<URL> internalCiUrl,
            @Value("${jenkins.internal-urls.vcs-url:#{null}}") Optional<URL> internalVcsUrl) {
        super(internalCiUrl, internalVcsUrl);
    }

}
