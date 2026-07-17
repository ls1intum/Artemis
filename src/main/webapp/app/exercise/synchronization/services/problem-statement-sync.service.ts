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
    ProblemStatementAwarenessUpdateEvent,
    ProblemStatementSyncFullContentRequestEvent,
    ProblemStatementSyncFullContentResponseEvent,
    ProblemStatementSyncUpdateEvent,
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
 * Holds the shared Yjs primitives for the problem statement editor.
 */
export type ProblemStatementSyncState = {
    doc: Y.Doc;
    text: Y.Text;
    awareness: Awareness;
};

export type ProblemStatementInitialSyncFinalizedEvent = {
    contentChangedDuringFinalize: boolean;
    contentDivergedFromFallback: boolean;
    finalContent: string;
};

enum ProblemStatementSyncOrigin {
    Remote = 'remote',
    Seed = 'seed',
}

/**
 * Fixed Yjs client id used only for the single local "seed" transaction that inserts fallback
 * content into a freshly created Y.Doc when no peer answers the initial full-content request.
 *
 * Why this matters: two editors joining at nearly the same moment can both time out and
 * independently seed identical fallback content. If each used its own random `doc.clientID` (the
 * Yjs default), the resulting Y.Text items would be structurally distinct even though the
 * content is byte-identical — any later incremental edit that positions itself relative to the
 * seed item (as its left/right origin) could then never be integrated into the other peer's
 * document, so the two documents would diverge permanently (see
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
 * problem-statement-sync.service.spec.ts).
 */
export const DETERMINISTIC_SEED_CLIENT_ID = 1;

/**
 * Manages Yjs-based collaborative real-time synchronization for problem statement editing.
 *
 * This service is provided at the root level (singleton). It supports only one active exercise
 * session at a time. Calling `init()` while a prior session is active will silently reset the
 * previous session. This is acceptable because the consuming component
 * (`ProgrammingExerciseEditableInstructionComponent`) is always destroyed and recreated on
 * navigation, ensuring a clean lifecycle.
 */
@Injectable({ providedIn: 'root' })
export class ProblemStatementSyncService {
    private syncService = inject(ExerciseEditorSyncService);
    private accountService = inject(AccountService);

    private exerciseId?: number;
    private incomingMessageSubscription?: Subscription;
    private yDoc?: Y.Doc;
    private yText?: Y.Text;
    private awareness?: Awareness;
    private awaitingInitialSync = false;
    private localLeaderTimestamp = Date.now();
    private fallbackInitialContent = '';
    private latestInitialSyncRequestId?: string;
    private queuedFullContentRequests: string[] = [];
    // This Subject is intentionally never completed. As a root singleton, the service outlives
    // individual component lifecycles. Completing it on reset() would prevent subsequent init()
    // calls from emitting. Consumers must unsubscribe when they are destroyed.
    private stateReplacedSubject = new Subject<ProblemStatementSyncState>();
    private initialSyncFinalizedSubject = new Subject<ProblemStatementInitialSyncFinalizedEvent>();
    // Track initial leader selection and buffer updates until we seed the doc.
    private pendingInitialSync?: {
        requestId: string;
        responses: ProblemStatementSyncFullContentResponseEvent[];
        bufferedUpdates: Uint8Array[];
        timeoutId?: ReturnType<typeof setTimeout>;
    };

    /**
     * Stream that would emit replacement Yjs primitives if the local Y.Doc were ever swapped out
     * for a different one after initial sync.
     *
     * As of the state-vector reconciliation fix, this no longer happens: a late-arriving full
     * state is always merged into the existing doc via `Y.applyUpdate` (see `handleSyncResponse`)
     * instead of replacing it, so consumers never need to rebind. The observable is kept so that
     * existing subscribers (e.g. Monaco bindings) continue to compile unchanged; it currently
     * never emits.
     */
    get stateReplaced$(): Observable<ProblemStatementSyncState> {
        return this.stateReplacedSubject.asObservable();
    }

