import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, timeout } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';

interface AgentChatRequest {
    message: string;
    sessionId?: string;
}

interface AgentChatResponse {
    message: string;
    sessionId?: string;
    timestamp: string;
    success: boolean;
    competenciesModified: boolean;
}

export interface AgentHistoryMessage {
    content: string;
    isUser: boolean;
}

@Injectable({
    providedIn: 'root',
})
export class AgentChatService {
    private http = inject(HttpClient);
    private translateService = inject(TranslateService);
    private accountService = inject(AccountService);

    /**
     * Generates a unique session ID for the user's conversation in a specific course.
     * Format: course_{courseId}_user_{userId}
     */
    getSessionId(courseId: number): string {
        const userId = this.accountService.userIdentity?.id;
        if (!userId) {
            throw new Error('User must be authenticated to use agent chat');
        }
        return `course_${courseId}_user_${userId}`;
    }

    sendMessage(message: string, courseId: number): Observable<AgentChatResponse> {
        const sessionId = this.getSessionId(courseId);
        const request: AgentChatRequest = {
            message,
            sessionId,
        };

        return this.http.post<AgentChatResponse>(`api/atlas/agent/courses/${courseId}/chat`, request).pipe(
            timeout(30000),
            catchError(() => {
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
