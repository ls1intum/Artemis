package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class LocalVCRepositoryResolverServiceTest {

    private final LocalVCRepositoryResolverService repositoryResolver = new LocalVCRepositoryResolverService();

    @Test
    void resolveRepository_withAPathEscapingTheBaseDirectory_isNotFound(@TempDir java.nio.file.Path baseDir) {
        ReflectionTestUtils.setField(repositoryResolver, "localVCBasePath", baseDir);

        // A path that climbs out of the base directory must be refused before the file system is touched, not resolved to whatever lies outside.
        assertThatExceptionOfType(RepositoryNotFoundException.class).isThrownBy(() -> repositoryResolver.resolveRepository("../../../../../../etc/passwd"));
    }

    @Test
    void resolveRepository_withAPathThatDoesNotExist_isNotFound(@TempDir java.nio.file.Path baseDir) {
        ReflectionTestUtils.setField(repositoryResolver, "localVCBasePath", baseDir);

        assertThatExceptionOfType(RepositoryNotFoundException.class).isThrownBy(() -> repositoryResolver.resolveRepository("ABC/abc-exercise.git"));
    }

    @Test
    void resolveRepository_withASymlinkLeadingOutOfTheBaseDirectory_isNotFound(@TempDir java.nio.file.Path baseDir, @TempDir java.nio.file.Path outside) throws Exception {
        // The path itself stays inside the base directory, so only resolving symlinks reveals that the repository is elsewhere.
        java.nio.file.Files.createDirectories(outside.resolve("escaped.git"));
        java.nio.file.Path project = java.nio.file.Files.createDirectories(baseDir.resolve("ABC"));
        java.nio.file.Files.createSymbolicLink(project.resolve("abc-exercise.git"), outside.resolve("escaped.git"));
        ReflectionTestUtils.setField(repositoryResolver, "localVCBasePath", baseDir);

        assertThatExceptionOfType(RepositoryNotFoundException.class).isThrownBy(() -> repositoryResolver.resolveRepository("ABC/abc-exercise.git"));
    }

    @Test
    void resolveRepository_withCarriageReturnsInThePath_isNotFoundAndLogsNoLineBreak(@TempDir java.nio.file.Path baseDir) {
        // A repository path comes straight from the request. Logging it unchanged would let a caller forge log lines (CWE-117), so it is sanitised before it is logged.
        ReflectionTestUtils.setField(repositoryResolver, "localVCBasePath", baseDir);
        Logger logger = (Logger) LoggerFactory.getLogger(LocalVCRepositoryResolverService.class);
        ListAppender<ILoggingEvent> loggedEvents = new ListAppender<>();
        loggedEvents.start();
        logger.addAppender(loggedEvents);

        try {
            assertThatExceptionOfType(RepositoryNotFoundException.class).isThrownBy(() -> repositoryResolver.resolveRepository("ABC\r\ninjected/abc-exercise.git"));

            assertThat(loggedEvents.list).as("the failed lookup is logged").isNotEmpty();
            assertThat(loggedEvents.list).allSatisfy(
                    event -> assertThat(event.getFormattedMessage()).as("no log line may carry a line break from the request").doesNotContain("\r").doesNotContain("\n"));
            assertThat(loggedEvents.list).as("the path is still identifiable in the log, with the line breaks replaced")
                    .anyMatch(event -> event.getFormattedMessage().contains("ABC__injected/abc-exercise.git"));
        }
        finally {
            logger.detachAppender(loggedEvents);
        }
    }
}
