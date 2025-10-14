import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, timeout } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';

interface AgentChatRequest {
    message: string;
    sessionId?: string;
}

interface AgentChatResponse {
    message: string;
    sessionId?: string;
    timestamp: string;
    success: boolean;
    competenciesModified?: boolean;
}

@Injectable({
    providedIn: 'root',
})
export class AgentChatService {
    private http = inject(HttpClient);
    private translateService = inject(TranslateService);

    sendMessage(message: string, courseId: number, sessionId?: string): Observable<AgentChatResponse> {
        const request: AgentChatRequest = {
            message,
            sessionId,
        };

        return this.http.post<AgentChatResponse>(`api/atlas/agent/courses/${courseId}/chat`, request).pipe(
            timeout(30000),
            catchError(() => {
                // Return error response on failure
                return of({
                    message: this.translateService.instant('artemisApp.agent.chat.error'),
                    sessionId,
                    timestamp: new Date().toISOString(),
                    success: false,
                    competenciesModified: false,
                });
            }),
        );
    }
}
