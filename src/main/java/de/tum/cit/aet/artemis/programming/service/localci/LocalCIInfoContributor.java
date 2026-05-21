package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;

@Component
@Lazy
@Profile(PROFILE_LOCALCI)
public class LocalCIInfoContributor implements InfoContributor {

    @Value("${artemis.continuous-integration.build-timeout-seconds.min:10}")
    private int minInstructorBuildTimeoutOption;

    @Value("${artemis.continuous-integration.build-timeout-seconds.max:240}")
    private int maxInstructorBuildTimeoutOption;

    @Value("${artemis.continuous-integration.build-timeout-seconds.default:120}")
    private int defaultInstructorBuildTimeoutOption;

    @Value("${artemis.continuous-integration.container-flags-limit.allowed-custom-networks:none}")
    private List<String> networks;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    public LocalCIInfoContributor(ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    @Override
    public void contribute(Info.Builder builder) {
        // Store name of the continuous integration system
        builder.withDetail(Constants.CONTINUOUS_INTEGRATION_NAME, "Local CI");

        // Store the build timeout options for the instructor build
        builder.withDetail(Constants.INSTRUCTOR_BUILD_TIMEOUT_MIN_OPTION, minInstructorBuildTimeoutOption);
        builder.withDetail(Constants.INSTRUCTOR_BUILD_TIMEOUT_MAX_OPTION, maxInstructorBuildTimeoutOption);
        builder.withDetail(Constants.INSTRUCTOR_BUILD_TIMEOUT_DEFAULT_OPTION, defaultInstructorBuildTimeoutOption);

        // store the allowed custom networks
        builder.withDetail(Constants.DOCKER_FLAG_ALLOWED_CUSTOM_NETWORKS, networks);

        List<String> defaultDockerFlags = programmingLanguageConfiguration.getDefaultDockerFlags();

        for (int i = 0; i < defaultDockerFlags.size(); i += 2) {
            String flag = defaultDockerFlags.get(i);
            String value = defaultDockerFlags.get(i + 1);

            switch (flag) {
                case "--cpus" -> builder.withDetail(Constants.DOCKER_FLAG_CPUS, Long.parseLong(value.replaceAll("[^0-9]", "")));
                case "--memory" -> builder.withDetail(Constants.DOCKER_FLAG_MEMORY_MB, parseMemoryStringToMB(value));
                case "--memory-swap" -> builder.withDetail(Constants.DOCKER_FLAG_MEMORY_SWAP_MB, parseMemoryStringToMB(value));
            }
        }

    }

    private static long parseMemoryStringToMB(String memoryString) {
        if (memoryString.endsWith("g\"")) {
            return Long.parseLong(memoryString.replaceAll("[^0-9]", "")) * 1024L;
        }
        else if (memoryString.endsWith("m\"")) {
            return Long.parseLong(memoryString.replaceAll("[^0-9]", ""));
        }
        else if (memoryString.endsWith("k\"")) {
            return Long.parseLong(memoryString.replaceAll("[^0-9]", "")) / 1024L;
        }
        else {
            return Long.parseLong(memoryString);
        }
    }

}
