package de.tum.in.www1.artemis.service.iris.session.codeeditor.file;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.iris.session.codeeditor.IrisChangeException;

/**
 * Iris can generate different kinds of changes to files. This interface represents a change that can be applied to a
 * file.
 */
// @formatter:off
@JsonSubTypes({
        @JsonSubTypes.Type(value = RenameFileChange.class, name = "rename"),
        @JsonSubTypes.Type(value = DeleteFileChange.class, name = "delete"),
        @JsonSubTypes.Type(value = CreateFileChange.class, name = "create"),
        @JsonSubTypes.Type(value = OverwriteFileChange.class, name = "overwrite"),
        @JsonSubTypes.Type(value = ModifyFileChange.class, name = "modify")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:on
public interface FileChange {

    /**
     * Returns the path of the file that this change should be applied to.
     *
     * @return The path of the file
     */
    String path();

    /**
     * Applies the change to the provided Optional<File>. Some change types expect the file to exist, while others do
     * not. If the file does not exist, the Optional will be empty.
     *
     * @param file              The file to apply the change to
     * @param repositoryService The repository service to use for applying the change
     * @param repository        The repository to apply the change to
     * @throws IOException         If an I/O error occurs
     * @throws IrisChangeException If the change cannot be applied because of a mistake made by Iris
     */
    void apply(Optional<File> file, RepositoryService repositoryService, Repository repository) throws IOException, IrisChangeException;
}
