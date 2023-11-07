package de.tum.in.www1.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.connectors.aeolus.*;

class AeolusTest {

    private AeolusDefinition aeolusDefinition;

    @BeforeEach
    void setup() {
        aeolusDefinition = new AeolusDefinition();
        aeolusDefinition.setApi("v.0.0.1");
        Metadata metadata = new Metadata();
        metadata.setAuthor("author");
        metadata.setId("id");
        metadata.setDescription("description");
        metadata.setDocker(new DockerConfig());
        metadata.setName("name");
        metadata.setGitCredentials("gitCredentials");
        aeolusDefinition.setMetadata(metadata);
        SerializedAction scriptAction = new SerializedAction();
        scriptAction.setName("name");
        scriptAction.setScript("script");
        scriptAction.setEnvironment(Map.of("key", "value"));
        scriptAction.setParameters(Map.of("key", "value"));
        scriptAction.setRunAlways(true);

        SerializedAction platformAction = new SerializedAction();
        platformAction.setName("name");
        platformAction.setKind("junit");
        platformAction.setType("platform");
        platformAction.setEnvironment(Map.of("key", "value"));
        platformAction.setParameters(Map.of("key", "value"));
        platformAction.setRunAlways(true);

        List<SerializedAction> actions = List.of(scriptAction, platformAction);
        aeolusDefinition.setActions(actions);
    }

    @Test
    void testToWindfile() {
        Windfile windfile = Windfile.toWindfile(aeolusDefinition);
        assertThat(windfile).isNotNull();
        assertThat(windfile.getActions()).hasSize(2);
        assertThat(windfile.getMetadata()).isEqualTo(aeolusDefinition.getMetadata());
        assertThat(windfile.getApi()).isEqualTo(aeolusDefinition.getApi());
        for (int i = 0; i < windfile.getActions().size(); i++) {
            assertThat(windfile.getActions().get(i).getName()).isEqualTo(aeolusDefinition.getActions().get(i).getName());
            assertThat(windfile.getActions().get(i).getEnvironment()).isEqualTo(aeolusDefinition.getActions().get(i).getEnvironment());
            assertThat(windfile.getActions().get(i).getParameters()).isEqualTo(aeolusDefinition.getActions().get(i).getParameters());
            assertThat(windfile.getActions().get(i).isRunAlways()).isEqualTo(aeolusDefinition.getActions().get(i).isRunAlways());
        }
    }
}
