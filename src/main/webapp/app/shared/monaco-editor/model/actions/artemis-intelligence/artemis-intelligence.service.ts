import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { AlertService } from 'app/core/util/alert.service';

/**
 * Service providing shared functionality for Artemis Intelligence of the markdown editor.
 * This service is intended to be used by the AI actions of the Monaco editors.
 */
@Injectable({ providedIn: 'root' })
export class ArtemisIntelligenceService {
    public resourceUrl = 'api';

    private http = inject(HttpClient);
    private jhiWebsocketService = inject(JhiWebsocketService);
    private alertService = inject(AlertService);

    private isLoadingRewrite = signal(false);
    private isLoadingConsistencyCheck = signal(false);
    isLoading = computed(() => this.isLoadingRewrite() || this.isLoadingConsistencyCheck);

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
                .post(`${this.resourceUrl}/courses/${courseId}/rewrite-text`, null, {
                    params: {
                        toBeRewritten: toBeRewritten,
                        variant: rewritingVariant,
                    },
                })
                .subscribe({
                    next: () => {
                        const websocketTopic = `/user/topic/iris/rewriting/${courseId}`;
                        this.jhiWebsocketService.subscribe(websocketTopic);
                        this.jhiWebsocketService.receive(websocketTopic).subscribe({
                            next: (update: any) => {
                                if (update.result) {
                                    observer.next(update.result);
                                    observer.complete();
                                    this.isLoadingRewrite.set(false);
                                    this.jhiWebsocketService.unsubscribe(websocketTopic);
                                    this.alertService.success('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                                }
                            },
                            error: (error) => {
                                observer.error(error);
                                this.isLoadingRewrite.set(false);
                                this.jhiWebsocketService.unsubscribe(websocketTopic);
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

    consistencyCheck(toBeChecked: string, exerciseId: number): Observable<string> {
        this.isLoadingConsistencyCheck.set(true);
        return new Observable<string>((observer) => {
            this.http
                .post(`${this.resourceUrl}/exercises/${exerciseId}/consistency-check`, null, {
                    params: {
                        toBeChecked: toBeChecked,
                    },
                })
                .subscribe({
                    next: () => {
                        const websocketTopic = `/user/topic/iris/consistency-check/${exerciseId}`;
                        this.jhiWebsocketService.subscribe(websocketTopic);
                        this.jhiWebsocketService.receive(websocketTopic).subscribe({
                            next: (update: any) => {
                                if (update.result) {
                                    observer.next(update.result);
                                    observer.complete();
                                    this.isLoadingConsistencyCheck.set(false);
                                    this.jhiWebsocketService.unsubscribe(websocketTopic);
                                }
                            },
                            error: (error) => {
                                observer.error(error);
                                this.isLoadingConsistencyCheck.set(false);
                                this.jhiWebsocketService.unsubscribe(websocketTopic);
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
