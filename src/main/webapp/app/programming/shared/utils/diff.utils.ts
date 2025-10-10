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

// helper: simple concurrency limiter
async function mapWithLimit<T, R>(items: T[], limit: number, fn: (x: T) => Promise<R>): Promise<R[]> {
    const results: R[] = new Array(items.length);
    let i = 0;
    await Promise.all(
        Array.from({ length: Math.max(1, limit) }, async () => {
            while (i < items.length) {
                const idx = i++;
                results[idx] = await fn(items[idx]);
            }
        }),
    );
    return results;
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

    // Limit concurrent Monaco diff computations (e.g., 3 at a time)
    await mapWithLimit(diffInformation, 3, async (diffInfo) => {
        const original = originalFileContentByPath.get(diffInfo.originalPath) ?? '';
        const modified = modifiedFileContentByPath.get(diffInfo.modifiedPath) ?? '';
        const lineChange = await computeDiffsMonaco(original, modified);

        diffInfo.lineChange = lineChange;
        repositoryDiffInformation.totalLineChange.addedLineCount += lineChange.addedLineCount;
        repositoryDiffInformation.totalLineChange.removedLineCount += lineChange.removedLineCount;
    });

    return repositoryDiffInformation;
}

const MAX_INLINE_BYTES = 512 * 1024; // keep contents only if <= 512 KB per side

// Cheap difference check that avoids full linear scans where possible
function differs(a?: string, b?: string): boolean {
    if (a === b) return false; // covers both undefined
    if (a === undefined || b === undefined) return true;
    if (a.length !== b.length) return true; // cheap guard
    return a !== b; // deep compare only when same length
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
            const inlineOriginal = original && original.length <= MAX_INLINE_BYTES ? original : undefined;
            const inlineModified = modified && modified.length <= MAX_INLINE_BYTES ? modified : undefined;

            if (!modified && original) {
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
            } else if (modified && !original) {
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
                    modifiedFileContent: inlineModified, // was missing before -> UI showed “No changes…”
                    originalFileContent: inlineOriginal,
                    diffReady: false,
                    fileStatus: FileStatus.UNCHANGED, // path unchanged; content differs
                };
            }
        });

    diffInformation = mergeRenamedFiles(diffInformation, created, deleted);
    return diffInformation;
}

let __diffHost: HTMLDivElement | undefined = undefined;
function getDiffHost(): HTMLDivElement {
    if (!__diffHost) {
        __diffHost = document.createElement('div');
        __diffHost.style.position = 'fixed';
        __diffHost.style.width = '1px';
        __diffHost.style.height = '1px';
        __diffHost.style.left = '-99999px';
        document.body.appendChild(__diffHost);
    }
    return __diffHost;
}

/**
 * Computes the line changes between two files using Monaco Editor
 * @param originalFileContent The original file content
 * @param modifiedFileContent The modified file content
 * @returns Promise resolving to the line change object containing added and removed line counts
 */
