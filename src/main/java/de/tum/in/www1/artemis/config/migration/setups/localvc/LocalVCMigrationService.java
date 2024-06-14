package de.tum.in.www1.artemis.config.migration.setups.localvc;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Services for executing migration tasks for bitbucket to local vc, needed for the value injection
 */
@Service
@Profile("bitbucket & localvc")
public class LocalVCMigrationService {

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${migration.base-url:http://0.0.0.0}")
    private URL localVCBaseUrl;

    @Value("${migration.local-vcs-repo-path:null}")
    private String localVCBasePath;

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public URL getLocalVCBaseUrl() {
        return localVCBaseUrl;
    }

    public String getLocalVCBasePath() {
        return localVCBasePath;
    }
}
