package de.tum.in.www1.artemis.service.iris.session.codeeditor.file;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.IrisChangeException;

/**
 * A file change that deletes a file.
 *
 * @param path The path of the file to delete
 */
public record DeleteFileChange(String path) implements FileChange {

    @Override
    public void apply(Optional<File> requestedFile, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException {
        if (requestedFile.isEmpty()) {
            throw new IrisChangeException("Could not delete file '" + path + "' because it does not exist");
        }
        repositoryService.deleteFile(repository, path);
    }

    /**
     * Parses a JsonNode into a DeleteFileChange. The JsonNode must have a string for the field "path".
     *
     * @param node The JsonNode to parse
     * @return The parsed DeleteFileChange
     * @throws IllegalArgumentException If the JsonNode does not have the correct structure
     */
    public static FileChange parse(JsonNode node) throws IllegalArgumentException {
        String path = node.required("path").asText();
        return new DeleteFileChange(path);
    }
}
