import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, timeout } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { CompetencyPreview, CompetencyRelationPreview, RelationGraphPreview } from 'app/atlas/shared/entities/chat-message.model';

interface AgentChatRequest {
    message: string;
    sessionId?: string;
}

export interface AgentChatResponse {
    message: string;
    sessionId?: string;
    timestamp: string;
    success: boolean;
    competenciesModified: boolean;
    competencyPreview?: SingleCompetencyPreviewResponse;
    batchCompetencyPreview?: BatchCompetencyPreviewResponse;
    relationPreview?: SingleRelationPreviewResponse;
    batchRelationPreview?: BatchRelationPreviewResponse;
    relationGraphPreview?: RelationGraphPreview;
}

interface SingleCompetencyPreviewResponse {
    preview: boolean;
    competency: CompetencyPreview;
    competencyId?: number;
    viewOnly?: boolean;
}

interface BatchCompetencyPreviewResponse {
    batchPreview: boolean;
    count: number;
    competencies: CompetencyPreview[];
    viewOnly?: boolean;
}

interface SingleRelationPreviewResponse {
    preview: boolean;
    relation: CompetencyRelationPreview;
    viewOnly?: boolean;
}

interface BatchRelationPreviewResponse {
    batchPreview: boolean;
    count: number;
    relations: CompetencyRelationPreview[];
    viewOnly?: boolean;
}

export interface AgentHistoryMessage {
    content: string;
    isUser: boolean;
    competencyPreview?: SingleCompetencyPreviewResponse;
    batchCompetencyPreview?: BatchCompetencyPreviewResponse;
    relationPreview?: SingleRelationPreviewResponse;
    batchRelationPreview?: BatchRelationPreviewResponse;
    relationGraphPreview?: RelationGraphPreview;
}

@Injectable({
    providedIn: 'root',
})
export class AgentChatService {
    private readonly http = inject(HttpClient);
    private readonly translateService = inject(TranslateService);
    private readonly accountService = inject(AccountService);

    /**
     * Generates a unique session ID for the user's conversation in a specific course.
     * Format: course_{courseId}_user_{userId}
     */
    getSessionId(courseId: number): string {
        const userId = this.accountService.userIdentity()?.id;
        if (userId == undefined) {
            throw new Error(this.translateService.instant('artemisApp.agent.chat.authentication'));
        }
        return `course_${courseId}_user_${userId}`;
    }

    sendMessage(message: string, courseId: number): Observable<AgentChatResponse> {
        try {
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
        } catch (error) {
            return of({
                message: this.translateService.instant('artemisApp.agent.chat.authentication'),
                sessionId: '',
                timestamp: new Date().toISOString(),
                success: false,
                competenciesModified: false,
            });
        }
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
