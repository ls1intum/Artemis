import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisMessageContentDTO } from 'app/iris/shared/entities/iris-message-content-dto.model';
import { IrisMessageRequestDTO } from 'app/iris/shared/entities/iris-message-request-dto.model';
import { IrisSender, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';
import { IrisChatSessionResponse } from 'app/openapi/model/iris-chat-session-response';
import { IrisMessageResponse } from 'app/openapi/model/iris-message-response';

describe('IrisChatHttpService', () => {
    let service: IrisChatHttpService;
    let http: HttpTestingController;
    const session: IrisChatSessionResponse = { id: 2, userId: 4, entityId: 7, mode: 'COURSE_CHAT', creationDate: '2026-09-20T12:00:00Z' };
    const message: IrisMessageResponse = { id: 3, sender: 'USER', content: [{ type: 'text', textContent: 'Hello Iris' }] };
    const handoff = { courseId: 7, question: 'What is a signal?', answer: 'A reactive value.' };

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(IrisChatHttpService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    function request(method: string, path: string): TestRequest {
        return http.expectOne((req) => req.method === method && req.urlWithParams.split('?')[0] === '/api/iris' + path);
    }

    function queryParams(req: TestRequest): Record<string, string> {
        return Object.fromEntries(new URL(req.request.urlWithParams, 'https://artemis.example').searchParams);
    }

    it('sends message content, lecture context and uncommitted files and preserves the created response', async () => {
        const body: IrisMessageRequestDTO = {
            content: [IrisMessageContentDTO.text('Hello Iris')],
            pendingContext: { mode: ChatServiceMode.LECTURE, entityId: 7 },
            context: [
                { type: 'video', lectureUnitId: 8, timestamp: 0 },
                { type: 'slides', lectureUnitId: 8, page: 2 },
                { type: 'combinedView', slides: { type: 'slides', lectureUnitId: 8, page: 2 } },
            ],
            uncommittedFiles: { 'src/Main.java': 'class Main {}' },
        };
        const result = firstValueFrom(service.createMessage(2, body));
        const req = request('POST', '/sessions/2/messages');
        const location = '/api/iris/sessions/2/messages/3';
        expect(req.request.body).toEqual(body);
        req.flush(message, { status: 201, statusText: 'Created', headers: { Location: location } });

        const response = await result;
        expect(response).toBeInstanceOf(HttpResponse);
        expect(response.status).toBe(201);
        expect(response.headers.get('Location')).toBe(location);
        expect(response.body).toMatchObject(message);
    });

    it('resends with the client ID and updates the original message', async () => {
        const original: IrisUserMessage = { id: 3, sender: IrisSender.USER, content: [] };
        const result = firstValueFrom(service.resendMessage(2, original, 'client + /'));
        const req = request('POST', '/sessions/2/messages/3/resend');
        expect(req.request.body).toBeNull();
        expect(queryParams(req)).toEqual({ clientId: 'client + /' });
        expect(original.messageDifferentiator).toEqual(expect.any(Number));
        req.flush({ ...message, id: 9 });
        expect((await result).body?.id).toBe(9);
        expect(original.id).toBe(9);
    });

    it.each([true, false])('sends helpful=%s as a bare boolean body', async (helpful) => {
        const result = firstValueFrom(service.rateMessage(2, 3, helpful));
        const req = request('PUT', '/sessions/2/messages/3/helpful');
        expect(req.request.body).toBe(helpful);
        req.flush({ ...message, helpful });
        expect((await result).body?.helpful).toBe(helpful);
    });

    it('saves MCQ selection as an object and returns the bodyless HTTP response', async () => {
        const body = { selectedIndex: 0, submitted: false, questionIndex: 1 };
        const result = firstValueFrom(service.saveMcqResponse(2, 3, body));
        const req = request('PUT', '/sessions/2/messages/3/mcq-response');
        expect(req.request.body).toEqual(body);
        req.flush(null, { status: 200, statusText: 'OK' });
        expect(await result).toMatchObject({ status: 200, body: null });
    });

    it('requests a tutor suggestion with no body and returns the 201 response', async () => {
        const result = firstValueFrom(service.createTutorSuggestion(2));
        const req = request('POST', '/sessions/2/tutor-suggestion');
        expect(req.request.body).toBeNull();
        req.flush(null, { status: 201, statusText: 'Created' });
        expect(await result).toMatchObject({ status: 201, body: null });
    });

    it('creates a course session using the course query parameter', async () => {
        const result = firstValueFrom(service.createCourseSession(7));
        const req = request('POST', '/chat/sessions');
        expect(queryParams(req)).toEqual({ courseId: '7' });
        expect(req.request.body).toBeNull();
        req.flush(session, { status: 201, statusText: 'Created' });
        expect(await result).toMatchObject({ status: 201, body: { id: 2, creationDate: session.creationDate } });
    });

    it('requests the current chat session using its mode and entity ID', async () => {
        const result = firstValueFrom(service.getCurrentSessionOrCreateIfNotExists(ChatServiceMode.PROGRAMMING_EXERCISE, 7));
        const req = request('POST', '/chat/sessions/current');
        expect(queryParams(req)).toEqual({ mode: 'PROGRAMMING_EXERCISE_CHAT', entityId: '7' });
        expect(req.request.body).toBeNull();
        req.flush(session);
        expect(await result).toMatchObject({ status: 200, body: { id: 2 } });
    });

    it('uses the tutor suggestion endpoint for tutor sessions', async () => {
        const result = firstValueFrom(service.getCurrentSessionOrCreateIfNotExists(ChatServiceMode.TUTOR_SUGGESTION, 7));
        const req = request('POST', '/tutor-suggestion/posts/7/sessions/current');
        expect(req.request.body).toBeNull();
        req.flush({ id: 2, userId: 4, entityId: 7, creationDate: session.creationDate }, { status: 201, statusText: 'Created' });
        expect(await result).toMatchObject({ status: 201, body: { id: 2, mode: undefined } });
    });

    it('returns the handoff session body', async () => {
        const result = firstValueFrom(service.createSessionFromGlobalSearch(handoff));
        const req = request('POST', '/chat/sessions/global-search-handoff');
        expect(req.request.body).toEqual(handoff);
        req.flush(session, { status: 201, statusText: 'Created' });
        expect(await result).toMatchObject(session);
    });

    it('reports a successful handoff response without a session', async () => {
        const result = firstValueFrom(service.createSessionFromGlobalSearch(handoff));
        request('POST', '/chat/sessions/global-search-handoff').flush(null, { status: 201, statusText: 'Created' });
        await expect(result).rejects.toThrow('Iris global search handoff returned no session');
    });

    it('reads the canonical overview and session details', async () => {
        const overview = firstValueFrom(service.getChatSessions(7));
        const summary = { id: 2, entityId: 7, mode: 'COURSE_CHAT', creationDate: session.creationDate };
        request('GET', '/chat/courses/7/sessions/overview').flush([summary]);
        expect(await overview).toMatchObject([summary]);

        const details = firstValueFrom(service.getChatSessionById(7, 2));
        request('GET', '/chat/courses/7/sessions/2').flush({ ...session, messages: [message] });
        expect(await details).toMatchObject({ ...session, messages: [message] });
    });

    it('preserves zero counts', async () => {
        const result = firstValueFrom(service.getSessionAndMessageCount());
        request('GET', '/chat/sessions/count').flush({ sessions: 1, messages: 0 });
        expect(await result).toEqual({ sessions: 1, messages: 0 });
    });

    it('returns bodyless 204 responses for single and bulk deletes', async () => {
        const single = firstValueFrom(service.deleteSession(2));
        request('DELETE', '/chat/sessions/2').flush(null, { status: 204, statusText: 'No Content' });
        expect(await single).toBeInstanceOf(HttpResponse);
        expect(await single).toMatchObject({ status: 204, body: null });

        const all = firstValueFrom(service.deleteAllSessions());
        request('DELETE', '/chat/sessions').flush(null, { status: 204, statusText: 'No Content' });
        expect(await all).toBeInstanceOf(HttpResponse);
        expect(await all).toMatchObject({ status: 204, body: null });
    });
});
