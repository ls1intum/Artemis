import * as monaco from 'monaco-editor';
import ignore from 'ignore';
import { diff, DiffOpts } from 'monaco-diff';

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
 * Converts Monaco line changes to a LineChange object
 * @param monacoLineChanges The Monaco line changes to convert
 * @returns The converted LineChange object
 */
export function convertMonacoLineChanges(monacoLineChanges: monaco.editor.ILineChange[] | null): LineChange {
    const lineChange: LineChange = { addedLineCount: 0, removedLineCount: 0 };
    if (!monacoLineChanges) {
        return lineChange;
    }

    for (const change of monacoLineChanges) {
        const addedLines = change.modifiedEndLineNumber >= change.modifiedStartLineNumber
            ? change.modifiedEndLineNumber - change.modifiedStartLineNumber + 1
            : 0;
            
        const removedLines = change.originalEndLineNumber >= change.originalStartLineNumber
            ? change.originalEndLineNumber - change.originalStartLineNumber + 1
            : 0;
            
        lineChange.addedLineCount += addedLines;
        lineChange.removedLineCount += removedLines;
    }

    return lineChange;
}

export function processRepositoryDiff(
    originalFileContentByPath: Map<string, string>,
    modifiedFileContentByPath: Map<string, string>,
): RepositoryDiffInformation {
    const diffInformation = getDiffInformation(originalFileContentByPath, modifiedFileContentByPath);

    const repositoryDiffInformation: RepositoryDiffInformation = {
        diffInformations: diffInformation,
        totalLineChange: { addedLineCount: 0, removedLineCount: 0 },
    };

    diffInformation.forEach((diffInformation) => {
        const modifiedPath = diffInformation.modifiedPath;        
        const originalPath = diffInformation.originalPath;
        const originalFileContent = originalFileContentByPath.get(originalPath) || '';
        const modifiedFileContent = modifiedFileContentByPath.get(modifiedPath) || '';
        
        const lineChange = computeDiffsMonaco(originalFileContent, modifiedFileContent);
        
        diffInformation.lineChange = lineChange;
        repositoryDiffInformation.totalLineChange.addedLineCount += lineChange.addedLineCount;
        repositoryDiffInformation.totalLineChange.removedLineCount += lineChange.removedLineCount;
    });

    return repositoryDiffInformation;
}

/**
 * Gets the diff information for a given template and solution file content.
 * It also handles renamed files and ignores .gitignore patterns.
 * @param originalFileContentByPath The template file content by path
 * @param modifiedFileContentByPath The solution file content by path
 * @returns The diff information
 */
export function getDiffInformation(originalFileContentByPath: Map<string, string>, modifiedFileContentByPath: Map<string, string>): DiffInformation[] {
    const paths = [...new Set([...originalFileContentByPath.keys(), ...modifiedFileContentByPath.keys()])];
    const created: string[] = [];
    const deleted: string[] = [];
    const ig = ignore().add(modifiedFileContentByPath.get('.gitignore') || '');

    let diffInformation: DiffInformation[] = paths
        .filter(ig.createFilter())
        .filter((path) => {
            const originalContent = originalFileContentByPath.get(path);
            const modifiedContent = modifiedFileContentByPath.get(path);
            return path && (modifiedContent !== originalContent || (modifiedContent === undefined) !== (originalContent === undefined));
        })
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

            return ({
                title: path,
                modifiedPath: modifiedPath,
                originalPath: originalPath,
                modifiedFileContent: modifiedFileContent,
                originalFileContent: originalFileContent,
                diffReady: false,
                fileStatus: fileStatus,
            });
        });

    diffInformation = mergeRenamedFiles(diffInformation, created, deleted);

    return diffInformation;
}

function computeDiffsMonaco(originalFileContent: string, modifiedFileContent: string): LineChange {
    const options: DiffOpts = {
        shouldPostProcessCharChanges: true,
        shouldComputeCharChanges: true,
        shouldIgnoreTrimWhitespace: true,
        shouldMakePrettyDiff: true,
        maxComputationTime: 1000,
    }
    return convertMonacoLineChanges(diff(originalFileContent.split('\n'), modifiedFileContent.split('\n'), options));
}

/**
 * Checks similarities between CREATED and DELETED files and merges them into a single RENAMED file.
 * Also handles title creation for RENAMED files.
 * @param diffInformation The diff information to merge into
 * @param created The created files
 * @param deleted The deleted files
 * @returns The merged diff information
 */
export function mergeRenamedFiles(diffInformation: DiffInformation[], created?: string[], deleted?: string[]): DiffInformation[] {
    if (!created || !deleted) {
        created = diffInformation.filter((info) => info.fileStatus === FileStatus.CREATED).map((info) => info.modifiedPath);
        deleted = diffInformation.filter((info) => info.fileStatus === FileStatus.DELETED).map((info) => info.originalPath);
    }

    const toRemove = new Set<string>();
    for (const createdPath of created) {
        const createdFileContent = diffInformation.find((info) => info.modifiedPath === createdPath)?.modifiedFileContent;
        for (const deletedPath of deleted) {
            const deletedFileContent = diffInformation.find((info) => info.originalPath === deletedPath)?.originalFileContent;
            //TODO: Use a similarity check instead of a string equality
            if (createdFileContent === deletedFileContent) {
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
                        modifiedFileContent: createdFileContent || '',
                        originalFileContent: deletedFileContent || '',
                    };
                    toRemove.add(deletedPath);
                }
            }
        }
    }
    // Remove deleted entries that have been merged into a RENAMED file (oldPath is deleted)
    return diffInformation.filter((info) => !toRemove.has(info.originalPath));
}