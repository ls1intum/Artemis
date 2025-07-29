import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { AlertService } from 'app/shared/service/alert.service';
import { RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-result';
import { WebsocketService } from 'app/shared/service/websocket.service';

/**
 * Service providing shared functionality for Artemis Intelligence of the markdown editor.
 * This service is intended to be used by the AI actions of the Monaco editors.
 */
@Injectable({ providedIn: 'root' })
export class ArtemisIntelligenceService {
    public resourceUrl = 'api/iris';
    public hyperionResourceUrl = 'api/hyperion';

    private http = inject(HttpClient);
    private alertService = inject(AlertService);
    private websocketService = inject(WebsocketService);

    private isLoadingRewrite = signal<boolean>(false);
    private isLoadingConsistencyCheck = signal<boolean>(false);
    isLoading = computed(() => this.isLoadingRewrite() || this.isLoadingConsistencyCheck());

    /**
     * Triggers the rewriting pipeline via HTTP and returns the result directly.
     * @param toBeRewritten The text to be rewritten.
     * @param rewritingVariant The variant for rewriting.
     * @param courseId The ID of the course to which the rewritten text belongs.
     * @return Observable that emits the rewritten text when available.
     */
    /**
     * Triggers the rewriting pipeline via HTTP and subscribes to its WebSocket updates.
     * @param toBeRewritten The text to be rewritten.
     * @param rewritingVariant The variant for rewriting.
     * @param courseId The ID of the course to which the rewritten text belongs.
     * @return Observable that emits the rewritten text when available.
     */
    rewrite(toBeRewritten: string | undefined, rewritingVariant: RewritingVariant, courseId: number): Observable<RewriteResult> {
        this.isLoadingRewrite.set(true);

        if (rewritingVariant === RewritingVariant.FAQ) {
            // Use WebSocket approach for FAQ rewriting via Iris
            return new Observable<RewriteResult>((observer) => {
                this.http
                    .post(`${this.resourceUrl}/courses/${courseId}/rewrite-text`, {
                        toBeRewritten: toBeRewritten,
                        variant: rewritingVariant,
                    })
                    .subscribe({
                        next: () => {
                            const websocketTopic = `/user/topic/iris/rewriting/${courseId}`;
                            this.websocketService.subscribe(websocketTopic);

                            this.websocketService.receive(websocketTopic).subscribe({
                                next: (update: any) => {
                                    if (update.result) {
                                        observer.next({
                                            result: update.result || undefined,
                                            inconsistencies: update.inconsistencies || [],
                                            suggestions: update.suggestions || [],
                                            improvement: update.improvement || '',
                                        });
                                        observer.complete();
                                        this.isLoadingRewrite.set(false);
                                        this.websocketService.unsubscribe(websocketTopic);
                                        this.alertService.success('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                                    }
                                },
                                error: (error) => {
                                    observer.error(error);
                                    this.isLoadingRewrite.set(false);
                                    this.websocketService.unsubscribe(websocketTopic);
                                },
                            });
                        },
                        error: (error) => {
                            this.isLoadingRewrite.set(false);
                            observer.error(error);
                        },
                    });
            });
        } else {
            // Use simple HTTP approach for Hyperion rewriting
            const endpoint = `${this.hyperionResourceUrl}/review-and-refine/courses/${courseId}/rewrite-problem-statement`;
            const requestBody = { text: toBeRewritten };

            return this.http.post<RewriteResult>(endpoint, requestBody).pipe(
                tap(() => {
                    this.alertService.success('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                }),
                finalize(() => this.isLoadingRewrite.set(false)),
            );
        }
    }

    /**
     * Triggers the consistency check pipeline via HTTP and returns the result directly.
     *
     * @param exerciseId The ID of the exercise to check for consistency.
     * @return Observable that emits the consistency check result immediately.
     */
    consistencyCheck(exerciseId: number): Observable<string> {
        this.isLoadingConsistencyCheck.set(true);
        return this.http
            .post(`${this.hyperionResourceUrl}/review-and-refine/exercises/${exerciseId}/check-consistency`, null, { responseType: 'text' })
            .pipe(finalize(() => this.isLoadingConsistencyCheck.set(false)));
    }
}
