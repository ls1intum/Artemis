package de.tum.in.www1.artemis.service.iris.session.codeeditor.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.IrisChangeException;

/**
 * A file change that creates a new file with the given contents.
 *
 * @param path    The path of the file to create
 * @param content The contents of the file to create
 */
public record CreateFileChange(String path, String content) implements FileChange {

    @Override
    public void apply(Optional<File> requestedFile, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException {
        if (requestedFile.isPresent()) {
            throw new IrisChangeException("Could not create file '" + path + "' because it already exists");
        }
        repositoryService.createFile(repository, path, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Parses a JsonNode into a CreateFileChange. The JsonNode must have strings for the fields "path" and "content".
     *
     * @param node The JsonNode to parse
     * @return The parsed CreateFileChange
     * @throws IllegalArgumentException If the JsonNode does not have the correct structure
     */
    public static FileChange parse(JsonNode node) throws IllegalArgumentException {
        String path = node.required("path").asText();
        String content = node.required("content").asText();
        return new CreateFileChange(path, content);
    }
}
