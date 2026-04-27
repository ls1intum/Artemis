import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { WebsocketService } from 'app/shared/service/websocket.service';

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
     * The Observable completes after the final result message (`isThinking: false`).
     * `switchMap` in the caller guarantees the previous subscription is torn down before the next starts.
     */
    ask(query: string, limit = 5): Observable<IrisSearchStatusUpdate> {
        return new Observable<IrisSearchStatusUpdate>((subscriber) => {
            // 1. Subscribe to the WebSocket channel first so we never miss the thinking callback.
            const wsSubscription = this.websocketService.subscribe<IrisSearchStatusUpdate>(LECTURE_SEARCH_WS_CHANNEL).subscribe({
                next: (update) => {
                    subscriber.next(update);
                    if (!update.isThinking) {
                        // Final result received — complete the observable.
                        subscriber.complete();
                    }
                },
                error: (err) => subscriber.error(err),
            });

            // 2. Fire the HTTP request. Artemis returns 202; results arrive via WebSocket.
            const httpSubscription = this.http.post('api/iris/search-answer', { query, limit }, { observe: 'response' }).subscribe({
                error: (err) => subscriber.error(err),
            });

            // Teardown: unsubscribe from both when the outer Observable is cancelled or completed.
            return () => {
                wsSubscription.unsubscribe();
                httpSubscription.unsubscribe();
            };
        });
    }
}
