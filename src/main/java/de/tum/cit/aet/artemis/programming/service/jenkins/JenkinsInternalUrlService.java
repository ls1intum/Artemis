package de.tum.cit.aet.artemis.programming.service.jenkins;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import java.net.URL;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.service.InternalUrlService;

@Profile(PROFILE_JENKINS)
@Lazy
@Service
public class JenkinsInternalUrlService extends InternalUrlService {

    public JenkinsInternalUrlService(@Value("${jenkins.internal-urls.ci-url:#{null}}") Optional<URL> internalCiUrl,
            @Value("${jenkins.internal-urls.vcs-url:#{null}}") Optional<URL> internalVcsUrl) {
        super(internalCiUrl, internalVcsUrl);
    }

}
