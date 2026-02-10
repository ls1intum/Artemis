import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import * as Y from 'yjs';
import { Awareness, applyAwarenessUpdate, encodeAwarenessUpdate } from 'y-protocols/awareness';
import { AccountService } from 'app/core/auth/account.service';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
    FileAwarenessUpdateEvent,
    FileCreatedEvent,
    FileDeletedEvent,
    FileRenamedEvent,
    FileSyncFullContentRequestEvent,
    FileSyncFullContentResponseEvent,
    FileSyncUpdateEvent,
} from 'app/exercise/services/exercise-editor-sync.service';
import {
    AwarenessUpdatePayload,
    clearRemoteSelectionStyles,
    decodeBase64ToUint8Array,
    encodeUint8ArrayToBase64,
    ensureRemoteSelectionStyle,
    getColorForClientId,
} from 'app/programming/manage/services/yjs-utils';

/**
 * Holds the shared Yjs primitives for a single file in the code editor.
 */
export type FileSyncState = {
    doc: Y.Doc;
    text: Y.Text;
    awareness: Awareness;
};

enum FileSyncOrigin {
    Remote = 'remote',
    Seed = 'seed',
}

/**
 * Delay in milliseconds before finalizing initial sync.
 * Allows time for peers to respond with their full content state.
 */
const INITIAL_SYNC_FINALIZE_DELAY_MS = 500;

/**
 * Time-to-live in milliseconds for rename redirect mappings.
 * Late-arriving updates on the old path will be forwarded to the new path within this window.
 */
const RENAME_REDIRECT_TTL_MS = 5000;

type FileSyncEntry = {
    doc: Y.Doc;
    text: Y.Text;
    awareness: Awareness;
    awaitingInitialSync: boolean;
    localLeaderTimestamp: number;
    activeLeaderTimestamp: number;
    latestRequestId?: string;
    fallbackInitialContent: string;
    queuedFullContentRequests: string[];
    pendingInitialSync?: {
        requestId: string;
        responses: FileSyncFullContentResponseEvent[];
        bufferedUpdates: Uint8Array[];
        timeoutId?: ReturnType<typeof setTimeout>;
    };
};

/**
 * Manages per-file Yjs synchronization for the online code editor.
 *
 * Each open file gets its own Y.Doc and Awareness instance, keyed by `{target}:{filePath}`.
 * Follows the same initial-sync, late-winning, and request-queuing patterns as
 * `ProblemStatementSyncService`.
 *
 * Provided at the component level (not root) for clean lifecycle per editor session.
 */
@Injectable()
export class CodeEditorFileSyncService {
    private syncService = inject(ExerciseEditorSyncService);
    private accountService = inject(AccountService);

    private exerciseId?: number;
    private currentTarget?: ExerciseEditorSyncTarget;
    private auxiliaryRepositoryId?: number;
    private incomingMessageSubscription?: Subscription;

    private fileDocs = new Map<string, FileSyncEntry>();
    private recentRenames = new Map<string, string>();
    private renameTimeouts = new Map<string, ReturnType<typeof setTimeout>>();
    private fileTreeChangeSubject = new Subject<FileCreatedEvent | FileDeletedEvent | FileRenamedEvent>();
    private stateReplacedSubject = new Subject<{ filePath: string } & FileSyncState>();

    /**
     * Stream emitting file tree changes (create/delete/rename) from remote peers.
     */
    get fileTreeChange$(): Observable<FileCreatedEvent | FileDeletedEvent | FileRenamedEvent> {
        return this.fileTreeChangeSubject.asObservable();
    }

    /**
     * Stream emitting replacement Yjs primitives when a late-winning leader response is accepted
     * for a specific file. Consumers (e.g. Monaco bindings) must rebind when their active file
     * is replaced.
     */
    get stateReplaced$(): Observable<{ filePath: string } & FileSyncState> {
        return this.stateReplacedSubject.asObservable();
    }

    /**
     * Whether the service has been initialized with an exercise and target.
     */
    isInitialized(): boolean {
        return this.exerciseId !== undefined && this.currentTarget !== undefined;
    }

