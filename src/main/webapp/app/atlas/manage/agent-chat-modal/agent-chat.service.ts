import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, timeout } from 'rxjs/operators';
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
}

@Injectable({
    providedIn: 'root',
})
export class AgentChatService {
    private http = inject(HttpClient);
    private translateService = inject(TranslateService);

    sendMessage(message: string, courseId: number, sessionId?: string): Observable<string> {
        const request: AgentChatRequest = {
            message,
            sessionId,
        };

        return this.http.post<AgentChatResponse>(`api/atlas/agent/courses/${courseId}/chat`, request).pipe(
            timeout(30000),
            map((response) => {
                // Return response message regardless of success status
                return response.message || this.translateService.instant('artemisApp.agent.chat.error');
            }),
            catchError(() => {
                // Return translated fallback message on any error
                const fallbackMessage = this.translateService.instant('artemisApp.agent.chat.error');
                return of(fallbackMessage);
            }),
        );
    }
}
