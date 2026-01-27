import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { CommitState, DeleteFileChange, FileType, RenameFileChange, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileService } from 'app/programming/shared/code-editor/services/code-editor-file.service';
import {
    ProgrammingExerciseEditorAwarenessType,
    ProgrammingExerciseEditorFileChangeType,
    ProgrammingExerciseEditorFileSync,
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import * as Y from 'yjs';
import { Awareness, applyAwarenessUpdate, encodeAwarenessUpdate } from 'y-protocols/awareness';
import {
    AwarenessUpdatePayload,
    decodeBase64ToUint8Array,
    encodeUint8ArrayToBase64,
    ensureRemoteSelectionStyle,
    getColorForClientId,
    normalizeYjsOrigin,
} from 'app/programming/manage/services/yjs-utils';

type TargetFilter = (message: ProgrammingExerciseEditorSyncMessage) => boolean;

export type FileContentEditOperation = { type: ProgrammingExerciseEditorFileChangeType.CONTENT; fileName: string; content: string };
export type FileCreateOperation = { type: ProgrammingExerciseEditorFileChangeType.CREATE; fileName: string; content: string; fileType?: FileType };
export type FileDeleteOperation = { type: ProgrammingExerciseEditorFileChangeType.DELETE; fileName: string };
export type FileRenameOperation = {
    type: ProgrammingExerciseEditorFileChangeType.RENAME;
    fileName: string;
    newFileName: string;
    content: string;
    fileType: FileType;
};
export type NewCommitAlertOperation = { type: 'NEW_COMMIT_ALERT' };

export type FileOperation = FileContentEditOperation | FileCreateOperation | FileDeleteOperation | FileRenameOperation | NewCommitAlertOperation;

type YjsFileDoc = {
    doc: Y.Doc;
    text: Y.Text;
    awareness: Awareness;
    target: ProgrammingExerciseEditorSyncTarget;
    auxiliaryId?: number;
    fileName: string;
};

/**
 * Service responsible for synchronizing file changes across multiple code editors in real-time.
 * This enables collaborative editing of programming exercise repositories (template, solution, tests, auxiliary)
 * by broadcasting local file changes to other connected editors and applying remote changes received from them.
 *
 * @see ProgrammingExerciseEditorSyncService for WebSocket message handling
 * @see CodeEditorContainerComponent for integration with the code editor UI
 * @see ProblemStatementSyncService for synchronization of problem statement
 */
@Injectable({ providedIn: 'root' })
export class RepositoryFileSyncService {
    private syncService = inject(ProgrammingExerciseEditorSyncService);
    private fileService = inject(CodeEditorFileService);

    /** Yjs documents for live synchronization per file */
    private docs: Record<string, YjsFileDoc> = {};

    /** The current programming exercise ID being synchronized */
    private exerciseId?: number;

    /** Filter function to determine which incoming sync messages to process */
    private targetFilter: TargetFilter = () => true;

    /** WebSocket subscription for incoming synchronization messages */
    private incomingMessageSubscription?: Subscription;

    /** Subject that emits file operations received from other editors */
    private patchOperations = new Subject<FileOperation>();

    /**
     * Sends a synchronization message to other connected editors via WebSocket.
     * This is the central point for all outgoing synchronization messages.
     *
     * @param message - The synchronization message containing file changes, requests, or full file content
     * @throws Error if the service hasn't been initialized with an exercise ID
     * @private
     */
    private send(message: ProgrammingExerciseEditorSyncMessage) {
        if (!this.exerciseId) {
            throw new Error('RepositoryFileSyncService not initialized before sending synchronization message; exerciseId is undefined');
        }
        this.syncService.sendSynchronizationUpdate(this.exerciseId!, message);
    }

    /**
     * Initializes the synchronization service for a specific programming exercise.
     * This must be called before any file operations can be synchronized.
     *
     * Key responsibilities:
     * - Resets any previous state from earlier exercises
     * - Sets up WebSocket subscription to receive incoming file changes
     * - Configures filtering to determine which messages should be processed
     *
     * @param exerciseId - The ID of the programming exercise to sync
     * @param targetFilter - Function to filter which sync messages should be processed (e.g., only template repo)
     * @returns Observable that emits FileOperation events when remote changes are received
     */
    init(exerciseId: number, targetFilter: TargetFilter): Observable<FileOperation> {
        this.reset();
        this.exerciseId = exerciseId;
        this.targetFilter = targetFilter;
        this.docs = {};
        this.incomingMessageSubscription = this.syncService.subscribeToUpdates(exerciseId).subscribe((message) => this.handleIncomingMessage(message));
        return this.patchOperations.asObservable();
    }

    /**
     * Resets the service to its initial state, cleaning up all resources.
     * Called automatically during init() and should be called when navigating away from an exercise.
     *
     * Cleanup operations:
     * - Unsubscribes from WebSocket synchronization messages
     * - Clears tracked synchronization state
     * - Completes the patchOperations observable and creates a new one
     */
    reset() {
        this.syncService.unsubscribe();
        this.exerciseId = undefined;
        Object.values(this.docs).forEach((doc) => doc.doc.destroy());
        this.docs = {};
        this.targetFilter = () => true;
        this.incomingMessageSubscription?.unsubscribe();
        this.incomingMessageSubscription = undefined;
        this.patchOperations.complete();
        this.patchOperations = new Subject<FileOperation>();
    }

    getOrCreateFileDoc(repositoryType: RepositoryType, fileName: string, initialContent: string, auxiliaryId?: number): YjsFileDoc | undefined {
        const target = RepositoryFileSyncService.REPOSITORY_TYPE_TO_SYNC_TARGET[repositoryType];
        if (!target) {
            return undefined;
        }
        return this.ensureYjsDoc(target, fileName, auxiliaryId, initialContent);
    }

    /**
     * Requests the full content of a file from other connected editors.
     * This is used when synchronizing a new editor or recovering missing file content.
     *
     * When this is needed:
     * - When an editor joins and needs to catch up
     *
     * Other editors listening to the sync channel will respond with the full file content
     * via the respondWithFullFiles() method.
     *
     * @param repositoryType - The repository type to request from
     * @param fileName - The file path to request
     * @param auxiliaryId - Optional ID for auxiliary repositories
     */
    requestFullFile(repositoryType: RepositoryType, fileName: string, auxiliaryId?: number) {
        if (!this.exerciseId) {
            return;
        }
        const target = RepositoryFileSyncService.REPOSITORY_TYPE_TO_SYNC_TARGET[repositoryType];
        if (!target) {
            return;
        }
        this.send({
            target,
            auxiliaryRepositoryId: auxiliaryId,
            fileRequests: [fileName],
        });
    }

    /**
     * Main dispatcher for handling local file operations.
     * Routes the FileOperation event to the appropriate handler based on operation type.
     *
     * @param operation - The file operation event from CodeEditorContainerComponent
     * @param repositoryType - The repository type (template, solution, tests, etc.)
     * @param auxiliaryId - Optional auxiliary repository ID
     */
    handleLocalFileOperation(operation: FileOperation, repositoryType: RepositoryType, auxiliaryId?: number) {
        if (!this.exerciseId) {
            return;
        }

        const target = RepositoryFileSyncService.REPOSITORY_TYPE_TO_SYNC_TARGET[repositoryType];
        if (!target) {
            return;
        }

        switch (operation.type) {
            case ProgrammingExerciseEditorFileChangeType.CONTENT:
                this.handleLocalContentEdit(operation, target, auxiliaryId);
                break;
            case ProgrammingExerciseEditorFileChangeType.CREATE:
                this.handleLocalFileCreate(operation, target, auxiliaryId);
                return;
            case ProgrammingExerciseEditorFileChangeType.DELETE:
                this.handleLocalFileDelete(operation, target, auxiliaryId);
                return;
            case ProgrammingExerciseEditorFileChangeType.RENAME:
                this.handleLocalFileRename(operation, target, auxiliaryId);
                return;
        }
    }

    /**
     * Handles local file content edits and broadcasts them as Yjs updates.
     *
     * 1. Updates the Yjs document and broadcasts the update
     *
     * @param operation - The content edit operation with fileName and new content
     * @param target - The sync target (which repository type)
     * @param auxiliaryId - Optional ID for auxiliary repositories
     * @private
     */
    private handleLocalContentEdit(operation: FileContentEditOperation, target: ProgrammingExerciseEditorSyncTarget, auxiliaryId?: number) {
        const previousContent = this.getDocContent(target, operation.fileName, auxiliaryId);
        if (previousContent === operation.content) {
            return;
        }
        const yjsDoc = this.ensureYjsDoc(target, operation.fileName, auxiliaryId, operation.content);
        this.updateDocContent(yjsDoc, operation.content, 'local');
    }

    /**
     * Handles local file creation by initializing the Yjs document.
     *
     * @param operation - The create operation with fileName, content, and optional fileType
     * @param target - The sync target (which repository type)
     * @param auxiliaryId - Optional ID for auxiliary repositories
     * @private
     */
    private handleLocalFileCreate(operation: FileCreateOperation, target: ProgrammingExerciseEditorSyncTarget, auxiliaryId?: number) {
        this.ensureYjsDoc(target, operation.fileName, auxiliaryId, operation.content);
    }

    /**
     * Handles local file deletion by removing the Yjs document.
     *
     * @param operation - The delete operation with fileName
     * @param target - The sync target (which repository type)
     * @param auxiliaryId - Optional ID for auxiliary repositories
     * @private
     */
    private handleLocalFileDelete(operation: FileDeleteOperation, target: ProgrammingExerciseEditorSyncTarget, auxiliaryId?: number) {
        const docKey = this.getDocKey(target, operation.fileName, auxiliaryId);
        this.docs[docKey]?.doc.destroy();
        delete this.docs[docKey];
    }

    /**
     * Handles local file rename by moving any existing Yjs document.
     *
     * @param operation - The rename operation with fileName, newFileName, and content
     * @param target - The sync target (which repository type)
     * @param auxiliaryId - Optional ID for auxiliary repositories
     * @private
     */
    private handleLocalFileRename(operation: FileRenameOperation, target: ProgrammingExerciseEditorSyncTarget, auxiliaryId?: number) {
        const docKey = this.getDocKey(target, operation.fileName, auxiliaryId);

        // Skip if a file is renamed before its document is registered (e.g., file was never opened AND no live edits were received yet)
        if (operation.fileType === FileType.FILE && !this.docs[docKey]) {
            return;
        }
        this.renameDocEntries(target, auxiliaryId, operation.fileName, operation.newFileName);
    }

    /**
     * Main entry point for processing incoming WebSocket synchronization messages.
     * This method is called automatically when a sync message is received from other editors.
     *
     * Message processing flow:
     * 1. Filters out problem statement messages (handled separately)
     * 2. Applies the configured targetFilter (e.g., only process template repo)
     * 3. If message contains newCommitAlert, emits a special commit alert operation
     * 4. If message contains file requests, responds with full file content
     * 5. If message contains file patches, applies them to local Yjs documents
     *
     * @param message - The incoming synchronization message from another editor
     * @private
     */
    private handleIncomingMessage(message: ProgrammingExerciseEditorSyncMessage) {
        if (!message.target || message.target === ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT) {
            return;
        }
        if (!this.targetFilter(message)) {
            return;
        }

        // Handle new commit alerts - these indicate a commit was made (potentially from an offline IDE)
        // and the user should refresh to get the latest changes
        if (message.newCommitAlert) {
            this.patchOperations.next({ type: 'NEW_COMMIT_ALERT' });
            return;
        }

        if (message.awareness?.type === ProgrammingExerciseEditorAwarenessType.UPDATE && message.awareness.update) {
            this.handleAwarenessUpdate(message);
            return;
        }

        if (message.fileRequests?.length) {
            this.respondWithFullFiles(message);
            return;
        }

        message.filePatches?.forEach((filePatch) => {
            const op = this.handleRemoteFilePatch(message, filePatch);
            if (op) {
                this.patchOperations.next(op);
            }
        });
    }

    /**
     * Processes a single file patch received from another editor.
     * This is the core method for applying remote changes to the local Yjs documents.
     *
     * @param message - The sync message containing metadata (timestamp, target, etc.)
     * @param filePatch - The specific file change (content update, create, delete, or rename)
     * @returns FileOperation to apply to the UI, or undefined if not applicable
     * @private
     */
    private handleRemoteFilePatch(message: ProgrammingExerciseEditorSyncMessage, filePatch: ProgrammingExerciseEditorFileSync): FileOperation | undefined {
        if (!message.target) {
            return undefined;
        }
        const auxiliaryId = message.target === ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY ? message.auxiliaryRepositoryId : undefined;
        const docKey = this.getDocKey(message.target, filePatch.fileName, auxiliaryId);
        // **DELETE**: Removes the file's Yjs document and returns a delete operation
        if (filePatch.changeType === ProgrammingExerciseEditorFileChangeType.DELETE) {
            this.docs[docKey]?.doc.destroy();
            delete this.docs[docKey];
            return { type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: filePatch.fileName };
        }

        // **RENAME**: Moves the Yjs document to the new filename and returns a rename operation
        if (filePatch.changeType === ProgrammingExerciseEditorFileChangeType.RENAME && filePatch.newFileName) {
            const fileType = filePatch.fileType ?? (filePatch.fileName.includes('.') ? FileType.FILE : FileType.FOLDER);
            const newKey = this.getDocKey(message.target, filePatch.newFileName, auxiliaryId);
            if (fileType === FileType.FOLDER) {
                this.renameDocEntries(message.target, auxiliaryId, filePatch.fileName, filePatch.newFileName);
                return {
                    type: ProgrammingExerciseEditorFileChangeType.RENAME,
                    fileName: filePatch.fileName,
                    newFileName: filePatch.newFileName,
                    content: filePatch.patch ?? '',
                    fileType,
                };
            }
            const existingDoc = this.docs[docKey];
            const currentContent = existingDoc?.text.toString() ?? filePatch.patch ?? '';
            if (existingDoc) {
                this.docs[newKey] = { ...existingDoc, fileName: filePatch.newFileName };
                delete this.docs[docKey];
            }
            return {
                type: ProgrammingExerciseEditorFileChangeType.RENAME,
                fileName: filePatch.fileName,
                newFileName: filePatch.newFileName,
                content: currentContent,
                fileType,
            };
        }

        //  **CREATE**: Registers a new Yjs document with initial content and returns a create operation
        if (filePatch.changeType === ProgrammingExerciseEditorFileChangeType.CREATE) {
            const content = filePatch?.patch ?? '';
            return { type: ProgrammingExerciseEditorFileChangeType.CREATE, fileName: filePatch.fileName, content, fileType: filePatch.fileType };
        }

        if (!filePatch.yjsUpdate) {
            return undefined;
        }
        const yjsDoc = this.getExistingDoc(message.target, filePatch.fileName, auxiliaryId);
        if (!yjsDoc) {
            return undefined;
        }
        const update = decodeBase64ToUint8Array(filePatch.yjsUpdate);
        Y.applyUpdate(yjsDoc.doc, update, 'remote');
        return undefined;
    }

    /**
     * Applies a remote file operation to the code editor UI.
     * This is the main entry point for updating the UI based on changes from other editors.
     *
     * Process:
     * 1. Routes to the appropriate apply handler based on operation type
     * 2. Updates the code editor container state
     * 3. Refreshes the repository tree view to reflect changes
     *
     * Note: This method modifies the UI but does NOT update Yjs documents - those are already
     * updated by handleRemoteFilePatch() before this method is called.
     *
     * NEW_COMMIT_ALERT operations are handled by the calling component and should not
     * be passed to this method.
     *
     * @param operation - The file operation to apply (from handleRemoteFilePatch)
     * @param codeEditorContainer - The code editor container component to update
     */
    applyRemoteOperation(operation: FileOperation, codeEditorContainer: CodeEditorContainerComponent) {
        // NEW_COMMIT_ALERT is handled by the calling component, skip it here
        if (operation.type === 'NEW_COMMIT_ALERT') {
            return;
        }

        switch (operation.type) {
            case ProgrammingExerciseEditorFileChangeType.CONTENT:
                codeEditorContainer.applyRemoteFileContent(operation.fileName, operation.content);
                break;
            case ProgrammingExerciseEditorFileChangeType.CREATE:
                this.applyRemoteCreate(operation.fileName, operation.content, codeEditorContainer, operation.fileType);
                this.markRepositoryDirty(codeEditorContainer);
                break;
            case ProgrammingExerciseEditorFileChangeType.RENAME:
                if (operation.newFileName) {
                    this.applyRemoteRename(operation.fileName, operation.newFileName, operation.content, codeEditorContainer, operation.fileType);
                    this.markRepositoryDirty(codeEditorContainer);
                }
                break;
            case ProgrammingExerciseEditorFileChangeType.DELETE:
                this.applyRemoteDelete(operation.fileName, codeEditorContainer);
                this.markRepositoryDirty(codeEditorContainer);
                break;
        }
        this.refreshRepositoryTree(codeEditorContainer);
    }

    private markRepositoryDirty(codeEditorContainer: CodeEditorContainerComponent) {
        if (codeEditorContainer.commitState !== CommitState.CONFLICT && codeEditorContainer.commitState !== CommitState.COMMITTING) {
            codeEditorContainer.commitState = CommitState.UNCOMMITTED_CHANGES;
        }
    }

    /**
     * Applies a remote file creation to the UI.
     *
     * Steps:
     * 1. Determines file type (FILE vs FOLDER) from provided metadata or filename heuristic
     * 2. Adds the file to the file browser's repository file list
     * 3. If it's a regular file (not folder), updates the editor content
     *
     * @param fileName - The path of the created file
     * @param content - The initial content of the file
     * @param codeEditorContainer - The code editor container to update
     * @param fileType - Optional file type; inferred from fileName if not provided
     * @private
     */
    private applyRemoteCreate(fileName: string, content: string, codeEditorContainer: CodeEditorContainerComponent, fileType?: FileType) {
        const fileBrowser = codeEditorContainer.fileBrowser;
        const resolvedFileType = fileType ?? this.lookupFileType(fileName, codeEditorContainer);
        if (fileBrowser) {
            fileBrowser.repositoryFiles = { ...(fileBrowser.repositoryFiles ?? {}), [fileName]: resolvedFileType };
        }
        if (resolvedFileType === FileType.FILE) {
            codeEditorContainer.applyRemoteFileContent(fileName, content);
        }
        codeEditorContainer.onFileChanged.emit();
    }

    /**
     * Applies a remote file deletion to the UI.
     *
     * Steps:
     * 1. Removes the file from the file browser's repository file list
     * 2. Removes the file from unsaved files tracking
     * 3. If the deleted file was currently selected, clears the selection
     * 4. Emits file change event to notify other components
     *
     * @param fileName - The path of the deleted file
     * @param codeEditorContainer - The code editor container to update
     * @private
     */
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

    /**
     * Applies a remote file rename to the UI.
     *
     * Steps:
     * 1. Updates file references in the file browser (old name → new name)
     * 2. Updates unsaved files tracking with the new name
     * 3. If the renamed file was selected, updates selection to the new name
     * 4. Updates the editor content for the renamed file
     *
     * @param oldFileName - The previous file path
     * @param newFileName - The new file path
     * @param content - The file content (same before/after rename)
     * @param codeEditorContainer - The code editor container to update
     * @private
     */
    private applyRemoteRename(oldFileName: string, newFileName: string, content: string, codeEditorContainer: CodeEditorContainerComponent, fileType: FileType) {
        const fileBrowser = codeEditorContainer.fileBrowser;
        const renameChange = new RenameFileChange(fileType, oldFileName, newFileName);
        if (fileBrowser?.repositoryFiles) {
            fileBrowser.repositoryFiles = this.fileService.updateFileReferences(fileBrowser.repositoryFiles, renameChange);
        }
        codeEditorContainer.unsavedFiles = this.fileService.updateFileReferences(codeEditorContainer.unsavedFiles, renameChange);
        if (codeEditorContainer.selectedFile) {
            codeEditorContainer.selectedFile = this.fileService.updateFileReference(codeEditorContainer.selectedFile, renameChange);
        }
        if (fileType === FileType.FILE && !!content) {
            codeEditorContainer.applyRemoteFileContent(newFileName, content);
        }
        codeEditorContainer.onFileChanged.emit();
    }

    /**
     * Determines whether a path represents a file or folder.
     *
     * @param path - The file path to check
     * @param codeEditorContainer - The code editor container with file browser state
     * @returns FileType.FILE or FileType.FOLDER
     * @private
     */
    private lookupFileType(path: string, codeEditorContainer: CodeEditorContainerComponent): FileType {
        const fileBrowser = codeEditorContainer.fileBrowser;
        const knownType = fileBrowser?.repositoryFiles?.[path];
        return knownType ?? (path.includes('.') ? FileType.FILE : FileType.FOLDER);
    }

    /**
     * Refreshes the file browser's tree view to reflect current file state.
     * Should be called after any file operation (create, delete, rename) to update the UI.
     *
     * Steps:
     * 1. Re-initializes the repository files structure
     * 2. Refreshes the tree view component
     * 3. Marks the file browser for change detection (required for OnPush strategy)
     *
     * Note: The manual change detection trigger is crucial because the parent component
     * uses ChangeDetectionStrategy.OnPush, which means Angular won't automatically
     * detect changes unless explicitly notified.
     *
     * @param codeEditorContainer - The code editor container with file browser
     * @private
     */
    private refreshRepositoryTree(codeEditorContainer: CodeEditorContainerComponent) {
        const fileBrowser = codeEditorContainer.fileBrowser;
        if (fileBrowser) {
            fileBrowser.initializeRepositoryFiles();
            fileBrowser.refreshTreeview();
            // Trigger change detection to update the UI immediately
            fileBrowser.changeDetectorRef.markForCheck();
        }
    }

    /**
     * Responds to file content requests from other editors.
     * Called when another editor sends a fileRequests message (e.g., after joining late).
     *
     * Process:
     * 1. Looks up requested files in our local Yjs documents
     * 2. Sends back Yjs updates for each file we have
     * 3. Ignores requests for files we don't have (another editor may respond)
     *
     * @param message - The incoming file request message from another editor
     * @private
     */
    private respondWithFullFiles(message: ProgrammingExerciseEditorSyncMessage) {
        if (!message.target || !message.fileRequests?.length) {
            return;
        }
        const filePatches: ProgrammingExerciseEditorFileSync[] = [];
        message.fileRequests.forEach((fileName) => {
            const doc = this.getExistingDoc(message.target!, fileName, message.auxiliaryRepositoryId);
            if (!doc) {
                return;
            }
            const update = Y.encodeStateAsUpdate(doc.doc);
            filePatches.push({
                fileName,
                changeType: ProgrammingExerciseEditorFileChangeType.CONTENT,
                yjsUpdate: encodeUint8ArrayToBase64(update),
            });
        });
        if (!filePatches.length) {
            return;
        }
        this.send({
            target: message.target,
            auxiliaryRepositoryId: message.auxiliaryRepositoryId,
            filePatches,
        });
    }

    private renameDocEntries(target: ProgrammingExerciseEditorSyncTarget, auxiliaryId: number | undefined, oldFileName: string, newFileName: string) {
        const basePrefix = `${this.exerciseId ?? 'unknown'}-${target}-${auxiliaryId ?? 'none'}::`;
        const oldPrefix = `${basePrefix}${oldFileName}`;
        const newPrefix = `${basePrefix}${newFileName}`;
        Object.keys(this.docs).forEach((key) => {
            if (key.startsWith(oldPrefix) && (key.length === oldPrefix.length || key.charAt(oldPrefix.length) === '/')) {
                const suffix = key.slice(oldPrefix.length);
                const newKey = `${newPrefix}${suffix}`;
                const doc = this.docs[key];
                doc.fileName = `${newFileName}${suffix}`;
                this.docs[newKey] = doc;
                delete this.docs[key];
            }
        });
    }

    private ensureYjsDoc(target: ProgrammingExerciseEditorSyncTarget, fileName: string, auxiliaryId: number | undefined, initialContent?: string) {
        const key = this.getDocKey(target, fileName, auxiliaryId);
        const existing = this.docs[key];
        if (existing) {
            const shouldApplyInitialContent = initialContent !== undefined && (initialContent.length > 0 || existing.text.length === 0);
            if (shouldApplyInitialContent && existing.text.toString() !== initialContent) {
                this.updateDocContent(existing, initialContent, 'init');
            }
            return existing;
        }
        const doc = new Y.Doc();
        const text = doc.getText('content');
        const awareness = new Awareness(doc);
        doc.transact(() => {
            if (initialContent) {
                text.insert(0, initialContent);
            }
        }, 'init');
        const fileDoc: YjsFileDoc = { doc, text, awareness, target, auxiliaryId, fileName };
        doc.on('update', (update, origin: unknown) => {
            if (!this.exerciseId) {
                return;
            }
            const originTag = normalizeYjsOrigin(origin);
            if (originTag === 'remote' || originTag === 'init') {
                return;
            }
            this.send({
                target: fileDoc.target,
                auxiliaryRepositoryId: fileDoc.auxiliaryId,
                filePatches: [
                    {
                        fileName: fileDoc.fileName,
                        changeType: ProgrammingExerciseEditorFileChangeType.CONTENT,
                        yjsUpdate: encodeUint8ArrayToBase64(update),
                    },
                ],
            });
        });
        awareness.on('update', ({ added, updated, removed }: AwarenessUpdatePayload, origin: unknown) => {
            const originTag = normalizeYjsOrigin(origin);
            if (!this.exerciseId || originTag === 'remote') {
                return;
            }
            const update = encodeAwarenessUpdate(awareness, [...added, ...updated, ...removed]);
            this.send({
                target: fileDoc.target,
                auxiliaryRepositoryId: fileDoc.auxiliaryId,
                awareness: {
                    type: ProgrammingExerciseEditorAwarenessType.UPDATE,
                    update: encodeUint8ArrayToBase64(update),
                    fileName: fileDoc.fileName,
                },
            });
        });
        this.initializeLocalAwareness(awareness);
        this.docs[key] = fileDoc;
        return fileDoc;
    }

    private initializeLocalAwareness(awareness: Awareness) {
        const clientInstanceId = this.syncService.clientInstanceId;
        const clientName = clientInstanceId ? `Editor ${clientInstanceId.slice(0, 6)}` : 'Editor';
        const color = getColorForClientId(awareness.clientID);
        awareness.setLocalStateField('user', { name: clientName, color });
    }

    private handleAwarenessUpdate(message: ProgrammingExerciseEditorSyncMessage) {
        if (!message.target || !message.awareness?.update) {
            return;
        }
        const fileName = message.awareness.fileName;
        if (!fileName) {
            return;
        }
        const fileDoc = this.getExistingDoc(message.target, fileName, message.auxiliaryRepositoryId);
        if (!fileDoc) {
            return;
        }
        const update = decodeBase64ToUint8Array(message.awareness.update);
        applyAwarenessUpdate(fileDoc.awareness, update, 'remote');
        this.registerRemoteClientStyles(fileDoc.awareness);
    }

    private registerRemoteClientStyles(awareness: Awareness) {
        awareness.getStates().forEach((state, clientId) => {
            if (clientId === awareness.clientID) {
                return;
            }
            const color = state?.user?.color ?? getColorForClientId(clientId);
            ensureRemoteSelectionStyle(clientId, color);
        });
    }

    private updateDocContent(fileDoc: YjsFileDoc, content: string, origin: string) {
        if (fileDoc.text.toString() === content) {
            return;
        }
        fileDoc.doc.transact(() => {
            fileDoc.text.delete(0, fileDoc.text.length);
            if (content) {
                fileDoc.text.insert(0, content);
            }
        }, origin);
    }

    private getExistingDoc(target: ProgrammingExerciseEditorSyncTarget, fileName: string, auxiliaryId?: number): YjsFileDoc | undefined {
        const key = this.getDocKey(target, fileName, auxiliaryId);
        return this.docs[key];
    }

    /**
     * Generates a unique key for storing file documents.
     *
     * Key format: `{exerciseId}-{target}-{auxiliaryId}::{fileName}`
     * Examples:
     * - "123-TEMPLATE_REPOSITORY-none::src/Main.java"
     * - "456-AUXILIARY_REPOSITORY-789::README.md"
     *
     * @param target - The repository sync target
     * @param fileName - The file path
     * @param auxiliaryId - Optional auxiliary repository ID
     * @returns Unique key for the file
     * @private
     */
    private getDocKey(target: ProgrammingExerciseEditorSyncTarget, fileName: string, auxiliaryId?: number) {
        return `${this.exerciseId ?? 'unknown'}-${target}-${auxiliaryId ?? 'none'}::${fileName}`;
    }

    private getDocContent(target: ProgrammingExerciseEditorSyncTarget, fileName: string, auxiliaryId?: number) {
        const key = this.getDocKey(target, fileName, auxiliaryId);
        return this.docs[key]?.text.toString();
    }

    static readonly REPOSITORY_TYPE_TO_SYNC_TARGET: Readonly<Record<RepositoryType, ProgrammingExerciseEditorSyncTarget | undefined>> = {
        [RepositoryType.TEMPLATE]: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
        [RepositoryType.SOLUTION]: ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY,
        [RepositoryType.AUXILIARY]: ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY,
        [RepositoryType.TESTS]: ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY,
        [RepositoryType.ASSIGNMENT]: undefined,
        [RepositoryType.USER]: undefined,
    };
}
