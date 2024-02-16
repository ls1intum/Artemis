package de.tum.in.www1.artemis.service.iris.session.codeeditor.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.IrisChangeException;

/**
 * A file change that renames a file.
 *
 * @param path    The path of the file to rename
 * @param updated The new path of the file
 */
public record RenameFileChange(String path, String updated) implements FileChange {

    @Override
    public void apply(Optional<File> requestedFile, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException {
        if (requestedFile.isEmpty()) {
            throw new IrisChangeException("Could not rename file '" + path + "' because it does not exist");
        }
        String content = FileUtils.readFileToString(requestedFile.get(), StandardCharsets.UTF_8);
        repositoryService.deleteFile(repository, path);
        repositoryService.createFile(repository, updated, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Parses a JsonNode into a RenameFileChange. The JsonNode must have strings for the fields "path" and "newPath".
     *
     * @param node The JsonNode to parse
     * @return The parsed RenameFileChange
     * @throws IllegalArgumentException If the JsonNode does not have the correct structure
     */
    public static FileChange parse(JsonNode node) throws IllegalArgumentException {
        String path = node.required("path").asText();
        String updated = node.required("updated").asText();
        return new RenameFileChange(path, updated);
    }
}
