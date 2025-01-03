package de.tum.cit.aet.artemis.programming.aelous;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.dto.aeolus.AeolusRepository;
import de.tum.cit.aet.artemis.programming.dto.aeolus.AeolusResult;
import de.tum.cit.aet.artemis.programming.dto.aeolus.DockerConfig;
import de.tum.cit.aet.artemis.programming.dto.aeolus.PlatformAction;
import de.tum.cit.aet.artemis.programming.dto.aeolus.ScriptAction;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.dto.aeolus.WindfileMetadata;

class AeolusTest {

    private Windfile windfile;

    @BeforeEach
    void setup() {
        DockerConfig dockerConfig = new DockerConfig("image", "tag", List.of("host:container"), List.of("--param1", "--param2"));
        WindfileMetadata metadata = new WindfileMetadata("name", "id", "description", "author", "gitCredentials", dockerConfig, null, null);

        AeolusResult scriptResult = new AeolusResult("junit", "text.xml", "ignore", "junit", false);
        ScriptAction scriptAction = new ScriptAction("scriptAction", Map.of("key", "value"), Map.of("key", "value"), List.of(scriptResult), null, true, null, "script");
        assertThat(scriptAction.results().getFirst().name()).isEqualTo("junit");
        assertThat(scriptAction.results().getFirst().path()).isEqualTo("text.xml");
        assertThat(scriptAction.results().getFirst().ignore()).isEqualTo("ignore");
        assertThat(scriptAction.results().getFirst().type()).isEqualTo("junit");
        assertThat(scriptAction.results().getFirst().before()).isEqualTo(false);

        AeolusResult result = new AeolusResult("name", "path", "ignore", "type", true);
        PlatformAction platformAction = new PlatformAction("platformAction", Map.of("key", "value"), Map.of("key", "value"), List.of(result), "workdir", true, "jenkins", "junit",
                "type");
        assertThat(platformAction.results().getFirst().name()).isEqualTo("name");
        assertThat(platformAction.results().getFirst().path()).isEqualTo("path");
        assertThat(platformAction.results().getFirst().ignore()).isEqualTo("ignore");
        assertThat(platformAction.results().getFirst().type()).isEqualTo("type");
        assertThat(platformAction.results().getFirst().before()).isEqualTo(true);

        windfile = new Windfile("v0.0.1", metadata, List.of(scriptAction, platformAction), null);
    }

    @Test
    void testGetResults() {
        var results = windfile.results();
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.getFirst().name()).isEqualTo("junit");
        assertThat(results.getFirst().path()).isEqualTo("text.xml");
        assertThat(results.getFirst().ignore()).isEqualTo("ignore");
        assertThat(results.getFirst().type()).isEqualTo("junit");
        assertThat(results.getFirst().before()).isEqualTo(false);
        assertThat(results.get(1).name()).isEqualTo("name");
        assertThat(results.get(1).path()).isEqualTo("path");
        assertThat(results.get(1).ignore()).isEqualTo("ignore");
        assertThat(results.get(1).type()).isEqualTo("type");
        assertThat(results.get(1).before()).isEqualTo(true);
    }

    @Test
    void testWindfileGetterAndSetter() {
        assertThat(windfile.api()).isEqualTo("v0.0.1");
        assertThat(windfile.metadata().author()).isEqualTo("author");
        assertThat(windfile.metadata().id()).isEqualTo("id");
        assertThat(windfile.metadata().description()).isEqualTo("description");
        DockerConfig dockerConfig = new DockerConfig("image", "tag", List.of("host:container"), List.of("--param1", "--param2"));
        assertThat(windfile.metadata().docker()).isEqualTo(dockerConfig);
        assertThat(windfile.metadata().name()).isEqualTo("name");
        assertThat(windfile.metadata().gitCredentials()).isEqualTo("gitCredentials");
        assertThat(windfile.actions().getFirst().name()).isEqualTo("scriptAction");
        assertThat(windfile.actions().getFirst().runAlways()).isEqualTo(true);
        ScriptAction scriptAction = (ScriptAction) windfile.actions().getFirst();
        assertThat(scriptAction.script()).isEqualTo("script");
        assertThat(scriptAction.environment()).isEqualTo(Map.of("key", "value"));
        assertThat(scriptAction.parameters()).isEqualTo(Map.of("key", "value"));
        PlatformAction platformAction = (PlatformAction) windfile.actions().get(1);
        assertThat(platformAction.workdir()).isEqualTo("workdir");
        assertThat(platformAction.name()).isEqualTo("platformAction");
        assertThat(platformAction.runAlways()).isEqualTo(true);
        assertThat(platformAction.platform()).isEqualTo("jenkins");
    }

    @Test
    void testAeolusRepository() {
        AeolusRepository aeolusRepository = new AeolusRepository("oldurl", "oldbranch", "oldPath");
        assertThat(aeolusRepository.branch()).isEqualTo("oldbranch");
        assertThat(aeolusRepository.path()).isEqualTo("oldPath");
        assertThat(aeolusRepository.url()).isEqualTo("oldurl");
    }

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
