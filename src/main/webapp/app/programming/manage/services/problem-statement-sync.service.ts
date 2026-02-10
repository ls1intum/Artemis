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
 * Holds the shared Yjs primitives for the problem statement editor.
 */
export type ProblemStatementSyncState = {
    doc: Y.Doc;
    text: Y.Text;
    awareness: Awareness;
};

enum ProblemStatementSyncOrigin {
    Remote = 'remote',
    Seed = 'seed',
}

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
    private activeLeaderTimestamp = Date.now();
    private fallbackInitialContent = '';
    private latestInitialSyncRequestId?: string;
    private queuedFullContentRequests: string[] = [];
    private stateReplacedSubject = new Subject<ProblemStatementSyncState>();
    // Track initial leader selection and buffer updates until we seed the doc.
    private pendingInitialSync?: {
        requestId: string;
        responses: ProblemStatementSyncFullContentResponseEvent[];
        bufferedUpdates: Uint8Array[];
        timeoutId?: ReturnType<typeof setTimeout>;
    };

    /**
     * Stream emitting replacement Yjs primitives when a late winning leader response is accepted.
     *
     * Consumers (e.g. Monaco bindings) must rebind to the emitted `text` and `awareness`
     * instances, because they belong to a freshly created Y.Doc.
     */
    get stateReplaced$(): Observable<ProblemStatementSyncState> {
        return this.stateReplacedSubject.asObservable();
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
        this.activeLeaderTimestamp = this.localLeaderTimestamp;
        this.fallbackInitialContent = initialContent ?? '';
        this.awaitingInitialSync = true;
        this.incomingMessageSubscription = this.syncService.subscribeToUpdates(exerciseId).subscribe((message) => this.handleRemoteMessage(message));
        this.initializeYjsDocument();
        this.requestInitialSync();
        return { doc: this.yDoc!, text: this.yText!, awareness: this.awareness! };
    }

    /**
     * Reset all synchronization state and dispose the Yjs document.
     * Safe to call multiple times; clears any pending initial sync timeout.
     */
    reset() {
        this.incomingMessageSubscription?.unsubscribe();
        this.incomingMessageSubscription = undefined;
        this.yDoc?.destroy();
        this.yDoc = undefined;
        this.yText = undefined;
        this.awareness = undefined;
        this.exerciseId = undefined;
        this.awaitingInitialSync = false;
        this.fallbackInitialContent = '';
        this.latestInitialSyncRequestId = undefined;
        this.queuedFullContentRequests = [];
        if (this.pendingInitialSync?.timeoutId) {
            clearTimeout(this.pendingInitialSync.timeoutId);
        }
        this.pendingInitialSync = undefined;
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
     * Track full-content responses for the initial leader selection.
     * Responses are evaluated on timeout to pick the earliest leader.
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
        if (!this.shouldReplaceWithRemoteLeader(message.leaderTimestamp)) {
            return;
        }
        const update = decodeBase64ToUint8Array(message.yjsUpdate);
        this.replaceDocumentWithRemoteState(update, message.leaderTimestamp);
    }

    /**
     * Apply incremental Yjs updates from other editors.
     * While initial sync is pending, updates are buffered until initilization finalize.
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
        const responses = this.pendingInitialSync.responses;
        if (responses.length) {
            const selected = responses.reduce((best, next) => (next.leaderTimestamp < best.leaderTimestamp ? next : best));
            const update = decodeBase64ToUint8Array(selected.yjsUpdate);
            if (this.yDoc) {
                Y.applyUpdate(this.yDoc, update, ProblemStatementSyncOrigin.Remote);
            }
            this.activeLeaderTimestamp = selected.leaderTimestamp;
        } else if (this.fallbackInitialContent && this.yDoc && this.yText) {
            this.yDoc.transact(() => {
                this.yText?.insert(0, this.fallbackInitialContent);
            }, ProblemStatementSyncOrigin.Seed);
            this.activeLeaderTimestamp = this.localLeaderTimestamp;
        }
        if (this.pendingInitialSync.bufferedUpdates.length && this.yDoc) {
            this.pendingInitialSync.bufferedUpdates.forEach((update) => {
                Y.applyUpdate(this.yDoc!, update, ProblemStatementSyncOrigin.Remote);
            });
        }
        // scenario for high network latency
        // we must send queued full-content responses after the seed update has been sent
        // because even tho we sent the "seed" update, remote might have initialized with their own seed already
        // this ensures that remote will replace their seed with our seed
        this.flushQueuedFullContentRequests();
        this.awaitingInitialSync = false;
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
        awareness.setLocalStateField('user', { name: fallbackName, color });
        const user = this.accountService.userIdentity();
        if (!user) {
            return;
        }
        const name = (user.name ?? [user.firstName, user.lastName].filter(Boolean).join(' ').trim()) || user.login || fallbackName;
        awareness.setLocalStateField('user', { name, color });
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
     * Used to match responses to the most recent request.
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
     * Determine if a remote leader should replace the active local leader.
     *
     * Lower leader timestamp wins deterministically.
     *
     * @param remoteLeaderTimestamp Timestamp carried by the remote full-content response.
     * @returns True when the remote leader has precedence.
     */
    private shouldReplaceWithRemoteLeader(remoteLeaderTimestamp: number): boolean {
        return remoteLeaderTimestamp < this.activeLeaderTimestamp;
    }

    /**
     * Replace current Yjs primitives with a full state received from a winning remote leader.
     *
     * This path handles late full-content responses that arrive after local initialization
     * has timed out, but are still relevant to the latest request and have a better leader.
     *
     * It creates a new Y.Doc, applies the remote snapshot, emits `stateReplaced$` so editor
     * bindings can reattach to the new doc, and finally destroys the previous doc.
     *
     * @param update Encoded full Yjs state to apply.
     * @param leaderTimestamp Leader timestamp of the selected remote response.
     */
    private replaceDocumentWithRemoteState(update: Uint8Array, leaderTimestamp: number) {
        const oldDoc = this.yDoc;
        this.initializeYjsDocument();
        if (!this.yDoc) {
            return;
        }
        Y.applyUpdate(this.yDoc, update, ProblemStatementSyncOrigin.Remote);
        this.activeLeaderTimestamp = leaderTimestamp;
        this.awaitingInitialSync = false;
        this.pendingInitialSync = undefined;
        clearRemoteSelectionStyles();
        this.stateReplacedSubject.next({ doc: this.yDoc, text: this.yText!, awareness: this.awareness! });
        oldDoc?.destroy();
    }
}