    /**
     * Initialize synchronization for a specific exercise and repository target.
     * Subscribes to the shared WebSocket topic.
     */
    init(exerciseId: number, target: ExerciseEditorSyncTarget, auxiliaryRepositoryId?: number): void {
        this.reset();
        this.exerciseId = exerciseId;
        this.currentTarget = target;
        this.auxiliaryRepositoryId = auxiliaryRepositoryId;
        this.incomingMessageSubscription = this.syncService.subscribeToUpdates(exerciseId).subscribe((message) => this.handleRemoteMessage(message));
    }

    /**
     * Destroy all Y.Docs and Awareness instances, unsubscribe from WebSocket, and clear all state.
     */
    reset(): void {
        this.incomingMessageSubscription?.unsubscribe();
        this.incomingMessageSubscription = undefined;
        this.fileDocs.forEach((entry) => {
            if (entry.pendingInitialSync?.timeoutId) {
                clearTimeout(entry.pendingInitialSync.timeoutId);
            }
            entry.awareness.destroy();
            entry.doc.destroy();
        });
        this.fileDocs.clear();
        this.renameTimeouts.forEach((timeoutId) => clearTimeout(timeoutId));
        this.renameTimeouts.clear();
        this.recentRenames.clear();
        this.exerciseId = undefined;
        this.currentTarget = undefined;
        this.auxiliaryRepositoryId = undefined;
        clearRemoteSelectionStyles();
    }

    /**
     * Open a file for synchronization. Creates a Y.Doc + Awareness, wires update handlers,
     * and requests initial sync from peers.
     *
     * @param filePath The file path relative to the repository root.
     * @param initialContent Fallback content if no peer responds with existing state.
     * @returns The Yjs document, shared text, and per-file awareness instance.
     */
    openFile(filePath: string, initialContent: string): FileSyncState | undefined {
        if (!this.isInitialized()) {
            return undefined;
        }
        const key = this.buildKey(filePath);
        const existing = this.fileDocs.get(key);
        if (existing) {
            return { doc: existing.doc, text: existing.text, awareness: existing.awareness };
        }

        const doc = new Y.Doc();
        const text = doc.getText('file-content');
        const awareness = new Awareness(doc);
        const now = Date.now();

        const entry: FileSyncEntry = {
            doc,
            text,
            awareness,
            awaitingInitialSync: true,
            localLeaderTimestamp: now,
            activeLeaderTimestamp: now,
            fallbackInitialContent: initialContent ?? '',
            queuedFullContentRequests: [],
        };

        this.wireDocumentHandlers(entry, filePath);
        this.wireAwarenessHandlers(entry, filePath);
        this.initializeLocalAwareness(awareness);
        this.fileDocs.set(key, entry);
        this.requestInitialSync(entry, filePath);

        return { doc, text, awareness };
    }

    /**
     * Close a file's synchronization. Destroys its Y.Doc and Awareness, removes from the map.
     */
    closeFile(filePath: string): void {
        this.destroyEntry(this.buildKey(filePath));
    }

    /**
     * Check if a file is currently open for synchronization.
     */
    isFileOpen(filePath: string): boolean {
        return this.fileDocs.has(this.buildKey(filePath));
    }

    /**
     * Emit a FILE_CREATED event to notify peers of a new file.
     */
    emitFileCreated(filePath: string, fileType: 'FILE' | 'FOLDER'): void {
        if (!this.exerciseId || !this.currentTarget) {
            return;
        }
        const event: FileCreatedEvent = {
            eventType: ExerciseEditorSyncEventType.FILE_CREATED,
            target: this.currentTarget,
            filePath,
            fileType,
            auxiliaryRepositoryId: this.auxiliaryRepositoryId,
        };
        this.syncService.sendSynchronizationUpdate(this.exerciseId, event);
    }

    /**
     * Emit a FILE_DELETED event to notify peers. Also destroys the local Y.Doc if open.
     */
    emitFileDeleted(filePath: string, fileType: 'FILE' | 'FOLDER'): void {
        if (!this.exerciseId || !this.currentTarget) {
            return;
        }
        if (fileType === 'FILE') {
            this.closeFile(filePath);
        } else {
            this.closeFilesUnderDirectory(filePath);
        }
        const event: FileDeletedEvent = {
            eventType: ExerciseEditorSyncEventType.FILE_DELETED,
            target: this.currentTarget,
            filePath,
            fileType,
            auxiliaryRepositoryId: this.auxiliaryRepositoryId,
        };
        this.syncService.sendSynchronizationUpdate(this.exerciseId, event);
    }

