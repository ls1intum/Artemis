import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

enum RephrasingVariant {
    FAQ = 'FAQ',
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
}

export default RephrasingVariant;

/**
 * Service providing shared functionality for rephrasing context of the markdown editor.
 * This service is intended to be used by components that need to rephrase text of the Monaco editors.
 */

@Injectable({ providedIn: 'root' })
export class RephraseService {
    public resourceUrl = 'api/courses';

    private isLoadingSubject = new BehaviorSubject<boolean>(false);
    isLoading = this.isLoadingSubject.asObservable();

    private http = inject(HttpClient);
    private jhiWebsocketService = inject(JhiWebsocketService);

    /**
     * Triggers the rephrasing pipeline via HTTP and subscribes to its WebSocket updates.
     * @param toBeRephrased The text to be rephrased.
     * @param rephrasingVariant The variant for rephrasing.
     * @param courseId The ID of the course to which the rephrased text belongs.
     * @return Observable that emits the rephrased text when available.
     */
    rephraseMarkdown(toBeRephrased: string, rephrasingVariant: string, courseId: number): Observable<string> {
        this.isLoadingSubject.next(true);
        return new Observable<string>((observer) => {
            this.http
                .post(`${this.resourceUrl}/${courseId}/rephrase-text`, null, {
                    params: {
                        toBeRephrased: toBeRephrased,
                        variant: rephrasingVariant,
                    },
                })
                .subscribe({
                    next: () => {
                        const websocketTopic = `/user/topic/iris/rephrasing/${courseId}`;
                        this.jhiWebsocketService.subscribe(websocketTopic);
                        this.jhiWebsocketService.receive(websocketTopic).subscribe({
                            next: (update: any) => {
                                if (update.result) {
                                    observer.next(update.result);
                                    observer.complete();
                                    this.isLoadingSubject.next(false);
                                    this.jhiWebsocketService.unsubscribe(websocketTopic);
                                }
                            },
                            error: (error) => {
                                console.error('WebSocket Error:', error);
                                observer.error(error);
                                this.isLoadingSubject.next(false);
                                this.jhiWebsocketService.unsubscribe(websocketTopic);
                            },
                        });
                    },
                    error: (error) => {
                        console.error('HTTP Request Error:', error);
                        observer.error(error);
                    },
                });
        });
    }
}
