package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

class CompatibleVersionsInfoConfigurationTest {

    @Test
    void testContribute() {
        ArtemisCompatibleVersionsConfiguration config = new ArtemisCompatibleVersionsConfiguration();

        ArtemisCompatibleVersionsConfiguration.Platform android = new ArtemisCompatibleVersionsConfiguration.Platform();
        android.setMin("619");
        android.setRecommended("619");
        config.setAndroid(android);

        ArtemisCompatibleVersionsConfiguration.Platform ios = new ArtemisCompatibleVersionsConfiguration.Platform();
        ios.setMin("1.6.1");
        ios.setRecommended("1.6.1");
        config.setIos(ios);

        CompatibleVersionsInfoContributor contributor = new CompatibleVersionsInfoContributor(config);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        assertThat(info.getDetails()).containsKey("compatible-versions");

        Object value = info.getDetails().get("compatible-versions");
        assertThat(value).isInstanceOf(ArtemisCompatibleVersionsConfiguration.class);

        ArtemisCompatibleVersionsConfiguration resultConfig = (ArtemisCompatibleVersionsConfiguration) value;

        assertThat(resultConfig.getAndroid().getMin()).isEqualTo("619");
        assertThat(resultConfig.getAndroid().getRecommended()).isEqualTo("619");

        assertThat(resultConfig.getIos().getMin()).isEqualTo("1.6.1");
        assertThat(resultConfig.getIos().getRecommended()).isEqualTo("1.6.1");
    }
}
