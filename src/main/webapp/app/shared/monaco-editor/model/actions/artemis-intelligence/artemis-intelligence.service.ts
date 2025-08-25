import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize } from 'rxjs';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ConsistencyCheckResult, RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence-results';

/**
 * Service providing shared functionality for Artemis Intelligence of the markdown editor.
 * This service is intended to be used by the AI actions of the Monaco editors.
 */
@Injectable({ providedIn: 'root' })
export class ArtemisIntelligenceService {
    public resourceUrl = 'api/nebula';

    private http = inject(HttpClient);
    private websocketService = inject(WebsocketService);

    private isLoadingRewrite = signal<boolean>(false);
    private isLoadingConsistencyCheck = signal<boolean>(false);
    isLoading = computed(() => this.isLoadingRewrite() || this.isLoadingConsistencyCheck());

    /**
     * Triggers the rewriting pipeline via HTTP and subscribes to its WebSocket updates.
     * @param toBeRewritten The text to be rewritten.
     * @param courseId The ID of the course to which the rewritten text belongs.
     * @return Observable that emits the rewritten text when available.
     */
    rewrite(toBeRewritten: string | undefined, courseId: number): Observable<RewriteResult> {
        this.isLoadingRewrite.set(true);
        return this.http
            .post<RewriteResult>(`${this.resourceUrl}/courses/${courseId}/rewrite-text`, {
                toBeRewritten: toBeRewritten,
            })
            .pipe(finalize(() => this.isLoadingRewrite.set(false)));
    }

    faqConsistencyCheck(courseId: number, toBeChecked: string): Observable<ConsistencyCheckResult> {
        this.isLoadingRewrite.set(true);
        return this.http
            .post<ConsistencyCheckResult>(`${this.resourceUrl}/courses/${courseId}/consistency-check`, { toBeChecked: toBeChecked })
            .pipe(finalize(() => this.isLoadingRewrite.set(false)));
    }

    /**
     * Triggers the consistency check pipeline via HTTP and subscribes to its WebSocket updates.
     *
     * @param exerciseId The ID of the exercise to check for consistency.
     * @return Observable that emits the consistency check result when available.
     */
    consistencyCheck(exerciseId: number): Observable<string> {
        this.isLoadingConsistencyCheck.set(true);
        return new Observable<string>((observer) => {
            this.http.post(`api/iris/consistency-check/exercises/${exerciseId}`, null).subscribe({
                next: () => {
                    const websocketTopic = `/user/topic/iris/consistency-check/exercises/${exerciseId}`;
                    this.websocketService.subscribe(websocketTopic);
                    this.websocketService.receive(websocketTopic).subscribe({
                        next: (update: any) => {
                            if (update.result) {
                                observer.next(update.result);
                                observer.complete();
                                this.isLoadingConsistencyCheck.set(false);
                                this.websocketService.unsubscribe(websocketTopic);
                            }
                        },
                        error: (error) => {
                            observer.error(error);
                            this.isLoadingConsistencyCheck.set(false);
                            this.websocketService.unsubscribe(websocketTopic);
                        },
                    });
                },
                error: (error) => {
                    this.isLoadingConsistencyCheck.set(false);
                    observer.error(error);
                },
            });
        });
    }
}
