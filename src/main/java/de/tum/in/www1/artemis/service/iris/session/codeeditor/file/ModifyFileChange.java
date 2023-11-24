package de.tum.in.www1.artemis.service.iris.session.codeeditor.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.IrisChangeException;

/**
 * A file change that modifies the contents of a file by performing a find-and-replace operation.
 *
 * @param path     The path of the file to modify
 * @param original The original contents of the file (to replace)
 * @param updated  The updated contents of the file
 */
public record ModifyFileChange(String path, String original, String updated) implements FileChange {

    /**
     * Replaces the first occurrence of the original string with the updated string in the given file.
     *
     * @throws IOException If the file could not be read or written to
     */
    @Override
    public void apply(Optional<File> requestedFile, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException {
        if (requestedFile.isEmpty()) {
            throw new IrisChangeException("Could not modify file '" + path + "' because it does not exist");
        }
        String currentContent = FileUtils.readFileToString(requestedFile.get(), StandardCharsets.UTF_8);
        // We only want to replace the first occurrence of the original string (for now)
        // String.replaceFirst() uses regex, so we need to escape the original string with Pattern.quote()
        // Matcher.quoteReplacement() escapes the updated string so that it can be used as a replacement
        String newContents = currentContent.replaceFirst(Pattern.quote(original), Matcher.quoteReplacement(updated));
        FileUtils.writeStringToFile(requestedFile.get(), newContents, StandardCharsets.UTF_8);
    }

    /**
     * Parses a JsonNode into a ModifyFileChange. The JsonNode must have strings for the fields "path", "original", and
     * "updated".
     *
     * @param node The JsonNode to parse
     * @return The parsed ModifyFileChange
     * @throws IllegalArgumentException If the JsonNode does not have the correct structure
     */
    public static FileChange parse(JsonNode node) throws IllegalArgumentException {
        String path = node.required("path").asText();
        String original = node.required("original").asText();
        String updated = node.required("updated").asText();
        if (original.equals("!all!")) {
            return new OverwriteFileChange(path, updated);
        }
        return new ModifyFileChange(path, original, updated);
    }
}
