package de.tum.cit.aet.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.service.connectors.aeolus.AeolusRepository;
import de.tum.cit.aet.artemis.service.connectors.aeolus.AeolusResult;
import de.tum.cit.aet.artemis.service.connectors.aeolus.DockerConfig;
import de.tum.cit.aet.artemis.service.connectors.aeolus.PlatformAction;
import de.tum.cit.aet.artemis.service.connectors.aeolus.ScriptAction;
import de.tum.cit.aet.artemis.service.connectors.aeolus.Windfile;
import de.tum.cit.aet.artemis.service.connectors.aeolus.WindfileMetadata;

class AeolusTest {

    private Windfile windfile;

    @BeforeEach
    void setup() {
        DockerConfig dockerConfig = new DockerConfig("image", "tag", List.of("host:container"), List.of("--param1", "--param2"));
        WindfileMetadata metadata = new WindfileMetadata("name", "id", "description", "author", "gitCredentials", dockerConfig, null, null);

        windfile = new Windfile();
        windfile.setApi("v0.0.1");
        windfile.setMetadata(metadata);

        ScriptAction scriptAction = new ScriptAction();
        scriptAction.setName("scriptAction");
        scriptAction.setRunAlways(true);
        scriptAction.setScript("script");
        scriptAction.setEnvironment(Map.of("key", "value"));
        scriptAction.setParameters(Map.of("key", "value"));
        AeolusResult scriptResult = new AeolusResult("junit", "text.xml", "ignore", "junit", false);
        scriptAction.setResults(List.of(scriptResult));
        assertThat(scriptAction.getResults().getFirst().name()).isEqualTo("junit");
        assertThat(scriptAction.getResults().getFirst().path()).isEqualTo("text.xml");
        assertThat(scriptAction.getResults().getFirst().ignore()).isEqualTo("ignore");
        assertThat(scriptAction.getResults().getFirst().type()).isEqualTo("junit");
        assertThat(scriptAction.getResults().getFirst().before()).isEqualTo(false);

        PlatformAction platformAction = new PlatformAction();
        platformAction.setName("platformAction");
        platformAction.setWorkdir("workdir");
        platformAction.setRunAlways(true);
        platformAction.setPlatform("jenkins");
        platformAction.setPlatform("jenkins");
        platformAction.setKind("junit");
        AeolusResult result = new AeolusResult("name", "path", "ignore", "type", true);
        platformAction.setResults(List.of(result));
        assertThat(platformAction.getResults().getFirst().name()).isEqualTo("name");
        assertThat(platformAction.getResults().getFirst().path()).isEqualTo("path");
        assertThat(platformAction.getResults().getFirst().ignore()).isEqualTo("ignore");
        assertThat(platformAction.getResults().getFirst().type()).isEqualTo("type");
        assertThat(platformAction.getResults().getFirst().before()).isEqualTo(true);

        windfile.setActions(List.of(scriptAction, platformAction));
    }

    @Test
    void testGetResults() {
        var results = windfile.getResults();
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
        assertThat(windfile.getApi()).isEqualTo("v0.0.1");
        assertThat(windfile.getMetadata().author()).isEqualTo("author");
        assertThat(windfile.getMetadata().id()).isEqualTo("id");
        assertThat(windfile.getMetadata().description()).isEqualTo("description");
        DockerConfig dockerConfig = new DockerConfig("image", "tag", List.of("host:container"), List.of("--param1", "--param2"));
        assertThat(windfile.getMetadata().docker()).isEqualTo(dockerConfig);
        assertThat(windfile.getMetadata().name()).isEqualTo("name");
        assertThat(windfile.getMetadata().gitCredentials()).isEqualTo("gitCredentials");
        assertThat(windfile.getActions().getFirst().getName()).isEqualTo("scriptAction");
        assertThat(windfile.getActions().getFirst().isRunAlways()).isEqualTo(true);
        ScriptAction scriptAction = (ScriptAction) windfile.getActions().getFirst();
        assertThat(scriptAction.getScript()).isEqualTo("script");
        assertThat(scriptAction.getEnvironment()).isEqualTo(Map.of("key", "value"));
        assertThat(scriptAction.getParameters()).isEqualTo(Map.of("key", "value"));
        PlatformAction platformAction = (PlatformAction) windfile.getActions().get(1);
        platformAction.setKind("junit");
        assertThat(platformAction.getKind()).isEqualTo("junit");
        platformAction.setType("type");
        assertThat(platformAction.getType()).isEqualTo("type");
        assertThat(platformAction.getWorkdir()).isEqualTo("workdir");
        assertThat(platformAction.getName()).isEqualTo("platformAction");
        assertThat(platformAction.isRunAlways()).isEqualTo(true);
        assertThat(platformAction.getPlatform()).isEqualTo("jenkins");
    }

    @Test
    void testSettersWithoutMetadata() {
        windfile.setMetadata(null);
        AeolusRepository aeolusRepository = new AeolusRepository("url", "branch", "path");
        windfile.setPreProcessingMetadata("id", "name", "gitCredentials", "resultHook", "description", Map.of("key", aeolusRepository), "resultHookCredentials");
        assertThat(windfile.getMetadata().id()).isEqualTo("id");
        assertThat(windfile.getMetadata().description()).isEqualTo("description");
        assertThat(windfile.getMetadata().name()).isEqualTo("name");
        assertThat(windfile.getRepositories().get("key")).isEqualTo(aeolusRepository);
        assertThat(windfile.getMetadata().gitCredentials()).isEqualTo("gitCredentials");
        assertThat(windfile.getMetadata().resultHook()).isEqualTo("resultHook");
        assertThat(windfile.getMetadata().resultHookCredentials()).isEqualTo("resultHookCredentials");
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
