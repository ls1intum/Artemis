package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIInfoContributor;

class LocalCIInfoContributorTest {

    @Test
    void testContribute() {
        Info.Builder builder = new Info.Builder();
        LocalCIInfoContributor localCIInfoContributor = new LocalCIInfoContributor(new ProgrammingLanguageConfiguration());
        ReflectionTestUtils.setField(localCIInfoContributor, "minInstructorBuildTimeoutOption", 10);
        ReflectionTestUtils.setField(localCIInfoContributor, "maxInstructorBuildTimeoutOption", 240);
        ReflectionTestUtils.setField(localCIInfoContributor, "defaultInstructorBuildTimeoutOption", 120);
        List<String> networks = List.of("none", "custom-network-name");
        ReflectionTestUtils.setField(localCIInfoContributor, "networks", networks);

        try {
            localCIInfoContributor.contribute(builder);
        }
        catch (NullPointerException e) {
        }

        Info info = builder.build();
        assertThat(info.getDetails().get("buildTimeoutMin")).isEqualTo(10);
        assertThat(info.getDetails().get("buildTimeoutMax")).isEqualTo(240);
        assertThat(info.getDetails().get("buildTimeoutDefault")).isEqualTo(120);
        assertThat(info.getDetails().get("allowedCustomDockerNetworks")).isEqualTo(networks);
    }
}
