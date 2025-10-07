// Mock Monaco Editor at the top before imports
jest.mock('monaco-editor', () => ({
    editor: {
        createModel: jest.fn(),
        createDiffEditor: jest.fn(),
    },
}));

import * as monaco from 'monaco-editor';
import { FileStatus, processRepositoryDiff } from './diff.utils';

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
            if (content === 'original') return mockOriginalModel;
            if (content === 'modified') return mockModifiedModel;
            return content.includes('original') ? mockOriginalModel : mockModifiedModel;
        });

        (monaco.editor.createDiffEditor as jest.Mock).mockReturnValue(mockDiffEditor);
        jest.spyOn(Document.prototype, 'createElement').mockImplementation(() => ({}) as HTMLElement);
    });

    describe('processRepositoryDiff', () => {
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
    });

    describe('Edge Cases and Error Handling', () => {
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
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.UNCHANGED);
            expect(result.diffInformations[1].fileStatus).toBe(FileStatus.UNCHANGED);
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
    });
});
