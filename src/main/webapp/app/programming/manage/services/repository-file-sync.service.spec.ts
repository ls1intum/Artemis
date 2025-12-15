import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { DiffMatchPatch } from 'diff-match-patch-typescript';

import { FileOperation, RepositoryFileSyncService } from 'app/programming/manage/services/repository-file-sync.service';
import {
    ProgrammingExerciseEditorFileChangeType,
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import { CodeEditorFileService } from 'app/programming/shared/code-editor/services/code-editor-file.service';
import { DeleteFileChange, FileType, RenameFileChange, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';

describe('RepositoryFileSyncService', () => {
    let service: RepositoryFileSyncService;
    let syncService: jest.Mocked<ProgrammingExerciseEditorSyncService>;
    let fileService: jest.Mocked<CodeEditorFileService>;
    let incomingMessages$: Subject<ProgrammingExerciseEditorSyncMessage>;
    const dmp = new DiffMatchPatch();

    beforeEach(() => {
        incomingMessages$ = new Subject<ProgrammingExerciseEditorSyncMessage>();

        TestBed.configureTestingModule({
            providers: [
                RepositoryFileSyncService,
                {
                    provide: ProgrammingExerciseEditorSyncService,
                    useValue: {
                        subscribeToUpdates: jest.fn().mockReturnValue(incomingMessages$.asObservable()),
                        sendSynchronizationUpdate: jest.fn(),
                        unsubscribe: jest.fn(),
                    },
                },
                {
                    provide: CodeEditorFileService,
                    useValue: {
                        updateFileReferences: jest.fn((files, _change) => files),
                    },
                },
            ],
        });

        service = TestBed.inject(RepositoryFileSyncService);
        syncService = TestBed.inject(ProgrammingExerciseEditorSyncService) as jest.Mocked<ProgrammingExerciseEditorSyncService>;
        fileService = TestBed.inject(CodeEditorFileService) as jest.Mocked<CodeEditorFileService>;
    });

    afterEach(() => {
        service.reset();
        jest.clearAllMocks();
    });

    describe('Initialization and cleanup', () => {
        it('subscribes to synchronization updates for an exercise', () => {
            const updates$ = service.init(42, () => true);

            expect(syncService.subscribeToUpdates).toHaveBeenCalledWith(42);
            expect(updates$).toBeDefined();
        });

        it('completes listeners and unsubscribes on reset', () => {
            let completed = false;
            service.init(42, () => true).subscribe({ complete: () => (completed = true) });

            service.reset();

            expect(syncService.unsubscribe).toHaveBeenCalled();
            expect(completed).toBeTrue();
        });
    });

    describe('Local file operations', () => {
        beforeEach(() => {
            service.init(42, () => true);
        });

        it('sends patches for content changes using existing baseline', () => {
            service.registerBaseline(RepositoryType.TEMPLATE, 'file.txt', 'old content');

            service.handleLocalFileOperation({ type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: 'file.txt', content: 'new content' }, RepositoryType.TEMPLATE);

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    filePatches: expect.arrayContaining([
                        expect.objectContaining({
                            fileName: 'file.txt',
                            changeType: ProgrammingExerciseEditorFileChangeType.CONTENT,
                            patch: expect.any(String),
                        }),
                    ]),
                }),
            );
        });

        it('sends create operation with full content', () => {
            service.handleLocalFileOperation(
                { type: ProgrammingExerciseEditorFileChangeType.CREATE, fileName: 'new.txt', content: 'hello', fileType: FileType.FILE },
                RepositoryType.TEMPLATE,
            );

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    filePatches: [
                        expect.objectContaining({
                            fileName: 'new.txt',
                            changeType: ProgrammingExerciseEditorFileChangeType.CREATE,
                            patch: 'hello',
                            fileType: FileType.FILE,
                        }),
                    ],
                }),
            );
        });

        it('sends delete operations and removes baseline', () => {
            service.registerBaseline(RepositoryType.TEMPLATE, 'old.txt', 'content');

            service.handleLocalFileOperation({ type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: 'old.txt' }, RepositoryType.TEMPLATE);

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    filePatches: [
                        expect.objectContaining({
                            fileName: 'old.txt',
                            changeType: ProgrammingExerciseEditorFileChangeType.DELETE,
                        }),
                    ],
                }),
            );
            const baselineKey = '42-TEMPLATE_REPOSITORY-none::old.txt';
            expect((service as any).baselines[baselineKey]).toBeUndefined();
        });

        it('moves baseline on rename operations and sends rename payload', () => {
            service.registerBaseline(RepositoryType.TEMPLATE, 'old.txt', 'content');

            service.handleLocalFileOperation(
                { type: ProgrammingExerciseEditorFileChangeType.RENAME, fileName: 'old.txt', newFileName: 'new.txt', content: 'content' },
                RepositoryType.TEMPLATE,
            );

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    filePatches: [
                        expect.objectContaining({
                            fileName: 'old.txt',
                            newFileName: 'new.txt',
                            changeType: ProgrammingExerciseEditorFileChangeType.RENAME,
                        }),
                    ],
                }),
            );

            const baselines = (service as any).baselines;
            expect(baselines['42-TEMPLATE_REPOSITORY-none::old.txt']).toBeUndefined();
            expect(baselines['42-TEMPLATE_REPOSITORY-none::new.txt']).toBe('content');
        });
    });

    describe('Remote synchronization handling', () => {
        beforeEach(() => {
            service.init(42, () => true);
        });

        it('emits applied content patches from remote changes', () => {
            const operations: FileOperation[] = [];
            service.init(42, () => true).subscribe((op) => operations.push(op));
            service.registerBaseline(RepositoryType.TEMPLATE, 'file.txt', 'old');

            const patch = dmp.patch_toText(dmp.patch_make('old', 'new'));
            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [
                    {
                        fileName: 'file.txt',
                        patch,
                        changeType: ProgrammingExerciseEditorFileChangeType.CONTENT,
                    },
                ],
                timestamp: 1,
            });

            expect(operations).toEqual([{ type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: 'file.txt', content: 'new' }]);
        });

        it('emits create, delete, and rename operations', () => {
            const operations: FileOperation[] = [];
            service.init(42, () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [
                    { fileName: 'new.txt', patch: 'content', changeType: ProgrammingExerciseEditorFileChangeType.CREATE, fileType: FileType.FILE },
                    { fileName: 'delete.txt', changeType: ProgrammingExerciseEditorFileChangeType.DELETE },
                    { fileName: 'old.txt', newFileName: 'renamed.txt', patch: 'content', changeType: ProgrammingExerciseEditorFileChangeType.RENAME },
                ],
                timestamp: 1,
            });

            expect(operations).toEqual([
                { type: ProgrammingExerciseEditorFileChangeType.CREATE, fileName: 'new.txt', content: 'content', fileType: FileType.FILE },
                { type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: 'delete.txt' },
                { type: ProgrammingExerciseEditorFileChangeType.RENAME, fileName: 'old.txt', newFileName: 'renamed.txt', content: 'content' },
            ]);
        });

        it('ignores messages without a target', () => {
            const operations: FileOperation[] = [];
            service.init(42, () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                filePatches: [{ fileName: 'file.txt', patch: 'content', changeType: ProgrammingExerciseEditorFileChangeType.CREATE }],
                timestamp: 1,
            });

            expect(operations).toHaveLength(0);
        });

        it('skips outdated messages based on timestamp', () => {
            const operations: FileOperation[] = [];
            service.init(42, () => true).subscribe((op) => operations.push(op));
            service.registerBaseline(RepositoryType.TEMPLATE, 'file.txt', 'initial');

            const newPatch = dmp.patch_toText(dmp.patch_make('initial', 'new'));
            const oldPatch = dmp.patch_toText(dmp.patch_make('initial', 'old'));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [{ fileName: 'file.txt', patch: newPatch, changeType: ProgrammingExerciseEditorFileChangeType.CONTENT }],
                timestamp: 5,
            });

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [{ fileName: 'file.txt', patch: oldPatch, changeType: ProgrammingExerciseEditorFileChangeType.CONTENT }],
                timestamp: 1,
            });

            expect(operations).toEqual([{ type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: 'file.txt', content: 'new' }]);
        });

        it('filters messages via provided target filter', () => {
            const operations: FileOperation[] = [];
            service.init(42, (message) => message.target === ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [{ fileName: 'file.txt', patch: 'content', changeType: ProgrammingExerciseEditorFileChangeType.CREATE }],
                timestamp: 1,
            });

            expect(operations).toHaveLength(0);
        });

        it('emits new commit alert operations', () => {
            const operations: FileOperation[] = [];
            service.init(42, () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                newCommitAlert: true,
            });

            expect(operations).toEqual([{ type: 'NEW_COMMIT_ALERT' }]);
        });

        it('responds to file requests with available baselines', () => {
            service.registerBaseline(RepositoryType.TEMPLATE, 'file.txt', 'baseline');

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                fileRequests: ['file.txt'],
            });

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    fileFulls: [{ fileName: 'file.txt', content: 'baseline' }],
                }),
            );
        });

        it('applies full file sync messages to baselines', () => {
            const operations: FileOperation[] = [];
            service.init(42, () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                fileFulls: [{ fileName: 'file.txt', content: 'complete content' }],
                timestamp: 2,
            });

            expect(operations).toEqual([{ type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: 'file.txt', content: 'complete content' }]);
        });
    });

    describe('Full file requests', () => {
        beforeEach(() => {
            service.init(42, () => true);
        });

        it('does not send requests when repository type has no sync target', () => {
            service.requestFullFile(RepositoryType.ASSIGNMENT, 'file.txt');

            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
        });

        it('requests full file content for a repository type', () => {
            service.requestFullFile(RepositoryType.TEMPLATE, 'file.txt');

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    fileRequests: ['file.txt'],
                }),
            );
        });
    });

    describe('Code editor integration', () => {
        let mockCodeEditor: Partial<CodeEditorContainerComponent>;

        beforeEach(() => {
            service.init(42, () => true);

            mockCodeEditor = {
                applyRemoteFileContent: jest.fn(),
                fileBrowser: {
                    repositoryFiles: { 'existing.txt': FileType.FILE },
                    initializeRepositoryFiles: jest.fn(),
                    refreshTreeview: jest.fn(),
                    changeDetectorRef: { markForCheck: jest.fn() },
                } as any,
                unsavedFiles: {},
                selectedFile: undefined,
                onFileChanged: { emit: jest.fn() } as any,
            };
        });

        it('applies remote content updates to the editor', () => {
            const operation: FileOperation = {
                type: ProgrammingExerciseEditorFileChangeType.CONTENT,
                fileName: 'file.txt',
                content: 'new content',
            };

            service.applyRemoteOperation(operation, mockCodeEditor as CodeEditorContainerComponent);

            expect(mockCodeEditor.applyRemoteFileContent).toHaveBeenCalledWith('file.txt', 'new content');
            expect(mockCodeEditor.fileBrowser!.refreshTreeview).toHaveBeenCalled();
        });

        it('adds created files to repository listing', () => {
            const operation: FileOperation = {
                type: ProgrammingExerciseEditorFileChangeType.CREATE,
                fileName: 'new.txt',
                content: 'content',
                fileType: FileType.FILE,
            };

            service.applyRemoteOperation(operation, mockCodeEditor as CodeEditorContainerComponent);

            expect(mockCodeEditor.fileBrowser!.repositoryFiles?.['new.txt']).toBe(FileType.FILE);
            expect(mockCodeEditor.applyRemoteFileContent).toHaveBeenCalledWith('new.txt', 'content');
        });

        it('ignores NEW_COMMIT_ALERT operations when applying', () => {
            const operation: FileOperation = { type: 'NEW_COMMIT_ALERT' };

            service.applyRemoteOperation(operation, mockCodeEditor as CodeEditorContainerComponent);

            expect(mockCodeEditor.applyRemoteFileContent).not.toHaveBeenCalled();
            expect(mockCodeEditor.fileBrowser!.refreshTreeview).not.toHaveBeenCalled();
        });

        it('removes files on delete operations and updates selection', () => {
            mockCodeEditor.selectedFile = 'deleted.txt';
            const operation: FileOperation = {
                type: ProgrammingExerciseEditorFileChangeType.DELETE,
                fileName: 'deleted.txt',
            };

            service.applyRemoteOperation(operation, mockCodeEditor as CodeEditorContainerComponent);

            expect(fileService.updateFileReferences).toHaveBeenCalledWith(expect.anything(), expect.any(DeleteFileChange));
            expect(mockCodeEditor.selectedFile).toBeUndefined();
            expect(mockCodeEditor.onFileChanged!.emit).toHaveBeenCalled();
        });

        it('renames files and updates unsaved files and selection', () => {
            mockCodeEditor.selectedFile = 'old.txt';
            const operation: FileOperation = {
                type: ProgrammingExerciseEditorFileChangeType.RENAME,
                fileName: 'old.txt',
                newFileName: 'new.txt',
                content: 'content',
            };

            service.applyRemoteOperation(operation, mockCodeEditor as CodeEditorContainerComponent);

            expect(fileService.updateFileReferences).toHaveBeenCalledWith(expect.anything(), expect.any(RenameFileChange));
            expect(mockCodeEditor.selectedFile).toBe('new.txt');
        });
    });
});
