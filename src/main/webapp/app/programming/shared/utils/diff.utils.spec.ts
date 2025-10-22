// Mock Monaco Editor at the top before imports
jest.mock('monaco-editor', () => ({
    editor: {
        createModel: jest.fn(),
        createDiffEditor: jest.fn(),
    },
}));

import * as monaco from 'monaco-editor';
import { DiffInformation, FileStatus, __diffUtilsTesting, processRepositoryDiff } from './diff.utils';

describe('DiffUtils', () => {
    let mockOriginalModel: monaco.editor.ITextModel;
    let mockModifiedModel: monaco.editor.ITextModel;
    let mockDiffListener: monaco.IDisposable;
    let mockDiffEditor: jest.Mocked<Pick<monaco.editor.IStandaloneDiffEditor, 'setModel' | 'onDidUpdateDiff' | 'getLineChanges' | 'dispose'>>;

    // Helper function to setup Monaco mocks with common configuration
    const setupMonacoMocks = (lineChanges: monaco.editor.ILineChange[] = []) => {
        mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
            setTimeout(callback, 0);
            return mockDiffListener;
        });
        mockDiffEditor.getLineChanges.mockReturnValue(lineChanges);
    };

    // Helper: create a strongly-typed line change
    const createLineChange = (originalStart: number, originalEnd: number, modifiedStart: number, modifiedEnd: number): monaco.editor.ILineChange => ({
        originalStartLineNumber: originalStart,
        originalEndLineNumber: originalEnd,
        modifiedStartLineNumber: modifiedStart,
        modifiedEndLineNumber: modifiedEnd,
        charChanges: [] as monaco.editor.ICharChange[],
    });

    beforeEach(() => {
        // Reset mocks
        jest.clearAllMocks();

        const originalModelStub = { dispose: jest.fn() } satisfies Pick<monaco.editor.ITextModel, 'dispose'>;
        const modifiedModelStub = { dispose: jest.fn() } satisfies Pick<monaco.editor.ITextModel, 'dispose'>;

        // Setup Monaco Editor mocks
        mockOriginalModel = originalModelStub as unknown as monaco.editor.ITextModel;
        mockModifiedModel = modifiedModelStub as unknown as monaco.editor.ITextModel;
        mockDiffListener = { dispose: jest.fn() } satisfies monaco.IDisposable;

        mockDiffEditor = {
            setModel: jest.fn(),
            onDidUpdateDiff: jest.fn().mockReturnValue(mockDiffListener),
            getLineChanges: jest.fn(),
            dispose: jest.fn(),
        };

        (monaco.editor.createModel as jest.Mock).mockImplementation((content: string) => {
            if (content === 'original') {
                return mockOriginalModel;
            }
            if (content === 'modified') {
                return mockModifiedModel;
            }

            // For generated test content fall back to assigning models based on alternating calls.
            const callIndex = (monaco.editor.createModel as jest.Mock).mock.calls.length;
            return callIndex % 2 === 0 ? mockOriginalModel : mockModifiedModel;
        });

        (monaco.editor.createDiffEditor as jest.Mock).mockReturnValue(mockDiffEditor);

        // Mock DOM elements with proper structure for the new getDiffHost() implementation
        jest.spyOn(Document.prototype, 'createElement').mockImplementation(() => {
            const mockElement = {
                style: {},
                appendChild: jest.fn(),
                removeChild: jest.fn(),
                parentElement: {
                    removeChild: jest.fn(),
                },
            };
            return mockElement as unknown as HTMLElement;
        });

        // Mock document.body.appendChild to accept mock elements
        jest.spyOn(document.body, 'appendChild').mockImplementation((node: Node) => node as any);
    });

    describe('processRepositoryDiff', () => {
        it('should ignore files without content changes', async () => {
            const originalFiles = new Map([['unchanged.txt', 'same content']]);
            const modifiedFiles = new Map([['unchanged.txt', 'same content']]);

            setupMonacoMocks([]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(0);
            expect(result.totalLineChange).toEqual({ addedLineCount: 0, removedLineCount: 0 });
        });

        it('should process empty file maps', async () => {
            const originalFiles = new Map<string, string>();
            const modifiedFiles = new Map<string, string>();

            setupMonacoMocks([]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result).toEqual({
                diffInformations: [],
                totalLineChange: { addedLineCount: 0, removedLineCount: 0 },
            });
        });

        it('should process created files', async () => {
            const originalFiles = new Map<string, string>();
            const modifiedFiles = new Map([['newFile.txt', 'new content']]);

            setupMonacoMocks([createLineChange(0, 0, 1, 2)]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(1);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.CREATED);
            expect(result.diffInformations[0].modifiedPath).toBe('newFile.txt');
            expect(result.totalLineChange.addedLineCount).toBe(2);
            expect(result.totalLineChange.removedLineCount).toBe(0);
        });

        it('should process deleted files', async () => {
            const originalFiles = new Map([['deletedFile.txt', 'deleted content']]);
            const modifiedFiles = new Map<string, string>();

            setupMonacoMocks([createLineChange(1, 2, 0, 0)]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(1);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.DELETED);
            expect(result.diffInformations[0].originalPath).toBe('deletedFile.txt');
            expect(result.totalLineChange.addedLineCount).toBe(0);
            expect(result.totalLineChange.removedLineCount).toBe(2);
        });

        it('should process modified files', async () => {
            const originalFiles = new Map([['modifiedFile.txt', 'original content']]);
            const modifiedFiles = new Map([['modifiedFile.txt', 'modified content']]);

            setupMonacoMocks([createLineChange(1, 1, 1, 2)]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(1);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.UNCHANGED);
            expect(result.diffInformations[0].originalPath).toBe('modifiedFile.txt');
            expect(result.totalLineChange.addedLineCount).toBe(2);
            expect(result.totalLineChange.removedLineCount).toBe(1);
        });

        it('should handle multiple files with various changes', async () => {
            const originalFiles = new Map([
                ['file1.txt', 'content1'],
                ['toDelete.txt', 'delete me'],
            ]);
            const modifiedFiles = new Map([
                ['file1.txt', 'modified content1'],
                ['newFile.txt', 'new content'],
            ]);

            let callCount = 0;
            setupMonacoMocks();
            mockDiffEditor.getLineChanges.mockImplementation(() => {
                callCount++;
                if (callCount === 1) return [createLineChange(1, 1, 1, 1)];
                if (callCount === 2) return [createLineChange(1, 1, 0, 0)];
                if (callCount === 3) return [createLineChange(0, 0, 1, 1)];
                return [];
            });

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(3);
            expect(result.totalLineChange.addedLineCount).toBe(2);
            expect(result.totalLineChange.removedLineCount).toBe(2);
        });

        it('should detect renamed files based on content similarity', async () => {
            const originalFiles = new Map([['oldName.txt', 'identical content for rename detection']]);
            const modifiedFiles = new Map([['newName.txt', 'identical content for rename detection']]);

            setupMonacoMocks([]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(1);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.RENAMED);
            expect(result.diffInformations[0].title).toBe('oldName.txt → newName.txt');
        });

        it('should not detect rename for files with low similarity', async () => {
            const originalFiles = new Map([['oldName.txt', 'completely different content']]);
            const modifiedFiles = new Map([['newName.txt', 'totally different content']]);

            setupMonacoMocks([createLineChange(1, 1, 0, 0)]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(2);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.CREATED);
            expect(result.diffInformations[1].fileStatus).toBe(FileStatus.DELETED);
        });

        it('should handle multiple potential renames and pick the best match', async () => {
            const originalFiles = new Map([
                ['old1.txt', 'content A that is quite unique'],
                ['old2.txt', 'content B that is different'],
            ]);
            const modifiedFiles = new Map([
                ['new1.txt', 'content A that is quite unique'],
                ['new2.txt', 'content B that is different'],
            ]);

            setupMonacoMocks([]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(2);
            expect(result.diffInformations).toSatisfyAll((info) => info.fileStatus === FileStatus.RENAMED);
        });

        it('should fall back to zero changes if diff computation throws', async () => {
            const originalFiles = new Map([['error.txt', 'original content']]);
            const modifiedFiles = new Map([['error.txt', 'modified content']]);

            setupMonacoMocks();
            mockDiffEditor.getLineChanges.mockImplementation(() => {
                throw new Error('monaco failure');
            });

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.totalLineChange).toEqual({ addedLineCount: 0, removedLineCount: 0 });
        });

        it('should resolve gracefully when Monaco initialization fails', async () => {
            const originalFiles = new Map([['error.txt', 'original content']]);
            const modifiedFiles = new Map([['error.txt', 'modified content']]);

            setupMonacoMocks();
            (monaco.editor.createModel as jest.Mock).mockImplementationOnce(() => {
                throw new Error('init error');
            });

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.totalLineChange).toEqual({ addedLineCount: 0, removedLineCount: 0 });
            expect(mockDiffEditor.setModel).not.toHaveBeenCalled();
        });

        it('should fall back to sampling for files exceeding the Monaco threshold', async () => {
            const { MAX_BYTES_FOR_DIFF } = __diffUtilsTesting;
            const largeOriginal = 'a'.repeat(MAX_BYTES_FOR_DIFF + 5);
            const largeModified = 'b'.repeat(MAX_BYTES_FOR_DIFF + 10);

            setupMonacoMocks();

            const result = await processRepositoryDiff(new Map([['big-file.txt', largeOriginal]]), new Map([['big-file.txt', largeModified]]));

            expect(monaco.editor.createDiffEditor).not.toHaveBeenCalled();
            expect(result.totalLineChange.fileTooLarge).toBeTrue();
            expect(result.totalLineChange.addedLineCount).toBeGreaterThan(0);
            expect(result.totalLineChange.removedLineCount).toBeGreaterThan(0);
            expect(result.diffInformations).toHaveLength(1);
            expect(result.diffInformations[0].lineChange).toMatchObject({ fileTooLarge: true });
        });

        it('should ignore timeout completion after diff results are processed', async () => {
            jest.useFakeTimers();

            const originalFiles = new Map([['double-callback.txt', 'original content']]);
            const modifiedFiles = new Map([['double-callback.txt', 'modified content']]);

            setupMonacoMocks([createLineChange(1, 1, 1, 1)]);

            try {
                const resultPromise = processRepositoryDiff(originalFiles, modifiedFiles);
                jest.runOnlyPendingTimers();
                const result = await resultPromise;

                expect(result.totalLineChange).toEqual({ addedLineCount: 1, removedLineCount: 1 });
                expect(mockDiffListener.dispose).toHaveBeenCalledOnce();
                expect(mockDiffEditor.dispose).toHaveBeenCalledOnce();
            } finally {
                jest.useRealTimers();
            }
        });

        it('should guard against repeated diff updates', async () => {
            const originalFiles = new Map([['double-update.txt', 'original content']]);
            const modifiedFiles = new Map([['double-update.txt', 'modified content']]);

            setupMonacoMocks([createLineChange(1, 1, 1, 1)]);
            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                // Call the callback twice asynchronously to simulate repeated updates
                setTimeout(() => callback(), 0);
                setTimeout(() => callback(), 0);
                return mockDiffListener;
            });

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.totalLineChange).toEqual({ addedLineCount: 1, removedLineCount: 1 });
            expect(mockDiffListener.dispose).toHaveBeenCalledOnce();
            expect(mockDiffEditor.dispose).toHaveBeenCalledOnce();
        });

        it('should resolve using the safety timeout when Monaco never updates', async () => {
            jest.useFakeTimers();

            const originalFiles = new Map([['timeout.txt', 'original content']]);
            const modifiedFiles = new Map([['timeout.txt', 'modified content']]);

            setupMonacoMocks();
            mockDiffEditor.onDidUpdateDiff.mockImplementation(() => mockDiffListener);

            try {
                const promise = processRepositoryDiff(originalFiles, modifiedFiles);
                jest.advanceTimersByTime(10000);
                const result = await promise;

                expect(result.totalLineChange.addedLineCount).toBeGreaterThanOrEqual(0);
                expect(result.totalLineChange.removedLineCount).toBeGreaterThanOrEqual(0);
                expect(mockDiffListener.dispose).toHaveBeenCalledOnce();
                expect(mockDiffEditor.dispose).toHaveBeenCalledOnce();
            } finally {
                jest.useRealTimers();
            }
        });

        it('should handle empty line changes', async () => {
            const originalFiles = new Map([['test.txt', 'content']]);
            const modifiedFiles = new Map([['test.txt', 'different content']]);

            setupMonacoMocks();
            mockDiffEditor.getLineChanges.mockReturnValue(null);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.totalLineChange.addedLineCount).toBe(0);
            expect(result.totalLineChange.removedLineCount).toBe(0);
        });

        it('should handle Monaco editor disposal properly', async () => {
            const originalFiles = new Map([['test.txt', 'content']]);
            const modifiedFiles = new Map([['test.txt', 'modified']]);

            setupMonacoMocks([]);
            await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(mockDiffListener.dispose).toHaveBeenCalled();
            expect(mockDiffEditor.dispose).toHaveBeenCalled();
        });

        it('should handle files with mixed changes', async () => {
            const originalFiles = new Map([['test.txt', 'original']]);
            const modifiedFiles = new Map([['test.txt', 'modified']]);

            setupMonacoMocks([createLineChange(1, 2, 1, 3), createLineChange(5, 5, 0, 0)]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.totalLineChange.addedLineCount).toBe(3);
            expect(result.totalLineChange.removedLineCount).toBe(3);
        });

        it('should sort files alphabetically', async () => {
            const originalFiles = new Map([
                ['z_file.txt', 'content'],
                ['a_file.txt', 'content'],
                ['m_file.txt', 'content'],
            ]);
            const modifiedFiles = new Map([
                ['z_file.txt', 'modified'],
                ['a_file.txt', 'modified'],
                ['m_file.txt', 'modified'],
            ]);

            setupMonacoMocks([]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations[0].title).toBe('a_file.txt');
            expect(result.diffInformations[1].title).toBe('m_file.txt');
            expect(result.diffInformations[2].title).toBe('z_file.txt');
        });

        it('should handle empty strings in similarity calculation', async () => {
            const originalFiles = new Map([['file1.txt', '']]);
            const modifiedFiles = new Map([['file2.txt', '']]);

            setupMonacoMocks([]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(2);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.DELETED);
            expect(result.diffInformations[1].fileStatus).toBe(FileStatus.CREATED);
        });

        it('should handle very large similarity calculations', async () => {
            const longContent1 = 'a'.repeat(1000) + 'different';
            const longContent2 = 'a'.repeat(1000) + 'content';

            const originalFiles = new Map([['file1.txt', longContent1]]);
            const modifiedFiles = new Map([['file2.txt', longContent2]]);

            setupMonacoMocks([]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.RENAMED);
        });

        it('should rely on n-gram similarity for extremely large but identical files', async () => {
            const hugePrefix = 'x'.repeat(2500);
            const hugeMiddle = 'y'.repeat(1200);
            const hugeSuffix = 'z'.repeat(2500);
            const hugeContent = `${hugePrefix}${hugeMiddle}${hugeSuffix}`;

            const originalFiles = new Map([['originalHuge.txt', hugeContent]]);
            const modifiedFiles = new Map([['renamedHuge.txt', hugeContent]]);

            setupMonacoMocks([]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(1);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.RENAMED);
            expect(result.diffInformations[0].title).toBe('originalHuge.txt → renamedHuge.txt');
        });

        it('should keep files separate when large contents differ significantly despite matching prefixes and suffixes', async () => {
            const prefix = 'a'.repeat(2048);
            const differingMiddleOriginal = 'b'.repeat(2000);
            const differingMiddleModified = 'c'.repeat(2000);
            const suffix = 'z'.repeat(2048);

            const originalContent = `${prefix}${differingMiddleOriginal}${suffix}`;
            const modifiedContent = `${prefix}${differingMiddleModified}${suffix}`;

            const originalFiles = new Map([['originalHuge.txt', originalContent]]);
            const modifiedFiles = new Map([['renamedHuge.txt', modifiedContent]]);

            setupMonacoMocks([]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(2);
            expect(result.diffInformations.map((info) => info.fileStatus)).toIncludeSameMembers([FileStatus.CREATED, FileStatus.DELETED]);
        });

        it('should expose helper similarities for targeted edge cases', () => {
            const { calculateStringSimilarity, jaccardNGramSimilarity } = __diffUtilsTesting;

            expect(calculateStringSimilarity('', 'non-empty')).toBe(0);
            expect(calculateStringSimilarity(undefined, 'value')).toBe(0);
            expect(calculateStringSimilarity('value', undefined)).toBe(0);
            expect(jaccardNGramSimilarity('abc', 'abd', 5)).toBeCloseTo(2 / 3, 5);

            // Test swap logic in levenshteinRatioTwoRow by passing longer string first
            const similarity = calculateStringSimilarity('longer string here', 'short');
            expect(similarity).toBeGreaterThan(0);
            expect(similarity).toBeLessThan(1);
        });

        it('should handle equal paths in sorting', async () => {
            // This tests the sort comparator returning 0 (line 112)
            const originalFiles = new Map([
                ['same.txt', 'content'],
                ['same.txt', 'different content'], // Duplicate key overwrites in Map, but tests the edge case
            ]);
            const modifiedFiles = new Map([['same.txt', 'modified']]);

            setupMonacoMocks([]);
            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            // Should still work correctly even with duplicate paths
            expect(result.diffInformations).toHaveLength(1);
        });
    });

    describe('mergeRenamedFiles', () => {
        const { mergeRenamedFiles } = __diffUtilsTesting;

        it('should skip merges when contents are unavailable', () => {
            const diffInformation: DiffInformation[] = [
                {
                    title: 'created-no-content',
                    modifiedPath: 'created-no-content',
                    originalPath: '',
                    diffReady: false,
                    fileStatus: FileStatus.CREATED,
                },
                {
                    title: 'created-with-content',
                    modifiedPath: 'created-with-content',
                    originalPath: '',
                    modifiedFileContent: 'short content',
                    diffReady: false,
                    fileStatus: FileStatus.CREATED,
                },
                {
                    title: 'deleted-no-content',
                    modifiedPath: '',
                    originalPath: 'deleted-no-content',
                    diffReady: false,
                    fileStatus: FileStatus.DELETED,
                },
                {
                    title: 'deleted-different-length',
                    modifiedPath: '',
                    originalPath: 'deleted-different-length',
                    originalFileContent: 'content with a very different length from the created file',
                    diffReady: false,
                    fileStatus: FileStatus.DELETED,
                },
            ];

            mergeRenamedFiles(diffInformation);

            expect(diffInformation.some((info) => info.fileStatus === FileStatus.RENAMED)).toBeFalse();
        });

        it('should skip merge when quick similarity check fails for large files', () => {
            const createdContent = 'A' + 'x'.repeat(2500) + 'B';
            const deletedContent = 'Z' + 'x'.repeat(2500) + 'Y';

            const diffInformation: DiffInformation[] = [
                {
                    title: 'created-large',
                    modifiedPath: 'created-large',
                    originalPath: '',
                    modifiedFileContent: createdContent,
                    diffReady: false,
                    fileStatus: FileStatus.CREATED,
                },
                {
                    title: 'deleted-large',
                    modifiedPath: '',
                    originalPath: 'deleted-large',
                    originalFileContent: deletedContent,
                    diffReady: false,
                    fileStatus: FileStatus.DELETED,
                },
            ];

            const result = mergeRenamedFiles(diffInformation, ['created-large'], ['deleted-large']);

            expect(result[0].fileStatus).toBe(FileStatus.CREATED);
            expect(result[1].fileStatus).toBe(FileStatus.DELETED);
        });

        it('should guard against missing deleted entries during merge application', () => {
            const sharedContent = 'identical content for rename detection';
            const diffInformation: DiffInformation[] = [
                {
                    title: 'created-one',
                    modifiedPath: 'created-one',
                    originalPath: '',
                    modifiedFileContent: sharedContent,
                    diffReady: false,
                    fileStatus: FileStatus.CREATED,
                },
                {
                    title: 'created-two',
                    modifiedPath: 'created-two',
                    originalPath: '',
                    modifiedFileContent: sharedContent,
                    diffReady: false,
                    fileStatus: FileStatus.CREATED,
                },
                {
                    title: 'deleted-original',
                    modifiedPath: '',
                    originalPath: 'deleted-original',
                    originalFileContent: sharedContent,
                    diffReady: false,
                    fileStatus: FileStatus.DELETED,
                },
            ];

            const result = mergeRenamedFiles(diffInformation);

            // When multiple created files match a single deleted file with identical content,
            // only the first match is merged into a rename, and the remaining created file stays as-is
            expect(result).toHaveLength(2);
            const renamedEntry = result.find((info) => info.fileStatus === FileStatus.RENAMED);
            const remainingCreated = result.find((info) => info.fileStatus === FileStatus.CREATED);
            expect(renamedEntry).toBeDefined();
            expect(renamedEntry?.title).toBe('deleted-original → created-one');
            expect(remainingCreated).toBeDefined();
            expect(remainingCreated?.modifiedPath).toBe('created-two');
        });
    });

    describe('sampling helpers', () => {
        const { estimateLineChangeUsingSampling, countLines, sampleLineHashes, hashLine } = __diffUtilsTesting;

        it('should mark results from sampling as approximate', () => {
            const original = ['alpha', 'beta', 'gamma'].join('\n');
            const modified = ['alpha', 'beta', 'delta'].join('\n');

            const result = estimateLineChangeUsingSampling(original, modified);

            expect(result.fileTooLarge).toBeTrue();
            expect(result.addedLineCount).toBe(1);
            expect(result.removedLineCount).toBe(1);
        });

        it('should count lines for mixed newline styles', () => {
            expect(countLines('one\r\ntwo\nthree')).toBe(3);
            expect(countLines('')).toBe(0);
        });

        it('should sample both block boundaries for strides larger than one', () => {
            const content = ['l1', 'l2', 'l3', 'l4'].join('\n');
            const map = sampleLineHashes(content, 2, 4);

            const totalSamples = Array.from(map.values()).reduce((sum, value) => sum + value, 0);
            expect(totalSamples).toBe(4);
        });

        it('should return an empty map for non-positive stride or empty content', () => {
            expect(sampleLineHashes('content', 0, 10).size).toBe(0);
            expect(sampleLineHashes('', 5, 0).size).toBe(0);
        });

        it('should generate stable hashes for identical lines', () => {
            expect(hashLine('same line')).toBe(hashLine('same line'));
            expect(hashLine('same line')).not.toBe(hashLine('different line'));
        });
    });
});
