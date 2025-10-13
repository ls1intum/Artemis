import * as monaco from 'monaco-editor';

/**
 * Interface for line change information from the diff editor
 */
export interface LineChange {
    addedLineCount: number;
    removedLineCount: number;
    fileTooLarge?: boolean;
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
    isCollapsed?: boolean;
    loadContent?: boolean;
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

    for (const diffInfo of diffInformation) {
        const original = originalFileContentByPath.get(diffInfo.originalPath) ?? '';
        const modified = modifiedFileContentByPath.get(diffInfo.modifiedPath) ?? '';

        const lineChange = await computeDiffsMonaco(original, modified);

        diffInfo.lineChange = lineChange;
        repositoryDiffInformation.totalLineChange.addedLineCount += lineChange.addedLineCount;
        repositoryDiffInformation.totalLineChange.removedLineCount += lineChange.removedLineCount;
        if (lineChange.fileTooLarge) {
            repositoryDiffInformation.totalLineChange.fileTooLarge = true;
        }
    }

    return repositoryDiffInformation;
}

const MAX_INLINE_BYTES = 512 * 1024; // keep contents only if <= 512 KB per side

/**
 * Cheap difference check that avoids full linear scans where possible
 * @param firstString The first string to compare
 * @param secondString The second string to compare
 * @returns True if the strings differ, false otherwise
 */
function differs(firstString?: string, secondString?: string): boolean {
    if (firstString === secondString) {
        return false;
    }
    if (firstString === undefined || secondString === undefined) {
        return true;
    }
    if (firstString.length !== secondString.length) {
        return true;
    }
    return firstString !== secondString;
}

/**
 * Gets the diff information for a given template and solution file content.
 * It also handles renamed files.
 * @param originalFileContentByPath The template file content by path
 * @param modifiedFileContentByPath The solution file content by path
 * @returns The diff information
 */
function getDiffInformation(originalFileContentByPath: Map<string, string>, modifiedFileContentByPath: Map<string, string>): DiffInformation[] {
    const paths = Array.from(new Set<string>([...originalFileContentByPath.keys(), ...modifiedFileContentByPath.keys()]));

    const created: string[] = [];
    const deleted: string[] = [];

    let diffInformation: DiffInformation[] = paths
        .filter((path) => {
            const original = originalFileContentByPath.get(path);
            const modified = modifiedFileContentByPath.get(path);
            return path && differs(modified, original);
        })
        .sort((a, b) => (a < b ? -1 : a > b ? 1 : 0))
        .map((path) => {
            const original = originalFileContentByPath.get(path);
            const modified = modifiedFileContentByPath.get(path);

            // helper: only inline when reasonably small
            const inlineOriginal = typeof original === 'string' && original.length <= MAX_INLINE_BYTES ? original : undefined;
            const inlineModified = typeof modified === 'string' && modified.length <= MAX_INLINE_BYTES ? modified : undefined;

            if (modified === undefined && original !== undefined) {
                // DELETED
                deleted.push(path);
                return {
                    title: path,
                    modifiedPath: '',
                    originalPath: path,
                    originalFileContent: inlineOriginal, // keep for rename detection / UI
                    modifiedFileContent: undefined,
                    diffReady: false,
                    fileStatus: FileStatus.DELETED,
                };
            } else if (modified !== undefined && original === undefined) {
                // CREATED
                created.push(path);
                return {
                    title: path,
                    modifiedPath: path,
                    originalPath: '',
                    modifiedFileContent: inlineModified, // keep for rename detection / UI
                    originalFileContent: undefined,
                    diffReady: false,
                    fileStatus: FileStatus.CREATED,
                };
            } else {
                // PATH UNCHANGED but contents differ → attach both (with size cap)
                return {
                    title: path,
                    modifiedPath: path,
                    originalPath: path,
                    modifiedFileContent: inlineModified,
                    originalFileContent: inlineOriginal,
                    diffReady: false,
                    fileStatus: FileStatus.UNCHANGED, // path unchanged; content differs
                };
            }
        });

    diffInformation = mergeRenamedFiles(diffInformation, created, deleted);
    return diffInformation;
}

let diffHost: HTMLDivElement | undefined = undefined;

/**
 * Gets or creates the hidden DOM container for Monaco diff editors
 * @returns The cached diff host element
 */
