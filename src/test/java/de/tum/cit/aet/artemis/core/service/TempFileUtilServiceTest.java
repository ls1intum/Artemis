package de.tum.cit.aet.artemis.core.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TempFileUtilServiceTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void replaceFileAtomicallyCreatesAndReplacesTargetWithoutLeavingTemporaryFiles() throws Exception {
        TempFileUtilService tempFileUtilService = new TempFileUtilService(tempDirectory);
        Path targetPath = tempDirectory.resolve("student").resolve("student-version.pdf");

        tempFileUtilService.replaceFileAtomically(targetPath, "first version".getBytes(UTF_8));
        assertThat(Files.readString(targetPath)).isEqualTo("first version");

        tempFileUtilService.replaceFileAtomically(targetPath, "second version".getBytes(UTF_8));
        assertThat(Files.readString(targetPath)).isEqualTo("second version");
        try (var files = Files.list(targetPath.getParent())) {
            assertThat(files.map(Path::getFileName).map(Path::toString)).containsExactly("student-version.pdf");
        }
    }
}
