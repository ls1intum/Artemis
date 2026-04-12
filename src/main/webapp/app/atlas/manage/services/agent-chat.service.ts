import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, timeout } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { AgentChatResponse, AgentHistoryMessage } from 'app/atlas/shared/entities/chat-message.model';

export type { AgentChatResponse, AgentHistoryMessage, CompetencyPreviewResponse, CompetencyRelationPreviewResponse } from 'app/atlas/shared/entities/chat-message.model';

interface AgentChatRequest {
    message: string;
}

@Injectable({
    providedIn: 'root',
})
export class AgentChatService {
    private readonly http = inject(HttpClient);
    private readonly translateService = inject(TranslateService);

    sendMessage(message: string, courseId: number): Observable<AgentChatResponse> {
        const request: AgentChatRequest = {
            message,
        };

        return this.http.post<AgentChatResponse>(`api/atlas/agent/courses/${courseId}/chat`, request).pipe(
            timeout(30000),
            catchError(() => {
                return of({
                    message: this.translateService.instant('artemisApp.agent.chat.error.general'),
                    timestamp: new Date().toISOString(),
                    competenciesModified: false,
                });
            }),
        );
    }

    /**
     * Fetches conversation history for the current user in the specified course
     */
    getConversationHistory(courseId: number): Observable<AgentHistoryMessage[]> {
        return this.http.get<AgentHistoryMessage[]>(`api/atlas/agent/courses/${courseId}/chat/history`).pipe(
            catchError(() => {
                return of([]);
            }),
        );
    }
}
