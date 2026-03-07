import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { concatMap, filter, take, tap } from 'rxjs/operators';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';
import { UserPublicInfoDTO } from 'app/core/user/user.model';

/**
 * Synchronization targets used to scope editor sync events.
 */
export enum ExerciseEditorSyncTarget {
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
    TEMPLATE_REPOSITORY = 'TEMPLATE_REPOSITORY',
    SOLUTION_REPOSITORY = 'SOLUTION_REPOSITORY',
    TESTS_REPOSITORY = 'TESTS_REPOSITORY',
    AUXILIARY_REPOSITORY = 'AUXILIARY_REPOSITORY',
    EXERCISE_METADATA = 'EXERCISE_METADATA',
}

/**
 * Discriminator for synchronization event payloads.
 */
export enum ExerciseEditorSyncEventType {
    PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST = 'PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST',
    PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE = 'PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE',
    PROBLEM_STATEMENT_SYNC_UPDATE = 'PROBLEM_STATEMENT_SYNC_UPDATE',
    PROBLEM_STATEMENT_AWARENESS_UPDATE = 'PROBLEM_STATEMENT_AWARENESS_UPDATE',
    NEW_COMMIT_ALERT = 'NEW_COMMIT_ALERT',
    NEW_EXERCISE_VERSION_ALERT = 'NEW_EXERCISE_VERSION_ALERT',
}

/**
 * Shared fields for synchronization events received over websocket.
 */
export interface ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType;
    target: ExerciseEditorSyncTarget;
    sessionId?: string;
    timestamp?: number;
}

export interface ProblemStatementSyncFullContentRequestEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST;
    requestId: string;
}

export interface ProblemStatementSyncFullContentResponseEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE;
    responseTo: string;
    yjsUpdate: string;
    leaderTimestamp: number;
}

export interface ProblemStatementSyncUpdateEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE;
    yjsUpdate: string;
}

export interface ProblemStatementAwarenessUpdateEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_AWARENESS_UPDATE;
    awarenessUpdate: string;
}

/**
 * Event payload indicating a repository commit was pushed.
 */
export interface ExerciseNewCommitAlertEvent {
    eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT;
    target: ExerciseEditorSyncTarget;
    auxiliaryRepositoryId?: number;
    sessionId?: string;
    timestamp?: number;
}

/**
 * Event payload indicating a new exercise version (metadata) was saved.
 */
export interface ExerciseNewVersionAlertEvent {
    eventType: ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT;
    target: ExerciseEditorSyncTarget;
    exerciseVersionId: number;
    author: UserPublicInfoDTO;
    changedFields?: string[];
    sessionId?: string;
    timestamp?: number;
}

/**
 * Union of all synchronization events received by the editor.
 */
export type ExerciseEditorSyncEvent =
    | ProblemStatementSyncFullContentRequestEvent
    | ProblemStatementSyncFullContentResponseEvent
    | ProblemStatementSyncUpdateEvent
    | ProblemStatementAwarenessUpdateEvent
    | ExerciseNewVersionAlertEvent
    | ExerciseNewCommitAlertEvent;

