import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { DiffMatchPatch } from 'diff-match-patch-typescript';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { DeleteFileChange, FileType, RenameFileChange, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileService } from 'app/programming/shared/code-editor/services/code-editor-file.service';
import {
    ProgrammingExerciseEditorFileChangeType,
    ProgrammingExerciseEditorFileFull,
    ProgrammingExerciseEditorFileSync,
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import { AlertService } from 'app/shared/service/alert.service';

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
    private alertService = inject(AlertService);
    private fileService = inject(CodeEditorFileService);

    /** Google's diff-match-patch library for generating and applying text diffs */
    private readonly diffMatchPatch = new DiffMatchPatch();

    /** Reference snapshots of file content used as the base for diff calculations */
    private baselines: Record<string, string> = {};

    /** Timestamps of last processed messages per file, used to prevent duplicate processing */
    private lastProcessedTimestamps: Record<string, number> = {};

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
        this.baselines = {};
        this.lastProcessedTimestamps = {};
        this.incomingMessageSubscription = this.syncService.subscribeToUpdates(exerciseId).subscribe((message) => this.handleIncomingMessage(message));
        return this.patchOperations.asObservable();
    }

    /**
     * Resets the service to its initial state, cleaning up all resources.
     * Called automatically during init() and should be called when navigating away from an exercise.
     *
     * Cleanup operations:
     * - Unsubscribes from WebSocket synchronization messages
     * - Clears all baseline content snapshots
     * - Resets timestamp tracking for duplicate detection
     * - Completes the patchOperations observable and creates a new one
     */
    reset() {
        this.syncService.unsubscribe();
        this.exerciseId = undefined;
        this.baselines = {};
        this.lastProcessedTimestamps = {};
        this.targetFilter = () => true;
        this.incomingMessageSubscription?.unsubscribe();
        this.incomingMessageSubscription = undefined;
        this.patchOperations.complete();
        this.patchOperations = new Subject<FileOperation>();
    }

    /**
     * Registers a baseline (reference snapshot) of a file's content.
     * This baseline is used as the starting point for generating and applying diff patches.
     *
     * When to call this:
     * - When a file is first loaded from the server
     * - When a file is created or renamed
     * - After successfully applying a remote change
     *
     * The baseline ensures that both local and remote editors work from the same reference point
     * when calculating diffs, preventing divergence in collaborative editing scenarios.
     *
     * @param repositoryType - The repository type (template, solution, tests, auxiliary)
     * @param fileName - The file path within the repository
     * @param content - The current content snapshot to use as baseline
     * @param auxiliaryId - Optional ID for auxiliary repositories
     */
    registerBaseline(repositoryType: RepositoryType, fileName: string, content: string, auxiliaryId?: number) {
        const target = RepositoryFileSyncService.REPOSITORY_TYPE_TO_SYNC_TARGET[repositoryType];
        if (!target) {
            return;
        }
        const key = this.getBaselineKey(target, fileName, auxiliaryId);
        this.baselines[key] = content;
    }

    /**
     * Requests the full content of a file from other connected editors.
     * This is used as a fallback when patch application fails or when synchronizing a new editor.
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
     * Handles local file content edits and broadcasts them as diff patches.
     *
     * 1. Retrieves the previously saved baseline (reference snapshot)
     * 2. Generates a diff patch between the baseline and new content
     * 3. Updates the baseline to the new content
     * 4. Sends the patch to other editors via WebSocket
     *
     * @param operation - The content edit operation with fileName and new content
     * @param target - The sync target (which repository type)
     * @param auxiliaryId - Optional ID for auxiliary repositories
     * @private
     */
    private handleLocalContentEdit(operation: FileContentEditOperation, target: ProgrammingExerciseEditorSyncTarget, auxiliaryId?: number) {
        const baselineKey = this.getBaselineKey(target, operation.fileName, auxiliaryId);
        const previousContent = this.baselines[baselineKey] ?? '';

        const patch = this.diffMatchPatch.patch_toText(this.diffMatchPatch.patch_make(previousContent, operation.content));
        if (!patch) {
            // No changes detected
            return;
        }

        this.baselines[baselineKey] = operation.content;

        this.send({
            target,
            auxiliaryRepositoryId: auxiliaryId,
            filePatches: [{ fileName: operation.fileName, changeType: ProgrammingExerciseEditorFileChangeType.CONTENT, patch }],
        });
    }

    /**
     * Handles local file creation and broadcasts it to other editors.
     *
     * Process:
     * 1. Registers the initial content as the baseline
     * 2. Sends the full file content (using 'patch' field for initial content)
     * 3. Includes file type metadata for proper rendering
     *
     * Note: For CREATE operations, the 'patch' field actually contains the full content,
     * not a diff patch. This is intentional for initial file creation.
     *
     * @param operation - The create operation with fileName, content, and optional fileType
     * @param target - The sync target (which repository type)
     * @param auxiliaryId - Optional ID for auxiliary repositories
     * @private
     */
    private handleLocalFileCreate(operation: FileCreateOperation, target: ProgrammingExerciseEditorSyncTarget, auxiliaryId?: number) {
        const baselineKey = this.getBaselineKey(target, operation.fileName, auxiliaryId);
        this.baselines[baselineKey] = operation.content;
    }

    /**
     * Handles local file deletion and broadcasts it to other editors.
     *
     * Process:
     * 1. Removes the file's baseline from tracking
     * 2. Sends a DELETE message with just the fileName (no content needed)
     *
     * Other editors will remove the file from their UI and tracking.
     *
     * @param operation - The delete operation with fileName
     * @param target - The sync target (which repository type)
     * @param auxiliaryId - Optional ID for auxiliary repositories
     * @private
     */
    private handleLocalFileDelete(operation: FileDeleteOperation, target: ProgrammingExerciseEditorSyncTarget, auxiliaryId?: number) {
        const baselineKey = this.getBaselineKey(target, operation.fileName, auxiliaryId);
        delete this.baselines[baselineKey];
    }

    /**
     * Handles local file rename. We keep baselines in sync locally and rely on the server broadcast to inform peers.
     *
     * 1. Retrieves content from the old file's baseline
     * 2. Transfers the baseline to the new fileName
     * 3. Deletes the old fileName's baseline
     *
     * The content is included to ensure all editors have the same final state,
     * even if they haven't fully synced the file before the rename.
     *
     * @param operation - The rename operation with fileName, newFileName, and content
     * @param target - The sync target (which repository type)
     * @param auxiliaryId - Optional ID for auxiliary repositories
     * @private
     */
    private handleLocalFileRename(operation: FileRenameOperation, target: ProgrammingExerciseEditorSyncTarget, auxiliaryId?: number) {
        const baselineKey = this.getBaselineKey(target, operation.fileName, auxiliaryId);
        const previousContent = this.baselines[baselineKey] ?? '';

        // Keep the current content even if we never registered a baseline for the old path
        const renameContent = previousContent || operation.content;
        this.renameTrackingEntries(target, auxiliaryId, operation.fileName, operation.newFileName);
        this.baselines[this.getBaselineKey(target, operation.newFileName, auxiliaryId)] = renameContent;
        delete this.baselines[baselineKey];
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
     * 5. If message contains patches, applies them to local baselines
     * 6. If message contains full file content, updates baselines directly
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

        this.handleFullFileSync(message);
    }

    /**
     * Processes full file content received from other editors.
     * This is used when another editor responds to our file request or sends complete file content.
     *
     * For each file:
     * 1. Updates the baseline with the full content
     * 2. Records the message timestamp to prevent processing older messages
     * 3. Emits a CONTENT operation to notify subscribers
     *
     * @param message - The sync message containing full file contents
     * @private
     */
    private handleFullFileSync(message: ProgrammingExerciseEditorSyncMessage) {
        if (!message.target || !message.fileFulls?.length) {
            return;
        }
        const auxiliaryId = message.target === ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY ? message.auxiliaryRepositoryId : undefined;
        message.fileFulls.forEach((fullFile) => {
            const baselineKey = this.getBaselineKey(message.target!, fullFile.fileName, auxiliaryId);
            this.lastProcessedTimestamps[baselineKey] = message.timestamp ?? Date.now();
            this.baselines[baselineKey] = fullFile.content;
            this.patchOperations.next({ type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: fullFile.fileName, content: fullFile.content });
        });
    }

    /**
     * Processes a single file patch received from another editor.
     * This is the core method for applying remote changes to the local baseline.
     *
     * Error handling:
     * - If patch application fails (content diverged), returns undefined
     *
     * @param message - The sync message containing metadata (timestamp, target, etc.)
     * @param filePatch - The specific file change (patch, create, delete, or rename)
     * @returns FileOperation to apply to the UI, or undefined if patch failed
     * @private
     */
    private handleRemoteFilePatch(message: ProgrammingExerciseEditorSyncMessage, filePatch: ProgrammingExerciseEditorFileSync): FileOperation | undefined {
        if (!message.target) {
            return undefined;
        }
        const auxiliaryId = message.target === ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY ? message.auxiliaryRepositoryId : undefined;
        const baselineKey = this.getBaselineKey(message.target, filePatch.fileName, auxiliaryId);
        const messageTimestamp = message.timestamp ?? Date.now();

        // Duplicate prevention:
        // - Checks message timestamp against lastProcessedTimestamps to skip duplicate/old messages
        // - This prevents race conditions when multiple editors send updates simultaneously
        if (this.lastProcessedTimestamps[baselineKey] && messageTimestamp <= this.lastProcessedTimestamps[baselineKey]) {
            return undefined;
        }

        // **DELETE**: Removes the file's baseline and returns a delete operation
        if (filePatch.changeType === ProgrammingExerciseEditorFileChangeType.DELETE) {
            delete this.baselines[baselineKey];
            this.lastProcessedTimestamps[baselineKey] = messageTimestamp;
            return { type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: filePatch.fileName };
        }

        // **RENAME**: Moves the baseline to the new filename and returns a rename operation
        if (filePatch.changeType === ProgrammingExerciseEditorFileChangeType.RENAME && filePatch.newFileName) {
            const fileType = filePatch.fileType ?? (filePatch.fileName.includes('.') ? FileType.FILE : FileType.FOLDER);
            const newKey = this.getBaselineKey(message.target, filePatch.newFileName, auxiliaryId);
            if (fileType === FileType.FOLDER) {
                this.renameTrackingEntries(message.target, auxiliaryId, filePatch.fileName, filePatch.newFileName);
                delete this.baselines[baselineKey];
                delete this.lastProcessedTimestamps[baselineKey];
                return {
                    type: ProgrammingExerciseEditorFileChangeType.RENAME,
                    fileName: filePatch.fileName,
                    newFileName: filePatch.newFileName,
                    content: filePatch.patch ?? '',
                    fileType,
                };
            }

            const currentContent = this.baselines[baselineKey] ?? this.baselines[newKey] ?? filePatch.patch ?? '';
            delete this.baselines[baselineKey];
            delete this.lastProcessedTimestamps[baselineKey];

            this.baselines[newKey] = currentContent;
            this.lastProcessedTimestamps[newKey] = messageTimestamp;
            return {
                type: ProgrammingExerciseEditorFileChangeType.RENAME,
                fileName: filePatch.fileName,
                newFileName: filePatch.newFileName,
                content: currentContent,
                fileType,
            };
        }

        //  **CREATE**: Registers a new baseline with initial content and returns a create operation
        if (filePatch.changeType === ProgrammingExerciseEditorFileChangeType.CREATE) {
            const content = filePatch?.patch ?? '';
            this.baselines[baselineKey] = content;
            this.lastProcessedTimestamps[baselineKey] = messageTimestamp;
            return { type: ProgrammingExerciseEditorFileChangeType.CREATE, fileName: filePatch.fileName, content, fileType: filePatch.fileType };
        }

        // **CONTENT** (default): Applies diff patch to baseline and returns updated content
        const patches = filePatch.patch ? this.diffMatchPatch.patch_fromText(filePatch.patch) : [];
        const currentContent = this.baselines[baselineKey];

        if (currentContent === undefined) {
            this.requestFullFileForTarget(message.target, filePatch.fileName, auxiliaryId);
            return undefined;
        }

        let patchedContent: string;
        try {
            if (patches.length) {
                const [appliedContent, results] = this.diffMatchPatch.patch_apply(patches, currentContent);
                // Check if any patch failed to apply (results array contains false for failed patches)
                const hasFailedPatches = results.some((success) => !success);
                if (hasFailedPatches) {
                    this.alertService.info('artemisApp.editor.synchronization.patchFailedAlert');
                    return undefined;
                }
                patchedContent = appliedContent;
            } else {
                patchedContent = filePatch.patch ?? currentContent;
            }
        } catch (error) {
            // If patch parsing or application throws an error, request full file sync as fallback
            this.alertService.info('artemisApp.editor.synchronization.syncErrorAlert');
            return undefined;
        }

        this.baselines[baselineKey] = patchedContent;
        this.lastProcessedTimestamps[baselineKey] = messageTimestamp;
        return { type: ProgrammingExerciseEditorFileChangeType.CONTENT, fileName: filePatch.fileName, content: patchedContent };
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
     * Note: This method modifies the UI but does NOT update baselines - those are already
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
                break;
            case ProgrammingExerciseEditorFileChangeType.RENAME:
                if (operation.newFileName) {
                    this.applyRemoteRename(operation.fileName, operation.newFileName, operation.content, codeEditorContainer, operation.fileType);
                }
                break;
            case ProgrammingExerciseEditorFileChangeType.DELETE:
                this.applyRemoteDelete(operation.fileName, codeEditorContainer);
                break;
        }
        this.refreshRepositoryTree(codeEditorContainer);
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
        if (fileType === FileType.FILE) {
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
     * Called when another editor sends a fileRequests message (e.g., after failed patch application).
     *
     * Process:
     * 1. Looks up requested files in our local baselines
     * 2. Sends back full file content for each file we have
     * 3. Ignores requests for files we don't have (another editor may respond)
     *
     * @param message - The incoming file request message from another editor
     * @private
     */
    private respondWithFullFiles(message: ProgrammingExerciseEditorSyncMessage) {
        if (!message.target || !message.fileRequests?.length) {
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
        this.send({
            target: message.target,
            auxiliaryRepositoryId: message.auxiliaryRepositoryId,
            fileFulls: files,
        });
    }

    /**
     * Renames all tracked baseline and timestamp entries for a file or folder rename.
     * For folders, cascades to all nested paths.
     */
    private renameTrackingEntries(target: ProgrammingExerciseEditorSyncTarget, auxiliaryId: number | undefined, oldFileName: string, newFileName: string) {
        const basePrefix = `${this.exerciseId ?? 'unknown'}-${target}-${auxiliaryId ?? 'none'}::`;
        const oldPrefix = `${basePrefix}${oldFileName}`;
        const newPrefix = `${basePrefix}${newFileName}`;
        const renameKeys = (collection: Record<string, unknown>) => {
            Object.keys(collection).forEach((key) => {
                if (key.startsWith(oldPrefix) && (key.length === oldPrefix.length || key.charAt(oldPrefix.length) === '/')) {
                    const suffix = key.slice(oldPrefix.length);
                    const newKey = `${newPrefix}${suffix}`;
                    collection[newKey] = collection[key];
                    delete collection[key];
                }
            });
        };
        renameKeys(this.baselines);
        renameKeys(this.lastProcessedTimestamps);
    }

    /**
     * Generates a unique key for storing file baselines and timestamps.
     *
     * Key format: `{exerciseId}-{target}-{auxiliaryId}::{fileName}`
     * Examples:
     * - "123-TEMPLATE_REPOSITORY-none::src/Main.java"
     * - "456-AUXILIARY_REPOSITORY-789::README.md"
     *
     * @param target - The repository sync target
     * @param fileName - The file path
     * @param auxiliaryId - Optional auxiliary repository ID
     * @returns Unique baseline key for the file
     * @private
     */
    private getBaselineKey(target: ProgrammingExerciseEditorSyncTarget, fileName: string, auxiliaryId?: number) {
        return `${this.exerciseId ?? 'unknown'}-${target}-${auxiliaryId ?? 'none'}::${fileName}`;
    }

    private requestFullFileForTarget(target: ProgrammingExerciseEditorSyncTarget, fileName: string, auxiliaryId?: number) {
        const repositoryType = RepositoryFileSyncService.SYNC_TARGET_TO_REPOSITORY_TYPE[target];
        if (!repositoryType) {
            return;
        }
        this.requestFullFile(repositoryType, fileName, auxiliaryId);
    }

    static readonly REPOSITORY_TYPE_TO_SYNC_TARGET: Readonly<Record<RepositoryType, ProgrammingExerciseEditorSyncTarget | undefined>> = {
        [RepositoryType.TEMPLATE]: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
        [RepositoryType.SOLUTION]: ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY,
        [RepositoryType.AUXILIARY]: ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY,
        [RepositoryType.TESTS]: ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY,
        [RepositoryType.ASSIGNMENT]: undefined,
        [RepositoryType.USER]: undefined,
    };

    static readonly SYNC_TARGET_TO_REPOSITORY_TYPE: Readonly<Record<ProgrammingExerciseEditorSyncTarget, RepositoryType | undefined>> = {
        [ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY]: RepositoryType.TEMPLATE,
        [ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY]: RepositoryType.SOLUTION,
        [ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY]: RepositoryType.AUXILIARY,
        [ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY]: RepositoryType.TESTS,
        [ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT]: undefined,
    };
}
