package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncomingObjectsCleanupServiceTest {

    @TempDir
    Path base;

    private Path place(String name, long daysOld) throws Exception {
        Path place = Files.createDirectories(base.resolve("PROJECT").resolve("repo.git").resolve(name));
        Files.createFile(place.resolve("file"));
        Files.setLastModifiedTime(place, FileTime.from(Instant.now().minus(daysOld, ChronoUnit.DAYS)));
        return place;
    }

    @Test
    void removesOnlyOldPlacesOfPushes() throws Exception {
        Path old = place(PushQuarantine.DIRECTORY_PREFIX + "old", 3);
        Path recent = place(PushQuarantine.DIRECTORY_PREFIX + "recent", 0);
        Path other = place("objects", 3);

        new IncomingObjectsCleanupService(base).cleanup();

        assertThat(old).doesNotExist();
        assertThat(recent).exists();
        assertThat(other).as("directories that are not places of pushes stay").exists();
    }

    @Test
    void aFileWithTheNameOfAPlaceIsNotRemoved() throws Exception {
        Path repository = Files.createDirectories(base.resolve("PROJECT").resolve("repo.git"));
        Path file = Files.createFile(repository.resolve(PushQuarantine.DIRECTORY_PREFIX + "file"));
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(3, ChronoUnit.DAYS)));

        new IncomingObjectsCleanupService(base).cleanup();

        assertThat(file).exists();
    }

    @Test
    void placesInSeveralRepositoriesAndProjectsAreRemoved() throws Exception {
        Path first = place(PushQuarantine.DIRECTORY_PREFIX + "one", 2);
        Path secondRepository = Files.createDirectories(base.resolve("OTHER").resolve("other.git"));
        Path second = Files.createDirectories(secondRepository.resolve(PushQuarantine.DIRECTORY_PREFIX + "two"));
        Files.setLastModifiedTime(second, FileTime.from(Instant.now().minus(5, ChronoUnit.DAYS)));

        new IncomingObjectsCleanupService(base).cleanup();

        assertThat(first).doesNotExist();
        assertThat(second).doesNotExist();
    }

    @Test
    void aMissingBaseDirectoryIsNotAnError() {
        new IncomingObjectsCleanupService(base.resolve("does-not-exist")).cleanup();
    }
}
