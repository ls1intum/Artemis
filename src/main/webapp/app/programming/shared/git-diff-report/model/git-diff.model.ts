/**
 * Enum representing the status of a file in a git diff.
 */
export enum FileStatus {
    CREATED = 'created',
    DELETED = 'deleted',
    UNCHANGED = 'unchanged',
}

/**
 * Simple interface representing line changes between two text versions.
 * This decouples consumers from requiring monaco-editor imports.
 */
export interface LineChange {
    addedLineCount: number;
    removedLineCount: number;
}

/**
 * Interface representing the diff information for a file.
 */
export interface DiffInformation {
    path: string;
    templateFileContent?: string;
    solutionFileContent?: string;
    diffReady: boolean;
    fileStatus: FileStatus;
    lineChange?: LineChange;
} 