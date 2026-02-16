import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { concatMap, filter, take, tap } from 'rxjs/operators';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

export enum ExerciseEditorSyncTarget {
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
}

export enum ExerciseEditorSyncEventType {
    PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST = 'PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST',
    PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE = 'PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE',
    PROBLEM_STATEMENT_SYNC_UPDATE = 'PROBLEM_STATEMENT_SYNC_UPDATE',
    PROBLEM_STATEMENT_AWARENESS_UPDATE = 'PROBLEM_STATEMENT_AWARENESS_UPDATE',
}

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

export type ExerciseEditorSyncEvent =
    | ProblemStatementSyncFullContentRequestEvent
    | ProblemStatementSyncFullContentResponseEvent
    | ProblemStatementSyncUpdateEvent
    | ProblemStatementAwarenessUpdateEvent;

/**
 * Relays exercise editor synchronization messages over WebSocket.
 *
 * This service is provided at the root level (singleton) and supports only one exercise
 * subscription at a time. Calling `subscribeToUpdates()` for a different exercise will
 * silently complete the previous Subject and unsubscribe existing observers. This is by
 * design — the consuming component is always destroyed and recreated on navigation.
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
     * Sends a synchronization event to all other editors of the given exercise.
     *
     * The message is enqueued in an internal buffer and flushed to the WebSocket
     * as soon as the STOMP connection is established. If the connection is already
     * active the message is sent immediately. A `timestamp` (epoch ms) and the
     * current `sessionId` are automatically attached to the outgoing payload.
     *
     * @param exerciseId - must match the exercise that is currently subscribed to
     * @param message    - the synchronization event to broadcast
     * @throws Error if no subscription is active, the exercise ID does not match, or
     *               the outgoing buffer has not been initialized
     */
    sendSynchronizationUpdate(exerciseId: number, message: ExerciseEditorSyncEvent): void {
        if (!this.subscription) {
            throw new Error('Cannot send synchronization message: not subscribed to websocket topic');
        }
        if (this.exerciseId !== exerciseId) {
            throw new Error(`Cannot send synchronization message: exerciseId ${exerciseId} does not match subscribed exerciseId ${this.exerciseId}`);
        }
        if (!this.outgoing$) {
            throw new Error('Cannot send synchronization message: outgoing message buffer not initialized');
        }
        const topic = this.getTopic(exerciseId);
        this.outgoing$.next({ topic, payload: { ...message, timestamp: message.timestamp ?? Date.now(), sessionId: this.sessionId } });
    }

    /**
     * Subscribes to real-time synchronization events for the given exercise.
     *
     * On the first call (or after a previous {@link unsubscribe}), this method:
     *  1. Creates a new `Subject` that downstream consumers observe.
     *  2. Opens a STOMP subscription on the exercise's synchronization topic.
     *  3. Initializes an outgoing message buffer that holds messages until the
     *     WebSocket connection is ready, then flushes them in FIFO order.
     *
     * Subsequent calls with the **same** `exerciseId` return the existing Subject
     * without creating a new subscription (idempotent). Calling with a **different**
     * `exerciseId` automatically tears down the previous subscription first.
     *
     * Incoming messages whose `sessionId` matches this client's own session are
     * filtered out so that a user never receives their own edits as remote changes.
     *
     * @param exerciseId - the ID of the exercise to subscribe to
     * @returns an Observable that emits {@link ExerciseEditorSyncEvent}s from other
     *          connected editors. The Observable completes when {@link unsubscribe}
     *          is called or when a new exercise subscription replaces it.
     */
    subscribeToUpdates(exerciseId: number): Observable<ExerciseEditorSyncEvent> {
        if (this.exerciseId !== undefined && this.exerciseId !== exerciseId) {
            this.unsubscribe();
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
                .pipe(filter((message: ExerciseEditorSyncEvent) => message.sessionId !== this.sessionId))
                .subscribe((message: ExerciseEditorSyncEvent) => this.handleIncomingMessage(message));
        }
        return this.subject;
    }

    /**
     * Tears down all synchronization state for the currently subscribed exercise.
     *
     * This method performs a full cleanup in the following order:
     *  1. **Completes** the `Subject` returned by {@link subscribeToUpdates}, signaling
     *     all downstream observers that the stream has ended.
     *  2. **Cancels** the outgoing message buffer — any queued but unsent messages are
     *     discarded and in-flight `concatMap` inner observables are unsubscribed.
     *  3. **Unsubscribes** from the STOMP topic, which also removes the server-side
     *     subscription so that the client stops receiving messages.
     *  4. **Clears** the stored `exerciseId`, allowing a fresh subscription via
     *     {@link subscribeToUpdates}.
     *
     * Safe to call multiple times; subsequent calls are no-ops.
     * Should be called when the consuming component is destroyed (e.g. navigating
     * away from the exercise editor).
     */
    unsubscribe(): void {
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
