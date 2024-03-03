package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

import org.springframework.util.ResourceUtils;

public class TestResourceUtils {

    /**
     * @param path path relative to the test resources folder complaint
     * @return string representation of given file
     * @throws IOException if the resource cannot be loaded
     */
    public static String loadFileFromResources(String path) throws IOException {
        java.io.File file = ResourceUtils.getFile("classpath:" + path);
        try (var lines = Files.lines(file.toPath())) {
            String result = lines.collect(Collectors.joining());
            assertThat(result).as("file has been correctly read from file").isNotBlank();
            return result;
        }
    }
}