    /**
     * Emit a FILE_RENAMED event to notify peers. Remaps the local Y.Doc key(s) without
     * destroying the document (preserves CRDT history).
     */
    emitFileRenamed(oldPath: string, newPath: string, fileType: 'FILE' | 'FOLDER'): void {
        if (!this.exerciseId || !this.currentTarget) {
            return;
        }
        if (fileType === 'FILE') {
            this.remapFileKey(oldPath, newPath);
        } else {
            this.remapDirectoryKeys(oldPath, newPath);
        }
        const event: FileRenamedEvent = {
            eventType: ExerciseEditorSyncEventType.FILE_RENAMED,
            target: this.currentTarget,
            oldPath,
            newPath,
            fileType,
            auxiliaryRepositoryId: this.auxiliaryRepositoryId,
        };
        this.syncService.sendSynchronizationUpdate(this.exerciseId, event);
    }

    // ── Private: Key management ──────────────────────────────────────────

    private buildKey(filePath: string): string {
        return `${this.currentTarget}:${filePath}`;
    }

    /**
     * Remap a single file's Y.Doc from old path to new path without destroying it.
     */
    private remapFileKey(oldPath: string, newPath: string): void {
        const oldKey = this.buildKey(oldPath);
        const newKey = this.buildKey(newPath);
        const entry = this.fileDocs.get(oldKey);
        if (!entry) {
            return;
        }
        this.fileDocs.delete(oldKey);
        this.fileDocs.set(newKey, entry);
        this.addRecentRename(oldKey, newKey);
    }

    /**
     * Remap all Y.Docs under a directory prefix to new paths.
     */
    private remapDirectoryKeys(oldDir: string, newDir: string): void {
        const oldPrefix = this.buildKey(oldDir.endsWith('/') ? oldDir : oldDir + '/');
        const newPrefix = this.buildKey(newDir.endsWith('/') ? newDir : newDir + '/');
        const toRemap: [string, string, FileSyncEntry][] = [];
        this.fileDocs.forEach((entry, key) => {
            if (key.startsWith(oldPrefix)) {
                const suffix = key.slice(oldPrefix.length);
                const newKey = newPrefix + suffix;
                toRemap.push([key, newKey, entry]);
            }
        });
        for (const [oldKey, newKey, entry] of toRemap) {
            this.fileDocs.delete(oldKey);
            this.fileDocs.set(newKey, entry);
            this.addRecentRename(oldKey, newKey);
        }
    }

    /**
     * Close all synced files under a directory prefix.
     */
    private closeFilesUnderDirectory(dirPath: string): void {
        const prefix = this.buildKey(dirPath.endsWith('/') ? dirPath : dirPath + '/');
        const toClose: string[] = [];
        this.fileDocs.forEach((_entry, key) => {
            if (key.startsWith(prefix)) {
                toClose.push(key);
            }
        });
        for (const key of toClose) {
            this.destroyEntry(key);
        }
    }

    /**
     * Destroy a single FileSyncEntry by its key: clears pending timeouts, destroys awareness
     * and doc, and removes it from the map.
     */
    private destroyEntry(key: string): void {
        const entry = this.fileDocs.get(key);
        if (!entry) {
            return;
        }
        if (entry.pendingInitialSync?.timeoutId) {
            clearTimeout(entry.pendingInitialSync.timeoutId);
        }
        entry.awareness.destroy();
        entry.doc.destroy();
        this.fileDocs.delete(key);
    }

    /**
     * Track a rename mapping with a 5-second TTL for late-arriving updates on the old path.
     */
    private addRecentRename(oldKey: string, newKey: string): void {
        this.recentRenames.set(oldKey, newKey);
        const existingTimeout = this.renameTimeouts.get(oldKey);
        if (existingTimeout) {
            clearTimeout(existingTimeout);
        }
        const timeoutId = setTimeout(() => {
            this.recentRenames.delete(oldKey);
            this.renameTimeouts.delete(oldKey);
        }, RENAME_REDIRECT_TTL_MS);
        this.renameTimeouts.set(oldKey, timeoutId);
    }

