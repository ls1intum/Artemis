package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class RejectedPushCleanupServiceTest {

    @TempDir
    Path base;

    private Repository createRepository() throws Exception {
        Path path = base.resolve("PROJECT").resolve("repo.git");
        Files.createDirectories(path);
        return Git.init().setBare(true).setDirectory(path.toFile()).call().getRepository();
    }

    private static Path looseObject(Repository repository, ObjectId id) {
        String name = id.name();
        return repository.getDirectory().toPath().resolve("objects").resolve(name.substring(0, 2)).resolve(name.substring(2));
    }

    private RejectedPushCleanupService service() {
        RejectedPushCleanupService service = new RejectedPushCleanupService();
        ReflectionTestUtils.setField(service, "localVCBasePath", base);
        return service;
    }

    @Test
    void unreferencedObjectsAreRemovedOnceTheMarkIsOlderThanTheGracePeriod() throws Exception {
        try (Repository repository = createRepository()) {
            ObjectId orphan;
            try (ObjectInserter inserter = repository.newObjectInserter()) {
                orphan = inserter.insert(Constants.OBJ_BLOB, "rejected".getBytes());
                inserter.flush();
            }
            Path object = looseObject(repository, orphan);
            Path marker = repository.getDirectory().toPath().resolve("artemis-rejected-push");
            RejectedPushCleanupService.markForCleanup(repository);
            assertThat(marker).exists();

            // within the grace period nothing happens
            service().cleanup();
            assertThat(object).exists();
            assertThat(marker).exists();

            FileTime old = FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS));
            Files.setLastModifiedTime(marker, old);
            Files.setLastModifiedTime(object, old);
            service().cleanup();
            assertThat(object).doesNotExist();
            // Objects that a collection only made loose are removed by a second pass, so the mark stays for one more round
            assertThat(marker).content().isEqualTo("1");

            service().cleanup();
            assertThat(marker).exists();
            Files.setLastModifiedTime(marker, old);
            service().cleanup();
            assertThat(marker).doesNotExist();
        }
    }

    @Test
    void aClaimThatAnInterruptedRunLeftBehindIsProcessedAgain() throws Exception {
        try (Repository repository = createRepository()) {
            ObjectId orphan;
            try (ObjectInserter inserter = repository.newObjectInserter()) {
                orphan = inserter.insert(Constants.OBJ_BLOB, "rejected".getBytes());
                inserter.flush();
            }
            Path object = looseObject(repository, orphan);
            Path claim = repository.getDirectory().toPath().resolve("artemis-rejected-push.claimed-interrupted");
            Files.writeString(claim, "0");
            FileTime old = FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS));
            Files.setLastModifiedTime(claim, old);
            Files.setLastModifiedTime(object, old);

            service().cleanup();

            assertThat(object).doesNotExist();
            assertThat(claim).doesNotExist();
            assertThat(repository.getDirectory().toPath().resolve("artemis-rejected-push")).content().isEqualTo("1");
        }
    }

    @Test
    void aMarkOfANewerRejectedPushIsNotReplacedByTheResultOfAnOlderCleanup() throws Exception {
        try (Repository repository = createRepository()) {
            Path directory = repository.getDirectory().toPath();
            Path claim = directory.resolve("artemis-rejected-push.claimed-interrupted");
            Files.writeString(claim, "0");
            Files.setLastModifiedTime(claim, FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS)));
            // a push was rejected after the interrupted cleanup took the older mark
            RejectedPushCleanupService.markForCleanup(repository);

            service().cleanup();

            assertThat(claim).doesNotExist();
            assertThat(directory.resolve("artemis-rejected-push")).content().isEqualTo("0");
        }
    }

    @Test
    void repositoryWithoutMarkIsNotTouched() throws Exception {
        try (Repository repository = createRepository()) {
            ObjectId orphan;
            try (ObjectInserter inserter = repository.newObjectInserter()) {
                orphan = inserter.insert(Constants.OBJ_BLOB, "kept".getBytes());
                inserter.flush();
            }
            Path object = looseObject(repository, orphan);
            Files.setLastModifiedTime(object, FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS)));
            service().cleanup();
            assertThat(object).exists();
        }
    }
}
