package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PushQuarantineTest {

    @TempDir
    Path base;

    private Repository createRepository() throws Exception {
        return Git.init().setBare(true).setDirectory(base.resolve("repo.git").toFile()).call().getRepository();
    }

    private static ObjectId insert(Repository repository, String content) throws Exception {
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            ObjectId id = inserter.insert(Constants.OBJ_BLOB, content.getBytes(StandardCharsets.UTF_8));
            inserter.flush();
            return id;
        }
    }

    private static Path objectsDirectory(Repository repository) {
        return ((FileRepository) repository).getObjectDatabase().getDirectory().toPath();
    }

    private static boolean hasPlace(Repository repository) throws Exception {
        try (var entries = Files.list(repository.getDirectory().toPath())) {
            return entries.anyMatch(entry -> entry.getFileName().toString().startsWith(PushQuarantine.DIRECTORY_PREFIX));
        }
    }

    @Test
    void newObjectsStayOutOfTheRepositoryUntilTheyAreMovedIntoIt() throws Exception {
        try (Repository repository = createRepository()) {
            ObjectId existing = insert(repository, "existing");
            try (PushQuarantine quarantine = PushQuarantine.open(repository)) {
                ObjectId received = insert(quarantine.repository(), "received");

                assertThat(quarantine.repository().getObjectDatabase().has(received)).isTrue();
                assertThat(quarantine.repository().getObjectDatabase().has(existing)).as("the existing history is readable for the checks of the push").isTrue();
                assertThat(repository.getObjectDatabase().has(received)).as("the repository does not have the received object").isFalse();

                quarantine.migrate();

                assertThat(repository.getObjectDatabase().has(received)).as("the object is in the repository once the push is accepted").isTrue();
            }
            assertThat(hasPlace(repository)).as("the place is removed when the push ends").isFalse();
        }
    }

    @Test
    void aRejectedPushLeavesNothingBehind() throws Exception {
        try (Repository repository = createRepository()) {
            ObjectId received;
            try (PushQuarantine quarantine = PushQuarantine.open(repository)) {
                received = insert(quarantine.repository(), "rejected");
            }

            assertThat(repository.getObjectDatabase().has(received)).isFalse();
            assertThat(hasPlace(repository)).isFalse();
        }
    }

    @Test
    void aPackIsMovedWithItsIndexLastAndAnUnfinishedPackIsLeftOut() throws Exception {
        try (Repository repository = createRepository()) {
            try (PushQuarantine quarantine = PushQuarantine.open(repository)) {
                Path packs = objectsDirectory(quarantine.repository()).resolve("pack");
                FileUtils.writeStringToFile(packs.resolve("pack-abc.pack").toFile(), "pack", StandardCharsets.UTF_8);
                FileUtils.writeStringToFile(packs.resolve("pack-abc.idx").toFile(), "index", StandardCharsets.UTF_8);
                FileUtils.writeStringToFile(packs.resolve("tmp_pack_unfinished").toFile(), "partial", StandardCharsets.UTF_8);
                // JGit locks a received pack with a keep file, which it removes from the place of the push when the receive pack is released
                FileUtils.writeStringToFile(packs.resolve("pack-abc.keep").toFile(), "", StandardCharsets.UTF_8);

                quarantine.migrate();
            }

            Path objectsPack = objectsDirectory(repository).resolve("pack");
            assertThat(objectsPack.resolve("pack-abc.pack")).exists();
            assertThat(objectsPack.resolve("pack-abc.idx")).exists();
            assertThat(objectsPack.resolve("tmp_pack_unfinished")).doesNotExist();
            assertThat(objectsPack.resolve("pack-abc.keep")).doesNotExist();
        }
    }

    @Test
    void anObjectThatTheRepositoryHasAlreadyIsNotMovedTwice() throws Exception {
        try (Repository repository = createRepository()) {
            ObjectId id = insert(repository, "same content");
            try (PushQuarantine quarantine = PushQuarantine.open(repository)) {
                // an object that the repository has is not written again, but a client may send it, which would put the same file in both places
                String name = id.name();
                Path fanOut = objectsDirectory(quarantine.repository()).resolve(name.substring(0, 2));
                FileUtils.writeStringToFile(fanOut.resolve(name.substring(2)).toFile(), "duplicate", StandardCharsets.UTF_8);

                quarantine.migrate();
            }

            assertThat(repository.getObjectDatabase().has(id)).isTrue();
        }
    }
}