function getDiffHost(): HTMLDivElement {
    if (!diffHost) {
        diffHost = document.createElement('div');
        diffHost.style.position = 'fixed';
        diffHost.style.width = '1px';
        diffHost.style.height = '1px';
        diffHost.style.left = '-99999px';
        document.body.appendChild(diffHost);
    }
    return diffHost;
}

/**
 * Computes the line changes between two files using Monaco Editor
 * @param originalFileContent The original file content
 * @param modifiedFileContent The modified file content
 * @returns Promise resolving to the line change object containing added and removed line counts
 */
/**
 * Files larger than this threshold will not be diffed with Monaco because the computation is expensive and prone to timeouts.
 * Instead we fall back to a lightweight sampling approach that estimates the line changes.
 */
const MAX_BYTES_FOR_DIFF = 1_000_000; // ~1 MB per side

function computeDiffsMonaco(originalFileContent: string, modifiedFileContent: string): Promise<LineChange> {
    return new Promise((resolve) => {
        let finished = false;
        if (originalFileContent.length > MAX_BYTES_FOR_DIFF || modifiedFileContent.length > MAX_BYTES_FOR_DIFF) {
            finished = true;
            resolve(estimateLineChangeUsingSampling(originalFileContent, modifiedFileContent));
            return;
        }

        const hostElement = getDiffHost();
        const diffContainer = document.createElement('div');
        diffContainer.style.width = '1px';
        diffContainer.style.height = '1px';
        hostElement.appendChild(diffContainer);

        const finish = (res: LineChange) => {
            if (finished) {
                return;
            }
            finished = true;
            clearTimeout(safetyTimeout);
            diffContainer.parentElement?.removeChild(diffContainer);
            diffListener?.dispose();
            diffEditor?.dispose();
            modifiedModel?.dispose();
            originalModel?.dispose();
            resolve(res);
        };

        // Safety timeout to ensure the promise always resolves even if Monaco never emits
        const safetyTimeout = setTimeout(() => {
            finish(estimateLineChangeUsingSampling(originalFileContent, modifiedFileContent));
        }, 10000); // 10 second timeout

        let originalModel: monaco.editor.ITextModel | undefined;
        let modifiedModel: monaco.editor.ITextModel | undefined;
        let diffEditor: monaco.editor.IStandaloneDiffEditor | undefined;
        let diffListener: monaco.IDisposable | undefined;

        try {
            originalModel = monaco.editor.createModel(originalFileContent, 'plaintext');
            modifiedModel = monaco.editor.createModel(modifiedFileContent, 'plaintext');

            diffEditor = monaco.editor.createDiffEditor(diffContainer, { readOnly: true, automaticLayout: false });
            diffEditor.setModel({ original: originalModel, modified: modifiedModel });

            diffListener = diffEditor.onDidUpdateDiff(() => {
                try {
                    const changes = diffEditor!.getLineChanges() ?? [];
                    let added = 0;
                    let removed = 0;
                    for (const change of changes) {
                        const isOriginalEmpty = change.originalEndLineNumber === 0;
                        const isModifiedEmpty = change.modifiedEndLineNumber === 0;
                        const originalCount = isOriginalEmpty ? 0 : change.originalEndLineNumber - change.originalStartLineNumber + 1;
                        const modifiedCount = isModifiedEmpty ? 0 : change.modifiedEndLineNumber - change.modifiedStartLineNumber + 1;
                        added += modifiedCount;
                        removed += originalCount;
                    }
                    finish({ addedLineCount: added, removedLineCount: removed });
                } catch {
                    finish({ addedLineCount: 0, removedLineCount: 0 });
                }
            });
        } catch {
            finish({ addedLineCount: 0, removedLineCount: 0 });
        }
    });
}

/**
 * Calculates the similarity ratio between two strings.
 * - For small inputs: Levenshtein (two-row DP) => 1 - distance / maxLength
 * - For large inputs: 5-gram Jaccard similarity (fast, O(n))
 * Returns a value in [0, 1].
 * @param firstString The first string to compare
 * @param secondString The second string to compare
 * @returns Similarity ratio between 0 and 1
 */