    /**
     * Resolve a key, following the rename chain if the original key no longer exists.
     * Handles multi-level renames (A -> B -> C) within the TTL window.
     */
    private resolveKey(key: string): string {
        let resolved = key;
        const visited = new Set<string>();
        while (this.recentRenames.has(resolved) && !visited.has(resolved)) {
            visited.add(resolved);
            resolved = this.recentRenames.get(resolved)!;
        }
        return resolved;
    }

    /**
     * Look up a FileSyncEntry by file path, falling back to recent renames if the
     * original key no longer exists in the map.
     */
    private getEntryByFilePath(filePath: string): FileSyncEntry | undefined {
        const key = this.buildKey(filePath);
        const entry = this.fileDocs.get(key);
        if (entry) {
            return entry;
        }
        const resolvedKey = this.resolveKey(key);
        if (resolvedKey !== key) {
            return this.fileDocs.get(resolvedKey);
        }
        return undefined;
    }

    // ── Private: Initial sync protocol ───────────────────────────────────

    private requestInitialSync(entry: FileSyncEntry, filePath: string): void {
        if (!this.exerciseId || !this.currentTarget) {
            return;
        }
        const requestId = this.generateRequestId();
        entry.latestRequestId = requestId;
        entry.pendingInitialSync = { requestId, responses: [], bufferedUpdates: [] };
        const requestEvent: FileSyncFullContentRequestEvent = {
            eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST,
            target: this.currentTarget,
            filePath,
            requestId,
            auxiliaryRepositoryId: this.auxiliaryRepositoryId,
        };
        this.syncService.sendSynchronizationUpdate(this.exerciseId, requestEvent);
        entry.pendingInitialSync.timeoutId = setTimeout(() => this.finalizeInitialSync(entry, filePath), INITIAL_SYNC_FINALIZE_DELAY_MS);
    }

    private respondWithFullContent(entry: FileSyncEntry, filePath: string, responseTo: string): void {
        if (!this.exerciseId || !this.currentTarget) {
            return;
        }
        const update = Y.encodeStateAsUpdate(entry.doc);
        const responseEvent: FileSyncFullContentResponseEvent = {
            eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
            target: this.currentTarget,
            filePath,
            responseTo,
            yjsUpdate: encodeUint8ArrayToBase64(update),
            leaderTimestamp: entry.localLeaderTimestamp,
            auxiliaryRepositoryId: this.auxiliaryRepositoryId,
        };
        this.syncService.sendSynchronizationUpdate(this.exerciseId, responseEvent);
    }

    private handleFullContentRequest(filePath: string, requestId: string): void {
        const entry = this.getEntryByFilePath(filePath);
        if (!entry) {
            return;
        }
        if (entry.awaitingInitialSync) {
            entry.queuedFullContentRequests.push(requestId);
            return;
        }
        this.respondWithFullContent(entry, filePath, requestId);
    }

    private handleSyncResponse(message: FileSyncFullContentResponseEvent): void {
        const entry = this.getEntryByFilePath(message.filePath);
        if (!entry) {
            return;
        }
        if (entry.pendingInitialSync) {
            if (message.responseTo !== entry.pendingInitialSync.requestId) {
                return;
            }
            entry.pendingInitialSync.responses.push(message);
            return;
        }
        if (message.responseTo !== entry.latestRequestId) {
            return;
        }
        if (message.leaderTimestamp >= entry.activeLeaderTimestamp) {
            return;
        }
        const update = decodeBase64ToUint8Array(message.yjsUpdate);
        this.replaceDocumentWithRemoteState(entry, message.filePath, update, message.leaderTimestamp);
    }

