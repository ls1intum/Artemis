import * as monaco from 'monaco-editor';

/**
 * Interface for line change information from the diff editor
 */
export interface LineChange {
    addedLineCount: number;
    removedLineCount: number;
}

/**
 * Interface for diff information about a file
 */
export interface DiffInformation {
    title: string;
    modifiedPath: string;
    originalPath: string;
    modifiedFileContent?: string;
    originalFileContent?: string;
    diffReady: boolean;
    fileStatus: FileStatus;
    lineChange?: LineChange;
}

export interface RepositoryDiffInformation {
    diffInformations: DiffInformation[];
    totalLineChange: LineChange;
}

/**
 * Enum for file status in diff view
 */
export enum FileStatus {
    CREATED = 'created',
    DELETED = 'deleted',
    RENAMED = 'renamed',
    UNCHANGED = 'unchanged',
}

/**
 * Processes the repository diff information
 * @param originalFileContentByPath The original file content by path
 * @param modifiedFileContentByPath The modified file content by path
 * @returns Promise resolving to the repository diff information
 */
export async function processRepositoryDiff(originalFileContentByPath: Map<string, string>, modifiedFileContentByPath: Map<string, string>): Promise<RepositoryDiffInformation> {
    const diffInformation = getDiffInformation(originalFileContentByPath, modifiedFileContentByPath);

    const repositoryDiffInformation: RepositoryDiffInformation = {
        diffInformations: diffInformation,
        totalLineChange: { addedLineCount: 0, removedLineCount: 0 },
    };

    // Process each diff information asynchronously
    await Promise.all(
        diffInformation.map(async (diffInfo) => {
            const modifiedPath = diffInfo.modifiedPath;
            const originalPath = diffInfo.originalPath;
            const originalFileContent = originalFileContentByPath.get(originalPath) || '';
            const modifiedFileContent = modifiedFileContentByPath.get(modifiedPath) || '';

            const lineChange = await computeDiffsMonaco(originalFileContent, modifiedFileContent);

            diffInfo.lineChange = lineChange;
            repositoryDiffInformation.totalLineChange.addedLineCount += lineChange.addedLineCount;
            repositoryDiffInformation.totalLineChange.removedLineCount += lineChange.removedLineCount;
        }),
    );

    return repositoryDiffInformation;
}

/**
 * Gets the diff information for a given template and solution file content.
 * It also handles renamed files.
 * @param originalFileContentByPath The template file content by path
 * @param modifiedFileContentByPath The solution file content by path
 * @returns The diff information
 */
function getDiffInformation(originalFileContentByPath: Map<string, string>, modifiedFileContentByPath: Map<string, string>): DiffInformation[] {
    const paths = Array.from(new Set(Array.from(originalFileContentByPath.keys()).concat(Array.from(modifiedFileContentByPath.keys()))));
    const created: string[] = [];
    const deleted: string[] = [];

    let diffInformation: DiffInformation[] = paths
        .filter((path) => {
            const originalContent = originalFileContentByPath.get(path);
            const modifiedContent = modifiedFileContentByPath.get(path);
            return path && (modifiedContent !== originalContent || (modifiedContent === undefined) !== (originalContent === undefined));
        })
        .sort((a, b) => a.localeCompare(b))
        .map((path) => {
            const originalFileContent = originalFileContentByPath.get(path);
            const modifiedFileContent = modifiedFileContentByPath.get(path);

            let originalPath: string;
            let modifiedPath: string;
            let fileStatus: FileStatus;

            if (!modifiedFileContent && originalFileContent) {
                deleted.push(path);
                fileStatus = FileStatus.DELETED;
                originalPath = path;
                modifiedPath = '';
            } else if (modifiedFileContent && !originalFileContent) {
                created.push(path);
                fileStatus = FileStatus.CREATED;
                originalPath = '';
                modifiedPath = path;
            } else {
                fileStatus = FileStatus.UNCHANGED;
                originalPath = path;
                modifiedPath = path;
            }

            return {
                title: path,
                modifiedPath: modifiedPath,
                originalPath: originalPath,
                modifiedFileContent: modifiedFileContent,
                originalFileContent: originalFileContent,
                diffReady: false,
                fileStatus: fileStatus,
            };
        });

    diffInformation = mergeRenamedFiles(diffInformation, created, deleted);

    return diffInformation;
}

/**
 * Computes the line changes between two files using Monaco Editor
 * @param originalFileContent The original file content
 * @param modifiedFileContent The modified file content
 * @returns Promise resolving to the line change object containing added and removed line counts
 */