    /**
     * Stream emitting when initial synchronization finalizes.
     *
     * `contentChangedDuringFinalize` indicates whether finalize changed local Y.Text compared
     * to its pre-finalize value.
     * `contentDivergedFromFallback` indicates whether finalized shared content differs from the
     * server fallback used during initialization. `finalContent` carries the finalized text.
     */
    get initialSyncFinalized$(): Observable<ProblemStatementInitialSyncFinalizedEvent> {
        return this.initialSyncFinalizedSubject.asObservable();
    }

    /**
     * Whether the current problem statement synchronization is still in its initial bootstrap phase.
     */
    isAwaitingInitialSync(): boolean {
        return this.awaitingInitialSync;
    }

    /**
     * Initialize synchronization for a specific exercise.
     * Creates a new Yjs document, wires local update propagation, and requests the current shared state.
     *
     * @param exerciseId The exercise id used to scope websocket updates.
     * @param initialContent The current problem statement content used as fallback if no leader responds.
     * @returns The Yjs document, shared text, and awareness instance.
     */
    init(exerciseId: number, initialContent: string): ProblemStatementSyncState {
        this.reset();
        this.exerciseId = exerciseId;
        this.localLeaderTimestamp = Date.now();
        this.fallbackInitialContent = initialContent ?? '';
        this.awaitingInitialSync = true;
        this.incomingMessageSubscription = this.syncService.subscribeToUpdates().subscribe((message) => this.handleRemoteMessage(message));
        this.initializeYjsDocument();
        this.requestInitialSync();
        return { doc: this.yDoc!, text: this.yText!, awareness: this.awareness! };
    }

    /**
     * Reset all synchronization state and dispose the Yjs document.
     * Safe to call multiple times; clears any pending initial sync timeout.
     *
     * This method only tears down the local subscription, not the shared WebSocket
     * subscription managed by {@link ExerciseEditorSyncService}. The shared teardown
     * is handled by {@link ExerciseMetadataSyncService.destroy} when the containing
     * editor component is destroyed.
     */
    reset() {
        // Clear pending timeout first to prevent finalizeInitialSync from firing
        // after cleanup.
        if (this.pendingInitialSync?.timeoutId) {
            clearTimeout(this.pendingInitialSync.timeoutId);
        }
        this.pendingInitialSync = undefined;
        this.incomingMessageSubscription?.unsubscribe();
        this.incomingMessageSubscription = undefined;
        // Clear exerciseId before doc destroy so that any Yjs update events
        // triggered by doc.destroy() bail out in the guard (line: if (!this.exerciseId)).
        this.exerciseId = undefined;
        this.yDoc?.destroy();
        this.yDoc = undefined;
        this.yText = undefined;
        this.awareness = undefined;
        this.awaitingInitialSync = false;
        this.fallbackInitialContent = '';
        this.latestInitialSyncRequestId = undefined;
        this.queuedFullContentRequests = [];
        clearRemoteSelectionStyles();
    }