function computeDiffsMonaco(originalFileContent: string, modifiedFileContent: string): Promise<LineChange> {
    return new Promise((resolve) => {
        let finished = false;
        const finish = (res: LineChange) => {
            if (finished) {
                return;
            }
            finished = true;
            try {
                diffListener?.dispose();
            } catch {
                // do nothing
            }
            try {
                diffEditor?.dispose();
            } catch {
                // do nothing
            }
            try {
                originalModel?.dispose();
            } catch {
                // do nothing
            }
            try {
                modifiedModel?.dispose();
            } catch {
                // do nothing
            }
            resolve(res);
        };

        let originalModel: monaco.editor.ITextModel | undefined;
        let modifiedModel: monaco.editor.ITextModel | undefined;
        let diffEditor: monaco.editor.IStandaloneDiffEditor | undefined;
        let diffListener: monaco.IDisposable | undefined;

        try {
            originalModel = monaco.editor.createModel(originalFileContent, 'plaintext');
            modifiedModel = monaco.editor.createModel(modifiedFileContent, 'plaintext');

            diffEditor = monaco.editor.createDiffEditor(getDiffHost(), { readOnly: true, automaticLayout: false });
            diffEditor.setModel({ original: originalModel, modified: modifiedModel });

            diffListener = diffEditor.onDidUpdateDiff(() => {
                try {
                    const changes = diffEditor!.getLineChanges() ?? [];
                    let added = 0,
                        removed = 0;
                    for (const c of changes) {
                        const o0 = c.originalEndLineNumber === 0;
                        const m0 = c.modifiedEndLineNumber === 0;
                        const origCount = o0 ? 0 : c.originalEndLineNumber - c.originalStartLineNumber + 1;
                        const modCount = m0 ? 0 : c.modifiedEndLineNumber - c.modifiedStartLineNumber + 1;
                        added += modCount;
                        removed += origCount;
                    }
                    finish({ addedLineCount: added, removedLineCount: removed });
                } catch {
                    finish({ addedLineCount: 0, removedLineCount: 0 });
                }
            });

            // Hard timeout in case Monaco doesn't emit
            setTimeout(() => finish({ addedLineCount: 0, removedLineCount: 0 }), 5000);
        } catch {
            finish({ addedLineCount: 0, removedLineCount: 0 });
        }
    });
}

/**
 * Calculates the similarity ratio between two strings.
 * - For small inputs: Levenshtein (two-row DP) => 1 - distance / maxLen
 * - For large inputs: 5-gram Jaccard similarity (fast, O(n))
 * Returns a value in [0, 1].
 */
function calculateStringSimilarity(str1: string, str2: string): number {
    if (!str1 || !str2) return 0;
    if (str1 === str2) return 1;

    const len1 = str1.length;
    const len2 = str2.length;
    const maxLen = Math.max(len1, len2);

    // Quick guards
    if (len1 === 0 || len2 === 0) return 0;
    if (len1 === len2 && len1 <= 4096 && str1 === str2) return 1; // tiny fast path

    // If the DP would be too big (time-wise), fall back to Jaccard on 5-grams
    // With two-row DP, time is still O(n*m). Cap the "cell count" to keep UI responsive.
    const MAX_DP_CELLS = 4_000_000; // ~2k x 2k; adjust if you can afford more
    if (len1 * len2 > MAX_DP_CELLS) {
        return jaccardNGramSimilarity(str1, str2, 5, 250_000);
    }

    // Two-row Levenshtein (O(min(n,m)) memory)
    return 1 - levenshteinRatioTwoRow(str1, str2) / maxLen;
}

/** Two-row Levenshtein distance (returns integer edit distance). */
function levenshteinRatioTwoRow(a: string, b: string): number {
    // Ensure a is the shorter one to minimize memory and cache misses
    if (a.length > b.length) {
        const t = a;
        a = b;
        b = t;
    }
    const n = a.length,
        m = b.length;

    const prev = new Uint32Array(n + 1);
    const curr = new Uint32Array(n + 1);
    for (let i = 0; i <= n; i++) prev[i] = i;

    for (let j = 1; j <= m; j++) {
        curr[0] = j;
        const bj = b.charCodeAt(j - 1);
        for (let i = 1; i <= n; i++) {
            const cost = a.charCodeAt(i - 1) === bj ? 0 : 1;
            const del = prev[i] + 1;
            const ins = curr[i - 1] + 1;
            const sub = prev[i - 1] + cost;
            curr[i] = del < ins ? (del < sub ? del : sub) : ins < sub ? ins : sub;
        }
        // swap rows
        for (let k = 0; k <= n; k++) {
            prev[k] = curr[k];
        }
    }
    return prev[n];
}

/**
 * Fast similarity for large strings: Jaccard over character n-grams.
 * - n: gram size (default 5)
 * - limit: max grams considered per string (sampling down if needed)
 */
