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
}
