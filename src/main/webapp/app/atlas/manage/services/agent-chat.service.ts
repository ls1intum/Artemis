import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, timeout } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyRelationType, CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';

interface AgentChatRequest {
    message: string;
}

export interface CompetencyPreviewResponse {
    title: string;
    description: string;
    taxonomy: CompetencyTaxonomy;
    icon: string;
    competencyId?: number;
    viewOnly?: boolean;
}

export interface CompetencyRelationPreviewResponse {
    relationId?: number;
    tailCompetencyId: number;
    tailCompetencyTitle: string;
    headCompetencyId: number;
    headCompetencyTitle: string;
    relationType: CompetencyRelationType;
    viewOnly?: boolean;
}

export interface AgentChatResponse {
    message: string;
    timestamp: string;
    competenciesModified: boolean;
    competencyPreviews?: CompetencyPreviewResponse[];
    relationPreviews?: CompetencyRelationPreviewResponse[];
}

export interface AgentHistoryMessage {
    content: string;
    isUser: boolean;
    competencyPreviews?: CompetencyPreviewResponse[];
    relationPreviews?: CompetencyRelationPreviewResponse[];
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
                    message: this.translateService.instant('artemisApp.agent.chat.error'),
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