function calculateStringSimilarity(firstString?: string, secondString?: string): number {
    if (firstString === undefined || secondString === undefined) {
        return 0;
    }
    if (firstString === secondString) {
        return 1;
    }

    const firstLength = firstString.length;
    const secondLength = secondString.length;
    const maxLength = Math.max(firstLength, secondLength);

    // Quick guards
    if (firstLength === 0 || secondLength === 0) {
        return 0;
    }

    // If the DP would be too big (time-wise), fall back to Jaccard on 5-grams
    // With two-row DP, time is still O(n*m). Cap the "cell count" to keep UI responsive.
    const MAX_DP_CELLS = 4_000_000; // ~2k x 2k; adjust if you can afford more
    if (firstLength * secondLength > MAX_DP_CELLS) {
        return jaccardNGramSimilarity(firstString, secondString, 5, 250_000);
    }

    // Two-row Levenshtein (O(min(n,m)) memory)
    return 1 - levenshteinRatioTwoRow(firstString, secondString) / maxLength;
}

/**
 * Two-row Levenshtein distance (returns integer edit distance).
 * @param firstString The first string to compare
 * @param secondString The second string to compare
 * @returns The edit distance between the two strings
 */
function levenshteinRatioTwoRow(firstString: string, secondString: string): number {
    // Ensure firstString is the shorter one to minimize memory and cache misses
    if (firstString.length > secondString.length) {
        const temp = firstString;
        firstString = secondString;
        secondString = temp;
    }
    const shorterLength = firstString.length;
    const longerLength = secondString.length;

    const previousRow = new Uint32Array(shorterLength + 1);
    const currentRow = new Uint32Array(shorterLength + 1);
    for (let i = 0; i <= shorterLength; i++) {
        previousRow[i] = i;
    }

    for (let j = 1; j <= longerLength; j++) {
        currentRow[0] = j;
        const currentCharCode = secondString.charCodeAt(j - 1);
        for (let i = 1; i <= shorterLength; i++) {
            const cost = firstString.charCodeAt(i - 1) === currentCharCode ? 0 : 1;
            const deletionCost = previousRow[i] + 1;
            const insertionCost = currentRow[i - 1] + 1;
            const substitutionCost = previousRow[i - 1] + cost;
            currentRow[i] = Math.min(deletionCost, insertionCost, substitutionCost);
        }
        // Swap rows
        for (let k = 0; k <= shorterLength; k++) {
            previousRow[k] = currentRow[k];
        }
    }
    return previousRow[shorterLength];
}

/**
 * Fast similarity for large strings: Jaccard over character n-grams.
 * @param firstString The first string to compare
 * @param secondString The second string to compare
 * @param gramSize The size of n-grams (default 5)
 * @param gramLimit The max grams considered per string (sampling down if needed, default 250,000)
 * @returns Similarity ratio between 0 and 1
 */
function jaccardNGramSimilarity(firstString: string, secondString: string, gramSize = 5, gramLimit = 250_000): number {
    if (firstString.length < gramSize || secondString.length < gramSize) {
        // Fallback to simple prefix equality ratio if too short for n-grams
        const comparisonLength = Math.min(firstString.length, secondString.length, 1024);
        if (comparisonLength === 0) {
            return 0;
        }
        let sameCharacters = 0;
        for (let i = 0; i < comparisonLength; i++) {
            if (firstString.charCodeAt(i) === secondString.charCodeAt(i)) {
                sameCharacters++;
            }
        }
        return ((sameCharacters / comparisonLength) * Math.min(firstString.length, secondString.length)) / Math.max(firstString.length, secondString.length);
    }

    // Build n-gram sets (with lightweight hashing) and optionally sample
    const firstGramSet = buildGramSet(firstString, gramSize, gramLimit);
    const secondGramSet = buildGramSet(secondString, gramSize, gramLimit);

    // Compute Jaccard
    let intersection = 0;
    // Iterate over smaller set for speed
    const [smallerSet, largerSet] = firstGramSet.size <= secondGramSet.size ? [firstGramSet, secondGramSet] : [secondGramSet, firstGramSet];
    for (const hash of smallerSet) {
        if (largerSet.has(hash)) {
            intersection++;
        }
    }
    const union = firstGramSet.size + secondGramSet.size - intersection;
    return union === 0 ? 1 : intersection / union;
}

/**
 * Builds a set of n-gram hashes for a string using rolling hash.
 * @param inputString The string to extract n-grams from
 * @param gramSize The size of each n-gram
 * @param gramLimit The maximum number of grams to sample
 * @returns A set of hash values representing the n-grams
 */