/**
 * Relays exercise editor synchronization messages over WebSocket.
 *
 * This service is provided at the root level (singleton) and supports only one exercise
 * connection at a time. The lifecycle is managed by parent components (exercise edit page
 * or code editor page) via {@link connect} and {@link disconnect}. Consumer services
 * (e.g. ExerciseMetadataSyncService, ProblemStatementSyncService) subscribe to the shared
 * Subject via {@link subscribeToUpdates} but do not control the connection lifecycle.
 *
 * Outgoing messages are buffered until the WebSocket connection is established, ensuring
 * that messages sent during a cold page load (before the STOMP handshake completes) are
 * never silently dropped. Messages are flushed in FIFO order once connected.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseEditorSyncService {
    private websocketService = inject(WebsocketService);
    private browserFingerprintService = inject(BrowserFingerprintService);

    private exerciseId?: number;
    private subject: Subject<ExerciseEditorSyncEvent> | undefined;
    private subscription: Subscription | undefined;

    private outgoing$?: Subject<{ topic: string; payload: Record<string, unknown> }>;
    private outgoingSubscription?: Subscription;

    /**
     * Constructs the STOMP destination topic for a given exercise's synchronization channel.
     *
     * @param exerciseId - the ID of the exercise to build the topic path for
     * @returns the fully-qualified STOMP topic string (e.g. `/topic/exercises/42/synchronization`)
     */
    private getTopic(exerciseId: number): string {
        return `/topic/exercises/${exerciseId}/synchronization`;
    }

    /**
     * The unique session identifier for this browser tab/window.
     *
     * Used to filter out messages that originated from this client so that
     * a user's own edits are not echoed back as remote changes.
     *
     * @returns the browser session ID, or `undefined` if the fingerprint service has not yet initialized
     */
    get sessionId(): string | undefined {
        return this.browserFingerprintService.browserSessionId.value;
    }

    /**
     * Establishes the WebSocket connection for synchronizing the given exercise.
     *
     * This method creates the shared Subject, STOMP subscription, and outgoing
     * message buffer. It must be called by the parent component that owns the
     * exercise editing session **before** any consumer services call
     * {@link subscribeToUpdates}.
     *
     * Calling `connect()` for a different exercise will automatically tear down
     * the previous connection first. Calling it for the same exercise is a no-op.
     *
     * @param exerciseId - the ID of the exercise to connect to
     */
    connect(exerciseId: number): void {
        if (this.exerciseId !== undefined && this.exerciseId !== exerciseId) {
            this.disconnect();
        }

        if (!this.subject) {
            const topic = this.getTopic(exerciseId);
            this.exerciseId = exerciseId;
            this.subject = new Subject<ExerciseEditorSyncEvent>();
            this.outgoing$ = new Subject<{ topic: string; payload: Record<string, unknown> }>();
            this.outgoingSubscription = this.outgoing$
                .pipe(
                    concatMap((msg) =>
                        this.websocketService.connectionState.pipe(
                            filter((state) => state.connected),
                            take(1),
                            tap(() => this.websocketService.send(msg.topic, msg.payload)),
                        ),
                    ),
                )
                .subscribe();
            this.subscription = this.websocketService
                .subscribe(topic)
                .pipe(
                    filter((message: ExerciseEditorSyncEvent) => {
                        if (!message.sessionId || !this.sessionId) {
                            return true;
                        }
                        return message.sessionId !== this.sessionId;
                    }),
                )
                .subscribe((message: ExerciseEditorSyncEvent) => this.handleIncomingMessage(message));
        }
    }

    /**
     * Sends a synchronization event to all other editors of the given exercise.
     *
     * The message is enqueued in an internal buffer and flushed to the WebSocket
     * as soon as the STOMP connection is established. If the connection is already
     * active the message is sent immediately. A `timestamp` (epoch ms) and the
     * current `sessionId` are automatically attached to the outgoing payload.
     *
     * @param exerciseId - must match the exercise that is currently connected to
     * @param message    - the synchronization event to broadcast
     * @throws Error if no connection is active, the exercise ID does not match, or
     *               the outgoing buffer has not been initialized
     */
    sendSynchronizationUpdate(exerciseId: number, message: ExerciseEditorSyncEvent): void {
        if (!this.subscription) {
            throw new Error('Cannot send synchronization message: not connected to websocket topic');
        }
        if (this.exerciseId !== exerciseId) {
            throw new Error(`Cannot send synchronization message: exerciseId ${exerciseId} does not match connected exerciseId ${this.exerciseId}`);
        }
        if (!this.outgoing$) {
            throw new Error('Cannot send synchronization message: outgoing message buffer not initialized');
        }
        const topic = this.getTopic(exerciseId);
        this.outgoing$.next({ topic, payload: { ...message, timestamp: message.timestamp ?? Date.now(), sessionId: this.sessionId } });
    }

    /**
     * Returns the shared Observable for synchronization events.
     *
     * Consumer services call this method to observe incoming sync events. The
     * connection must already be established via {@link connect} by the parent
     * component. This method does **not** create the connection — it only
     * returns the existing Subject.
     *
     * @returns an Observable that emits {@link ExerciseEditorSyncEvent}s from other
     *          connected editors. The Observable completes when {@link disconnect}
     *          is called.
     * @throws Error if {@link connect} has not been called yet
     */
    subscribeToUpdates(): Observable<ExerciseEditorSyncEvent> {
        if (!this.subject) {
            throw new Error('Cannot subscribe to updates: not connected. Call connect(exerciseId) first.');
        }
        return this.subject;
    }

    /**
     * Tears down all synchronization state for the currently connected exercise.
     *
     * This method performs a full cleanup in the following order:
     *  1. **Completes** the `Subject` returned by {@link subscribeToUpdates}, signaling
     *     all downstream observers that the stream has ended.
     *  2. **Cancels** the outgoing message buffer — any queued but unsent messages are
     *     discarded and in-flight `concatMap` inner observables are unsubscribed.
     *  3. **Unsubscribes** from the STOMP topic, which also removes the server-side
     *     subscription so that the client stops receiving messages.
     *  4. **Clears** the stored `exerciseId`, allowing a fresh connection via
     *     {@link connect}.
     *
     * Safe to call multiple times; subsequent calls are no-ops.
     * Should be called by the parent component when the exercise editing session
     * ends (e.g. navigating away from the exercise editor).
     */
    disconnect(): void {
        // Complete the Subject to notify all observers that the stream has ended
        if (this.subject) {
            this.subject.complete();
            this.subject = undefined;
        }

        // Tear down the outgoing message buffer — unsubscribe first to cancel
        // any in-flight concatMap inner observable, then complete the subject.
        this.outgoingSubscription?.unsubscribe();
        this.outgoingSubscription = undefined;
        if (this.outgoing$) {
            this.outgoing$.complete();
            this.outgoing$ = undefined;
        }

        // Unsubscribing the RxJS subscription also tears down the underlying STOMP topic subscription.
        if (this.subscription) {
            this.subscription.unsubscribe();
            this.subscription = undefined;
        }
        this.exerciseId = undefined;
    }

    /**
     * Forwards an incoming WebSocket message to the internal Subject so that all
     * observers registered via {@link subscribeToUpdates} receive the event.
     *
     * @param message - the deserialized synchronization event received from the STOMP topic
     */
    private handleIncomingMessage(message: ExerciseEditorSyncEvent) {
        this.subject?.next(message);
    }
}
