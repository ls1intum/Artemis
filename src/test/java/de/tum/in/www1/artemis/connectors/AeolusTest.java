package de.tum.in.www1.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.connectors.aeolus.*;

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

        PlatformAction platformAction = new PlatformAction();
        platformAction.setName("platformAction");
        platformAction.setRunAlways(true);
        platformAction.setPlatform("bamboo");

        windfile.setActions(List.of(scriptAction, platformAction));
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
        assertThat(platformAction.getName()).isEqualTo("platformAction");
        assertThat(platformAction.isRunAlways()).isEqualTo(true);
        assertThat(platformAction.getPlatform()).isEqualTo("bamboo");
    }

    @Test
    void testSettersWithoutMetadata() {
        windfile.setMetadata(null);
        windfile.setApi("v0.0.1");
        assertThat(windfile.getApi()).isEqualTo("v0.0.1");
        windfile.setMetadata(null);
        windfile.setId("id");
        assertThat(windfile.getMetadata().getId()).isEqualTo("id");
        windfile.setMetadata(null);
        windfile.setDescription("description");
        assertThat(windfile.getMetadata().getDescription()).isEqualTo("description");
        windfile.setDescription("newDescription");
        assertThat(windfile.getMetadata().getDescription()).isEqualTo("newDescription");
        windfile.setMetadata(null);
        windfile.setName("name");
        assertThat(windfile.getMetadata().getName()).isEqualTo("name");
        windfile.setName("newName");
        assertThat(windfile.getMetadata().getName()).isEqualTo("newName");
        windfile.setMetadata(null);
        windfile.setResultHook("resultHook");
        assertThat(windfile.getMetadata().getResultHook()).isEqualTo("resultHook");
        windfile.setResultHook("newResultHook");
        assertThat(windfile.getMetadata().getResultHook()).isEqualTo("newResultHook");
        AeolusRepository aeolusRepository = new AeolusRepository("url", "branch", "path");
        Map<String, AeolusRepository> map = Map.of("key", aeolusRepository);
        windfile.setRepositories(map);
        assertThat(windfile.getRepositories().get("key")).isEqualTo(aeolusRepository);
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
    }
}