    /**
     * Request the newest problem statement content from other active editors (if any).
     * Ensures unsaved edits in other sessions are synchronized before local editing starts.
     */
    private requestInitialSync() {
        if (!this.exerciseId) {
            return;
        }
        const requestId = this.generateRequestId();
        this.latestInitialSyncRequestId = requestId;
        this.pendingInitialSync = { requestId, responses: [], bufferedUpdates: [] };
        const requestEvent: ProblemStatementSyncFullContentRequestEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            requestId,
        };
        this.syncService.sendSynchronizationUpdate(this.exerciseId, requestEvent);
        // 500ms collection window for initial sync responses. This balances responsiveness with
        // giving peers enough time to respond. On slow networks, late responses are still handled
        // correctly by merging them into the local doc in handleSyncResponse() — Yjs updates are
        // commutative and idempotent, so this never discards local edits made in the meantime.
        this.pendingInitialSync.timeoutId = setTimeout(() => this.finalizeInitialSync(), 500);
    }

    /**
     * Respond to a full-content request with the current Yjs document state.
     * Used by other editors to seed their initial sync.
     *
     * @param responseTo The request id to respond to.
     */
    private respondWithFullContent(responseTo: string) {
        if (!this.exerciseId) {
            return;
        }
        if (!this.yDoc) {
            return;
        }
        const update = Y.encodeStateAsUpdate(this.yDoc);
        // localLeaderTimestamp is informational only: it lets peers pick a deterministic winner
        // among several responses collected within their own collection window (see
        // finalizeInitialSync()). It no longer gates whether a late response is accepted, because
        // late responses are always merged (see handleSyncResponse()).
        const responseEvent: ProblemStatementSyncFullContentResponseEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo,
            yjsUpdate: encodeUint8ArrayToBase64(update),
            leaderTimestamp: this.localLeaderTimestamp,
        };
        this.syncService.sendSynchronizationUpdate(this.exerciseId, responseEvent);
    }

    /**
     * Handle incoming full-content requests from peers.
     *
     * While this client is still choosing its own initial state, requests are queued so we
     * do not answer with a transient state. Once initialization is finalized, queued requests
     * are answered in `flushQueuedFullContentRequests()`.
     *
     * @param requestId The request id to respond to.
     */
    private handleFullContentRequest(requestId: string) {
        if (this.awaitingInitialSync) {
            this.queuedFullContentRequests.push(requestId);
            return;
        }
        this.respondWithFullContent(requestId);
    }

    /**
     * Route a synchronization message from the websocket subscription to the correct handler.
     *
     * @param message The synchronization message to process.
     */
    private handleRemoteMessage(message: ExerciseEditorSyncEvent) {
        if (message.target !== ExerciseEditorSyncTarget.PROBLEM_STATEMENT) {
            return;
        }
        switch (message.eventType) {
            case ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST:
                this.handleFullContentRequest(message.requestId);
                break;
            case ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE:
                this.handleSyncResponse(message);
                break;
            case ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE:
                this.handleSyncUpdate(message);
                break;
            case ExerciseEditorSyncEventType.PROBLEM_STATEMENT_AWARENESS_UPDATE:
                this.handleAwarenessUpdate(message);
                break;
            default:
                break;
        }
    }

    /**
     * Initialize the Yjs document and wire up local update + awareness propagation.
     * Local changes are emitted to the websocket; remote changes are ignored here.
     */
    private initializeYjsDocument() {
        const doc = new Y.Doc();
        const text = doc.getText('problem-statement');
        const awareness = new Awareness(doc);
        doc.on('update', (update, origin: ProblemStatementSyncOrigin | unknown) => {
            if (!this.exerciseId) {
                return;
            }
            // do not rebroadcast updates made from remote or seed
            if (origin === ProblemStatementSyncOrigin.Remote || origin === ProblemStatementSyncOrigin.Seed) {
                return;
            }
            const updateEvent: ProblemStatementSyncUpdateEvent = {
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                yjsUpdate: encodeUint8ArrayToBase64(update),
            };
            this.syncService.sendSynchronizationUpdate(this.exerciseId, updateEvent);
        });
        awareness.on('update', ({ added, updated, removed }: AwarenessUpdatePayload, origin: ProblemStatementSyncOrigin | unknown) => {
            if (!this.exerciseId || origin === ProblemStatementSyncOrigin.Remote) {
                return;
            }
            const update = encodeAwarenessUpdate(awareness, [...added, ...updated, ...removed]);
            const awarenessEvent: ProblemStatementAwarenessUpdateEvent = {
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_AWARENESS_UPDATE,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                awarenessUpdate: encodeUint8ArrayToBase64(update),
            };
            this.syncService.sendSynchronizationUpdate(this.exerciseId, awarenessEvent);
        });
        this.initializeLocalAwareness(awareness);
        this.yDoc = doc;
        this.yText = text;
        this.awareness = awareness;
    }

    /**
     * Track full-content responses for the initial leader selection while a request is still
     * pending, or merge a late-arriving response into the already-finalized local doc.
     *
     * Responses collected while `pendingInitialSync` exists are evaluated on timeout to pick the
     * earliest leader (see `finalizeInitialSync()`); this is a bootstrap-time optimization to
     * avoid seeding when a peer answer is imminent, not a correctness requirement.
     *
     * A response that arrives after this client already finalized (e.g. on a slow network) is
     * always merged into the local doc via `Y.applyUpdate`, never used to replace or reject it.
     * Yjs updates are commutative and idempotent, so merging can only add state the local doc
     * doesn't already have — it can never wipe local edits made since finalization. This also
     * fixes the case where two editors joined near-simultaneously, both timed out, and
     * independently seeded identical fallback content: because that seed now uses a deterministic
     * client id (see `DETERMINISTIC_SEED_CLIENT_ID`), their seed items are structurally identical,
     * so merging a peer's full state is a no-op for the shared seed and correctly integrates any
     * edits the peer made on top of it.
     *
     * @param message The incoming full-content response.
     */
    private handleSyncResponse(message: ProblemStatementSyncFullContentResponseEvent) {
        if (this.pendingInitialSync) {
            if (message.responseTo !== this.pendingInitialSync.requestId) {
                return;
            }
            this.pendingInitialSync.responses.push(message);
            return;
        }
        if (message.responseTo !== this.latestInitialSyncRequestId) {
            return;
        }
        if (!this.yDoc) {
            return;
        }
        const update = decodeBase64ToUint8Array(message.yjsUpdate);
        Y.applyUpdate(this.yDoc, update, ProblemStatementSyncOrigin.Remote);
    }

    /**
     * Apply incremental Yjs updates from other editors.
     * While initial sync is pending, updates are buffered until initialization is finalized.
     *
     * @param message The incoming incremental update.
     */
    private handleSyncUpdate(message: ProblemStatementSyncUpdateEvent) {
        if (!this.yDoc) {
            return;
        }
        const update = decodeBase64ToUint8Array(message.yjsUpdate);
        if (this.awaitingInitialSync) {
            if (this.pendingInitialSync) {
                this.pendingInitialSync.bufferedUpdates.push(update);
            }
            return;
        }
        Y.applyUpdate(this.yDoc, update, ProblemStatementSyncOrigin.Remote);
    }

    /**
     * Finalize initial synchronization after the timeout.
     * Selects the earliest leader response, or falls back to buffered updates / local seed.
     */
    private finalizeInitialSync() {
        if (!this.pendingInitialSync) {
            return;
        }
        const textBeforeFinalize = this.yText?.toJSON() ?? '';
        const responses = this.pendingInitialSync.responses;
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
            if (this.yDoc) {
                Y.applyUpdate(this.yDoc, update, ProblemStatementSyncOrigin.Remote);
            }
        } else if (this.fallbackInitialContent && this.yDoc && this.yText) {
            this.seedFallbackContent(this.yDoc, this.yText, this.fallbackInitialContent);
        }
        if (this.pendingInitialSync.bufferedUpdates.length && this.yDoc) {
            this.pendingInitialSync.bufferedUpdates.forEach((update) => {
                Y.applyUpdate(this.yDoc!, update, ProblemStatementSyncOrigin.Remote);
            });
        }
        // scenario for high network latency
        // we must send queued full-content responses after the seed update has been sent
        // because even tho we sent the "seed" update, remote might have initialized with their own seed already
        // this ensures remote will merge our seed into theirs (a no-op if both seeded identical
        // fallback content, since the seed uses a deterministic client id — see seedFallbackContent())
        this.flushQueuedFullContentRequests();
        const finalContent = this.yText?.toJSON() ?? '';
        const contentChangedDuringFinalize = textBeforeFinalize !== finalContent;
        const contentDivergedFromFallback = finalContent !== this.fallbackInitialContent;
        this.awaitingInitialSync = false;
        this.initialSyncFinalizedSubject.next({ contentChangedDuringFinalize, contentDivergedFromFallback, finalContent });
        if (this.pendingInitialSync.timeoutId) {
            clearTimeout(this.pendingInitialSync.timeoutId);
        }
        this.pendingInitialSync = undefined;
    }

    /**
     * Apply awareness updates (cursor positions + user metadata) from other editors.
     * Also registers styles for remote selections.
     *
     * @param message The incoming awareness update.
     */
    private handleAwarenessUpdate(message: ProblemStatementAwarenessUpdateEvent) {
        if (!this.awareness || !message.awarenessUpdate) {
            return;
        }
        const update = decodeBase64ToUint8Array(message.awarenessUpdate);
        applyAwarenessUpdate(this.awareness, update, ProblemStatementSyncOrigin.Remote);
        this.registerRemoteClientStyles(this.awareness);
    }

    /**
     * Populate local awareness state with a display name and color for cursor rendering.
     * Updates the name once the user identity has been resolved.
     *
     * @param awareness The awareness instance to initialize.
     */
    private initializeLocalAwareness(awareness: Awareness) {
        const sessionId = this.syncService.sessionId;
        const color = getColorForClientId(awareness.clientID);
        const fallbackName = sessionId ? `Editor ${sessionId.slice(0, 6)}` : 'Editor';
        const user = this.accountService.userIdentity();
        if (user) {
            const name = (user.name ?? [user.firstName, user.lastName].filter(Boolean).join(' ').trim()) || user.login || fallbackName;
            awareness.setLocalStateField('user', { name, color });
        } else {
            awareness.setLocalStateField('user', { name: fallbackName, color });
        }
    }

    /**
     * Register CSS styles for remote collaborator selections based on awareness state.
     * Ensures a consistent color per remote client id.
     *
     * @param awareness The awareness instance containing remote cursor data.
     */
    private registerRemoteClientStyles(awareness: Awareness) {
        awareness.getStates().forEach((state, clientId) => {
            if (clientId === awareness.clientID) {
                return;
            }
            const color = state?.user?.color ?? getColorForClientId(clientId);
            const name = state?.user?.name;
            ensureRemoteSelectionStyle(clientId, color, name);
        });
    }

    /**
     * Generate a request id for full-content synchronization requests.
     * Used to match responses to the most recent request. Uses Date.now() + Math.random()
     * which is sufficient for matching request/response pairs in a small peer group.
     *
     * @returns A unique request id.
     */
    private generateRequestId(): string {
        return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    }

    /**
     * Reply to deferred full-content requests collected during initialization.
     *
     * Requests are deferred only while `awaitingInitialSync` is true, then drained in FIFO
     * order right after local initialization is finalized.
     */
    private flushQueuedFullContentRequests() {
        if (!this.queuedFullContentRequests.length) {
            return;
        }
        const requests = this.queuedFullContentRequests;
        this.queuedFullContentRequests = [];
        requests.forEach((requestId) => this.respondWithFullContent(requestId));
    }

    /**
     * Seed a freshly created Y.Doc with fallback content using a deterministic client id.
     *
     * See {@link DETERMINISTIC_SEED_CLIENT_ID} for why this matters: it ensures two peers who
     * both time out and seed the same fallback string end up with structurally identical (not
     * just visually identical) Y.Text state, so their histories share a common ancestor and later
     * merges (see `handleSyncResponse`) converge instead of diverging permanently.
     *
     * @param doc The Y.Doc to seed.
     * @param text The shared Y.Text belonging to `doc`.
     * @param content The fallback content to insert.
     */
    private seedFallbackContent(doc: Y.Doc, text: Y.Text, content: string): void {
        const realClientId = doc.clientID;
        doc.clientID = DETERMINISTIC_SEED_CLIENT_ID;
        doc.transact(() => {
            text.insert(0, content);
        }, ProblemStatementSyncOrigin.Seed);
        doc.clientID = realClientId;
    }
}
