package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

class CompatibleVersionsInfoConfigurationTest {

    @Test
    void testContribute() {
        final var info = getInfo();

        assertThat(info.getDetails()).containsKey("compatibleVersions");

        Object value = info.getDetails().get("compatibleVersions");
        assertThat(value).isInstanceOf(ArtemisCompatibleVersionsConfiguration.class);

        ArtemisCompatibleVersionsConfiguration resultConfig = (ArtemisCompatibleVersionsConfiguration) value;

        assertThat(resultConfig.getAndroid().getMin()).isEqualTo("1.2.0");
        assertThat(resultConfig.getAndroid().getRecommended()).isEqualTo("1.2.0");

        assertThat(resultConfig.getIos().getMin()).isEqualTo("1.6.1");
        assertThat(resultConfig.getIos().getRecommended()).isEqualTo("1.6.1");
    }

    private static Info getInfo() {
        ArtemisCompatibleVersionsConfiguration config = new ArtemisCompatibleVersionsConfiguration();

        ArtemisCompatibleVersionsConfiguration.Platform android = new ArtemisCompatibleVersionsConfiguration.Platform();
        android.setMin("1.2.0");
        android.setRecommended("1.2.0");
        config.setAndroid(android);

        ArtemisCompatibleVersionsConfiguration.Platform ios = new ArtemisCompatibleVersionsConfiguration.Platform();
        ios.setMin("1.6.1");
        ios.setRecommended("1.6.1");
        config.setIos(ios);

        CompatibleVersionsInfoContributor contributor = new CompatibleVersionsInfoContributor(config);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        return builder.build();
    }
}
