package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

@Component
@Profile(PROFILE_LOCALCI)
public class LocalCIInfoContributor implements InfoContributor {

    @Value("${artemis.continuous-integration.instructor-build-timeout-options-seconds.min-option:10}")
    private int minInstructorBuildTimeoutOption;

    @Value("${artemis.continuous-integration.instructor-build-timeout-options-seconds.max-option:240}")
    private int maxInstructorBuildTimeoutOption;

    @Value("${artemis.continuous-integration.instructor-build-timeout-options-seconds.default-option:120}")
    private int defaultInstructorBuildTimeoutOption;

    @Override
    public void contribute(Info.Builder builder) {
        // Store name of the continuous integration system
        builder.withDetail(Constants.CONTINUOUS_INTEGRATION_NAME, "Local CI");

        // Store the build timeout options for the instructor build
        builder.withDetail(Constants.INSTRUCTOR_BUILD_TIMEOUT_MIN_OPTION, minInstructorBuildTimeoutOption);
        builder.withDetail(Constants.INSTRUCTOR_BUILD_TIMEOUT_MAX_OPTION, maxInstructorBuildTimeoutOption);
        builder.withDetail(Constants.INSTRUCTOR_BUILD_TIMEOUT_DEFAULT_OPTION, defaultInstructorBuildTimeoutOption);
    }
}