function computeDiffsMonaco(originalFileContent: string, modifiedFileContent: string): Promise<LineChange> {
    return new Promise((resolve, reject) => {
        try {
            const originalModel = monaco.editor.createModel(originalFileContent, 'plaintext');
            const modifiedModel = monaco.editor.createModel(modifiedFileContent, 'plaintext');

            const diffEditor = monaco.editor.createDiffEditor(document.createElement('div'), {
                readOnly: true,
                automaticLayout: false,
            });

            diffEditor.setModel({ original: originalModel, modified: modifiedModel });

            // Set up a one-time listener for diff updates
            const diffListener = diffEditor.onDidUpdateDiff(() => {
                try {
                    const changes = diffEditor.getLineChanges();
                    let added = 0,
                        removed = 0;

                    if (changes) {
                        changes.forEach((change, index) => {
                            const origCount = change.originalEndLineNumber - change.originalStartLineNumber + 1;
                            const modCount = change.modifiedEndLineNumber - change.modifiedStartLineNumber + 1;

                            if (change.originalEndLineNumber === 0) {
                                added += modCount;
                            } else if (change.modifiedEndLineNumber === 0) {
                                removed += origCount;
                            } else {
                                added += modCount;
                                removed += origCount;
                            }
                        });
                    }

                    // Clean up - dispose in reverse order of creation
                    diffListener.dispose();

                    try {
                        diffEditor.dispose();
                    } catch (error) {
                        // Disposal may fail but continue anyway
                    }

                    // Small delay to allow Monaco's internal cleanup
                    setTimeout(() => {
                        try {
                            originalModel.dispose();
                            modifiedModel.dispose();
                        } catch (error) {
                            // Disposal may fail but continue anyway
                        }

                        resolve({ addedLineCount: added, removedLineCount: removed });
                    }, 10);
                } catch (error) {
                    resolve({ addedLineCount: 0, removedLineCount: 0 });
                }
            });
        } catch (error) {
            resolve({ addedLineCount: 0, removedLineCount: 0 });
        }
    });
}

/**
 * Calculates the similarity ratio between two strings using Levenshtein distance
 * @param str1 First string to compare
 * @param str2 Second string to compare
 * @returns Similarity ratio between 0 and 1
 */
function calculateStringSimilarity(str1: string, str2: string): number {
    if (!str1 || !str2) {
        return 0;
    }

    const len1 = str1.length;
    const len2 = str2.length;
    const matrix: number[][] = Array(len1 + 1)
        .fill(0)
        .map(() => Array(len2 + 1).fill(0));

    // Initialize the matrix
    for (let i = 0; i <= len1; i++) {
        matrix[i][0] = i;
    }
    for (let j = 0; j <= len2; j++) {
        matrix[0][j] = j;
    }

    // Fill the matrix
    for (let i = 1; i <= len1; i++) {
        for (let j = 1; j <= len2; j++) {
            const cost = str1[i - 1] === str2[j - 1] ? 0 : 1;
            matrix[i][j] = Math.min(
                matrix[i - 1][j] + 1, // deletion
                matrix[i][j - 1] + 1, // insertion
                matrix[i - 1][j - 1] + cost, // substitution
            );
        }
    }

    const maxLength = Math.max(len1, len2);
    return 1 - matrix[len1][len2] / maxLength;
}

/**
 * Checks similarity between CREATED and DELETED files and merges them into a RENAMED file if they are similar enough.
 *
 * @param diffInformation Array of file diff information.
 * @param created Optional list of CREATED file paths.
 * @param deleted Optional list of DELETED file paths.
 * @returns Updated array with RENAMED files merged.
 */
function mergeRenamedFiles(diffInformation: DiffInformation[], created?: string[], deleted?: string[]): DiffInformation[] {
    // If created or deleted is not provided, compute it from the diffInformation
    if (!created || !deleted) {
        created = diffInformation.filter((info) => info.fileStatus === FileStatus.CREATED).map((info) => info.modifiedPath);
        deleted = diffInformation.filter((info) => info.fileStatus === FileStatus.DELETED).map((info) => info.originalPath);
    }

    // Possible improvements for algorithm:
    // - Reduce time complexity of this function, currently it is O(n^2).
    // - Use highest similarity ratio to merge files. Currently, the first pair of files with similarity above the threshold is merged.
    const SIMILARITY_THRESHOLD = 0.8; // Similarity threshold for merging files

    for (const createdPath of created) {
        const createdFileContent = diffInformation.find((info) => info.modifiedPath === createdPath)?.modifiedFileContent;
        for (const deletedPath of deleted) {
            const deletedFileContent = diffInformation.find((info) => info.originalPath === deletedPath)?.originalFileContent;

            if (createdFileContent && deletedFileContent) {
                const similarity = calculateStringSimilarity(createdFileContent, deletedFileContent);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    const createdIndex = diffInformation.findIndex((info) => info.modifiedPath === createdPath);
                    const deletedIndex = diffInformation.findIndex((info) => info.originalPath === deletedPath);
                    if (createdIndex !== -1 && deletedIndex !== -1) {
                        // Merge into a single RENAMED entry using old/new fields
                        diffInformation[createdIndex] = {
                            title: `${deletedPath} → ${createdPath}`,
                            diffReady: false,
                            fileStatus: FileStatus.RENAMED,
                            modifiedPath: createdPath,
                            originalPath: deletedPath,
                            modifiedFileContent: createdFileContent,
                            originalFileContent: deletedFileContent,
                        };
                        diffInformation.splice(deletedIndex, 1);
                    }
                }
            }
        }
    }
    return diffInformation;
}
