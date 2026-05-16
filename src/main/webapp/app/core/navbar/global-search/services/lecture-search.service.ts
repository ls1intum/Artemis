import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, filter, timeout } from 'rxjs';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { WebsocketService } from 'app/shared/service/websocket.service';

/** Maximum time (ms) to wait for a WebSocket response before the Observable errors. */
const LECTURE_SEARCH_WS_TIMEOUT_MS = 30_000;

/** STOMP channel on which Artemis pushes lecture-search status updates for the current user. */
const LECTURE_SEARCH_WS_CHANNEL = '/user/topic/iris/lecture-search';

@Injectable({
    providedIn: 'root',
})
export class LectureSearchService {
    private readonly http = inject(HttpClient);
    private readonly websocketService = inject(WebsocketService);

    search(query: string, limit = 10): Observable<LectureSearchResult[]> {
        return this.http.post<LectureSearchResult[]>('api/iris/lecture-search', { query, limit });
    }

    /**
     * Fires an async ask-Iris request and returns a multi-emit Observable:
     *
     * 1. `{ isThinking: true }`  — Pyris classified the query as a real question; show thinking animation.
     * 2. `{ isThinking: false, answer, sources }` — LLM finished; show or hide the answer card.
     *
     * The WebSocket subscription is set up BEFORE the HTTP request to avoid missing the early
     * "thinking" callback (~2 ms after Pyris receives the request).
     *
     * WebSocket messages are filtered by runId to discard stale results from a previous run
     * that finished after the user already changed their query.
     *
     * The Observable completes after the final result message (`isThinking: false`).
     * `switchMap` in the caller guarantees the previous subscription is torn down before the next starts.
     */
    ask(query: string, limit = 5): Observable<IrisSearchStatusUpdate> {
        return new Observable<IrisSearchStatusUpdate>((subscriber) => {
            // Generate the correlation ID client-side so it's known before the HTTP call.
            // The server registers this ID as the Hazelcast job token; WebSocket callbacks echo it back.
            const runId = window.crypto.randomUUID();

            // 1. Subscribe to the WebSocket channel first so we never miss the thinking callback.
            //    The filter is definite from the start — no undefined fallback needed.
            const wsSubscription = this.websocketService
                .subscribe<IrisSearchStatusUpdate>(LECTURE_SEARCH_WS_CHANNEL)
                .pipe(
                    filter((update) => update.runId === runId),
                    timeout(LECTURE_SEARCH_WS_TIMEOUT_MS),
                )
                .subscribe({
                    next: (update) => {
                        subscriber.next(update);
                        if (!update.isThinking) {
                            subscriber.complete();
                        }
                    },
                    error: (err) => subscriber.error(err),
                });

            // 2. Fire the HTTP request with the client-generated runId. Server returns 202 with no body.
            const httpSubscription = this.http.post('api/iris/search-answer', { query, limit, runId }).subscribe({
                error: (err) => subscriber.error(err),
            });

            return () => {
                wsSubscription.unsubscribe();
                httpSubscription.unsubscribe();
            };
        });
    }
}
