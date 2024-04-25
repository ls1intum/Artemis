package de.tum.in.www1.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusRepository;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusResult;
import de.tum.in.www1.artemis.service.connectors.aeolus.DockerConfig;
import de.tum.in.www1.artemis.service.connectors.aeolus.PlatformAction;
import de.tum.in.www1.artemis.service.connectors.aeolus.ScriptAction;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;
import de.tum.in.www1.artemis.service.connectors.aeolus.WindfileMetadata;

class AeolusTest {

    private Windfile windfile;

    @BeforeEach
    void setup() {
        windfile = new Windfile();
        WindfileMetadata metadata = new WindfileMetadata();
        metadata.setAuthor("author");
        windfile.setApi("v0.0.1");
        metadata.setId("id");
        metadata.setDescription("description");
        DockerConfig dockerConfig = new DockerConfig();
        dockerConfig.setImage("image");
        dockerConfig.setVolumes(List.of("host:container"));
        dockerConfig.setParameters(List.of("--param1", "--param2"));
        dockerConfig.setTag("tag");
        metadata.setDocker(dockerConfig);
        metadata.setName("name");
        metadata.setGitCredentials("gitCredentials");
        windfile.setMetadata(metadata);

        ScriptAction scriptAction = new ScriptAction();
        scriptAction.setName("scriptAction");
        scriptAction.setRunAlways(true);
        scriptAction.setScript("script");
        scriptAction.setEnvironment(Map.of("key", "value"));
        scriptAction.setParameters(Map.of("key", "value"));
        AeolusResult scriptResult = new AeolusResult("junit", "text.xml", "ignore", "junit", false);
        scriptAction.setResults(List.of(scriptResult));
        assertThat(scriptAction.getResults().get(0).getName()).isEqualTo("junit");
        assertThat(scriptAction.getResults().get(0).getPath()).isEqualTo("text.xml");
        assertThat(scriptAction.getResults().get(0).getIgnore()).isEqualTo("ignore");
        assertThat(scriptAction.getResults().get(0).getType()).isEqualTo("junit");
        assertThat(scriptAction.getResults().get(0).isBefore()).isEqualTo(false);

        PlatformAction platformAction = new PlatformAction();
        platformAction.setName("platformAction");
        platformAction.setWorkdir("workdir");
        platformAction.setRunAlways(true);
        platformAction.setPlatform("jenkins");
        platformAction.setPlatform("jenkins");
        platformAction.setKind("junit");
        AeolusResult result = new AeolusResult();
        result.setName("name");
        result.setPath("path");
        result.setIgnore("ignore");
        result.setType("type");
        result.setBefore(true);
        platformAction.setResults(List.of(result));
        assertThat(platformAction.getResults().get(0).getName()).isEqualTo("name");
        assertThat(platformAction.getResults().get(0).getPath()).isEqualTo("path");
        assertThat(platformAction.getResults().get(0).getIgnore()).isEqualTo("ignore");
        assertThat(platformAction.getResults().get(0).getType()).isEqualTo("type");
        assertThat(platformAction.getResults().get(0).isBefore()).isEqualTo(true);

        windfile.setActions(List.of(scriptAction, platformAction));
    }

