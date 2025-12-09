import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { DiffMatchPatch } from 'diff-match-patch-typescript';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { DeleteFileChange, FileType, RenameFileChange } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileService } from 'app/programming/shared/code-editor/services/code-editor-file.service';
import {
    ProgrammingExerciseEditorFileChangeType,
    ProgrammingExerciseEditorFileFull,
    ProgrammingExerciseEditorFileSync,
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';

type TargetFilter = (message: ProgrammingExerciseEditorSyncMessage) => boolean;

export type RemoteFileOperation =
    | { type: ProgrammingExerciseEditorFileChangeType.CONTENT; fileName: string; content: string }
    | { type: ProgrammingExerciseEditorFileChangeType.CREATE; fileName: string; content: string; fileType?: FileType }
    | { type: ProgrammingExerciseEditorFileChangeType.DELETE; fileName: string }
    | { type: ProgrammingExerciseEditorFileChangeType.RENAME; fileName: string; newFileName: string; content: string };

@Injectable({ providedIn: 'root' })
export class RepositoryFileSyncService {
    private syncService = inject(ProgrammingExerciseEditorSyncService);
    private fileService = inject(CodeEditorFileService);
    private readonly diffMatchPatch = new DiffMatchPatch();
    private baselines: Record<string, string> = {};
    private lastProcessedTimestamps: Record<string, number> = {};
    private exerciseId?: number;
    private clientInstanceId?: string;

    private targetFilter: TargetFilter = () => true;
    private incomingMessageSubscription?: Subscription;
    private remoteOperations$ = new Subject<RemoteFileOperation>();

    private sendFn(message: ProgrammingExerciseEditorSyncMessage) {
        if (!this.exerciseId) {
            throw new Error('RepositoryFileSyncService not initialized before sending synchronization message; exerciseId is undefined');
        }
        this.syncService.sendSynchronizationUpdate(this.exerciseId!, message);
    }

    init(exerciseId: number, clientInstanceId: string | undefined, targetFilter: TargetFilter): Observable<RemoteFileOperation> {
        this.dispose();
        this.exerciseId = exerciseId;
        this.clientInstanceId = clientInstanceId;
        this.targetFilter = targetFilter;
        this.baselines = {};
        this.lastProcessedTimestamps = {};
        this.incomingMessageSubscription = this.syncService.subscribeToUpdates(exerciseId).subscribe((message) => this.handleIncomingMessage(message));
        return this.remoteOperations$.asObservable();
    }

    dispose() {
        this.syncService.unsubscribe();
        this.exerciseId = undefined;
        this.clientInstanceId = undefined;
        this.baselines = {};
        this.lastProcessedTimestamps = {};
        this.targetFilter = () => true;
        this.incomingMessageSubscription?.unsubscribe();
        this.incomingMessageSubscription = undefined;
        this.remoteOperations$.complete();
        this.remoteOperations$ = new Subject<RemoteFileOperation>();
    }

    registerBaseline(target: ProgrammingExerciseEditorSyncTarget, fileName: string, content: string, auxiliaryId?: number) {
        const key = this.getBaselineKey(target, fileName, auxiliaryId);
        this.baselines[key] = content;
    }

    requestFullFile(target: ProgrammingExerciseEditorSyncTarget, fileName: string, auxiliaryId?: number) {
        if (!this.sendFn || !this.exerciseId) {
            return;
        }
        this.sendFn({
            target,
            auxiliaryRepositoryId: auxiliaryId,
            fileRequests: [fileName],
            clientInstanceId: this.clientInstanceId,
        });
    }

    handleLocalChange(
        fileName: string,
        content: string,
        target: ProgrammingExerciseEditorSyncTarget | undefined,
        auxiliaryId?: number,
        changeType: ProgrammingExerciseEditorFileChangeType = ProgrammingExerciseEditorFileChangeType.CONTENT,
        newFileName?: string,
        fileType?: FileType,
    ) {
        if (!this.exerciseId || !this.sendFn || !target) {
            return;
        }

        const baselineKey = this.getBaselineKey(target, fileName, auxiliaryId);
        const previousContent = this.baselines[baselineKey] ?? '';

        const filePatch: ProgrammingExerciseEditorFileSync = { fileName, changeType, fileType };
        if (changeType === ProgrammingExerciseEditorFileChangeType.DELETE) {
            delete this.baselines[baselineKey];
        } else if (changeType === ProgrammingExerciseEditorFileChangeType.RENAME) {
            filePatch.newFileName = newFileName;
            // Keep the current content even if we never registered a baseline for the old path.
            const renameContent = previousContent || content;
            this.baselines[this.getBaselineKey(target, newFileName ?? fileName, auxiliaryId)] = renameContent;
            delete this.baselines[baselineKey];
            filePatch.patch = renameContent;
        } else {
            const patch = this.diffMatchPatch.patch_toText(this.diffMatchPatch.patch_make(previousContent, content));
            const effectivePatch = patch || (changeType === ProgrammingExerciseEditorFileChangeType.CREATE ? content : '');
            if (!effectivePatch && changeType !== ProgrammingExerciseEditorFileChangeType.CREATE) {
                return;
            }
            filePatch.patch = effectivePatch;
            this.baselines[baselineKey] = content;
        }

        this.sendFn({
            target,
            auxiliaryRepositoryId: auxiliaryId,
            filePatches: [filePatch],
            clientInstanceId: this.clientInstanceId,
        });
    }

    handleRemoteMessage(message: ProgrammingExerciseEditorSyncMessage): RemoteFileOperation[] | undefined {
        if (!message.target || !message.filePatches?.length) {
            return undefined;
        }

        const operations: RemoteFileOperation[] = [];
        message.filePatches.forEach((filePatch) => {
            const op = this.applyRemoteFilePatch(message, filePatch);
            if (op) {
                operations.push(op);
            }
        });

        return operations.length ? operations : undefined;
    }

    private handleFullFileSync(message: ProgrammingExerciseEditorSyncMessage) {
        if (!message.target || !message.fileFulls?.length) {
            return;
        }
        const auxiliaryId = message.target === ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY ? message.auxiliaryRepositoryId : undefined;
        message.fileFulls.forEach((fullFile) => {
            const baselineKey = this.getBaselineKey(message.target!, fullFile.fileName, auxiliaryId);
            this.lastProcessedTimestamps[baselineKey] = message.timestamp ?? Date.now();
            this.baselines[baselineKey] = fullFile.content;
            this.remoteOperations$.next({ type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: fullFile.fileName, content: fullFile.content });
        });
    }

    private applyRemoteFilePatch(message: ProgrammingExerciseEditorSyncMessage, filePatch: ProgrammingExerciseEditorFileSync): RemoteFileOperation | undefined {
        if (!message.target) {
            return undefined;
        }
        const auxiliaryId = message.target === ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY ? message.auxiliaryRepositoryId : undefined;
        const baselineKey = this.getBaselineKey(message.target, filePatch.fileName, auxiliaryId);

        const messageTimestamp = message.timestamp ?? Date.now();
        if (this.lastProcessedTimestamps[baselineKey] && messageTimestamp <= this.lastProcessedTimestamps[baselineKey]) {
            return undefined;
        }
        this.lastProcessedTimestamps[baselineKey] = messageTimestamp;

        if (filePatch.changeType === ProgrammingExerciseEditorFileChangeType.DELETE) {
            delete this.baselines[baselineKey];
            return { type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: filePatch.fileName };
        }

        if (filePatch.changeType === ProgrammingExerciseEditorFileChangeType.RENAME && filePatch.newFileName) {
            const currentContent = this.baselines[baselineKey] ?? filePatch.patch ?? '';
            delete this.baselines[baselineKey];

            const newKey = this.getBaselineKey(message.target, filePatch.newFileName, auxiliaryId);
            this.baselines[newKey] = currentContent;
            return { type: ProgrammingExerciseEditorFileChangeType.RENAME, fileName: filePatch.fileName, newFileName: filePatch.newFileName, content: currentContent };
        }

        const patches = filePatch.patch ? this.diffMatchPatch.patch_fromText(filePatch.patch) : [];
        const currentContent = this.baselines[baselineKey] ?? '';

        let patchedContent: string;
        try {
            if (patches.length) {
                const [appliedContent, results] = this.diffMatchPatch.patch_apply(patches, currentContent);
                // Check if any patch failed to apply (results array contains false for failed patches)
                const hasFailedPatches = results.some((success) => !success);
                if (hasFailedPatches) {
                    // Patch application failed - request full file sync as fallback
                    this.requestFullFile(message.target, filePatch.fileName, auxiliaryId);
                    return undefined;
                }
                patchedContent = appliedContent;
            } else {
                patchedContent = filePatch.patch ?? currentContent;
            }
        } catch (error) {
            // If patch parsing or application throws an error, request full file sync as fallback
            this.requestFullFile(message.target, filePatch.fileName, auxiliaryId);
            return undefined;
        }

        this.baselines[baselineKey] = patchedContent;

        if (filePatch.changeType === ProgrammingExerciseEditorFileChangeType.CREATE) {
            return { type: ProgrammingExerciseEditorFileChangeType.CREATE, fileName: filePatch.fileName, content: patchedContent, fileType: filePatch.fileType };
        }

        return { type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: filePatch.fileName, content: patchedContent };
    }

    private handleIncomingMessage(message: ProgrammingExerciseEditorSyncMessage) {
        if (!message.target || message.target === ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT) {
            return;
        }
        if (!this.targetFilter(message)) {
            return;
        }
        if (message.clientInstanceId && this.clientInstanceId && message.clientInstanceId === this.clientInstanceId) {
            return;
        }

        if (message.fileRequests?.length) {
            this.respondWithFullFiles(message);
            return;
        }

        const operations = this.handleRemoteMessage(message);
        operations?.forEach((operation) => this.remoteOperations$.next(operation));
        this.handleFullFileSync(message);
    }

    applyRemoteOperation(operation: RemoteFileOperation, codeEditorContainer: CodeEditorContainerComponent) {
        switch (operation.type) {
            case ProgrammingExerciseEditorFileChangeType.CONTENT:
                codeEditorContainer.applyRemoteFileContent(operation.fileName, operation.content);
                break;
            case ProgrammingExerciseEditorFileChangeType.CREATE:
                this.applyRemoteCreate(operation.fileName, operation.content, codeEditorContainer, operation.fileType);
                break;
            case ProgrammingExerciseEditorFileChangeType.RENAME:
                if (operation.newFileName) {
                    this.applyRemoteRename(operation.fileName, operation.newFileName, operation.content, codeEditorContainer);
                }
                break;
            case ProgrammingExerciseEditorFileChangeType.DELETE:
                this.applyRemoteDelete(operation.fileName, codeEditorContainer);
                break;
        }
        this.refreshRepositoryTree(codeEditorContainer);
    }

    private applyRemoteCreate(fileName: string, content: string, codeEditorContainer: CodeEditorContainerComponent, fileType?: FileType) {
        const fileBrowser = codeEditorContainer.fileBrowser;
        const resolvedFileType = fileType ?? this.lookupFileType(fileName, codeEditorContainer);
        if (fileBrowser) {
            fileBrowser.repositoryFiles = { ...(fileBrowser.repositoryFiles ?? {}), [fileName]: resolvedFileType };
        }
        if (resolvedFileType === FileType.FILE) {
            codeEditorContainer.applyRemoteFileContent(fileName, content);
        }
    }

    private applyRemoteDelete(fileName: string, codeEditorContainer: CodeEditorContainerComponent) {
        const fileBrowser = codeEditorContainer.fileBrowser;
        const fileType = this.lookupFileType(fileName, codeEditorContainer);
        if (fileBrowser?.repositoryFiles) {
            fileBrowser.repositoryFiles = this.fileService.updateFileReferences(fileBrowser.repositoryFiles, new DeleteFileChange(fileType, fileName));
        }
        codeEditorContainer.unsavedFiles = this.fileService.updateFileReferences(codeEditorContainer.unsavedFiles, new DeleteFileChange(fileType, fileName));
        if (codeEditorContainer.selectedFile === fileName) {
            codeEditorContainer.selectedFile = undefined;
        }
        codeEditorContainer.onFileChanged.emit();
    }

    private applyRemoteRename(oldFileName: string, newFileName: string, content: string, codeEditorContainer: CodeEditorContainerComponent) {
        const fileBrowser = codeEditorContainer.fileBrowser;
        const deleteChange = new RenameFileChange(this.lookupFileType(oldFileName, codeEditorContainer), oldFileName, newFileName);
        if (fileBrowser?.repositoryFiles) {
            fileBrowser.repositoryFiles = this.fileService.updateFileReferences(fileBrowser.repositoryFiles, deleteChange);
        }
        codeEditorContainer.unsavedFiles = this.fileService.updateFileReferences(codeEditorContainer.unsavedFiles, deleteChange);
        if (codeEditorContainer.selectedFile === oldFileName) {
            codeEditorContainer.selectedFile = newFileName;
        }
        codeEditorContainer.applyRemoteFileContent(newFileName, content);
    }

    private lookupFileType(path: string, codeEditorContainer: CodeEditorContainerComponent): FileType {
        const fileBrowser = codeEditorContainer.fileBrowser;
        const knownType = fileBrowser?.repositoryFiles?.[path];
        return knownType ?? (path.includes('.') ? FileType.FILE : FileType.FOLDER);
    }

    private refreshRepositoryTree(codeEditorContainer: CodeEditorContainerComponent) {
        const fileBrowser = codeEditorContainer.fileBrowser;
        if (fileBrowser) {
            fileBrowser.initializeRepositoryFiles();
            fileBrowser.refreshTreeview();
        }
    }

    private respondWithFullFiles(message: ProgrammingExerciseEditorSyncMessage) {
        if (!message.target || !message.fileRequests?.length || !this.sendFn) {
            return;
        }
        const auxiliaryId = message.target === ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY ? message.auxiliaryRepositoryId : undefined;
        const files: ProgrammingExerciseEditorFileFull[] = [];
        message.fileRequests.forEach((fileName) => {
            const baselineKey = this.getBaselineKey(message.target!, fileName, auxiliaryId);
            const content = this.baselines[baselineKey];
            if (content !== undefined) {
                files.push({ fileName, content });
            }
        });
        if (!files.length) {
            return;
        }
        this.sendFn({
            target: message.target,
            auxiliaryRepositoryId: message.auxiliaryRepositoryId,
            fileFulls: files,
            clientInstanceId: this.clientInstanceId,
        });
    }

    private getBaselineKey(target: ProgrammingExerciseEditorSyncTarget, fileName: string, auxiliaryId?: number) {
        return `${this.exerciseId ?? 'unknown'}-${target}-${auxiliaryId ?? 'none'}::${fileName}`;
    }
}
