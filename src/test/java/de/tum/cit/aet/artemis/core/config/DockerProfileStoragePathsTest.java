package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * The Docker profile puts the storage folders on the volume that is mounted at {@code /opt/artemis/data}, which is the one
 * the nodes of a multi-node deployment share. A folder it leaves out stays relative to the working directory of the
 * process, so it is local to one container, and a file that one node wrote cannot be found by another.
 */
class DockerProfileStoragePathsTest {

    private static final String DATA_VOLUME = "/opt/artemis/data/";

    /** Read from the source folder: the test resources carry their own copy of the application configuration, which shadows it on the classpath. */
    private static Map<String, Object> load(String file) throws IOException {
        Map<String, Object> properties = new TreeMap<>();
        for (var source : new YamlPropertySourceLoader().load(file, new FileSystemResource("src/main/resources/config/" + file))) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    properties.put(name, enumerable.getProperty(name));
                }
            }
        }
        return properties;
    }

    @Test
    void everyRelativeStorageFolderIsMovedOntoTheDataVolume() throws IOException {
        var defaults = load("application-artemis.yml");
        var docker = load("application-docker.yml");

        var relativeFolders = defaults.entrySet().stream().filter(entry -> entry.getKey().matches("artemis\\.[a-z-]+-path") && String.valueOf(entry.getValue()).startsWith("./"))
                .map(Map.Entry::getKey).toList();
        assertThat(relativeFolders).as("the storage folders the application configuration defaults to a relative path").isNotEmpty();

        var notOnTheDataVolume = relativeFolders.stream().filter(folder -> !String.valueOf(docker.get(folder)).startsWith(DATA_VOLUME)).toList();
        assertThat(notOnTheDataVolume).as("storage folders the Docker profile leaves local to one container").isEmpty();
    }
}
