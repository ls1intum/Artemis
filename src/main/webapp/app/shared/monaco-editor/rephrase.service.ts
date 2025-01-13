import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
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
    private readonly topicPrefix = '/topic/iris/rephrasing/';

    private http = inject(HttpClient);
    private jhiWebsocketService = inject(JhiWebsocketService);

    /**
     * Triggers the rephrasing pipeline via HTTP and subscribes to its WebSocket updates.
     * @param toBeRephrased The text to be rephrased.
     * @param rephrasingVariant The variant for rephrasing.
     * @return Observable that emits the rephrased text when available.
     */
    rephraseMarkdown(toBeRephrased: string, rephrasingVariant: string): Observable<string> {
        const courseId = 1;

        return new Observable<string>((observer) => {
            this.http
                .get(`${this.resourceUrl}/${courseId}/rephrase-text`, {
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
                                    console.log('Rephrased text:', update.result);
                                    observer.next(update.result); // Gib das Ergebnis an den Subscriber weiter
                                    observer.complete(); // Beende das Observable
                                }
                            },
                            error: (error) => {
                                console.error('WebSocket Error:', error);
                                observer.error(error); // Fehler weitergeben
                            },
                        });
                    },
                    error: (error) => {
                        console.error('HTTP Request Error:', error);
                        observer.error(error); // Fehler weitergeben
                    },
                });
        });
    }
}
