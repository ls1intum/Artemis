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
import { AlertService } from 'app/shared/service/alert.service';

describe('RepositoryFileSyncService', () => {
    let service: RepositoryFileSyncService;
    let syncService: jest.Mocked<ProgrammingExerciseEditorSyncService>;
    let fileService: jest.Mocked<CodeEditorFileService>;
    let alertService: jest.Mocked<AlertService>;
    let incomingMessages$: Subject<ProgrammingExerciseEditorSyncMessage>;
    const dmp = new DiffMatchPatch();

    const exerciseIdToUse = 42;
    type RepositoryFileSyncServiceInternals = {
        baselines: Record<string, string>;
        lastProcessedTimestamps: Record<string, number>;
        getBaselineKey: (target: ProgrammingExerciseEditorSyncTarget, fileName: string, auxiliaryId?: number) => string;
    };
    const getBaselines = (service: RepositoryFileSyncService) => (service as unknown as RepositoryFileSyncServiceInternals).baselines;
    const getTimestamps = (service: RepositoryFileSyncService) => (service as unknown as RepositoryFileSyncServiceInternals).lastProcessedTimestamps;
    const buildBaselineKey = (service: RepositoryFileSyncService, target: ProgrammingExerciseEditorSyncTarget, fileName: string, auxiliaryId?: number) =>
        (service as unknown as RepositoryFileSyncServiceInternals).getBaselineKey(target, fileName, auxiliaryId);

    beforeEach(() => {
        incomingMessages$ = new Subject<ProgrammingExerciseEditorSyncMessage>();

        TestBed.configureTestingModule({
            providers: [
                RepositoryFileSyncService,
                {
                    provide: AlertService,
                    useValue: {
                        info: jest.fn(),
                    },
                },
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
                        updateFileReference: jest.fn((fileName) => fileName),
                    },
                },
            ],
        });

        service = TestBed.inject(RepositoryFileSyncService);
        syncService = TestBed.inject(ProgrammingExerciseEditorSyncService) as jest.Mocked<ProgrammingExerciseEditorSyncService>;
        fileService = TestBed.inject(CodeEditorFileService) as jest.Mocked<CodeEditorFileService>;
        alertService = TestBed.inject(AlertService) as jest.Mocked<AlertService>;
    });

    afterEach(() => {
        service?.reset();
        jest.clearAllMocks();
    });

    describe('Initialization and cleanup', () => {
        it('subscribes to synchronization updates for an exercise', () => {
            const updates$ = service.init(exerciseIdToUse, () => true);

            expect(syncService.subscribeToUpdates).toHaveBeenCalledWith(exerciseIdToUse);
            expect(updates$).toBeDefined();
        });

        it('completes listeners and unsubscribes on reset', () => {
            let completed = false;
            service.init(exerciseIdToUse, () => true).subscribe({ complete: () => (completed = true) });

            service.reset();

            expect(syncService.unsubscribe).toHaveBeenCalled();
            expect(completed).toBeTrue();
        });
    });

    describe('Local file operations', () => {
        beforeEach(() => {
            service.init(exerciseIdToUse, () => true);
        });

        it('sends patches for content changes using existing baseline', () => {
            service.registerBaseline(RepositoryType.TEMPLATE, 'file.txt', 'old content');

            service.handleLocalFileOperation({ type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: 'file.txt', content: 'new content' }, RepositoryType.TEMPLATE);

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                exerciseIdToUse,
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

        it('skips sending patches when content is unchanged', () => {
            service.registerBaseline(RepositoryType.TEMPLATE, 'file.txt', 'same');

            service.handleLocalFileOperation({ type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: 'file.txt', content: 'same' }, RepositoryType.TEMPLATE);

            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
        });

        it('stores baseline for create operations without broadcasting', () => {
            service.handleLocalFileOperation(
                { type: ProgrammingExerciseEditorFileChangeType.CREATE, fileName: 'new.txt', content: 'hello', fileType: FileType.FILE },
                RepositoryType.TEMPLATE,
            );

            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
            const baselines = getBaselines(service);
            expect(baselines[buildBaselineKey(service, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'new.txt', undefined)]).toBe('hello');
        });

        it('removes baseline on delete without broadcasting', () => {
            service.registerBaseline(RepositoryType.TEMPLATE, 'old.txt', 'content');

            service.handleLocalFileOperation({ type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: 'old.txt' }, RepositoryType.TEMPLATE);

            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
            const baselineKey = buildBaselineKey(service, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'old.txt', undefined);
            expect(getBaselines(service)[baselineKey]).toBeUndefined();
        });

        it('moves baseline on rename operations without broadcasting', () => {
            service.registerBaseline(RepositoryType.TEMPLATE, 'old.txt', 'content');

            service.handleLocalFileOperation(
                { type: ProgrammingExerciseEditorFileChangeType.RENAME, fileName: 'old.txt', newFileName: 'new.txt', content: 'content', fileType: FileType.FILE },
                RepositoryType.TEMPLATE,
            );

            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();

            const baselines = getBaselines(service);
            expect(baselines[buildBaselineKey(service, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'old.txt', undefined)]).toBeUndefined();
            expect(baselines[buildBaselineKey(service, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'new.txt', undefined)]).toBe('content');
        });

        it('falls back to provided content on rename when no baseline exists', () => {
            service.handleLocalFileOperation(
                { type: ProgrammingExerciseEditorFileChangeType.RENAME, fileName: 'old.txt', newFileName: 'new.txt', content: 'renamed content', fileType: FileType.FILE },
                RepositoryType.TEMPLATE,
            );

            const baselines = getBaselines(service);
            expect(baselines[buildBaselineKey(service, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'new.txt', undefined)]).toBe('renamed content');
        });

        it('ignores unsupported repositories and uninitialized service', () => {
            service.reset();
            service.handleLocalFileOperation({ type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: 'test.txt' }, RepositoryType.TEMPLATE);
            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();

            service.init(exerciseIdToUse, () => true);
            service.handleLocalFileOperation({ type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: 'test.txt' }, RepositoryType.ASSIGNMENT);
            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
        });
    });

    describe('Remote synchronization handling', () => {
        it('emits applied content patches from remote changes', () => {
            const operations: FileOperation[] = [];
            service.init(exerciseIdToUse, () => true).subscribe((op) => operations.push(op));
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
            service.init(exerciseIdToUse, () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [
                    { fileName: 'new.txt', patch: 'content', changeType: ProgrammingExerciseEditorFileChangeType.CREATE, fileType: FileType.FILE },
                    { fileName: 'delete.txt', changeType: ProgrammingExerciseEditorFileChangeType.DELETE },
                    {
                        fileName: 'old.txt',
                        newFileName: 'renamed.txt',
                        patch: 'content',
                        changeType: ProgrammingExerciseEditorFileChangeType.RENAME,
                        fileType: FileType.FILE,
                    },
                ],
                timestamp: 1,
            });

            expect(operations).toEqual([
                { type: ProgrammingExerciseEditorFileChangeType.CREATE, fileName: 'new.txt', content: 'content', fileType: FileType.FILE },
                { type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: 'delete.txt' },
                {
                    type: ProgrammingExerciseEditorFileChangeType.RENAME,
                    fileName: 'old.txt',
                    newFileName: 'renamed.txt',
                    content: 'content',
                    fileType: FileType.FILE,
                },
            ]);
        });

        it('renames folders without creating baselines for folder paths', () => {
            const operations: FileOperation[] = [];
            service.init(exerciseIdToUse, () => true).subscribe((op) => operations.push(op));
            service.registerBaseline(RepositoryType.TEMPLATE, 'folder/file.txt', 'content');

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [
                    {
                        fileName: 'folder',
                        newFileName: 'renamed',
                        changeType: ProgrammingExerciseEditorFileChangeType.RENAME,
                        fileType: FileType.FOLDER,
                    },
                ],
                timestamp: 2,
            });

            const baselines = getBaselines(service);
            const oldFileKey = buildBaselineKey(service, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'folder/file.txt', undefined);
            const newFileKey = buildBaselineKey(service, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'renamed/file.txt', undefined);
            const folderKey = buildBaselineKey(service, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'renamed', undefined);
            const timestamps = getTimestamps(service);

            expect(baselines[oldFileKey]).toBeUndefined();
            expect(baselines[newFileKey]).toBe('content');
            expect(baselines[folderKey]).toBeUndefined();
            expect(timestamps[folderKey]).toBeUndefined();
            expect(operations).toEqual([
                {
                    type: ProgrammingExerciseEditorFileChangeType.RENAME,
                    fileName: 'folder',
                    newFileName: 'renamed',
                    content: '',
                    fileType: FileType.FOLDER,
                },
            ]);
        });

        it('returns undefined and does not emit when patch cannot be applied', () => {
            const operations: FileOperation[] = [];
            service.init(exerciseIdToUse, () => true).subscribe((op) => operations.push(op));
            service.registerBaseline(RepositoryType.TEMPLATE, 'file.txt', 'baseline');

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [{ fileName: 'file.txt', patch: '<<<invalid>>>', changeType: ProgrammingExerciseEditorFileChangeType.CONTENT }],
                timestamp: 2,
            });

            expect(operations).toEqual([]);
            expect(getBaselines(service)[buildBaselineKey(service, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'file.txt', undefined)]).toBe('baseline');
        });

        it('requests a full file instead of applying patches when no baseline exists', () => {
            const operations: FileOperation[] = [];
            service.init(exerciseIdToUse, () => true).subscribe((op) => operations.push(op));

            const patch = dmp.patch_toText(dmp.patch_make('old content', 'new content'));
            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [{ fileName: 'file.txt', patch, changeType: ProgrammingExerciseEditorFileChangeType.CONTENT }],
                timestamp: 3,
            });

            expect(operations).toHaveLength(0);
            expect(alertService.info).not.toHaveBeenCalled();
            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                exerciseIdToUse,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    fileRequests: ['file.txt'],
                }),
            );
        });

        it('ignores messages without a target', () => {
            const operations: FileOperation[] = [];
            service.init(exerciseIdToUse, () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                filePatches: [{ fileName: 'file.txt', patch: 'content', changeType: ProgrammingExerciseEditorFileChangeType.CREATE }],
                timestamp: 1,
            });

            expect(operations).toHaveLength(0);
        });

        it('skips outdated messages based on timestamp', () => {
            const operations: FileOperation[] = [];
            service.init(exerciseIdToUse, () => true).subscribe((op) => operations.push(op));
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
            service.init(exerciseIdToUse, (message) => message.target === ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [{ fileName: 'file.txt', patch: 'content', changeType: ProgrammingExerciseEditorFileChangeType.CREATE }],
                timestamp: 1,
            });

            expect(operations).toHaveLength(0);
        });

        it('emits new commit alert operations', () => {
            const operations: FileOperation[] = [];
            service.init(exerciseIdToUse, () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                newCommitAlert: true,
            });

            expect(operations).toEqual([{ type: 'NEW_COMMIT_ALERT' }]);
        });

        it('responds to file requests with available baselines', () => {
            service.init(exerciseIdToUse, () => true);
            service.registerBaseline(RepositoryType.TEMPLATE, 'file.txt', 'baseline');

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                fileRequests: ['file.txt'],
            });

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                exerciseIdToUse,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    fileFulls: [{ fileName: 'file.txt', content: 'baseline' }],
                }),
            );
        });

        it('stores auxiliary baselines and emits updates for full file syncs', () => {
            const operations: FileOperation[] = [];
            service.init(exerciseIdToUse, () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY,
                auxiliaryRepositoryId: 7,
                fileFulls: [{ fileName: 'aux/readme.md', content: 'aux content' }],
                timestamp: 3,
            });

            const baselineKey = buildBaselineKey(service, ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 'aux/readme.md', 7);
            expect(getBaselines(service)[baselineKey]).toBe('aux content');
            expect(operations).toEqual([{ type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: 'aux/readme.md', content: 'aux content' }]);
        });

        it('applies full file sync messages to baselines', () => {
            const operations: FileOperation[] = [];
            service.init(exerciseIdToUse, () => true).subscribe((op) => operations.push(op));

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
            service.init(exerciseIdToUse, () => true);
        });

        it('returns early when not initialized', () => {
            service.reset();

            service.requestFullFile(RepositoryType.TEMPLATE, 'file.txt');

            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
        });

        it('does not send requests when repository type has no sync target', () => {
            service.requestFullFile(RepositoryType.ASSIGNMENT, 'file.txt');

            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
        });

        it('requests full file content for a repository type', () => {
            service.requestFullFile(RepositoryType.TEMPLATE, 'file.txt');

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                exerciseIdToUse,
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
            service.init(exerciseIdToUse, () => true);

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
                fileType: FileType.FILE,
            };

            service.applyRemoteOperation(operation, mockCodeEditor as CodeEditorContainerComponent);

            expect(fileService.updateFileReferences).toHaveBeenCalledWith(expect.anything(), expect.any(RenameFileChange));
            expect(fileService.updateFileReference).toHaveBeenCalledWith('old.txt', expect.any(RenameFileChange));
        });
    });
});
