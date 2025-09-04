import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

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

    sendMessage(message: string, courseId: number): Observable<string> {
        const request: AgentChatRequest = {
            message,
            sessionId: `course_${courseId}_session_${Date.now()}`,
        };

        return this.http
            .post<AgentChatResponse>(`api/atlas/agent/courses/${courseId}/chat`, request)
            .pipe(map((response) => (response.success ? response.message : 'Sorry, I encountered an error processing your request.')));
    }
}
