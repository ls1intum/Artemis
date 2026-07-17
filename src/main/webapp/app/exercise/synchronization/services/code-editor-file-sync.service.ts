import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import * as Y from 'yjs';
import { Awareness, applyAwarenessUpdate, encodeAwarenessUpdate } from 'y-protocols/awareness';
import { AccountService } from 'app/core/auth/account.service';
import { Alert, AlertService, AlertType } from 'app/foundation/service/alert.service';
import { generateUuid } from 'app/foundation/util/crypto.utils';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
    ExerciseNewCommitAlertEvent,
    FileAwarenessUpdateEvent,
    FileCreatedEvent,
    FileDeletedEvent,
    FileRenamedEvent,
    FileSyncFullContentRequestEvent,
    FileSyncFullContentResponseEvent,
    FileSyncUpdateEvent,
} from 'app/exercise/synchronization/services/exercise-editor-sync.service';
import {
    AwarenessUpdatePayload,
    clearRemoteSelectionStyles,
    decodeBase64ToUint8Array,
    encodeUint8ArrayToBase64,
    ensureRemoteSelectionStyle,
    getColorForClientId,
} from 'app/exercise/synchronization/services/yjs-utils';

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
 * Fixed Yjs client id used only for the single local "seed" transaction that inserts fallback
 * content into a freshly created per-file Y.Doc when no peer answers the initial full-content
 * request.
 *
 * Why this matters: two editors opening the same file at nearly the same moment can both time
 * out and independently seed identical fallback content. If each used its own random
 * `doc.clientID` (the Yjs default), the resulting Y.Text items would be structurally distinct
 * even though the content is byte-identical — any later incremental edit that positions itself
 * relative to the seed item (as its left/right origin) could then never be integrated into the
 * other peer's document, so the two documents would diverge permanently (see
 * https://docs.yjs.dev/api/document-updates: merging requires shared history).
 *
 * Temporarily switching to this well-known constant for the seed transaction only (then
 * restoring the document's real client id, see `seedFallbackContent`) makes two independently
 * seeded documents produce a byte-identical Y.Text item for the same fallback string. Merging one
 * into the other is then a no-op for the seed, and any subsequent edits from either peer resolve
 * their positions correctly because they share that common ancestor item.
 *
 * Yjs itself defends against an accidental real collision with this reserved id: if a remote
 * update ever touches the clock for the local doc's current `clientID`, Yjs detects the clash and
 * regenerates a fresh random client id for the document (see yjs `Transaction.js`
 * `cleanupTransactions`), so briefly borrowing a fixed id here is safe.
 *
 * Exported so tests can construct a peer Y.Doc that mimics a real seed exactly (see
 * code-editor-file-sync.service.spec.ts).
 */
export const DETERMINISTIC_SEED_CLIENT_ID = 1;

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
const EXPECTED_COMMIT_GRACE_PERIOD_MS = 2000;

type FileSyncEntry = {
    // Mutable — kept in sync with the fileDocs map key by remapFileKey/remapDirectoryKeys.
    // Y.Doc has no off() API, so handlers are wired once and read entry.filePath at fire-time
    // instead of capturing the path as a closure variable. This prevents stale-path bugs after
    // a file is renamed while it is open in the editor.
    filePath: string;
    doc: Y.Doc;
    text: Y.Text;
    awareness: Awareness;
    awaitingInitialSync: boolean;
    localLeaderTimestamp: number;
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
 * Provided at the root level (singleton). It supports only one active exercise session at a
 * time. Calling `init()` while a prior session is active will silently reset the previous
 * session. This is acceptable because the consuming component
 * (`CodeEditorInstructorAndEditorContainerComponent`) is always destroyed and recreated on
 * navigation, ensuring a clean lifecycle.
 */
@Injectable({ providedIn: 'root' })
export class CodeEditorFileSyncService {
    private syncService = inject(ExerciseEditorSyncService);
    private accountService = inject(AccountService);
    private alertService = inject(AlertService);

    private exerciseId?: number;
    private expectedRepositoryUpdate = false;
    private expectedRepositoryUpdateCompletedAt?: number;
    private suppressNextTimestampLessCommitUntil?: number;
    private timestampLessExpectedCommitSeen = false;
    private readonly newCommitAlerts = new Set<Alert>();
    private currentTarget?: ExerciseEditorSyncTarget;
    private auxiliaryRepositoryId?: number;
    private incomingMessageSubscription?: Subscription;

    private fileDocs = new Map<string, FileSyncEntry>();
    private recentRenames = new Map<string, string>();
    private renameTimeouts = new Map<string, ReturnType<typeof setTimeout>>();
    // These Subjects are intentionally never completed. As a root singleton, the service outlives
    // individual component lifecycles. Completing them on reset() would prevent subsequent init()
    // calls from emitting. Consumers must unsubscribe when they are destroyed.
    private fileTreeChangeSubject = new Subject<FileCreatedEvent | FileDeletedEvent | FileRenamedEvent>();
    private stateReplacedSubject = new Subject<{ filePath: string } & FileSyncState>();
    private initialSyncFinalizedSubject = new Subject<{ filePath: string; contentDivergedFromFallback: boolean; finalContent: string }>();

    /**
     * Stream emitting file tree changes (create/delete/rename) from remote peers.
     */
    get fileTreeChange$(): Observable<FileCreatedEvent | FileDeletedEvent | FileRenamedEvent> {
        return this.fileTreeChangeSubject.asObservable();
    }

    /**
     * Stream that would emit replacement Yjs primitives if a file's Y.Doc were ever swapped out
     * for a different one after initial sync.
     *
     * As of the state-vector reconciliation fix, this no longer happens: a late-arriving full
     * state is always merged into the existing per-file doc via `Y.applyUpdate` (see
     * `handleSyncResponse`) instead of replacing it, so consumers never need to rebind. The
     * observable is kept so that existing subscribers (e.g. Monaco bindings) continue to compile
     * unchanged; it currently never emits.
     */
    get stateReplaced$(): Observable<{ filePath: string } & FileSyncState> {
        return this.stateReplacedSubject.asObservable();
    }

    /**
     * Stream emitted once per file when initial synchronization finalized.
     *
     * `contentDivergedFromFallback` indicates whether the finalized shared content differs from
     * the initial fallback content loaded from the server for this file. Consumers can use this
     * to decide whether the file should be marked as dirty after bootstrap.
     *
     * `finalContent` is the finalized shared text after applying winner-response/fallback and all
     * buffered updates.
     */
    get initialSyncFinalized$(): Observable<{ filePath: string; contentDivergedFromFallback: boolean; finalContent: string }> {
        return this.initialSyncFinalizedSubject.asObservable();
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
        this.incomingMessageSubscription = this.syncService.subscribeToUpdates().subscribe((message) => this.handleRemoteMessage(message));
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
        this.expectedRepositoryUpdate = false;
        this.expectedRepositoryUpdateCompletedAt = undefined;
        this.suppressNextTimestampLessCommitUntil = undefined;
        this.timestampLessExpectedCommitSeen = false;
        clearRemoteSelectionStyles();
    }

    /**
     * Suppresses commit warnings while this editor deliberately refreshes repositories changed by
     * an accepted server-side operation such as Hyperion generation.
     */
    beginExpectedRepositoryUpdate(operationCompletedAt?: number): void {
        this.expectedRepositoryUpdate = true;
        this.expectedRepositoryUpdateCompletedAt = operationCompletedAt;
        this.suppressNextTimestampLessCommitUntil = undefined;
        this.timestampLessExpectedCommitSeen = false;
        this.dismissNewCommitAlerts();
    }

    endExpectedRepositoryUpdate(): void {
        this.expectedRepositoryUpdate = false;
        if (!this.timestampLessExpectedCommitSeen) {
            this.suppressNextTimestampLessCommitUntil = Date.now() + EXPECTED_COMMIT_GRACE_PERIOD_MS;
        }
        this.dismissNewCommitAlerts();
    }

    /**
     * Open a file for synchronization. Creates a Y.Doc + Awareness, wires update handlers,
     * and requests initial sync from peers.
     *
     * Returns `undefined` if the service has not been initialized via `init()`. This is a
     * defensive guard — callers (e.g. `onFileSyncLoad`) already check `isInitialized()`
     * before calling, so this path is not expected during normal operation. If it does fire
     * (e.g. due to a race between domain change and file load), the file simply won't be
     * synced for that load; the next file selection after init() completes will work normally.
     *
     * @param filePath The file path relative to the repository root.
     * @param initialContent Fallback content if no peer responds with existing state.
     * @returns The Yjs document, shared text, and per-file awareness instance,
     *          or `undefined` if the service is not yet initialized.
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
            filePath,
            doc,
            text,
            awareness,
            awaitingInitialSync: true,
            localLeaderTimestamp: now,
            fallbackInitialContent: initialContent ?? '',
            queuedFullContentRequests: [],
        };

        this.wireDocumentHandlers(entry);
        this.wireAwarenessHandlers(entry);
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
     * Whether an open file is still awaiting initial synchronization.
     *
     * Returns false when the file is not open.
     */
    isFileAwaitingInitialSync(filePath: string): boolean {
        return this.getEntryByFilePath(filePath)?.awaitingInitialSync ?? false;
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
        // Keep entry.filePath current so that already-wired document/awareness handlers emit
        // under the new path rather than the stale pre-rename path.
        entry.filePath = newPath;
        this.addRecentRename(oldKey, newKey);
    }

    /**
     * Remap all Y.Docs under a directory prefix to new paths.
     */
    private remapDirectoryKeys(oldDir: string, newDir: string): void {
        const oldPrefix = this.buildKey(oldDir.endsWith('/') ? oldDir : oldDir + '/');
        const newPrefix = this.buildKey(newDir.endsWith('/') ? newDir : newDir + '/');
        const newDirNormalized = newDir.endsWith('/') ? newDir : newDir + '/';
        const toRemap: [string, string, FileSyncEntry, string][] = [];
        this.fileDocs.forEach((entry, key) => {
            if (key.startsWith(oldPrefix)) {
                const suffix = key.slice(oldPrefix.length);
                const newKey = newPrefix + suffix;
                toRemap.push([key, newKey, entry, suffix]);
            }
        });
        for (const [oldKey, newKey, entry, suffix] of toRemap) {
            this.fileDocs.delete(oldKey);
            this.fileDocs.set(newKey, entry);
            // Same as remapFileKey: update entry.filePath so wired handlers stay current.
            entry.filePath = newDirNormalized + suffix;
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
        entry.pendingInitialSync.timeoutId = setTimeout(() => this.finalizeInitialSync(entry), INITIAL_SYNC_FINALIZE_DELAY_MS);
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
        // Use entry.filePath (not the incoming filePath parameter) in case the file was renamed
        // after the request arrived on the old path and was forwarded via recentRenames.
        this.respondWithFullContent(entry, entry.filePath, requestId);
    }

    /**
     * Track full-content responses for the initial leader selection while a request is still
     * pending for this file, or merge a late-arriving response into the already-finalized entry.
     *
     * Responses collected while `entry.pendingInitialSync` exists are evaluated on timeout to
     * pick the earliest leader (see `finalizeInitialSync()`); this is a bootstrap-time
     * optimization to avoid seeding when a peer answer is imminent, not a correctness
     * requirement.
     *
     * A response that arrives after this file's entry already finalized (e.g. on a slow network)
     * is always merged into the entry's doc via `Y.applyUpdate`, never used to replace or reject
     * it. Yjs updates are commutative and idempotent, so merging can only add state the local doc
     * doesn't already have — it can never wipe local edits made since finalization. This also
     * fixes the case where two editors open the same file near-simultaneously, both time out, and
     * independently seed identical fallback content: because that seed now uses a deterministic
     * client id (see `DETERMINISTIC_SEED_CLIENT_ID`), their seed items are structurally identical,
     * so merging a peer's full state is a no-op for the shared seed and correctly integrates any
     * edits the peer made on top of it.
     */
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
        const update = decodeBase64ToUint8Array(message.yjsUpdate);
        Y.applyUpdate(entry.doc, update, FileSyncOrigin.Remote);
    }

    /**
     * Finalize a file's bootstrap sync phase.
     *
     * Resolution order:
     * 1) apply winning full-content response (if any),
     * 2) otherwise seed fallback server content,
     * 3) replay buffered incremental updates.
     *
     * After finalization, emits:
     * - `contentDivergedFromFallback`: whether finalized shared content differs from fallback,
     * - `finalContent`: finalized shared text.
     */
    private finalizeInitialSync(entry: FileSyncEntry): void {
        if (!entry.pendingInitialSync) {
            return;
        }
        const responses = entry.pendingInitialSync.responses;
        if (responses.length) {
            const selected = responses.reduce((best, next) => {
                // Primary sort: earliest timestamp wins
                if (next.leaderTimestamp < best.leaderTimestamp) {
                    return next;
                }
                if (next.leaderTimestamp > best.leaderTimestamp) {
                    return best;
                }
                // Tie-breaker: lexicographically smaller sessionId wins for determinism
                return (next.sessionId ?? '') < (best.sessionId ?? '') ? next : best;
            });
            const update = decodeBase64ToUint8Array(selected.yjsUpdate);
            Y.applyUpdate(entry.doc, update, FileSyncOrigin.Remote);
        } else if (entry.fallbackInitialContent.length > 0) {
            this.seedFallbackContent(entry.doc, entry.text, entry.fallbackInitialContent);
        }
        if (entry.pendingInitialSync.bufferedUpdates.length) {
            entry.pendingInitialSync.bufferedUpdates.forEach((update) => {
                Y.applyUpdate(entry.doc, update, FileSyncOrigin.Remote);
            });
        }
        this.flushQueuedFullContentRequests(entry, entry.filePath);
        const finalContent = entry.text.toJSON();
        const contentDivergedFromFallback = finalContent !== entry.fallbackInitialContent;
        entry.awaitingInitialSync = false;
        this.initialSyncFinalizedSubject.next({ filePath: entry.filePath, contentDivergedFromFallback, finalContent });
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

    // ── Private: Deterministic seeding ───────────────────────────────────

    /**
     * Seed a freshly created per-file Y.Doc with fallback content using a deterministic client id.
     *
     * See {@link DETERMINISTIC_SEED_CLIENT_ID} for why this matters: it ensures two peers who
     * both time out and seed the same fallback string for the same file end up with structurally
     * identical (not just visually identical) Y.Text state, so their histories share a common
     * ancestor and later merges (see `handleSyncResponse`) converge instead of diverging
     * permanently.
     */
    private seedFallbackContent(doc: Y.Doc, text: Y.Text, content: string): void {
        const realClientId = doc.clientID;
        doc.clientID = DETERMINISTIC_SEED_CLIENT_ID;
        doc.transact(() => {
            text.insert(0, content);
        }, FileSyncOrigin.Seed);
        doc.clientID = realClientId;
    }

    // ── Private: Document and awareness wiring ───────────────────────────

    private wireDocumentHandlers(entry: FileSyncEntry): void {
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
                // Read entry.filePath at fire-time, not from a closure-captured string.
                // Y.Doc has no off() API, so if this file is renamed while open, remapFileKey
                // updates entry.filePath and the next outgoing update carries the new path.
                filePath: entry.filePath,
                yjsUpdate: encodeUint8ArrayToBase64(update),
                auxiliaryRepositoryId: this.auxiliaryRepositoryId,
            };
            this.syncService.sendSynchronizationUpdate(this.exerciseId, updateEvent);
        });
    }

    private wireAwarenessHandlers(entry: FileSyncEntry): void {
        entry.awareness.on('update', ({ added, updated, removed }: AwarenessUpdatePayload, origin: FileSyncOrigin | unknown) => {
            if (!this.exerciseId || !this.currentTarget || origin === FileSyncOrigin.Remote) {
                return;
            }
            const update = encodeAwarenessUpdate(entry.awareness, [...added, ...updated, ...removed]);
            const awarenessEvent: FileAwarenessUpdateEvent = {
                eventType: ExerciseEditorSyncEventType.FILE_AWARENESS_UPDATE,
                target: this.currentTarget,
                // Same reasoning as wireDocumentHandlers: read at fire-time so renames are
                // reflected without re-registering the handler.
                filePath: entry.filePath,
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

    // ── Private: Commit alert handler ────────────────────────────────────

    private handleNewCommitAlert(message: ExerciseNewCommitAlertEvent): void {
        const isLateTimestampLessExpectedCommit =
            message.timestamp === undefined && this.suppressNextTimestampLessCommitUntil !== undefined && Date.now() <= this.suppressNextTimestampLessCommitUntil;
        if (isLateTimestampLessExpectedCommit) {
            this.suppressNextTimestampLessCommitUntil = undefined;
            return;
        }
        const isExpectedByTimestamp =
            this.expectedRepositoryUpdateCompletedAt !== undefined && message.timestamp !== undefined && message.timestamp <= this.expectedRepositoryUpdateCompletedAt;
        const cannotDistinguishDuringRefresh = this.expectedRepositoryUpdate && (this.expectedRepositoryUpdateCompletedAt === undefined || message.timestamp === undefined);
        if (isExpectedByTimestamp || cannotDistinguishDuringRefresh) {
            if (this.expectedRepositoryUpdate && message.timestamp === undefined) {
                this.timestampLessExpectedCommitSeen = true;
            }
            return;
        }
        const alert = this.alertService.addAlert({
            type: AlertType.WARNING,
            message: 'artemisApp.exercise.codeEditorSync.newCommitAlert',
            timeout: 0,
            onClose: (closedAlert) => this.newCommitAlerts.delete(closedAlert),
        });
        this.newCommitAlerts.add(alert);
    }

    private dismissNewCommitAlerts(): void {
        [...this.newCommitAlerts].forEach((alert) => alert.close());
        this.newCommitAlerts.clear();
    }

    // ── Private: Message routing ─────────────────────────────────────────

    private handleRemoteMessage(message: ExerciseEditorSyncEvent): void {
        if (!this.currentTarget) {
            return;
        }
        if (message.target !== this.currentTarget) {
            return;
        }
        if (this.currentTarget === ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY) {
            const messageAuxId = 'auxiliaryRepositoryId' in message ? message.auxiliaryRepositoryId : undefined;
            if (messageAuxId !== this.auxiliaryRepositoryId) {
                return;
            }
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
            case ExerciseEditorSyncEventType.NEW_COMMIT_ALERT:
                this.handleNewCommitAlert(message);
                break;
            default:
                break;
        }
    }

    private generateRequestId(): string {
        return generateUuid();
    }
}
