import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { WebsocketService } from 'app/shared/service/websocket.service';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { AlertService } from 'app/shared/service/alert.service';

/**
 * Service providing shared functionality for Artemis Intelligence of the markdown editor.
 * This service is intended to be used by the AI actions of the Monaco editors.
 */
@Injectable({ providedIn: 'root' })
export class ArtemisIntelligenceService {
    public resourceUrl = 'api/iris';

    private http = inject(HttpClient);
    private websocketService = inject(WebsocketService);
    private alertService = inject(AlertService);

    private isLoadingRewrite = signal<boolean>(false);
    private isLoadingConsistencyCheck = signal<boolean>(false);
    isLoading = computed(() => this.isLoadingRewrite() || this.isLoadingConsistencyCheck());

    /**
     * Triggers the rewriting pipeline via HTTP and subscribes to its WebSocket updates.
     * @param toBeRewritten The text to be rewritten.
     * @param rewritingVariant The variant for rewriting.
     * @param courseId The ID of the course to which the rewritten text belongs.
     * @return Observable that emits the rewritten text when available.
     */
    rewrite(toBeRewritten: string, rewritingVariant: RewritingVariant, courseId: number): Observable<string> {
        this.isLoadingRewrite.set(true);
        return new Observable<string>((observer) => {
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
                                    observer.next(update.result);
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
            this.http.post(`${this.resourceUrl}/consistency-check/exercises/${exerciseId}`, null).subscribe({
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
