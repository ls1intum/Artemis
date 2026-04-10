import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisMessage, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import { map, tap } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { IrisMessageRequestDTO } from 'app/iris/shared/entities/iris-message-request-dto.model';
import { randomInt } from 'app/shared/util/utils';

export type Response<T> = Observable<HttpResponse<T>>;

/**
 * Provides a set of methods to perform CRUD operations on messages
 */
@Injectable({ providedIn: 'root' })
export class IrisChatHttpService {
    protected httpClient = inject(HttpClient);

    protected apiPrefix: string = 'api/iris';

    /**
     * gets all messages for a session by its id
     * @param {number} sessionId
     * @return {Observable<EntityArrayResponseType>}
     */
    getMessages(sessionId: number): Response<IrisMessage[]> {
        return this.httpClient.get<IrisMessageResponseDTO[]>(`${this.apiPrefix}/sessions/${sessionId}/messages`, { observe: 'response' }).pipe(
            map((response) => {
                const dtos = response.body;
                if (!dtos) return response as unknown as HttpResponse<IrisMessage[]>;

                const messages: IrisMessage[] = dtos.map((dto) => {
                    return Object.assign({}, dto, {
                        sentAt: dto.sentAt ? dayjs(dto.sentAt) : undefined,
                    }) as IrisMessage;
                });

                messages.sort((a, b) => {
                    if (a.sentAt && b.sentAt) {
                        if (a.sentAt === b.sentAt) return 0;
                        return a.sentAt.isBefore(b.sentAt) ? -1 : 1;
                    }
                    return 0;
                });

                return Object.assign({}, response, {
                    body: messages,
                }) as HttpResponse<IrisMessage[]>;
            }),
        );
    }

    /**
     * creates a new message in a session
     * @param sessionId of the session
     * @param request  the message request DTO containing content and optional uncommitted files
     */
    createMessage(sessionId: number, request: IrisMessageRequestDTO): Response<IrisMessageResponseDTO> {
        return this.httpClient.post<IrisMessageResponseDTO>(`${this.apiPrefix}/sessions/${sessionId}/messages`, request, { observe: 'response' });
    }

    /**
     * Creates a new tutor suggestion message in a session
     * @param sessionId of the session
     */
    createTutorSuggestion(sessionId: number): Response<void> {
        return this.httpClient.post<void>(`${this.apiPrefix}/sessions/${sessionId}/tutor-suggestion`, Object.assign({}), { observe: 'response' });
    }

    /**
     * resends a message in a session
     * @param {number} sessionId
     * @param {IrisUserMessage} message
     * @return {Response<IrisMessage>}
     */
    resendMessage(sessionId: number, message: IrisUserMessage): Response<IrisMessageResponseDTO> {
        message.messageDifferentiator = message.messageDifferentiator ?? randomInt();
        return this.httpClient.post<IrisMessageResponseDTO>(`${this.apiPrefix}/sessions/${sessionId}/messages/${message.id}/resend`, null, { observe: 'response' }).pipe(
            tap((response) => {
                if (response.body && response.body.id) {
                    message.id = response.body.id;
                }
            }),
        );
    }

    /**
     * Sets a helpfulness rating for a message
     * @param {number} sessionId of the session of the message that should be rated
     * @param {number} messageId of the message that should be rated
     * @param {boolean} helpful rating of the message
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */
    rateMessage(sessionId: number, messageId: number, helpful: boolean): Response<IrisMessageResponseDTO> {
        return this.httpClient.put<IrisMessageResponseDTO>(`${this.apiPrefix}/sessions/${sessionId}/messages/${messageId}/helpful`, helpful, { observe: 'response' });
    }

    /**
     * Retrieves or creates the current chat session for the given context.
     * @param courseId the course ID
     * @param mode the chat mode (e.g. 'COURSE_CHAT', 'PROGRAMMING_EXERCISE_CHAT')
     * @param entityId optional exercise or lecture ID (omit for COURSE_CHAT)
     */
    getCurrentChatSessionOrCreate(courseId: number, mode: string, entityId?: number): Response<IrisSession> {
        const params = this.buildChatParams(mode, entityId);
        return this.httpClient.post<IrisSession>(`${this.apiPrefix}/chat/${courseId}/sessions/current`, null, { observe: 'response', params });
    }

    /**
     * Creates a new chat session for the given context.
     * @param courseId the course ID
     * @param mode the chat mode
     * @param entityId optional exercise or lecture ID
     */
    createChatSession(courseId: number, mode: string, entityId?: number): Response<IrisSession> {
        const params = this.buildChatParams(mode, entityId);
        return this.httpClient.post<IrisSession>(`${this.apiPrefix}/chat/${courseId}/sessions`, null, { observe: 'response', params });
    }

    /**
     * Retrieves or creates the current tutor suggestion session for the given post.
     * @param postId the post ID
     */
    getCurrentTutorSuggestionSessionOrCreate(postId: number): Response<IrisSession> {
        return this.httpClient.post<IrisSession>(`${this.apiPrefix}/tutor-suggestion/${postId}/sessions/current`, null, { observe: 'response' });
    }

    /**
     * Creates a new tutor suggestion session for the given post.
     * @param postId the post ID
     */
    createTutorSuggestionSession(postId: number): Response<IrisSession> {
        return this.httpClient.post<IrisSession>(`${this.apiPrefix}/tutor-suggestion/${postId}/sessions`, null, { observe: 'response' });
    }

    getChatSessions(courseId: number): Observable<IrisSessionDTO[]> {
        return this.httpClient.get<IrisSessionDTO[]>(`${this.apiPrefix}/chat/${courseId}/sessions/overview`).pipe();
    }

    getChatSessionById(courseId: number, sessionId: number): Observable<IrisSession> {
        return this.httpClient.get<IrisSession>(`${this.apiPrefix}/chat/${courseId}/session/${sessionId}`).pipe();
    }

    /**
     * Gets the session and message count for the current user.
     * @return Observable of the count response
     */
    getSessionAndMessageCount(): Observable<{ sessions: number; messages: number }> {
        return this.httpClient
            .get<{ sessions?: number; messages?: number }>(`${this.apiPrefix}/chat/sessions/count`)
            .pipe(map((counts) => ({ sessions: counts.sessions ?? 0, messages: counts.messages ?? 0 })));
    }

    /**
     * Deletes all Iris chat sessions for the current user.
     * @return Observable of the HTTP response
     */
    deleteAllSessions(): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(`${this.apiPrefix}/chat/sessions`, { observe: 'response' });
    }

    /**
     * Deletes a single Iris chat session by its ID.
     * @param sessionId the ID of the session to delete
     * @return Observable of the HTTP response
     */
    deleteSession(sessionId: number): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(`${this.apiPrefix}/chat/sessions/${sessionId}`, { observe: 'response' });
    }

    private buildChatParams(mode: string, entityId?: number): HttpParams {
        let params = new HttpParams().set('mode', mode);
        if (entityId !== undefined) {
            params = params.set('entityId', entityId);
        }
        return params;
    }
}
