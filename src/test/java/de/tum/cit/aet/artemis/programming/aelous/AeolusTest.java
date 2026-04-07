package de.tum.cit.aet.artemis.programming.aelous;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.dto.aeolus.DockerConfig;

class AeolusTest {

    @Test
    void testImageTagCombinations() {
        DockerConfig dockerConfig = new DockerConfig("image", "tag", null, null);
        assertThat(dockerConfig.getFullImageName()).isEqualTo("image:tag");

        dockerConfig = new DockerConfig("image", null, null, null);
        assertThat(dockerConfig.getFullImageName()).isEqualTo("image:latest");

        dockerConfig = new DockerConfig("image:tag", "notshown", null, null);
        assertThat(dockerConfig.getFullImageName()).isEqualTo("image:tag");

        dockerConfig = new DockerConfig(null, null, null, null);
        assertThat(dockerConfig.getFullImageName()).isNull();
    }
}
