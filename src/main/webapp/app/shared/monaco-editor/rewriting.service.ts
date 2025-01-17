import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import RewritingVariant from 'app/shared/monaco-editor/model/rewriting-variant';
import { AlertService } from 'app/core/util/alert.service';

/**
 * Service providing shared functionality for rewriting context of the markdown editor.
 * This service is intended to be used by components that need to rewrite text of the Monaco editors.
 */
@Injectable({ providedIn: 'root' })
export class RewritingService {
    public resourceUrl = 'api/courses';

    isLoadingSignal = signal<boolean>(false);

    private http = inject(HttpClient);
    private jhiWebsocketService = inject(JhiWebsocketService);
    private alertService = inject(AlertService);

    /**
     * Triggers the rewriting pipeline via HTTP and subscribes to its WebSocket updates.
     * @param toBeRewritten The text to be rewritten.
     * @param rewritingVariant The variant for rewriting.
     * @param courseId The ID of the course to which the rewritten text belongs.
     * @return Observable that emits the rewritten text when available.
     */
    rewritteMarkdown(toBeRewritten: string, rewritingVariant: RewritingVariant, courseId: number): Observable<string> {
        this.isLoadingSignal.set(true);
        return new Observable<string>((observer) => {
            this.http
                .post(`${this.resourceUrl}/${courseId}/rewrite-text`, null, {
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
                                    this.isLoadingSignal.set(false);
                                    this.jhiWebsocketService.unsubscribe(websocketTopic);
                                    this.alertService.success('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                                }
                            },
                            error: (error) => {
                                observer.error(error);
                                this.isLoadingSignal.set(false);
                                this.jhiWebsocketService.unsubscribe(websocketTopic);
                            },
                        });
                    },
                    error: (error) => {
                        observer.error(error);
                    },
                });
        });
    }
}