    private finalizeInitialSync(entry: FileSyncEntry, filePath: string): void {
        if (!entry.pendingInitialSync) {
            return;
        }
        const responses = entry.pendingInitialSync.responses;
        if (responses.length) {
            const selected = responses.reduce((best, next) => (next.leaderTimestamp < best.leaderTimestamp ? next : best));
            const update = decodeBase64ToUint8Array(selected.yjsUpdate);
            Y.applyUpdate(entry.doc, update, FileSyncOrigin.Remote);
            entry.activeLeaderTimestamp = selected.leaderTimestamp;
        } else if (entry.fallbackInitialContent) {
            entry.doc.transact(() => {
                entry.text.insert(0, entry.fallbackInitialContent);
            }, FileSyncOrigin.Seed);
            entry.activeLeaderTimestamp = entry.localLeaderTimestamp;
        }
        if (entry.pendingInitialSync.bufferedUpdates.length) {
            entry.pendingInitialSync.bufferedUpdates.forEach((update) => {
                Y.applyUpdate(entry.doc, update, FileSyncOrigin.Remote);
            });
        }
        this.flushQueuedFullContentRequests(entry, filePath);
        entry.awaitingInitialSync = false;
        if (entry.pendingInitialSync.timeoutId) {
            clearTimeout(entry.pendingInitialSync.timeoutId);
        }
        entry.pendingInitialSync = undefined;
    }

    private flushQueuedFullContentRequests(entry: FileSyncEntry, filePath: string): void {
        if (!entry.queuedFullContentRequests.length) {
            return;
        }
        const requests = entry.queuedFullContentRequests;
        entry.queuedFullContentRequests = [];
        requests.forEach((requestId) => this.respondWithFullContent(entry, filePath, requestId));
    }

    // ── Private: Late-winning replacement ────────────────────────────────

    private replaceDocumentWithRemoteState(oldEntry: FileSyncEntry, filePath: string, update: Uint8Array, leaderTimestamp: number): void {
        const key = this.buildKey(filePath);

        const doc = new Y.Doc();
        const text = doc.getText('file-content');
        const awareness = new Awareness(doc);

        const newEntry: FileSyncEntry = {
            doc,
            text,
            awareness,
            awaitingInitialSync: false,
            localLeaderTimestamp: oldEntry.localLeaderTimestamp,
            activeLeaderTimestamp: leaderTimestamp,
            latestRequestId: oldEntry.latestRequestId,
            fallbackInitialContent: oldEntry.fallbackInitialContent,
            queuedFullContentRequests: [],
        };

        this.wireDocumentHandlers(newEntry, filePath);
        this.wireAwarenessHandlers(newEntry, filePath);
        this.initializeLocalAwareness(awareness);

        Y.applyUpdate(doc, update, FileSyncOrigin.Remote);

        this.fileDocs.set(key, newEntry);
        clearRemoteSelectionStyles();
        this.stateReplacedSubject.next({ filePath, doc, text, awareness });

        oldEntry.awareness.destroy();
        oldEntry.doc.destroy();
    }

    // ── Private: Document and awareness wiring ───────────────────────────

    private wireDocumentHandlers(entry: FileSyncEntry, filePath: string): void {
        entry.doc.on('update', (update: Uint8Array, origin: FileSyncOrigin | unknown) => {
            if (!this.exerciseId || !this.currentTarget) {
                return;
            }
            if (origin === FileSyncOrigin.Remote || origin === FileSyncOrigin.Seed) {
                return;
            }
            const updateEvent: FileSyncUpdateEvent = {
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                target: this.currentTarget,
                filePath,
                yjsUpdate: encodeUint8ArrayToBase64(update),
                auxiliaryRepositoryId: this.auxiliaryRepositoryId,
            };
            this.syncService.sendSynchronizationUpdate(this.exerciseId, updateEvent);
        });
    }

    private wireAwarenessHandlers(entry: FileSyncEntry, filePath: string): void {
        entry.awareness.on('update', ({ added, updated, removed }: AwarenessUpdatePayload, origin: FileSyncOrigin | unknown) => {
            if (!this.exerciseId || !this.currentTarget || origin === FileSyncOrigin.Remote) {
                return;
            }
            const update = encodeAwarenessUpdate(entry.awareness, [...added, ...updated, ...removed]);
            const awarenessEvent: FileAwarenessUpdateEvent = {
                eventType: ExerciseEditorSyncEventType.FILE_AWARENESS_UPDATE,
                target: this.currentTarget,
                filePath,
                awarenessUpdate: encodeUint8ArrayToBase64(update),
                auxiliaryRepositoryId: this.auxiliaryRepositoryId,
            };
            this.syncService.sendSynchronizationUpdate(this.exerciseId, awarenessEvent);
        });
    }

