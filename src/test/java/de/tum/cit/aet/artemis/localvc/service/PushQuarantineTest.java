package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.storage.pack.PackConfig;
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
                quarantine.prepare();
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
                quarantine.prepare();
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
                quarantine.prepare();
                quarantine.prepare();
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
                quarantine.prepare();
                quarantine.prepare();
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
                quarantine.prepare();
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
            first.prepare();
            second.prepare();
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
                rejected.prepare();
                insert(rejected.repository(), "rejected push");
                try (PushQuarantine kept = PushQuarantine.open(repository)) {
                    kept.prepare();
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
                quarantine.prepare();
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
            assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> PushQuarantine.open(inMemory));
        }
    }

    @Test
    void twoPushesThatSendTheSameObjectAtTheSameTimeAreBothMoved() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Repository repository = createRepository()) {
            for (int round = 0; round < 50; round++) {
                String content = "shared blob of round " + round;
                try (PushQuarantine first = PushQuarantine.open(repository); PushQuarantine second = PushQuarantine.open(repository)) {
                    first.prepare();
                    second.prepare();
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
            quarantine.prepare();
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

    @Test
    void theDirectoryOfThePlaceIsCreatedOnlyWhenThePushIsPrepared() throws Exception {
        try (Repository repository = createRepository()) {
            try (PushQuarantine quarantine = PushQuarantine.open(repository)) {
                assertThat(hasPlace(repository)).as("a request that never reaches the receipt of a push costs no disk space").isFalse();

                quarantine.prepare();

                assertThat(hasPlace(repository)).isTrue();
            }
            assertThat(hasPlace(repository)).isFalse();
        }
    }

    @Test
    void closingAPlaceThatWasNeverPreparedIsHarmless() throws Exception {
        try (Repository repository = createRepository()) {
            PushQuarantine quarantine = PushQuarantine.open(repository);

            quarantine.migrate();
            quarantine.close();

            assertThat(hasPlace(repository)).isFalse();
        }
    }

    /** Writes a pack with the given objects, with the given compression, into the pack directory of a place, and returns the name of the pack. */
    private static String writePack(PushQuarantine quarantine, Repository source, Set<ObjectId> objects, int compressionLevel) throws Exception {
        PackConfig config = new PackConfig(source);
        config.setCompressionLevel(compressionLevel);
        config.setDeltaCompress(false);
        Path packs = objectsDirectory(quarantine.repository()).resolve("pack");
        List<RevObject> revObjects = new ArrayList<>();
        try (RevWalk walk = new RevWalk(source)) {
            for (ObjectId id : objects) {
                revObjects.add(walk.parseAny(id));
            }
        }
        try (PackWriter writer = new PackWriter(config, source.newObjectReader())) {
            writer.preparePack(revObjects.iterator());
            String name = "pack-" + writer.computeName().name();
            try (var out = Files.newOutputStream(packs.resolve(name + ".pack"))) {
                writer.writePack(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE, out);
            }
            try (var out = Files.newOutputStream(packs.resolve(name + ".idx"))) {
                writer.writeIndex(out);
            }
            return name;
        }
    }

    private static Set<ObjectId> blobs(Repository source, String... contents) throws Exception {
        Set<ObjectId> ids = new HashSet<>();
        for (String content : contents) {
            ids.add(insert(source, content.repeat(200)));
        }
        return ids;
    }

    private static void assertEveryObjectIsReadable(Repository repository, Set<ObjectId> objects) throws Exception {
        // a pack that is paired with the index of another pack of the same name fails here
        try (Repository fresh = new FileRepositoryBuilder().setGitDir(repository.getDirectory()).setBare().build()) {
            for (ObjectId id : objects) {
                assertThat(fresh.open(id).getSize()).as("object %s", id.name()).isPositive();
            }
        }
    }

    @Test
    void packsOfTheSameNameThatAreEncodedDifferentlyAreStoredAsPairsOneAfterTheOther() throws Exception {
        try (Repository repository = createRepository(); Repository source = Git.init().setDirectory(base.resolve("source").toFile()).call().getRepository()) {
            Set<ObjectId> objects = blobs(source, "alpha ", "beta ", "gamma ", "delta ");
            try (PushQuarantine first = PushQuarantine.open(repository); PushQuarantine second = PushQuarantine.open(repository)) {
                first.prepare();
                second.prepare();
                first.prepare();
                second.prepare();
                String firstName = writePack(first, source, objects, 0);
                String secondName = writePack(second, source, objects, 9);
                assertThat(secondName).as("the name comes from the objects").isEqualTo(firstName);
                assertThat(Files.size(objectsDirectory(first.repository()).resolve("pack").resolve(firstName + ".pack"))).as("the packs are encoded differently")
                        .isNotEqualTo(Files.size(objectsDirectory(second.repository()).resolve("pack").resolve(secondName + ".pack")));

                first.migrate();
                second.migrate();
            }

            assertEveryObjectIsReadable(repository, objects);
        }
    }

    @Test
    void packsOfTheSameNameThatAreEncodedDifferentlyAreStoredAsPairsAtTheSameTime() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Repository repository = createRepository(); Repository source = Git.init().setDirectory(base.resolve("source").toFile()).call().getRepository()) {
            for (int round = 0; round < 30; round++) {
                Set<ObjectId> objects = blobs(source, "round " + round + " alpha ", "round " + round + " beta ", "round " + round + " gamma ");
                try (PushQuarantine first = PushQuarantine.open(repository); PushQuarantine second = PushQuarantine.open(repository)) {
                    first.prepare();
                    second.prepare();
                    first.prepare();
                    second.prepare();
                    writePack(first, source, objects, 0);
                    writePack(second, source, objects, 9);
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
                }

                assertEveryObjectIsReadable(repository, objects);
            }
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void thePackOfAnEarlierPushWithTheSameNameIsKeptAndThePackOfThisPushIsStoredNextToIt() throws Exception {
        try (Repository repository = createRepository(); Repository source = Git.init().setDirectory(base.resolve("source").toFile()).call().getRepository()) {
            Set<ObjectId> objects = blobs(source, "one ", "two ", "three ");
            for (int compression : new int[] { 0, 9 }) {
                try (PushQuarantine quarantine = PushQuarantine.open(repository)) {
                    quarantine.prepare();
                    quarantine.prepare();
                    writePack(quarantine, source, objects, compression);
                    quarantine.migrate();
                }
            }

            assertEveryObjectIsReadable(repository, objects);
            try (var packs = Files.list(objectsDirectory(repository).resolve("pack"))) {
                assertThat(packs.filter(pack -> pack.getFileName().toString().endsWith(".pack")).count()).isEqualTo(2);
            }
        }
    }
}
