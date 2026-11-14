import { Service, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Observable, defer, throwError } from 'rxjs';
import { IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import { map, tap } from 'rxjs/operators';
import { McqResponseData } from 'app/iris/shared/entities/iris-content-type.model';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { IrisGlobalSearchHandoffDTO } from 'app/iris/shared/entities/iris-global-search-handoff-dto.model';
import { IrisMessageRequestDTO } from 'app/iris/shared/entities/iris-message-request-dto.model';
import { randomInt } from 'app/foundation/util/utils';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';
import { IrisChatSessionApi } from 'app/openapi/api/iris-chat-session-api';
import { IrisMessageApi } from 'app/openapi/api/iris-message-api';
import { IrisTutorSuggestionSessionApi } from 'app/openapi/api/iris-tutor-suggestion-session-api';
import { mapChatMessage, mapChatSession, mapChatSessionSummary, mapResponseBody, toPendingContext } from 'app/iris/overview/services/iris-chat-http.mapper';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

export type Response<T> = Observable<HttpResponse<T>>;

/**
 * Provides a set of methods to perform CRUD operations on messages
 */
@Service()
export class IrisChatHttpService {
    private readonly sessions = inject(IrisChatSessionApi);
    private readonly messages = inject(IrisMessageApi);
    private readonly tutorSessions = inject(IrisTutorSuggestionSessionApi);

    /**
     * creates a new message in a session
     * @param sessionId of the session
     * @param request   the message request DTO containing content, optional uncommitted files and optional pending context
     */
    createMessage(sessionId: number, request: IrisMessageRequestDTO): Response<IrisMessageResponseDTO> {
        return defer(() => {
            const body = cloneWith(request, { pendingContext: toPendingContext(request.pendingContext) });
            return this.messages.createMessage(sessionId, body);
        }).pipe(map((response) => mapResponseBody(response, mapChatMessage)));
    }

    /**
     * Creates a new tutor suggestion message in a session
     * @param sessionId of the session
     */
    createTutorSuggestion(sessionId: number): Response<void> {
        return this.messages.sendTutorSuggestionMessage(sessionId);
    }

    /**
     * resends a message in a session
     * @param {number} sessionId
     * @param {IrisUserMessage} message
     * @return {Response<IrisMessage>}
     */
    resendMessage(sessionId: number, message: IrisUserMessage, clientId: string): Response<IrisMessageResponseDTO> {
        message.messageDifferentiator = message.messageDifferentiator ?? randomInt();
        const messageId = message.id;
        if (messageId === undefined) {
            return throwError(() => new Error('Cannot resend an Iris message without an ID'));
        }
        return this.messages.resendMessage(sessionId, messageId, clientId).pipe(
            tap((response) => {
                if (response.body?.id) {
                    message.id = response.body.id;
                }
            }),
            map((response) => mapResponseBody(response, mapChatMessage)),
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
        return this.messages.rateMessage(sessionId, messageId, helpful).pipe(map((response) => mapResponseBody(response, mapChatMessage)));
    }

    /**
     * Saves the user's MCQ answer selection for persistence across page reloads
     * @param sessionId of the session
     * @param messageId of the message containing the MCQ
     * @param response the user's answer selection
     */
    saveMcqResponse(sessionId: number, messageId: number, response: McqResponseData): Response<void> {
        return this.messages.saveMcqResponse(sessionId, messageId, response);
    }

    getCurrentSessionOrCreateIfNotExists(mode: ChatServiceMode, entityId: number): Response<IrisSession> {
        const session$ =
            mode === ChatServiceMode.TUTOR_SUGGESTION
                ? this.tutorSessions.getCurrentSessionOrCreateIfNotExists(entityId)
                : this.sessions.getCurrentSessionOrCreateIfNotExists(mode, entityId);
        return session$.pipe(map((response) => mapResponseBody(response, mapChatSession)));
    }

    /**
     * Creates a new (empty) course chat session ("New Chat"). Every new session is a course session;
     * exercise/lecture context is layered on later via a context switch.
     */
    createCourseSession(courseId: number): Response<IrisSession> {
        return this.sessions.createCourseSession(courseId).pipe(map((response) => mapResponseBody(response, mapChatSession)));
    }

    /**
     * Continues a global search answer in a new course chat that already holds the question and the answer.
     * @param handoff the course, the optional lecture or exercise, and the question and answer to carry over
     * @return the new session, already on the requested lecture or exercise
     */
    createSessionFromGlobalSearch(handoff: IrisGlobalSearchHandoffDTO): Observable<IrisSession> {
        return defer(() => this.sessions.createSessionFromGlobalSearch(cloneWith(handoff, { context: toPendingContext(handoff.context) }))).pipe(
            map(({ body }) => {
                if (body === null) {
                    throw new Error('Iris global search handoff returned no session');
                }
                return mapChatSession(body);
            }),
        );
    }

    getChatSessions(courseId: number): Observable<IrisSessionDTO[]> {
        return this.sessions.getAllSessionsForCourse(courseId).pipe(map((sessions) => sessions.map(mapChatSessionSummary)));
    }

    getChatSessionById(courseId: number, sessionId: number): Observable<IrisSession> {
        return this.sessions.getSessionById(courseId, sessionId).pipe(map(mapChatSession));
    }

    /**
     * Gets the session and message count for the current user.
     * @return Observable of the count response
     */
    getSessionAndMessageCount(): Observable<{ sessions: number; messages: number }> {
        return this.sessions.getSessionAndMessageCount().pipe(map(({ sessions, messages }) => ({ sessions, messages })));
    }

    /**
     * Deletes all Iris chat sessions for the current user.
     * @return Observable of the HTTP response
     */
    deleteAllSessions(): Observable<HttpResponse<void>> {
        return this.sessions.deleteAllSessionsForCurrentUser();
    }

    /**
     * Deletes a single Iris chat session by its ID.
     * @param sessionId the ID of the session to delete
     * @return Observable of the HTTP response
     */
    deleteSession(sessionId: number): Observable<HttpResponse<void>> {
        return this.sessions.deleteSession(sessionId);
    }
}
