package de.tum.cit.aet.artemis.localvc.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * A separate place for the objects of one push. The objects that a push sends are written there and not into the repository, and they are moved into the repository only when
 * the push is accepted. A push that is rejected, or that breaks off, therefore leaves nothing in the repository: the place is removed with it.
 * <p>
 * The repository that the push is received into reads the objects of the real repository as well, so the checks of the push see the existing history together with the
 * new objects, and the references are those of the real repository.
 */
public final class PushQuarantine implements AutoCloseable {

    /** The prefix of the directory name. A directory with this name that is older than a day is left over from a push that was cut off by a restart. */
    public static final String DIRECTORY_PREFIX = "incoming-";

    private static final String PACK_DIRECTORY = "pack";

    private static final String PACK_SUFFIX = ".pack";

    private static final String INDEX_SUFFIX = ".idx";

    private static final String KEEP_SUFFIX = ".keep";

    /** The name of a directory of loose objects: the first two characters of their ids. */
    private static final Pattern FAN_OUT_NAME = Pattern.compile("[0-9a-f]{2}");

    private final Path directory;

    private final Path objectsDirectory;

    private final Repository repository;

    private boolean closed;

    private PushQuarantine(Path directory, Path objectsDirectory, Repository repository) {
        this.directory = directory;
        this.objectsDirectory = objectsDirectory;
        this.repository = repository;
    }

    /**
     * Opens a place for the objects of a push to the repository.
     *
     * @param repository the repository that receives the push
     * @return the place, or null if the repository does not store its objects in a directory
     * @throws IOException if the place cannot be created
     */
    public static PushQuarantine open(Repository repository) throws IOException {
        if (!(repository instanceof FileRepository fileRepository)) {
            return null;
        }
        Path objects = fileRepository.getObjectDatabase().getDirectory().toPath();
        Path directory = repository.getDirectory().toPath().resolve(DIRECTORY_PREFIX + UUID.randomUUID());
        Files.createDirectories(directory.resolve(PACK_DIRECTORY));
        try {
            Repository quarantined = new FileRepositoryBuilder().setGitDir(repository.getDirectory()).setObjectDirectory(directory.toFile())
                    .addAlternateObjectDirectory(objects.toFile()).setMustExist(true).build();
            return new PushQuarantine(directory, objects, quarantined);
        }
        catch (IOException | RuntimeException e) {
            FileUtils.deleteQuietly(directory.toFile());
            throw e;
        }
    }

    /**
     * The repository to receive the push into.
     *
     * @return the repository, which writes new objects into the place of the push and reads the objects of the real repository as well
     */
    public Repository repository() {
        return repository;
    }

    /**
     * Moves the objects of the push into the repository. A pack becomes visible with its index, so the index is moved last.
     *
     * @throws IOException if an object cannot be moved
     */
    public void migrate() throws IOException {
        Path packs = directory.resolve(PACK_DIRECTORY);
        Path targetPacks = Files.createDirectories(objectsDirectory.resolve(PACK_DIRECTORY));
        try (DirectoryStream<Path> files = Files.newDirectoryStream(packs)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                // The lock of the receive pack stays: it is removed from here when the receive pack is released, and a pack of the repository needs none
                if (name.startsWith("pack-") && !name.endsWith(INDEX_SUFFIX) && !name.endsWith(KEEP_SUFFIX)) {
                    move(file, targetPacks.resolve(name));
                }
            }
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(packs, "pack-*" + INDEX_SUFFIX)) {
            for (Path file : files) {
                move(file, targetPacks.resolve(file.getFileName()));
            }
        }
        try (DirectoryStream<Path> fanOut = Files.newDirectoryStream(directory, path -> FAN_OUT_NAME.matcher(path.getFileName().toString()).matches() && Files.isDirectory(path))) {
            for (Path fanOutDirectory : fanOut) {
                Path targetDirectory = Files.createDirectories(objectsDirectory.resolve(fanOutDirectory.getFileName()));
                try (DirectoryStream<Path> objects = Files.newDirectoryStream(fanOutDirectory)) {
                    for (Path object : objects) {
                        move(object, targetDirectory.resolve(object.getFileName()));
                    }
                }
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            FileUtils.moveFile(source.toFile(), new File(target.toString()));
        }
        catch (IOException e) {
            // The same object or pack is stored already: the repository had it, or another push published it a moment ago. Objects and packs are named by their content,
            // so the stored one is the same. Anything else that went wrong is passed on.
            if (!Files.exists(target)) {
                throw e;
            }
            FileUtils.deleteQuietly(source.toFile());
        }
    }

    /** Removes what is left of the place, which is everything if the push was rejected and nothing if it was accepted. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        repository.close();
        FileUtils.deleteQuietly(directory.toFile());
    }
}
