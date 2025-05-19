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
 * Interface for text content from the diff editor
 */
export interface MonacoEditorDiffText {
    original: string;
    modified: string;
}

/**
 * Interface for diff information about a file
 */
export interface DiffInformation {
    title: string;
    path: string;
    oldPath: string;
    templateFileContent?: string;
    solutionFileContent?: string;
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
    CREATED = 'CREATED',
    DELETED = 'DELETED',
    RENAMED = 'RENAMED',
    UNCHANGED = 'UNCHANGED',
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
        lineChange.addedLineCount += change.modifiedEndLineNumber - change.modifiedStartLineNumber + 1;
        lineChange.removedLineCount += change.originalEndLineNumber - change.originalStartLineNumber + 1;
    }

    return lineChange;
}

export function processRepositoryDiff(
    templateFileContentByPath: Map<string, string>,
    solutionFileContentByPath: Map<string, string>,
): RepositoryDiffInformation {
    const diffInformation = getDiffInformation(templateFileContentByPath, solutionFileContentByPath);

    const repositoryDiffInformation: RepositoryDiffInformation = {
        diffInformations: diffInformation,
        totalLineChange: { addedLineCount: 0, removedLineCount: 0 },
    };

    diffInformation.forEach((diffInformation) => {
        const path = diffInformation.path;        
        const templateContent = templateFileContentByPath.get(path) || '';
        const solutionContent = solutionFileContentByPath.get(path) || '';
        
        const lineChange = computeDiffsMonaco(templateContent, solutionContent);
        
        diffInformation.lineChange = lineChange;
        repositoryDiffInformation.totalLineChange.addedLineCount += lineChange.addedLineCount;
        repositoryDiffInformation.totalLineChange.removedLineCount += lineChange.removedLineCount;
    });

    return repositoryDiffInformation;
}

/**
 * Gets the diff information for a given template and solution file content.
 * It also handles renamed files and ignores .gitignore patterns.
 * @param templateFileContentByPath The template file content by path
 * @param solutionFileContentByPath The solution file content by path
 * @returns The diff information
 */
export function getDiffInformation(templateFileContentByPath: Map<string, string>, solutionFileContentByPath: Map<string, string>): DiffInformation[] {
    const paths = [...new Set([...templateFileContentByPath.keys(), ...solutionFileContentByPath.keys()])];
    const created: string[] = [];
    const deleted: string[] = [];
    const ig = ignore().add(solutionFileContentByPath.get('.gitignore') || '');

    let diffInformation: DiffInformation[] = paths
        .filter(ig.createFilter())
        .filter((path) => {
            const templateContent = templateFileContentByPath.get(path);
            const solutionContent = solutionFileContentByPath.get(path);
            return path && (templateContent !== solutionContent || (templateContent === undefined) !== (solutionContent === undefined));
        })
        .map((path) => {
            const templateFileContent = templateFileContentByPath.get(path);
            const solutionFileContent = solutionFileContentByPath.get(path);

            let fileStatus: FileStatus;
            if (!templateFileContent && solutionFileContent) {
                created.push(path);
                fileStatus = FileStatus.CREATED;
            } else if (templateFileContent && !solutionFileContent) {
                deleted.push(path);
                fileStatus = FileStatus.DELETED;
            } else {
                fileStatus = FileStatus.UNCHANGED;
            }

            return ({
                title: path,
                path: path,
                oldPath: '',
                templateFileContent: templateFileContentByPath.get(path),
                solutionFileContent: solutionFileContentByPath.get(path),
                diffReady: false,
                fileStatus: fileStatus,
            });
        });

    diffInformation = mergeRenamedFiles(diffInformation, created, deleted);

    return diffInformation;
}

function computeDiffsMonaco(templateFileContent: string, solutionFileContent: string): LineChange {
    const options: DiffOpts = {
        shouldPostProcessCharChanges: true,
        shouldComputeCharChanges: true,
        shouldIgnoreTrimWhitespace: true,
        shouldMakePrettyDiff: true,
        maxComputationTime: 1000,
    }
    return convertMonacoLineChanges(diff(templateFileContent.split('\n'), solutionFileContent.split('\n'), options));
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
        created = diffInformation.filter((info) => info.fileStatus === FileStatus.CREATED).map((info) => info.path);
        deleted = diffInformation.filter((info) => info.fileStatus === FileStatus.DELETED).map((info) => info.path);
    }

    const toRemove = new Set<string>();
    for (const createdPath of created) {
        const createdFileContent = diffInformation.find((info) => info.path === createdPath)?.solutionFileContent;
        for (const deletedPath of deleted) {
            const deletedFileContent = diffInformation.find((info) => info.path === deletedPath)?.templateFileContent;
            //TODO: Use a similarity check instead of a string equality
            if (createdFileContent === deletedFileContent) {
                const createdIndex = diffInformation.findIndex((info) => info.path === createdPath);
                const deletedIndex = diffInformation.findIndex((info) => info.path === deletedPath);
                if (createdIndex !== -1 && deletedIndex !== -1) {
                    // Merge into a single RENAMED entry using old/new fields
                    diffInformation[createdIndex] = {
                        title: `${deletedPath} → ${createdPath}`,
                        diffReady: false,
                        fileStatus: FileStatus.RENAMED,
                        path: createdPath,
                        oldPath: deletedPath || '',
                        templateFileContent: deletedFileContent || '',
                        solutionFileContent: createdFileContent || '',
                    };
                    toRemove.add(deletedPath);
                }
            }
        }
    }
    // Remove deleted entries that have been merged into a RENAMED file (oldPath is deleted)
    return diffInformation.filter((info) => !toRemove.has(info.path));
}