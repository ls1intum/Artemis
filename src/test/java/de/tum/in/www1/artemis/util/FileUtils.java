package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;

import org.springframework.util.ResourceUtils;

public class FileUtils {

    /**
     * @param path path relative to the test resources folder complaint
     * @return string representation of given file
     * @throws IOException if the resource cannot be loaded
     */
    public static String loadFileFromResources(String path) throws IOException {
        java.io.File file = ResourceUtils.getFile("classpath:" + path);
        StringBuilder builder = new StringBuilder();
        Files.lines(file.toPath()).forEach(builder::append);
        assertThat(builder.toString()).as("file has been correctly read from file").isNotEqualTo("");
        return builder.toString();
    }
}
