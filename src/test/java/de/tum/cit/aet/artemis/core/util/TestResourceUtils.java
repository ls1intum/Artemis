package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.assertj.core.data.TemporalUnitOffset;
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

    @NotNull
    public static TemporalUnitOffset HalfSecond() {
        return within(500, ChronoUnit.MILLIS);
    }
}
