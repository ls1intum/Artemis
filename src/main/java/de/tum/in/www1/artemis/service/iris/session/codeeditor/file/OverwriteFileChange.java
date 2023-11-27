package de.tum.in.www1.artemis.service.iris.session.codeeditor.file;

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
 * A file change that overwrites the contents of a file with the given contents.
 *
 * @param path    The path of the file to overwrite
 * @param updated The new contents of the file
 */
public record OverwriteFileChange(String path, String updated) implements FileChange {

    @Override
    public void apply(Optional<File> requestedFile, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException {
        if (requestedFile.isEmpty()) {
            throw new IrisChangeException("Could not overwrite file '" + path + "' because it does not exist");
        }
        FileUtils.writeStringToFile(requestedFile.get(), updated, StandardCharsets.UTF_8);
    }

    /**
     * Parses a JsonNode into an OverwriteFileChange. The JsonNode must have strings for the fields "path" and
     * "content".
     *
     * @param node The JsonNode to parse
     * @return The parsed OverwriteFileChange
     * @throws IllegalArgumentException If the JsonNode does not have the correct structure
     */
    public static FileChange parse(JsonNode node) throws IllegalArgumentException {
        String path = node.required("path").asText();
        String updated = node.required("updated").asText();
        return new OverwriteFileChange(path, updated);
    }
}