function buildGramSet(inputString: string, gramSize: number, gramLimit: number): Set<number> {
    const totalGrams = inputString.length - gramSize + 1;
    // If total grams exceed limit, sample at a stride
    const stride = Math.max(1, Math.floor(totalGrams / gramLimit));
    const gramSet = new Set<number>();
    // Simple rolling hash (base 257, modulo 2^32 via >>> 0)
    const BASE = 257 >>> 0;
    let hash = 0 >>> 0;
    let power = 1 >>> 0;
    for (let i = 0; i < gramSize; i++) {
        hash = (hash * BASE + inputString.charCodeAt(i)) >>> 0;
        if (i < gramSize - 1) {
            power = (power * BASE) >>> 0;
        }
    }
    for (let i = 0; i < totalGrams; i++) {
        if (i % stride === 0) {
            gramSet.add(hash);
        }
        if (i + gramSize < inputString.length) {
            const outgoingCharCode = inputString.charCodeAt(i);
            const incomingCharCode = inputString.charCodeAt(i + gramSize);
            // Remove leading char and add trailing char
            hash = (((hash - ((outgoingCharCode * power) >>> 0)) >>> 0) * BASE + incomingCharCode) >>> 0;
        }
    }
    return gramSet;
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
    // Build lists if not provided
    if (!created || !deleted) {
        created = diffInformation.filter((info) => info.fileStatus === FileStatus.CREATED).map((info) => info.modifiedPath);
        deleted = diffInformation.filter((info) => info.fileStatus === FileStatus.DELETED).map((info) => info.originalPath);
    }
    if (!created.length || !deleted.length) {
        return diffInformation;
    }

    // Helper to fetch contents from the DiffInformation entries (only CREATED/DELETED have them)
    const getByModifiedPath = (path: string) => diffInformation.find((info) => info.modifiedPath === path);
    const getByOriginalPath = (path: string) => diffInformation.find((info) => info.originalPath === path);

    // Quick similarity checks
    const LENGTH_TOLERANCE = 0.02; // ±2%
    const SAMPLE_SIZE = 2048; // prefix/suffix sample for large files
    const quickLikelySame = (firstContent: string, secondContent: string) =>
        firstContent.slice(0, SAMPLE_SIZE) === secondContent.slice(0, SAMPLE_SIZE) && firstContent.slice(-SAMPLE_SIZE) === secondContent.slice(-SAMPLE_SIZE);

    const SIMILARITY_THRESHOLD = 0.8;

    // Track merges to apply after scanning
    const merges: Array<{ createdPath: string; deletedPath: string }> = [];
    const usedDeletedPaths = new Set<string>();

    for (const createdPath of created) {
        const createdInfo = getByModifiedPath(createdPath);
        const createdContent = createdInfo?.modifiedFileContent;
        if (!createdContent) {
            continue;
        }

        const minLength = Math.floor(createdContent.length * (1 - LENGTH_TOLERANCE));
        const maxLength = Math.ceil(createdContent.length * (1 + LENGTH_TOLERANCE));

        for (const deletedPath of deleted) {
            // Skip deleted files that have already been matched
            if (usedDeletedPaths.has(deletedPath)) {
                continue;
            }

            const deletedInfo = getByOriginalPath(deletedPath);
            const deletedContent = deletedInfo?.originalFileContent;
            if (!deletedContent) {
                continue;
            }

            if (deletedContent.length < minLength || deletedContent.length > maxLength) {
                continue;
            }

            // For large files, use quick prefix/suffix check as optimization
            // For small files, always calculate similarity
            const bothLargeEnough = createdContent.length >= SAMPLE_SIZE && deletedContent.length >= SAMPLE_SIZE;
            if (bothLargeEnough && !quickLikelySame(createdContent, deletedContent)) {
                continue;
            }

            const similarity = calculateStringSimilarity(createdContent, deletedContent);
            if (similarity >= SIMILARITY_THRESHOLD) {
                merges.push({ createdPath, deletedPath });
                usedDeletedPaths.add(deletedPath);
                break; // One deleted partner is enough for this created
            }
        }
    }

    // Apply merges
    for (const { createdPath, deletedPath } of merges) {
        const createdIndex = diffInformation.findIndex((info) => info.modifiedPath === createdPath);
        const deletedIndex = diffInformation.findIndex((info) => info.originalPath === deletedPath);
        if (createdIndex === -1 || deletedIndex === -1) {
            continue;
        }

        const createdInfo = diffInformation[createdIndex];
        const deletedInfo = diffInformation[deletedIndex];

        diffInformation[createdIndex] = {
            title: `${deletedPath} → ${createdPath}`,
            diffReady: false,
            fileStatus: FileStatus.RENAMED,
            modifiedPath: createdPath,
            originalPath: deletedPath,
            // carry over contents (already present on these two entries)
            modifiedFileContent: createdInfo.modifiedFileContent,
            originalFileContent: deletedInfo.originalFileContent,
        };

        diffInformation.splice(deletedIndex, 1);
    }

    return diffInformation;
}

