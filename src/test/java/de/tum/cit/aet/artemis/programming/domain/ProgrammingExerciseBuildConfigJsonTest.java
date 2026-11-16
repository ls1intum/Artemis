package de.tum.cit.aet.artemis.programming.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * The value used to build and check build plan access addresses is only read by the server, so it must not be part of the JSON representation of a build configuration.
 */
class ProgrammingExerciseBuildConfigJsonTest {

    private final JsonMapper jsonMapper = JsonObjectMapper.get();

    @Test
    void buildPlanAccessValueIsNotSerialized() {
        var buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.generateAndSetBuildPlanAccessSecret();

        String json = jsonMapper.writeValueAsString(buildConfig);

        assertThat(buildConfig.hasBuildPlanAccessSecretSet()).isTrue();
        assertThat(json).doesNotContain("buildPlanAccessSecret").doesNotContain(buildConfig.getBuildPlanAccessSecret());
    }

    @Test
    void buildPlanAccessValueInIncomingJsonIsIgnored() {
        var buildConfig = jsonMapper.readValue("{\"buildPlanAccessSecret\":\"client-chosen\"}", ProgrammingExerciseBuildConfig.class);

        assertThat(buildConfig.getBuildPlanAccessSecret()).isNull();
    }
}