    private initializeLocalAwareness(awareness: Awareness): void {
        const sessionId = this.syncService.sessionId;
        const color = getColorForClientId(awareness.clientID);
        const fallbackName = sessionId ? `Editor ${sessionId.slice(0, 6)}` : 'Editor';
        awareness.setLocalStateField('user', { name: fallbackName, color });
        const user = this.accountService.userIdentity();
        if (!user) {
            return;
        }
        const name = (user.name ?? [user.firstName, user.lastName].filter(Boolean).join(' ').trim()) || user.login || fallbackName;
        awareness.setLocalStateField('user', { name, color });
    }

    private registerRemoteClientStyles(awareness: Awareness): void {
        awareness.getStates().forEach((state, clientId) => {
            if (clientId === awareness.clientID) {
                return;
            }
            const color = state?.user?.color ?? getColorForClientId(clientId);
            const name = state?.user?.name;
            ensureRemoteSelectionStyle(clientId, color, name);
        });
    }

    // ── Private: Incremental update + awareness handlers ─────────────────

    private handleSyncUpdate(message: FileSyncUpdateEvent): void {
        const entry = this.getEntryByFilePath(message.filePath);
        if (!entry) {
            return;
        }
        const update = decodeBase64ToUint8Array(message.yjsUpdate);
        if (entry.awaitingInitialSync) {
            if (entry.pendingInitialSync) {
                entry.pendingInitialSync.bufferedUpdates.push(update);
            }
            return;
        }
        Y.applyUpdate(entry.doc, update, FileSyncOrigin.Remote);
    }

    private handleAwarenessUpdate(message: FileAwarenessUpdateEvent): void {
        const entry = this.getEntryByFilePath(message.filePath);
        if (!entry || !message.awarenessUpdate) {
            return;
        }
        const update = decodeBase64ToUint8Array(message.awarenessUpdate);
        applyAwarenessUpdate(entry.awareness, update, FileSyncOrigin.Remote);
        this.registerRemoteClientStyles(entry.awareness);
    }

    // ── Private: File tree event handlers ────────────────────────────────

    private handleRemoteFileCreated(message: FileCreatedEvent): void {
        this.fileTreeChangeSubject.next(message);
    }

    private handleRemoteFileDeleted(message: FileDeletedEvent): void {
        if (message.fileType === 'FILE') {
            this.closeFile(message.filePath);
        } else {
            this.closeFilesUnderDirectory(message.filePath);
        }
        this.fileTreeChangeSubject.next(message);
    }

    private handleRemoteFileRenamed(message: FileRenamedEvent): void {
        if (message.fileType === 'FILE') {
            this.remapFileKey(message.oldPath, message.newPath);
        } else {
            this.remapDirectoryKeys(message.oldPath, message.newPath);
        }
        this.fileTreeChangeSubject.next(message);
    }

    // ── Private: Message routing ─────────────────────────────────────────

    private handleRemoteMessage(message: ExerciseEditorSyncEvent): void {
        if (!this.currentTarget) {
            return;
        }
        if (message.target !== this.currentTarget) {
            return;
        }
        if (
            this.currentTarget === ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY &&
            'auxiliaryRepositoryId' in message &&
            message.auxiliaryRepositoryId !== this.auxiliaryRepositoryId
        ) {
            return;
        }
        switch (message.eventType) {
            case ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST:
                this.handleFullContentRequest(message.filePath, message.requestId);
                break;
            case ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE:
                this.handleSyncResponse(message);
                break;
            case ExerciseEditorSyncEventType.FILE_SYNC_UPDATE:
                this.handleSyncUpdate(message);
                break;
            case ExerciseEditorSyncEventType.FILE_AWARENESS_UPDATE:
                this.handleAwarenessUpdate(message);
                break;
            case ExerciseEditorSyncEventType.FILE_CREATED:
                this.handleRemoteFileCreated(message);
                break;
            case ExerciseEditorSyncEventType.FILE_DELETED:
                this.handleRemoteFileDeleted(message);
                break;
            case ExerciseEditorSyncEventType.FILE_RENAMED:
                this.handleRemoteFileRenamed(message);
                break;
            default:
                break;
        }
    }

    private generateRequestId(): string {
        return window.crypto.randomUUID();
    }
}
