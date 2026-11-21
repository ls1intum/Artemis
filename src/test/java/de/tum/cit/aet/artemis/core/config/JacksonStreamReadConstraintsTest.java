package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class JacksonStreamReadConstraintsTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void jsonParserEnforcesConfiguredStreamReadConstraints() {
        var constraints = jsonMapper.tokenStreamFactory().streamReadConstraints();

        assertThat(constraints.getMaxDocumentLength()).isEqualTo(20L * 1024 * 1024);
        assertThat(constraints.getMaxStringLength()).isEqualTo(2 * 1024 * 1024);
        assertThat(constraints.getMaxNestingDepth()).isEqualTo(500);
    }
}