/**
 * Estimates line additions/deletions for very large files by sampling a subset of the lines.
 * The approach hashes the start and end of each stride-sized block to keep memory usage constant while still detecting
 * edits that fall near block boundaries. The result is flagged with {@link LineChange.fileTooLarge} to indicate reduced accuracy.
 */
function estimateLineChangeUsingSampling(originalFileContent: string, modifiedFileContent: string): LineChange {
    const originalLineCount = countLines(originalFileContent);
    const modifiedLineCount = countLines(modifiedFileContent);

    const totalLines = Math.max(originalLineCount, modifiedLineCount);
    const sampleStride = Math.max(1, Math.floor(totalLines / 2000)); // sample roughly up to 2000 lines per side

    const originalSample = sampleLineHashes(originalFileContent, sampleStride, originalLineCount);
    const modifiedSample = sampleLineHashes(modifiedFileContent, sampleStride, modifiedLineCount);

    let added = 0;
    let removed = 0;

    for (const [hash, count] of originalSample) {
        const modifiedCount = modifiedSample.get(hash) ?? 0;
        if (count > modifiedCount) {
            removed += count - modifiedCount;
        }
        modifiedSample.delete(hash);
    }

    for (const count of modifiedSample.values()) {
        added += count;
    }

    return {
        addedLineCount: added,
        removedLineCount: removed,
        fileTooLarge: true,
    };
}

/**
 * Counts the number of lines in the given content without allocating intermediate arrays.
 */
function countLines(content: string): number {
    if (content.length === 0) {
        return 0;
    }

    let count = 1;
    for (let i = 0; i < content.length; i++) {
        if (content.charCodeAt(i) === 10) {
            count++;
        }
    }
    return count;
}

/**
 * Samples hashes of lines from the provided content. Both the first line and the last line of each stride-sized block
 * are considered so single-line edits near block edges are still picked up.
 */
function sampleLineHashes(content: string, stride: number, totalLines: number): Map<number, number> {
    const map = new Map<number, number>();
    if (stride <= 0) {
        return map;
    }

    if (content.length === 0) {
        return map;
    }

    let lineIndex = 0;
    let lineStart = 0;
    for (let i = 0; i <= content.length; i++) {
        const isLineEnd = i === content.length || content.charCodeAt(i) === 10;
        if (!isLineEnd) {
            continue;
        }

        const blockOffset = stride === 1 ? 0 : lineIndex % stride;
        const isBlockStart = blockOffset === 0;
        const isBlockEnd = stride > 1 && (blockOffset === stride - 1 || lineIndex === totalLines - 1);

        if (isBlockStart || isBlockEnd) {
            const end = i > lineStart && content.charCodeAt(i - 1) === 13 ? i - 1 : i;
            const lineHash = hashLine(content.slice(lineStart, end));
            map.set(lineHash, (map.get(lineHash) ?? 0) + 1);
        }

        lineIndex++;
        lineStart = i + 1;
    }

    return map;
}

/**
 * Computes a fast, non-cryptographic 32-bit FNV-1a hash for the provided line.
 */
function hashLine(line: string): number {
    let hash = 0x811c9dc5;
    for (let i = 0; i < line.length; i++) {
        hash ^= line.charCodeAt(i);
        hash = (hash * 0x01000193) >>> 0;
    }
    return hash;
}

export const __diffUtilsTesting = {
    calculateStringSimilarity,
    jaccardNGramSimilarity,
    estimateLineChangeUsingSampling,
    countLines,
    sampleLineHashes,
    hashLine,
    MAX_BYTES_FOR_DIFF,
    mergeRenamedFiles,
};
