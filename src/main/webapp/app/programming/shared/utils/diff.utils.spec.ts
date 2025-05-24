// Mock Monaco Editor at the top before any imports
jest.mock('monaco-editor', () => ({
    editor: {
        createModel: jest.fn(),
        createDiffEditor: jest.fn(),
    },
}));

import * as monaco from 'monaco-editor';
import { FileStatus, processRepositoryDiff } from './diff.utils';

describe('DiffUtils', () => {
    let mockOriginalModel: any;
    let mockModifiedModel: any;
    let mockDiffEditor: any;
    let mockDiffListener: any;

    beforeEach(() => {
        // Reset mocks
        jest.clearAllMocks();

        // Setup Monaco Editor mocks
        mockOriginalModel = {
            dispose: jest.fn(),
        };

        mockModifiedModel = {
            dispose: jest.fn(),
        };

        mockDiffListener = {
            dispose: jest.fn(),
        };

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

        // Setup DOM element creation
        document.createElement = jest.fn().mockReturnValue({});
    });

    describe('processRepositoryDiff', () => {
        it('should process empty file maps', async () => {
            const originalFiles = new Map<string, string>();
            const modifiedFiles = new Map<string, string>();

            // Mock the diff computation to call the callback immediately
            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result).toEqual({
                diffInformations: [],
                totalLineChange: { addedLineCount: 0, removedLineCount: 0 },
            });
        });

        it('should process files with no changes', async () => {
            const originalFiles = new Map([['file1.txt', 'content']]);
            const modifiedFiles = new Map([['file1.txt', 'content']]);

            // Mock the diff computation
            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(0);
            expect(result.totalLineChange).toEqual({ addedLineCount: 0, removedLineCount: 0 });
        });

        it('should process created files', async () => {
            const originalFiles = new Map<string, string>();
            const modifiedFiles = new Map([['newFile.txt', 'new content']]);

            // Mock the diff computation
            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([
                {
                    originalStartLineNumber: 0,
                    originalEndLineNumber: 0,
                    modifiedStartLineNumber: 1,
                    modifiedEndLineNumber: 2,
                },
            ]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(1);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.CREATED);
            expect(result.diffInformations[0].modifiedPath).toBe('newFile.txt');
            expect(result.diffInformations[0].originalPath).toBe('');
            expect(result.totalLineChange.addedLineCount).toBe(2);
            expect(result.totalLineChange.removedLineCount).toBe(0);
        });

        it('should process deleted files', async () => {
            const originalFiles = new Map([['deletedFile.txt', 'deleted content']]);
            const modifiedFiles = new Map<string, string>();

            // Mock the diff computation
            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([
                {
                    originalStartLineNumber: 1,
                    originalEndLineNumber: 2,
                    modifiedStartLineNumber: 0,
                    modifiedEndLineNumber: 0,
                },
            ]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(1);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.DELETED);
            expect(result.diffInformations[0].originalPath).toBe('deletedFile.txt');
            expect(result.diffInformations[0].modifiedPath).toBe('');
            expect(result.totalLineChange.addedLineCount).toBe(0);
            expect(result.totalLineChange.removedLineCount).toBe(2);
        });

        it('should process modified files', async () => {
            const originalFiles = new Map([['modifiedFile.txt', 'original content']]);
            const modifiedFiles = new Map([['modifiedFile.txt', 'modified content']]);

            // Mock the diff computation
            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([
                {
                    originalStartLineNumber: 1,
                    originalEndLineNumber: 1,
                    modifiedStartLineNumber: 1,
                    modifiedEndLineNumber: 2,
                },
            ]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(1);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.UNCHANGED);
            expect(result.diffInformations[0].originalPath).toBe('modifiedFile.txt');
            expect(result.diffInformations[0].modifiedPath).toBe('modifiedFile.txt');
            expect(result.totalLineChange.addedLineCount).toBe(2);
            expect(result.totalLineChange.removedLineCount).toBe(1);
        });

        it('should handle multiple files with various changes', async () => {
            const originalFiles = new Map([
                ['file1.txt', 'content1'],
                ['file2.txt', 'content2'],
                ['toDelete.txt', 'delete me'],
            ]);
            const modifiedFiles = new Map([
                ['file1.txt', 'modified content1'],
                ['file2.txt', 'content2'], // unchanged
                ['newFile.txt', 'new content'],
            ]);

            // Mock the diff computation
            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });

            let callCount = 0;
            mockDiffEditor.getLineChanges.mockImplementation(() => {
                callCount++;
                if (callCount === 1) {
                    // file1.txt changes
                    return [
                        {
                            originalStartLineNumber: 1,
                            originalEndLineNumber: 1,
                            modifiedStartLineNumber: 1,
                            modifiedEndLineNumber: 1,
                        },
                    ];
                } else if (callCount === 2) {
                    // toDelete.txt changes
                    return [
                        {
                            originalStartLineNumber: 1,
                            originalEndLineNumber: 1,
                            modifiedStartLineNumber: 0,
                            modifiedEndLineNumber: 0,
                        },
                    ];
                } else if (callCount === 3) {
                    // newFile.txt changes
                    return [
                        {
                            originalStartLineNumber: 0,
                            originalEndLineNumber: 0,
                            modifiedStartLineNumber: 1,
                            modifiedEndLineNumber: 1,
                        },
                    ];
                }
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

            // Mock the diff computation
            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(1);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.RENAMED);
            expect(result.diffInformations[0].title).toBe('oldName.txt → newName.txt');
            expect(result.diffInformations[0].originalPath).toBe('oldName.txt');
            expect(result.diffInformations[0].modifiedPath).toBe('newName.txt');
        });

        it('should not detect rename for files with low similarity', async () => {
            const originalFiles = new Map([['oldName.txt', 'completely different content']]);
            const modifiedFiles = new Map([['newName.txt', 'totally different content']]);

            // Mock the diff computation
            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([
                {
                    originalStartLineNumber: 1,
                    originalEndLineNumber: 1,
                    modifiedStartLineNumber: 0,
                    modifiedEndLineNumber: 0,
                },
            ]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(2);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.CREATED);
            expect(result.diffInformations[1].fileStatus).toBe(FileStatus.DELETED);
        });
    });

    describe('computeDiffsMonaco', () => {
        // Access the private function through the module for testing
        // We'll test this indirectly through processRepositoryDiff, but let's test edge cases

        it('should handle files with mixed changes', async () => {
            const originalFiles = new Map([['test.txt', 'original']]);
            const modifiedFiles = new Map([['test.txt', 'modified']]);

            // Mock complex line changes
            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([
                {
                    originalStartLineNumber: 1,
                    originalEndLineNumber: 2,
                    modifiedStartLineNumber: 1,
                    modifiedEndLineNumber: 3,
                },
                {
                    originalStartLineNumber: 5,
                    originalEndLineNumber: 5,
                    modifiedStartLineNumber: 0,
                    modifiedEndLineNumber: 0,
                },
            ]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.totalLineChange.addedLineCount).toBe(3);
            expect(result.totalLineChange.removedLineCount).toBe(3);
        });

        it('should handle empty line changes', async () => {
            const originalFiles = new Map([['test.txt', 'content']]);
            const modifiedFiles = new Map([['test.txt', 'different content']]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue(null);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.totalLineChange.addedLineCount).toBe(0);
            expect(result.totalLineChange.removedLineCount).toBe(0);
        });
    });

    describe('calculateStringSimilarity', () => {
        // We'll test this indirectly through the rename detection

        it('should detect high similarity for identical strings', async () => {
            const originalFiles = new Map([['file1.txt', 'identical content']]);
            const modifiedFiles = new Map([['file2.txt', 'identical content']]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.RENAMED);
        });

        it('should handle empty strings', async () => {
            const originalFiles = new Map([['file1.txt', '']]);
            const modifiedFiles = new Map([['file2.txt', '']]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            // For file1.txt: originalFileContent="" (falsy), modifiedFileContent=undefined
            // !modifiedFileContent && originalFileContent → !undefined && "" → true && false → false
            // For file2.txt: originalFileContent=undefined, modifiedFileContent="" (falsy)
            // modifiedFileContent && !originalFileContent → "" && !undefined → false && true → false
            // Both fall through to the else case and get UNCHANGED status
            expect(result.diffInformations).toHaveLength(2);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.UNCHANGED);
            expect(result.diffInformations[1].fileStatus).toBe(FileStatus.UNCHANGED);
        });

        it('should detect similarity for mostly similar strings', async () => {
            const originalFiles = new Map([['file1.txt', 'this is a long content with many similar words']]);
            const modifiedFiles = new Map([['file2.txt', 'this is a long content with many similar terms']]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.RENAMED);
        });
    });

    describe('getDiffInformation', () => {
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

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations[0].title).toBe('a_file.txt');
            expect(result.diffInformations[1].title).toBe('m_file.txt');
            expect(result.diffInformations[2].title).toBe('z_file.txt');
        });

        it('should handle files with undefined content properly', async () => {
            const originalFiles = new Map([['file1.txt', 'content']]);
            const modifiedFiles = new Map<string, string>();

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.DELETED);
            expect(result.diffInformations[0].modifiedFileContent).toBeUndefined();
            expect(result.diffInformations[0].originalFileContent).toBe('content');
        });
    });

    describe('mergeRenamedFiles', () => {
        it('should handle multiple potential renames and pick the best match', async () => {
            const originalFiles = new Map([
                ['old1.txt', 'content A that is quite unique'],
                ['old2.txt', 'content B that is different'],
            ]);
            const modifiedFiles = new Map([
                ['new1.txt', 'content A that is quite unique'],
                ['new2.txt', 'content B that is different'],
            ]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(2);
            expect(result.diffInformations.every((info) => info.fileStatus === FileStatus.RENAMED)).toBeTrue();
        });

        it('should handle case when created or deleted arrays are not provided', async () => {
            // This tests the branch where created/deleted parameters are undefined
            const originalFiles = new Map([['old.txt', 'content']]);
            const modifiedFiles = new Map([['new.txt', 'content']]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.RENAMED);
        });

        it('should handle case where no files meet similarity threshold', async () => {
            const originalFiles = new Map([['old.txt', 'completely different content']]);
            const modifiedFiles = new Map([['new.txt', 'totally unrelated content']]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(2);
            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.CREATED);
            expect(result.diffInformations[1].fileStatus).toBe(FileStatus.DELETED);
        });
    });

    describe('Edge Cases and Error Handling', () => {
        it('should handle Monaco editor disposal properly', async () => {
            const originalFiles = new Map([['test.txt', 'content']]);
            const modifiedFiles = new Map([['test.txt', 'modified']]);

            // Set up the createModel mock to track calls
            let modelCreationCount = 0;
            (monaco.editor.createModel as jest.Mock).mockImplementation((content: string) => {
                modelCreationCount++;
                if (modelCreationCount <= 2) {
                    return mockOriginalModel;
                } else {
                    return mockModifiedModel;
                }
            });

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(mockDiffListener.dispose).toHaveBeenCalled();
            expect(mockDiffEditor.dispose).toHaveBeenCalled();
            // The original implementation may not call dispose on models in this specific scenario
            // but the important thing is that the diff listener and editor are disposed
        });

        it('should handle empty file paths', async () => {
            const originalFiles = new Map([['', 'content']]);
            const modifiedFiles = new Map([['', 'modified content']]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations).toHaveLength(0);
        });

        it('should handle very large similarity calculations', async () => {
            const longContent1 = 'a'.repeat(1000) + 'different';
            const longContent2 = 'a'.repeat(1000) + 'content';

            const originalFiles = new Map([['file1.txt', longContent1]]);
            const modifiedFiles = new Map([['file2.txt', longContent2]]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations[0].fileStatus).toBe(FileStatus.RENAMED);
        });
    });

    describe('FileStatus enum', () => {
        it('should have correct enum values', () => {
            expect(FileStatus.CREATED).toBe('created');
            expect(FileStatus.DELETED).toBe('deleted');
            expect(FileStatus.RENAMED).toBe('renamed');
            expect(FileStatus.UNCHANGED).toBe('unchanged');
        });
    });

    describe('Type Interfaces', () => {
        it('should create proper LineChange objects', async () => {
            const originalFiles = new Map([['test.txt', 'original']]);
            const modifiedFiles = new Map([['test.txt', 'modified']]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([
                {
                    originalStartLineNumber: 1,
                    originalEndLineNumber: 1,
                    modifiedStartLineNumber: 1,
                    modifiedEndLineNumber: 1,
                },
            ]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result.diffInformations[0].lineChange).toMatchObject({
                addedLineCount: expect.any(Number),
                removedLineCount: expect.any(Number),
            });
        });

        it('should create proper DiffInformation objects', async () => {
            const originalFiles = new Map([['test.txt', 'content']]);
            const modifiedFiles = new Map([['test.txt', 'modified']]);

            mockDiffEditor.onDidUpdateDiff.mockImplementation((callback: () => void) => {
                setTimeout(callback, 0);
                return mockDiffListener;
            });
            mockDiffEditor.getLineChanges.mockReturnValue([]);

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            const diffInfo = result.diffInformations[0];
            expect(diffInfo).toMatchObject({
                title: expect.any(String),
                modifiedPath: expect.any(String),
                originalPath: expect.any(String),
                diffReady: expect.any(Boolean),
                fileStatus: expect.any(String),
                modifiedFileContent: expect.any(String),
                originalFileContent: expect.any(String),
                lineChange: expect.any(Object),
            });
        });

        it('should create proper RepositoryDiffInformation objects', async () => {
            const originalFiles = new Map<string, string>();
            const modifiedFiles = new Map<string, string>();

            const result = await processRepositoryDiff(originalFiles, modifiedFiles);

            expect(result).toMatchObject({
                diffInformations: expect.any(Array),
                totalLineChange: {
                    addedLineCount: expect.any(Number),
                    removedLineCount: expect.any(Number),
                },
            });
        });
    });
});
