package de.tum.cit.aet.artemis.localvc.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
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
     * @return the place
     * @throws IllegalArgumentException if the repository does not store its objects in a directory
     * @throws IOException              if the place cannot be created
     */
    public static PushQuarantine open(Repository repository) throws IOException {
        if (!(repository instanceof FileRepository fileRepository)) {
            throw new IllegalArgumentException("Only a repository that stores its objects in a directory can have a place for the objects of a push");
        }
        Path objects = fileRepository.getObjectDatabase().getDirectory().toPath();
        // The directory is created when the push is received, see prepare(): a request that is refused before that, as by an authentication that fails, costs no disk space
        Path directory = repository.getDirectory().toPath().resolve(DIRECTORY_PREFIX + UUID.randomUUID());
        Repository quarantined = new FileRepositoryBuilder().setGitDir(repository.getDirectory()).setObjectDirectory(directory.toFile())
                .addAlternateObjectDirectory(objects.toFile()).build();
        return new PushQuarantine(directory, objects, quarantined);
    }

    /**
     * Creates the directories that the objects of the push are written into. Called when the push is received.
     *
     * @throws IOException if the directories cannot be created
     */
    public void prepare() throws IOException {
        Files.createDirectories(directory.resolve(PACK_DIRECTORY));
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
     * Moves the objects of the push into the repository. A pack becomes visible with its index, which is stored last, and a pack and its index are always stored as a pair:
     * the name of a pack comes from the objects in it, so two pushes with the same objects can send packs of the same name that are encoded differently, and the index of one
     * does not fit the other.
     *
     * @throws IOException if an object cannot be moved
     */
    public void migrate() throws IOException {
        Path packs = directory.resolve(PACK_DIRECTORY);
        if (!Files.isDirectory(packs)) {
            // nothing was received
            return;
        }
        Path targetPacks = Files.createDirectories(objectsDirectory.resolve(PACK_DIRECTORY));
        try (DirectoryStream<Path> files = Files.newDirectoryStream(packs, "pack-*" + PACK_SUFFIX)) {
            for (Path pack : files) {
                String name = pack.getFileName().toString();
                publishPack(packs, name.substring(0, name.length() - PACK_SUFFIX.length()), targetPacks);
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

    /**
     * Stores one pack with its index and the other files of the pack. The pack is claimed under its own name by creating the file, which fails if another push or an earlier
     * one stored a pack of that name. Then this pack is stored under a name of its own, next to the other one, as the two may differ even though they hold the same objects.
     * Both files of a pair are always stored under one name, and the index comes last.
     */
    private static void publishPack(Path packs, String name, Path targetPacks) throws IOException {
        String publishedName = name;
        try {
            Files.createLink(targetPacks.resolve(name + PACK_SUFFIX), packs.resolve(name + PACK_SUFFIX));
        }
        catch (FileAlreadyExistsException _) {
            publishedName = name + "-" + UUID.randomUUID().toString().substring(0, 8);
            Files.createLink(targetPacks.resolve(publishedName + PACK_SUFFIX), packs.resolve(name + PACK_SUFFIX));
        }
        try (DirectoryStream<Path> sidecars = Files.newDirectoryStream(packs, name + ".*")) {
            for (Path sidecar : sidecars) {
                String suffix = sidecar.getFileName().toString().substring(name.length());
                // The lock of the receive pack stays: it is removed from here when the receive pack is released, and a pack of the repository needs none
                if (!suffix.equals(PACK_SUFFIX) && !suffix.equals(INDEX_SUFFIX) && !suffix.equals(KEEP_SUFFIX)) {
                    Files.createLink(targetPacks.resolve(publishedName + suffix), sidecar);
                }
            }
        }
        Files.createLink(targetPacks.resolve(publishedName + INDEX_SUFFIX), packs.resolve(name + INDEX_SUFFIX));
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
