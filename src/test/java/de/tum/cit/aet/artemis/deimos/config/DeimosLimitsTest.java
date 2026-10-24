package de.tum.cit.aet.artemis.deimos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.actuate.info.Info;
import org.springframework.core.io.FileSystemResource;

class DeimosLimitsTest {

    /**
     * The {@code @Value} fallbacks all refer to {@link DeimosLimits}; {@code application.yml} repeats the values for
     * administrators. This keeps the two from drifting apart.
     */
    @Test
    void applicationYmlShipsTheDefaultsOfDeimosLimits() {
        var yaml = new YamlPropertiesFactoryBean();
        // Read from the source tree: the test classpath carries its own config/application.yml, which would shadow it.
        yaml.setResources(new FileSystemResource("src/main/resources/config/application.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.get(DeimosLimits.MAX_FILE_INPUT_BYTES_PROPERTY)).isEqualTo(DeimosLimits.DEFAULT_MAX_FILE_INPUT_BYTES);
        assertThat(properties.get(DeimosLimits.MAX_FILE_DIFF_BYTES_PROPERTY)).isEqualTo(DeimosLimits.DEFAULT_MAX_FILE_DIFF_BYTES);
        assertThat(properties.get(DeimosLimits.MAX_PAYLOAD_BYTES_PROPERTY)).isEqualTo(DeimosLimits.DEFAULT_MAX_PAYLOAD_BYTES);
        assertThat(properties.get(DeimosLimits.FINAL_STATE_RESERVED_BYTES_PROPERTY)).isEqualTo(DeimosLimits.DEFAULT_FINAL_STATE_RESERVED_BYTES);
        assertThat(((Number) properties.get(DeimosLimits.MAX_PARTICIPATIONS_PER_RUN_PROPERTY)).longValue()).isEqualTo(DeimosLimits.DEFAULT_MAX_PARTICIPATIONS_PER_RUN);
        assertThat(((Number) properties.get(DeimosLimits.MAX_WINDOW_DAYS_PROPERTY)).longValue()).isEqualTo(DeimosLimits.DEFAULT_MAX_WINDOW_DAYS);
    }

    @Test
    void defaultsSatisfyTheStartupValidation() {
        assertThat(DeimosLimits.DEFAULT_MAX_FILE_INPUT_BYTES).isGreaterThanOrEqualTo(DeimosLimits.MIN_BYTE_LIMIT);
        assertThat(DeimosLimits.DEFAULT_FINAL_STATE_RESERVED_BYTES).isGreaterThanOrEqualTo(DeimosLimits.MIN_BYTE_LIMIT).isLessThan(DeimosLimits.DEFAULT_MAX_PAYLOAD_BYTES);
        assertThat(DeimosLimits.DEFAULT_MAX_FILE_DIFF_BYTES).isGreaterThanOrEqualTo(DeimosLimits.MIN_BYTE_LIMIT).isLessThanOrEqualTo(DeimosLimits.DEFAULT_MAX_PAYLOAD_BYTES);
    }

    @Test
    void infoContributorPublishesTheConfiguredWindow() {
        var builder = new Info.Builder();

        new DeimosInfoContributor(7).contribute(builder);

        assertThat(builder.build().getDetails()).containsEntry(DeimosInfoContributor.MAX_WINDOW_DAYS_INFO_KEY, 7L);
    }
}
