import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { DiffMatchPatch } from 'diff-match-patch-typescript';

import { RemoteFileOperation, RepositoryFileSyncService } from 'app/programming/manage/services/repository-file-sync.service';
import {
    ProgrammingExerciseEditorFileChangeType,
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import { CodeEditorFileService } from 'app/programming/shared/code-editor/services/code-editor-file.service';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
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
                        getSynchronizationUpdates: jest.fn().mockReturnValue(incomingMessages$.asObservable()),
                        sendSynchronization: jest.fn(),
                        unsubscribeFromExercise: jest.fn(),
                    },
                },
                {
                    provide: CodeEditorFileService,
                    useValue: {
                        updateFileReferences: jest.fn((files) => files),
                    },
                },
            ],
        });

        service = TestBed.inject(RepositoryFileSyncService);
        syncService = TestBed.inject(ProgrammingExerciseEditorSyncService) as jest.Mocked<ProgrammingExerciseEditorSyncService>;
        fileService = TestBed.inject(CodeEditorFileService) as jest.Mocked<CodeEditorFileService>;
    });

    afterEach(() => {
        service.dispose();
    });

    describe('Initialization and Cleanup', () => {
        it('should initialize with exercise ID and subscribe to updates', () => {
            const targetFilter = jest.fn().mockReturnValue(true);
            service.init(42, 'client-a', targetFilter);

            expect(syncService.getSynchronizationUpdates).toHaveBeenCalledWith(42);
        });

        it('should dispose and cleanup subscriptions', () => {
            service.init(42, 'client-a', () => true);
            service.dispose();

            expect(syncService.unsubscribeFromExercise).toHaveBeenCalledWith(42);
        });

        it('should reset state on dispose', () => {
            service.init(42, 'client-a', () => true);
            service.registerBaseline(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'file.txt', 'content', undefined);

            service.dispose();

            // Re-initialize and verify baselines were cleared
            const operations: RemoteFileOperation[] = [];
            service.init(99, 'client-b', () => true).subscribe((op) => operations.push(op));

            // Should not have old baseline - new patch should work from empty baseline
            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [
                    {
                        fileName: 'file.txt',
                        patch: dmp.patch_toText(dmp.patch_make('', 'new content')),
                        changeType: ProgrammingExerciseEditorFileChangeType.CONTENT,
                    },
                ],
                clientInstanceId: 'client-c',
                timestamp: Date.now(),
            });

            expect(operations).toHaveLength(1);
            expect(operations[0].content).toBe('new content');
        });
    });

    describe('Baseline Management', () => {
        beforeEach(() => {
            service.init(42, 'client-a', () => true);
        });

        it('should register baseline for file', () => {
            service.registerBaseline(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'main.java', 'public class Main {}', undefined);

            // Verify baseline by sending a patch
            service.handleLocalChange('main.java', 'public class Main { }', ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, undefined);

            expect(syncService.sendSynchronization).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    filePatches: expect.arrayContaining([
                        expect.objectContaining({
                            fileName: 'main.java',
                            patch: expect.any(String),
                        }),
                    ]),
                }),
            );
        });

        it('should handle baseline for auxiliary repository with ID', () => {
            service.registerBaseline(ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 'config.yml', 'port: 8080', 123);

            service.handleLocalChange('config.yml', 'port: 9090', ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 123);

            expect(syncService.sendSynchronization).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY,
                    auxiliaryRepositoryId: 123,
                }),
            );
        });
    });

    describe('Local Change Handling', () => {
        beforeEach(() => {
            service.init(42, 'client-a', () => true);
        });

        it('should send patch for content changes', () => {
            service.registerBaseline(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'file.txt', 'old content', undefined);
            service.handleLocalChange('file.txt', 'new content', ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY);

            expect(syncService.sendSynchronization).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
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

        it('should send full content for CREATE change', () => {
            service.handleLocalChange(
                'new.txt',
                'file content',
                ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                undefined,
                ProgrammingExerciseEditorFileChangeType.CREATE,
            );

            expect(syncService.sendSynchronization).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    filePatches: expect.arrayContaining([
                        expect.objectContaining({
                            fileName: 'new.txt',
                            changeType: ProgrammingExerciseEditorFileChangeType.CREATE,
                            patch: 'file content',
                        }),
                    ]),
                }),
            );
        });

        it('should handle DELETE changes and remove baseline', () => {
            service.registerBaseline(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'deleted.txt', 'content', undefined);
            service.handleLocalChange('deleted.txt', '', ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, undefined, ProgrammingExerciseEditorFileChangeType.DELETE);

            expect(syncService.sendSynchronization).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    filePatches: expect.arrayContaining([
                        expect.objectContaining({
                            fileName: 'deleted.txt',
                            changeType: ProgrammingExerciseEditorFileChangeType.DELETE,
                        }),
                    ]),
                }),
            );
        });

        it('should handle RENAME changes and update baseline', () => {
            service.registerBaseline(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'old.txt', 'content', undefined);
            service.handleLocalChange(
                'old.txt',
                'content',
                ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                undefined,
                ProgrammingExerciseEditorFileChangeType.RENAME,
                'new.txt',
            );

            expect(syncService.sendSynchronization).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    filePatches: expect.arrayContaining([
                        expect.objectContaining({
                            fileName: 'old.txt',
                            newFileName: 'new.txt',
                            changeType: ProgrammingExerciseEditorFileChangeType.RENAME,
                        }),
                    ]),
                }),
            );
        });

        it('should not send message if no change detected', () => {
            service.registerBaseline(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'file.txt', 'same content', undefined);
            service.handleLocalChange('file.txt', 'same content', ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY);

            expect(syncService.sendSynchronization).not.toHaveBeenCalled();
        });
    });

    describe('Remote Message Handling', () => {
        beforeEach(() => {
            service.init(42, 'client-a', () => true);
        });

        it('should ignore messages from same client instance', () => {
            const operations: RemoteFileOperation[] = [];
            service.init(42, 'client-a', () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [
                    {
                        fileName: 'test.txt',
                        patch: 'content',
                        changeType: ProgrammingExerciseEditorFileChangeType.CREATE,
                    },
                ],
                clientInstanceId: 'client-a',
                timestamp: Date.now(),
            });

            expect(operations).toHaveLength(0);
        });

        it('should apply remote CONTENT patches', () => {
            const operations: RemoteFileOperation[] = [];
            service.init(42, 'client-a', () => true).subscribe((op) => operations.push(op));

            service.registerBaseline(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'file.txt', 'old', undefined);

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
                clientInstanceId: 'client-b',
                timestamp: Date.now(),
            });

            expect(operations).toHaveLength(1);
            expect(operations[0]).toEqual({
                type: ProgrammingExerciseEditorFileChangeType.CONTENT,
                fileName: 'file.txt',
                content: 'new',
            });
        });

        it('should apply remote CREATE operations', () => {
            const operations: RemoteFileOperation[] = [];
            service.init(42, 'client-a', () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [
                    {
                        fileName: 'new.txt',
                        patch: 'new file content',
                        changeType: ProgrammingExerciseEditorFileChangeType.CREATE,
                        fileType: FileType.FILE,
                    },
                ],
                clientInstanceId: 'client-b',
                timestamp: Date.now(),
            });

            expect(operations).toHaveLength(1);
            expect(operations[0]).toEqual({
                type: ProgrammingExerciseEditorFileChangeType.CREATE,
                fileName: 'new.txt',
                content: 'new file content',
                fileType: FileType.FILE,
            });
        });

        it('should apply remote DELETE operations', () => {
            const operations: RemoteFileOperation[] = [];
            service.init(42, 'client-a', () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [
                    {
                        fileName: 'deleted.txt',
                        changeType: ProgrammingExerciseEditorFileChangeType.DELETE,
                    },
                ],
                clientInstanceId: 'client-b',
                timestamp: Date.now(),
            });

            expect(operations).toHaveLength(1);
            expect(operations[0]).toEqual({
                type: ProgrammingExerciseEditorFileChangeType.DELETE,
                fileName: 'deleted.txt',
            });
        });

        it('should filter messages based on target filter', () => {
            const operations: RemoteFileOperation[] = [];
            const targetFilter = (message: ProgrammingExerciseEditorSyncMessage) => message.target === ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY;

            service.init(42, 'client-a', targetFilter).subscribe((op) => operations.push(op));

            // This should be filtered out
            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY,
                filePatches: [
                    {
                        fileName: 'solution.txt',
                        patch: 'content',
                        changeType: ProgrammingExerciseEditorFileChangeType.CREATE,
                    },
                ],
                clientInstanceId: 'client-b',
                timestamp: Date.now(),
            });

            // This should pass through
            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [
                    {
                        fileName: 'template.txt',
                        patch: 'content',
                        changeType: ProgrammingExerciseEditorFileChangeType.CREATE,
                    },
                ],
                clientInstanceId: 'client-b',
                timestamp: Date.now(),
            });

            expect(operations).toHaveLength(1);
            expect(operations[0].fileName).toBe('template.txt');
        });
    });

    describe('Error Handling - Patch Failure Recovery', () => {
        beforeEach(() => {
            service.init(42, 'client-a', () => true);
        });

        it('should request full file when patch application fails', () => {
            const operations: RemoteFileOperation[] = [];
            service.init(42, 'client-a', () => true).subscribe((op) => operations.push(op));

            // Register a baseline
            service.registerBaseline(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'file.txt', 'base content', undefined);

            // Send a patch that will fail (incompatible with baseline)
            const incompatiblePatch = dmp.patch_toText(dmp.patch_make('completely different base', 'new content'));
            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                filePatches: [
                    {
                        fileName: 'file.txt',
                        patch: incompatiblePatch,
                        changeType: ProgrammingExerciseEditorFileChangeType.CONTENT,
                    },
                ],
                clientInstanceId: 'client-b',
                timestamp: Date.now(),
            });

            // Should have requested full file
            expect(syncService.sendSynchronization).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    fileRequests: ['file.txt'],
                }),
            );
        });
    });

    describe('Full File Sync', () => {
        beforeEach(() => {
            service.init(42, 'client-a', () => true);
        });

        it('should request full file from other clients', () => {
            service.requestFullFile(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'file.txt', undefined);

            expect(syncService.sendSynchronization).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    fileRequests: ['file.txt'],
                }),
            );
        });

        it('should respond to file requests with baseline content', () => {
            service.registerBaseline(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, 'file.txt', 'my content', undefined);

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                fileRequests: ['file.txt'],
                clientInstanceId: 'client-b',
                timestamp: Date.now(),
            });

            expect(syncService.sendSynchronization).toHaveBeenCalledWith(
                42,
                expect.objectContaining({
                    target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    fileFulls: [
                        {
                            fileName: 'file.txt',
                            content: 'my content',
                        },
                    ],
                }),
            );
        });

        it('should update baseline when receiving full file', () => {
            const operations: RemoteFileOperation[] = [];
            service.init(42, 'client-a', () => true).subscribe((op) => operations.push(op));

            incomingMessages$.next({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                fileFulls: [
                    {
                        fileName: 'file.txt',
                        content: 'full content from remote',
                    },
                ],
                clientInstanceId: 'client-b',
                timestamp: Date.now(),
            });

            expect(operations).toHaveLength(1);
            expect(operations[0]).toEqual({
                type: ProgrammingExerciseEditorFileChangeType.CONTENT,
                fileName: 'file.txt',
                content: 'full content from remote',
            });
        });
    });

    describe('Code Editor Integration', () => {
        let mockCodeEditor: Partial<CodeEditorContainerComponent>;

        beforeEach(() => {
            service.init(42, 'client-a', () => true);

            mockCodeEditor = {
                applyRemoteFileContent: jest.fn(),
                fileBrowser: {
                    repositoryFiles: { 'existing.txt': FileType.FILE },
                    initializeRepositoryFiles: jest.fn(),
                    refreshTreeview: jest.fn(),
                } as any,
                unsavedFiles: {},
                selectedFile: undefined,
                onFileChanged: {
                    emit: jest.fn(),
                } as any,
            };
        });

        it('should apply remote content operation to code editor', () => {
            const operation: RemoteFileOperation = {
                type: ProgrammingExerciseEditorFileChangeType.CONTENT,
                fileName: 'file.txt',
                content: 'new content',
            };

            service.applyRemoteOperation(operation, mockCodeEditor as CodeEditorContainerComponent);

            expect(mockCodeEditor.applyRemoteFileContent).toHaveBeenCalledWith('file.txt', 'new content');
            expect(mockCodeEditor.fileBrowser!.refreshTreeview).toHaveBeenCalled();
        });

        it('should apply remote create operation to code editor', () => {
            const operation: RemoteFileOperation = {
                type: ProgrammingExerciseEditorFileChangeType.CREATE,
                fileName: 'new.txt',
                content: 'content',
                fileType: FileType.FILE,
            };

            service.applyRemoteOperation(operation, mockCodeEditor as CodeEditorContainerComponent);

            expect(mockCodeEditor.fileBrowser!.repositoryFiles).toHaveProperty('new.txt');
            expect(mockCodeEditor.applyRemoteFileContent).toHaveBeenCalledWith('new.txt', 'content');
        });

        it('should apply remote delete operation and update selection', () => {
            mockCodeEditor.selectedFile = 'deleted.txt';
            const operation: RemoteFileOperation = {
                type: ProgrammingExerciseEditorFileChangeType.DELETE,
                fileName: 'deleted.txt',
            };

            service.applyRemoteOperation(operation, mockCodeEditor as CodeEditorContainerComponent);

            expect(fileService.updateFileReferences).toHaveBeenCalled();
            expect(mockCodeEditor.selectedFile).toBeUndefined();
            expect(mockCodeEditor.onFileChanged!.emit).toHaveBeenCalled();
        });
    });
});
