package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LocalCIInfoContributor.class);

    private static final Pattern WHOLE_AMOUNT = Pattern.compile("\\d+");

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
                case "--cpus" -> parseAmount(value).ifPresent(cpuCount -> builder.withDetail(Constants.DOCKER_FLAG_CPUS, cpuCount));
                case "--memory" -> parseMemoryStringToMB(value).ifPresent(memory -> builder.withDetail(Constants.DOCKER_FLAG_MEMORY_MB, memory));
                case "--memory-swap" -> parseMemoryStringToMB(value).ifPresent(memory -> builder.withDetail(Constants.DOCKER_FLAG_MEMORY_SWAP_MB, memory));
            }
        }

    }

    /**
     * Reads a Docker memory value such as {@code "2g"} and converts it into the megabytes the client renders.
     *
     * <p>
     * A value that is not a whole amount, with or without a unit, is left out rather than published as something it is
     * not: stripping everything but the digits would turn a misconfigured {@code -1024} into a limit of 1024 MB, and the
     * exercise editor would then offer that as the default. Throwing instead is not an option either, because this
     * builds the info endpoint the client needs to render anything at all.
     *
     * @param memoryString the value of the Docker flag, as configured and quoted by {@link ProgrammingLanguageConfiguration}
     * @return the amount in megabytes, or empty if the value is not a whole amount
     */
    private static Optional<Long> parseMemoryStringToMB(String memoryString) {
        String value = unquote(memoryString);
        if (value.endsWith("g")) {
            return parseAmount(value.substring(0, value.length() - 1)).map(amount -> amount * 1024L);
        }
        if (value.endsWith("m")) {
            return parseAmount(value.substring(0, value.length() - 1));
        }
        if (value.endsWith("k")) {
            return parseAmount(value.substring(0, value.length() - 1)).map(amount -> amount / 1024L);
        }
        // A flag configured without a unit is already a plain megabyte count.
        return parseAmount(value);
    }

    private static Optional<Long> parseAmount(String amount) {
        String value = unquote(amount);
        if (!WHOLE_AMOUNT.matcher(value).matches()) {
            log.warn("Ignoring the Docker flag value '{}' because it is not a whole amount", value.replaceAll("[\\r\\n]", "_"));
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    /** {@link ProgrammingLanguageConfiguration} quotes every flag value so that a value containing spaces is not split. */
    private static String unquote(String value) {
        String trimmed = value.strip();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

}