    @Test
    void testGetResults() {
        var results = windfile.getResults();
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get(0).getName()).isEqualTo("junit");
        assertThat(results.get(0).getPath()).isEqualTo("text.xml");
        assertThat(results.get(0).getIgnore()).isEqualTo("ignore");
        assertThat(results.get(0).getType()).isEqualTo("junit");
        assertThat(results.get(0).isBefore()).isEqualTo(false);
        assertThat(results.get(1).getName()).isEqualTo("name");
        assertThat(results.get(1).getPath()).isEqualTo("path");
        assertThat(results.get(1).getIgnore()).isEqualTo("ignore");
        assertThat(results.get(1).getType()).isEqualTo("type");
        assertThat(results.get(1).isBefore()).isEqualTo(true);
    }

    @Test
    void testWindfileGetterAndSetter() {
        assertThat(windfile.getApi()).isEqualTo("v0.0.1");
        assertThat(windfile.getMetadata().getAuthor()).isEqualTo("author");
        assertThat(windfile.getMetadata().getId()).isEqualTo("id");
        assertThat(windfile.getMetadata().getDescription()).isEqualTo("description");
        assertThat(windfile.getMetadata().getDocker().getImage()).isEqualTo("image");
        assertThat(windfile.getMetadata().getDocker().getVolumes()).isEqualTo(List.of("host:container"));
        assertThat(windfile.getMetadata().getDocker().getParameters()).isEqualTo(List.of("--param1", "--param2"));
        assertThat(windfile.getMetadata().getDocker().getTag()).isEqualTo("tag");
        assertThat(windfile.getMetadata().getName()).isEqualTo("name");
        assertThat(windfile.getMetadata().getGitCredentials()).isEqualTo("gitCredentials");
        assertThat(windfile.getActions().get(0).getName()).isEqualTo("scriptAction");
        assertThat(windfile.getActions().get(0).isRunAlways()).isEqualTo(true);
        ScriptAction scriptAction = (ScriptAction) windfile.getActions().get(0);
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
        assertThat(windfile.getMetadata().getId()).isEqualTo("id");
        assertThat(windfile.getMetadata().getDescription()).isEqualTo("description");
        assertThat(windfile.getMetadata().getName()).isEqualTo("name");
        assertThat(windfile.getRepositories().get("key")).isEqualTo(aeolusRepository);
        assertThat(windfile.getMetadata().getGitCredentials()).isEqualTo("gitCredentials");
        assertThat(windfile.getMetadata().getResultHook()).isEqualTo("resultHook");
        assertThat(windfile.getMetadata().getResultHookCredentials()).isEqualTo("resultHookCredentials");
    }

    @Test
    void testSettersWithMetadata() {
        windfile.setMetadata(null);
        windfile.setApi("v0.0.1");
        assertThat(windfile.getApi()).isEqualTo("v0.0.1");
        windfile.setId("newId");
        assertThat(windfile.getMetadata().getId()).isEqualTo("newId");
        windfile.setDescription("newDescription");
        assertThat(windfile.getMetadata().getDescription()).isEqualTo("newDescription");
        windfile.setName("newName");
        assertThat(windfile.getMetadata().getName()).isEqualTo("newName");
        windfile.setMetadata(null);
        windfile.setResultHook("newResultHook");
        assertThat(windfile.getMetadata().getResultHook()).isEqualTo("newResultHook");
        windfile.setGitCredentials("newGitCredentials");
        assertThat(windfile.getMetadata().getGitCredentials()).isEqualTo("newGitCredentials");
    }

    @Test
    void testAeolusRepository() {
        AeolusRepository aeolusRepository = new AeolusRepository("oldurl", "oldbranch", "oldPath");
        assertThat(aeolusRepository.getBranch()).isEqualTo("oldbranch");
        assertThat(aeolusRepository.getPath()).isEqualTo("oldPath");
        assertThat(aeolusRepository.getUrl()).isEqualTo("oldurl");
        aeolusRepository.setBranch("branch");
        assertThat(aeolusRepository.getBranch()).isEqualTo("branch");
        aeolusRepository.setUrl("url");
        assertThat(aeolusRepository.getUrl()).isEqualTo("url");
        aeolusRepository.setPath("path");
        assertThat(aeolusRepository.getPath()).isEqualTo("path");
    }

    @Test
    void testImageTagCombinations() {
        DockerConfig dockerConfig = new DockerConfig();
        dockerConfig.setImage("image");
        dockerConfig.setTag("tag");
        assertThat(dockerConfig.getFullImageName()).isEqualTo("image:tag");

        dockerConfig.setTag(null);
        assertThat(dockerConfig.getFullImageName()).isEqualTo("image:latest");

        dockerConfig.setImage("image:tag");
        dockerConfig.setTag("notshown");
        assertThat(dockerConfig.getFullImageName()).isEqualTo("image:tag");

        dockerConfig.setImage(null);
        assertThat(dockerConfig.getFullImageName()).isNull();
    }
}
