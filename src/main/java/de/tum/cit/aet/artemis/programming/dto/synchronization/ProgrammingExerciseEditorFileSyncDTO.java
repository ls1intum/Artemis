package de.tum.cit.aet.artemis.programming.dto.synchronization;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseEditorFileSyncDTO(String fileName, @Nullable String patch, @Nullable ProgrammingExerciseEditorFileChangeType changeType,
        @Nullable String newFileName, @Nullable ProgrammingExerciseEditorFileType fileType) {

    /**
     * Creates a DTO for a file creation event.
     *
     * @param filePath the path of the created file
     * @return a DTO representing a file creation
     */
    public static ProgrammingExerciseEditorFileSyncDTO forFileCreate(String filePath) {
        return new ProgrammingExerciseEditorFileSyncDTO(filePath, null, ProgrammingExerciseEditorFileChangeType.CREATE, null, ProgrammingExerciseEditorFileType.FILE);
    }

    /**
     * Creates a DTO for a folder creation event.
     *
     * @param folderPath the path of the created folder
     * @return a DTO representing a folder creation
     */
    public static ProgrammingExerciseEditorFileSyncDTO forFolderCreate(String folderPath) {
        return new ProgrammingExerciseEditorFileSyncDTO(folderPath, null, ProgrammingExerciseEditorFileChangeType.CREATE, null, ProgrammingExerciseEditorFileType.FOLDER);
    }

    /**
     * Creates a DTO for a file/folder rename event.
     *
     * @param currentFilePath the current path of the file/folder
     * @param newFilename     the new name for the file/folder
     * @return a DTO representing a rename operation
     */
    public static ProgrammingExerciseEditorFileSyncDTO forRename(String currentFilePath, String newFilename) {
        return new ProgrammingExerciseEditorFileSyncDTO(currentFilePath, null, ProgrammingExerciseEditorFileChangeType.RENAME, newFilename, null);
    }

    /**
     * Creates a DTO for a file/folder deletion event.
     *
     * @param fileName the path of the deleted file/folder
     * @return a DTO representing a deletion
     */
    public static ProgrammingExerciseEditorFileSyncDTO forDelete(String fileName) {
        return new ProgrammingExerciseEditorFileSyncDTO(fileName, null, ProgrammingExerciseEditorFileChangeType.DELETE, null, null);
    }

}
