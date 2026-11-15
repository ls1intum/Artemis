package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
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

    @Test
    void movingTheObjectsTwiceIsHarmless() throws Exception {
        try (Repository repository = createRepository()) {
            try (PushQuarantine quarantine = PushQuarantine.open(repository)) {
                ObjectId received = insert(quarantine.repository(), "received");

                quarantine.migrate();
                quarantine.migrate();

                assertThat(repository.getObjectDatabase().has(received)).isTrue();
            }
        }
    }

    @Test
    void closingTwiceIsHarmless() throws Exception {
        try (Repository repository = createRepository()) {
            PushQuarantine quarantine = PushQuarantine.open(repository);
            quarantine.close();
            quarantine.close();

            assertThat(hasPlace(repository)).isFalse();
        }
    }

    @Test
    void theObjectsOfOnePushAreInvisibleToAnotherPushToTheSameRepository() throws Exception {
        try (Repository repository = createRepository(); PushQuarantine first = PushQuarantine.open(repository); PushQuarantine second = PushQuarantine.open(repository)) {
            ObjectId ofFirst = insert(first.repository(), "first push");
            ObjectId ofSecond = insert(second.repository(), "second push");

            assertThat(second.repository().getObjectDatabase().has(ofFirst)).isFalse();
            assertThat(first.repository().getObjectDatabase().has(ofSecond)).isFalse();
            assertThat(repository.getObjectDatabase().has(ofFirst)).isFalse();
            assertThat(repository.getObjectDatabase().has(ofSecond)).isFalse();
        }
    }

    @Test
    void rejectingOnePushDoesNotTakeAwayTheObjectsOfAnotherThatWasAccepted() throws Exception {
        try (Repository repository = createRepository()) {
            ObjectId accepted;
            try (PushQuarantine rejected = PushQuarantine.open(repository)) {
                insert(rejected.repository(), "rejected push");
                try (PushQuarantine kept = PushQuarantine.open(repository)) {
                    accepted = insert(kept.repository(), "accepted push");
                    kept.migrate();
                }
            }

            assertThat(repository.getObjectDatabase().has(accepted)).isTrue();
        }
    }

    @Test
    void looseObjectsAreMovedNextToLooseObjectsThatAreThereAlready() throws Exception {
        try (Repository repository = createRepository()) {
            // two contents whose ids start with the same two characters would be needed for a shared directory, so the directory is created for the id of the first
            ObjectId existing = insert(repository, "existing");
            String fanOut = existing.name().substring(0, 2);
            ObjectId received;
            try (PushQuarantine quarantine = PushQuarantine.open(repository)) {
                received = insert(quarantine.repository(), "received");
                // place the received object in the directory that already exists in the repository
                Path receivedFile = objectsDirectory(quarantine.repository()).resolve(received.name().substring(0, 2)).resolve(received.name().substring(2));
                Path sharedDirectory = objectsDirectory(quarantine.repository()).resolve(fanOut);
                Files.createDirectories(sharedDirectory);
                if (!receivedFile.getParent().equals(sharedDirectory)) {
                    FileUtils.moveFile(receivedFile.toFile(), sharedDirectory.resolve(received.name().substring(2)).toFile());
                }

                quarantine.migrate();
            }

            assertThat(repository.getObjectDatabase().has(existing)).isTrue();
        }
    }

    @Test
    void aRepositoryThatKeepsItsObjectsElsewhereHasNoPlace() throws Exception {
        try (var inMemory = new InMemoryRepository(new DfsRepositoryDescription("memory"))) {
            assertThat(PushQuarantine.open(inMemory)).isNull();
        }
    }

    @Test
    void twoPushesThatSendTheSameObjectAtTheSameTimeAreBothMoved() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Repository repository = createRepository()) {
            for (int round = 0; round < 50; round++) {
                String content = "shared blob of round " + round;
                try (PushQuarantine first = PushQuarantine.open(repository); PushQuarantine second = PushQuarantine.open(repository)) {
                    ObjectId id = insert(first.repository(), content);
                    assertThat(insert(second.repository(), content)).isEqualTo(id);
                    CountDownLatch start = new CountDownLatch(1);
                    Future<Void> firstMove = executor.submit(() -> {
                        start.await();
                        first.migrate();
                        return null;
                    });
                    Future<Void> secondMove = executor.submit(() -> {
                        start.await();
                        second.migrate();
                        return null;
                    });
                    start.countDown();

                    firstMove.get(30, TimeUnit.SECONDS);
                    secondMove.get(30, TimeUnit.SECONDS);

                    assertThat(repository.getObjectDatabase().has(id)).as("round %d", round).isTrue();
                }
            }
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void anObjectThatAppearsInTheRepositoryWhileTheMoveIsRunningIsNotAnError() throws Exception {
        try (Repository repository = createRepository(); PushQuarantine quarantine = PushQuarantine.open(repository)) {
            ObjectId id = insert(quarantine.repository(), "published meanwhile");
            // the other push published the same object before this one moved it
            String name = id.name();
            Path target = objectsDirectory(repository).resolve(name.substring(0, 2)).resolve(name.substring(2));
            Files.createDirectories(target.getParent());
            FileUtils.copyFile(objectsDirectory(quarantine.repository()).resolve(name.substring(0, 2)).resolve(name.substring(2)).toFile(), target.toFile());

            quarantine.migrate();

            assertThat(repository.getObjectDatabase().has(id)).isTrue();
        }
    }
}
