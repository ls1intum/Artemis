package de.tum.cit.aet.artemis.programming.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestFileUtil {

    public static void writeEmptyJsonFileToPath(Path path) throws Exception {
        var fileContent = "{}";
        path.toFile().getParentFile().mkdirs();
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(fileContent);
        }
    }
}
