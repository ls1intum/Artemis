package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.net.URL;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.InternalUrlService;

@Profile("bamboo")
@Service
public class BambooInternalUrlService extends InternalUrlService {

    public BambooInternalUrlService(@Value("${bamboo.internal-urls.ci-url:#{null}}") Optional<URL> internalCiUrl,
            @Value("${bamboo.internal-urls.vcs-url:#{null}}") Optional<URL> internalVcsUrl) {
        super(internalCiUrl, internalVcsUrl);
    }
}
