import { Injectable, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import * as Y from 'yjs';
import { Awareness, applyAwarenessUpdate, encodeAwarenessUpdate } from 'y-protocols/awareness';
import { AccountService } from 'app/core/auth/account.service';
import {
    ProblemStatementAwarenessUpdateEvent,
    ProblemStatementSyncFullContentRequestEvent,
    ProblemStatementSyncFullContentResponseEvent,
    ProblemStatementSyncUpdateEvent,
    ProgrammingExerciseEditorSyncEvent,
    ProgrammingExerciseEditorSyncEventType,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import {
    AwarenessUpdatePayload,
    decodeBase64ToUint8Array,
    encodeUint8ArrayToBase64,
    ensureRemoteSelectionStyle,
    getColorForClientId,
    normalizeYjsOrigin,
} from 'app/programming/manage/services/yjs-utils';

export type ProblemStatementSyncState = {
    doc: Y.Doc;
    text: Y.Text;
    awareness: Awareness;
};

enum ProblemStatementSyncOrigin {
    Remote = 'remote',
    Seed = 'seed',
    Init = 'init',
}

@Injectable({ providedIn: 'root' })
export class ProblemStatementSyncService {
    private syncService = inject(ProgrammingExerciseEditorSyncService);
    private accountService = inject(AccountService);

    private exerciseId?: number;
    private incomingMessageSubscription?: Subscription;
    private yDoc?: Y.Doc;
    private yText?: Y.Text;
    private awareness?: Awareness;
    private awaitingInitialSync = false;
    private localLeaderTimestamp = Date.now();
    private fallbackInitialContent = '';
    private pendingInitialSync?: {
        requestId: string;
        responses: ProblemStatementSyncFullContentResponseEvent[];
        bufferedUpdates: Uint8Array[];
        timeoutId?: ReturnType<typeof setTimeout>;
    };

    /**
     * Initialize synchronization for a specific exercise.
     * Creates a new Yjs document and requests the current shared state from other editors.
     */
    init(exerciseId: number, initialContent: string): ProblemStatementSyncState {
        this.reset();
        this.exerciseId = exerciseId;
        this.localLeaderTimestamp = Date.now();
        this.fallbackInitialContent = initialContent ?? '';
        this.awaitingInitialSync = true;
        this.incomingMessageSubscription = this.syncService.subscribeToUpdates(exerciseId).subscribe((message) => this.handleRemoteMessage(message));
        this.initializeYjsDocument();
        this.requestInitialSync();
        return { doc: this.yDoc!, text: this.yText!, awareness: this.awareness! };
    }

    /**
     * Reset all synchronization state and dispose the Yjs document.
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
        if (this.pendingInitialSync?.timeoutId) {
            clearTimeout(this.pendingInitialSync.timeoutId);
        }
        this.pendingInitialSync = undefined;
    }

    /**
     * Request the newest problem statement content from other active editors (if exists).
     * This ensures that unsaved problem statement from other editors are also synchronized.
     */
    requestInitialSync() {
        if (!this.exerciseId) {
            return;
        }
        const requestId = this.generateRequestId();
        this.pendingInitialSync = { requestId, responses: [], bufferedUpdates: [] };
        const requestEvent: ProblemStatementSyncFullContentRequestEvent = {
            eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            requestId,
        };
        this.syncService.sendSynchronizationUpdate(this.exerciseId, requestEvent);
        this.pendingInitialSync.timeoutId = setTimeout(() => this.finalizeInitialSync(), 500);
    }

    /**
     * Respond to a full-content request with the current Yjs document state.
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
            eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo,
            yjsUpdate: encodeUint8ArrayToBase64(update),
            leaderTimestamp: this.localLeaderTimestamp,
        };
        this.syncService.sendSynchronizationUpdate(this.exerciseId, responseEvent);
    }

    /**
     * Respond to a synchronization message from the websocket subscription for programming exercise problem statements.
     *
     * @param message The synchronization message to process
     */
    private handleRemoteMessage(message: ProgrammingExerciseEditorSyncEvent) {
        if (message.target !== ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT) {
            return;
        }
        switch (message.eventType) {
            case ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST:
                this.respondWithFullContent(message.requestId);
                break;
            case ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE:
                this.handleSyncResponse(message);
                break;
            case ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE:
                this.handleSyncUpdate(message);
                break;
            case ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_AWARENESS_UPDATE:
                this.handleAwarenessUpdate(message);
                break;
            default:
                break;
        }
    }

    /**
     * Initialize the Yjs document and wire up local update/awareness propagation.
     */
    private initializeYjsDocument() {
        const doc = new Y.Doc();
        const text = doc.getText('problem-statement');
        const awareness = new Awareness(doc);
        doc.on('update', (update, origin: unknown) => {
            if (!this.exerciseId) {
                return;
            }
            const originTag = normalizeYjsOrigin(origin);
            if (originTag === ProblemStatementSyncOrigin.Remote || originTag === ProblemStatementSyncOrigin.Init) {
                return;
            }
            const updateEvent: ProblemStatementSyncUpdateEvent = {
                eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                yjsUpdate: encodeUint8ArrayToBase64(update),
            };
            this.syncService.sendSynchronizationUpdate(this.exerciseId, updateEvent);
        });
        awareness.on('update', ({ added, updated, removed }: AwarenessUpdatePayload, origin: unknown) => {
            const originTag = normalizeYjsOrigin(origin);
            if (!this.exerciseId || originTag === ProblemStatementSyncOrigin.Remote) {
                return;
            }
            const update = encodeAwarenessUpdate(awareness, [...added, ...updated, ...removed]);
            const awarenessEvent: ProblemStatementAwarenessUpdateEvent = {
                eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_AWARENESS_UPDATE,
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
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
     */
    private handleSyncResponse(message: ProblemStatementSyncFullContentResponseEvent) {
        if (!this.pendingInitialSync || message.responseTo !== this.pendingInitialSync.requestId) {
            return;
        }
        this.pendingInitialSync.responses.push(message);
    }

    /**
     * Apply incremental Yjs updates from other editors.
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
        this.awaitingInitialSync = false;
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
        } else if (this.pendingInitialSync.bufferedUpdates.length && this.yDoc) {
            this.pendingInitialSync.bufferedUpdates.forEach((update) => {
                Y.applyUpdate(this.yDoc!, update, ProblemStatementSyncOrigin.Remote);
            });
        } else if (this.fallbackInitialContent && this.yDoc && this.yText) {
            this.yDoc.transact(() => {
                this.yText?.insert(0, this.fallbackInitialContent);
            }, ProblemStatementSyncOrigin.Seed);
        }
        this.awaitingInitialSync = false;
        if (this.pendingInitialSync.timeoutId) {
            clearTimeout(this.pendingInitialSync.timeoutId);
        }
        this.pendingInitialSync = undefined;
    }

    /**
     * Apply awareness updates (cursor positions + user metadata) from other editors.
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
     */
    private initializeLocalAwareness(awareness: Awareness) {
        const clientInstanceId = this.syncService.clientInstanceId;
        const color = getColorForClientId(awareness.clientID);
        const fallbackName = clientInstanceId ? `Editor ${clientInstanceId.slice(0, 6)}` : 'Editor';
        awareness.setLocalStateField('user', { name: fallbackName, color });
        this.accountService.identity().then((user) => {
            if (!user) {
                return;
            }
            const name = (user.name ?? [user.firstName, user.lastName].filter(Boolean).join(' ').trim()) || user.login || fallbackName;
            awareness.setLocalStateField('user', { name, color });
        });
    }

    /**
     * Register CSS styles for remote collaborator selections based on awareness state.
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
     */
    private generateRequestId(): string {
        return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    }
}