function jaccardNGramSimilarity(a: string, b: string, n = 5, limit = 250_000): number {
    if (a.length < n || b.length < n) {
        // fallback to simple prefix equality ratio if too short for n-grams
        const L = Math.min(a.length, b.length, 1024);
        if (L === 0) return 0;
        let same = 0;
        for (let i = 0; i < L; i++) if (a.charCodeAt(i) === b.charCodeAt(i)) same++;
        return ((same / L) * Math.min(a.length, b.length)) / Math.max(a.length, b.length);
    }

    // Build n-gram sets (with lightweight hashing) and optionally sample
    const setA = buildGramSet(a, n, limit);
    const setB = buildGramSet(b, n, limit);

    // Compute Jaccard
    let intersection = 0;
    // Iterate over smaller set for speed
    const [small, large] = setA.size <= setB.size ? [setA, setB] : [setB, setA];
    for (const h of small) if (large.has(h)) intersection++;
    const union = setA.size + setB.size - intersection;
    return union === 0 ? 1 : intersection / union;
}

function buildGramSet(s: string, n: number, limit: number): Set<number> {
    const total = s.length - n + 1;
    // If total grams exceed limit, sample at a stride
    const stride = Math.max(1, Math.floor(total / limit));
    const set = new Set<number>();
    // Simple rolling hash (base 257, modulo 2^32 via >>> 0)
    const BASE = 257 >>> 0;
    let hash = 0 >>> 0;
    let pow = 1 >>> 0;
    for (let i = 0; i < n; i++) {
        hash = (hash * BASE + s.charCodeAt(i)) >>> 0;
        if (i < n - 1) pow = (pow * BASE) >>> 0;
    }
    for (let i = 0; i < total; i++) {
        if (i % stride === 0) set.add(hash);
        if (i + n < s.length) {
            const outCode = s.charCodeAt(i);
            const inCode = s.charCodeAt(i + n);
            // remove leading char and add trailing char
            hash = (((hash - ((outCode * pow) >>> 0)) >>> 0) * BASE + inCode) >>> 0;
        }
    }
    return set;
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
        created = diffInformation.filter((i) => i.fileStatus === FileStatus.CREATED).map((i) => i.modifiedPath);
        deleted = diffInformation.filter((i) => i.fileStatus === FileStatus.DELETED).map((i) => i.originalPath);
    }
    if (!created.length || !deleted.length) return diffInformation;

    // Helper to fetch contents from the DiffInformation entries (only CREATED/DELETED have them)
    const getByModPath = (p: string) => diffInformation.find((i) => i.modifiedPath === p);
    const getByOrigPath = (p: string) => diffInformation.find((i) => i.originalPath === p);

    // Cheap guards
    const LEN_TOL = 0.02; // ±2%
    const k = 2048; // prefix/suffix sample
    const quickLikelySame = (a: string, b: string) => a.length >= k && b.length >= k && a.slice(0, k) === b.slice(0, k) && a.slice(-k) === b.slice(-k);

    const SIMILARITY_THRESHOLD = 0.8;

    // Track merges to apply after scanning
    const merges: Array<{ createdPath: string; deletedPath: string }> = [];

    for (const cPath of created) {
        const cInfo = getByModPath(cPath);
        const c = cInfo?.modifiedFileContent;
        if (!c) continue;

        const lenLow = Math.floor(c.length * (1 - LEN_TOL));
        const lenHigh = Math.ceil(c.length * (1 + LEN_TOL));

        for (const dPath of deleted) {
            const dInfo = getByOrigPath(dPath);
            const d = dInfo?.originalFileContent;
            if (!d) continue;

            if (d.length < lenLow || d.length > lenHigh) continue;
            if (!quickLikelySame(c, d)) continue;

            const sim = calculateStringSimilarity(c, d); // safe implementation you added earlier
            if (sim >= SIMILARITY_THRESHOLD) {
                merges.push({ createdPath: cPath, deletedPath: dPath });
                break; // one deleted partner is enough for this created
            }
        }
    }

    // Apply merges
    for (const { createdPath, deletedPath } of merges) {
        const createdIndex = diffInformation.findIndex((i) => i.modifiedPath === createdPath);
        const deletedIndex = diffInformation.findIndex((i) => i.originalPath === deletedPath);
        if (createdIndex === -1 || deletedIndex === -1) continue;

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
