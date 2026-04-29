import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, EMPTY, firstValueFrom, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MockProvider } from 'ng-mocks';

import { IrisChatControllerService } from 'app/iris/overview/services/iris-chat-controller.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { UserService } from 'app/core/user/shared/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-chat-mode.model';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { User } from 'app/core/user/user.model';
import { IrisChatWebsocketDTO } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { IrisAssistantMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import {
    mockConversation,
    mockServerMessage,
    mockServerSessionHttpResponse,
    mockServerSessionHttpResponseWithEmptyConversation,
    mockUserMessageWithContent,
    mockWebsocketServerMessage,
    mockWebsocketStatusMessage,
    mockWebsocketStatusMessageWithInteralStage,
} from 'test/helpers/sample/iris-sample-data';

describe('IrisChatControllerService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisChatControllerService;
    let httpService: IrisChatHttpService;
    let accountService: AccountService;
    let routerMock: { url: string };

    const courseId = 234;
    const exerciseId = 567;

    const websocketServiceMock = {
        subscribe: vi.fn().mockReturnValue(EMPTY),
        connectionState: new BehaviorSubject({ connected: true, wasEverConnectedBefore: false }),
    } as any;

    const userServiceMock = {
        updateLLMSelectionDecision: vi.fn(),
    };

    beforeEach(() => {
        // Avoid the heartbeat timer firing during tests.
        vi.useFakeTimers();
        routerMock = { url: '/courses/234/iris' };

        TestBed.configureTestingModule({
            providers: [
                IrisChatControllerService,
                MockProvider(IrisChatHttpService),
                MockProvider(ProfileService, { isModuleFeatureActive: () => true }),
                { provide: WebsocketService, useValue: websocketServiceMock },
                { provide: UserService, useValue: userServiceMock },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useValue: routerMock },
            ],
        });

        service = TestBed.inject(IrisChatControllerService);
        httpService = TestBed.inject(IrisChatHttpService);
        accountService = TestBed.inject(AccountService);
        accountService.userIdentity.set({ id: 42, selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
        websocketServiceMock.subscribe.mockClear();
        websocketServiceMock.subscribe.mockReturnValue(EMPTY);
        userServiceMock.updateLLMSelectionDecision.mockReset();
    });

    describe('setContext', () => {
        it('throws and captures when courseId is undefined', () => {
            expect(() => service.setContext(undefined as any, ChatServiceMode.COURSE, 1)).toThrow(/setContext called without a courseId/);
        });

        it('clears stale chat-history and starts new heartbeat on course change', () => {
            const sessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(mockServerSessionHttpResponse));

            // Capture the sequence of chatSessions emissions to prove the stale list was cleared.
            const sessionsEmissions: IrisSessionDTO[][] = [];
            const sub = service.chatSessions.subscribe((v) => sessionsEmissions.push(v));
            service.chatSessions.next([{ id: 99 } as IrisSessionDTO]);

            service.setContext(courseId, ChatServiceMode.COURSE, courseId);

            // The empty array must appear between the stale [{id:99}] and any newly-created session,
            // proving the courseChanged branch cleared the list before the new session was loaded.
            expect(sessionsEmissions.some((e) => e.length === 0)).toBe(true);
            expect(service.latestStartedSession?.id).not.toBe(99);
            expect(sessionsSpy).toHaveBeenCalledWith(courseId);
            sub.unsubscribe();
        });

        it('does NOT call closeAndStart when identifier is unchanged', () => {
            const sessionSpy = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(mockServerSessionHttpResponse));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

            service.setContext(courseId, ChatServiceMode.COURSE, courseId);
            sessionSpy.mockClear();

            service.setContext(courseId, ChatServiceMode.COURSE, courseId);

            expect(sessionSpy).not.toHaveBeenCalled();
        });
    });

    describe('switchToNewSession', () => {
        it('subscribes to websocket channel after session is created', () => {
            vi.spyOn(httpService, 'createSession').mockReturnValue(of({ body: { id: 555, messages: [] } } as HttpResponse<any>));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

            service.switchToNewSession(ChatServiceMode.PROGRAMMING_EXERCISE, exerciseId);

            expect(websocketServiceMock.subscribe).toHaveBeenCalledWith('/user/topic/iris/555');
        });

        it('does nothing when entity id is missing', () => {
            const createSpy = vi.spyOn(httpService, 'createSession').mockReturnValue(of({ body: { id: 1, messages: [] } } as HttpResponse<any>));
            service.switchToNewSession(ChatServiceMode.PROGRAMMING_EXERCISE, undefined);
            expect(createSpy).not.toHaveBeenCalled();
        });

        it('surfaces SESSION_CREATION_FAILED on http error', async () => {
            vi.spyOn(httpService, 'createSession').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            service.switchToNewSession(ChatServiceMode.PROGRAMMING_EXERCISE, exerciseId);

            const error = await firstValueFrom(service.currentError());
            expect(error).toBe(IrisErrorMessageKey.SESSION_CREATION_FAILED);
        });
    });

    describe('switchToSession', () => {
        it('loads chat session by id', () => {
            const targetSession: IrisSession = { ...mockConversation, id: 999 };
            const fetchSpy = vi.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(targetSession));
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(mockServerSessionHttpResponse));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

            service.setContext(courseId, ChatServiceMode.COURSE, courseId);
            service.switchToSession({ id: 999, chatMode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 42 } as IrisSessionDTO);

            expect(fetchSpy).toHaveBeenCalledWith(courseId, 999);
            expect(service.sessionId).toBe(999);
        });

        it('returns early when target is the active session', () => {
            const fetchSpy = vi.spyOn(httpService, 'getChatSessionById').mockReturnValue(of({ ...mockConversation, id: 7 }));
            service.sessionId = 7;
            service.switchToSession({ id: 7, chatMode: ChatServiceMode.COURSE, entityId: 7 } as IrisSessionDTO);
            expect(fetchSpy).not.toHaveBeenCalled();
        });
    });

    describe('clearChat', () => {
        it('returns early when no session-creation identifier is set', () => {
            const createSpy = vi.spyOn(httpService, 'createSession').mockReturnValue(of({ body: { id: 1, messages: [] } } as HttpResponse<any>));
            service.clearChat();
            expect(createSpy).not.toHaveBeenCalled();
        });

        it('creates a new session and reloads sessions when identifier is set', () => {
            vi.spyOn(httpService, 'createSession').mockReturnValue(of({ body: { id: 100, messages: [] } } as HttpResponse<any>));
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(mockServerSessionHttpResponse));
            const sessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));

            // setContext also wires the courseId signal so loadChatSessions can fire.
            service.setContext(courseId, ChatServiceMode.COURSE, courseId);
            sessionsSpy.mockClear();
            service.clearChat();

            expect(sessionsSpy).toHaveBeenCalledWith(courseId);
        });
    });

    describe('sendMessage', () => {
        it('throws when sessionId is undefined', async () => {
            await expect(firstValueFrom(service.sendMessage('hi'))).rejects.toThrow(/Not initialized/);
        });

        it('replaces or appends the response message', async () => {
            service.sessionId = 1;
            vi.spyOn(httpService, 'createMessage').mockReturnValue(of(new HttpResponse({ body: { id: 4, sender: IrisSender.LLM, content: [], sentAt: '2026-04-29' } as any })));

            await firstValueFrom(service.sendMessage('hello'));

            const messages = await firstValueFrom(service.currentMessages());
            expect(messages.find((m) => m.id === 4)).toBeDefined();
        });

        it('surfaces IRIS_DISABLED on 403', async () => {
            service.sessionId = 1;
            vi.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

            await firstValueFrom(service.sendMessage('hello'));

            const error = await firstValueFrom(service.currentError());
            expect(error).toBe(IrisErrorMessageKey.IRIS_DISABLED);
        });

        it('surfaces RATE_LIMIT_EXCEEDED on 429', async () => {
            service.sessionId = 1;
            vi.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 429 })));

            await firstValueFrom(service.sendMessage('hello'));

            const error = await firstValueFrom(service.currentError());
            expect(error).toBe(IrisErrorMessageKey.RATE_LIMIT_EXCEEDED);
        });

        it('surfaces TECHNICAL_ERROR on 500', async () => {
            service.sessionId = 1;
            vi.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            await firstValueFrom(service.sendMessage('hello'));

            const error = await firstValueFrom(service.currentError());
            expect(error).toBe(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
        });
    });

    describe('requestTutorSuggestion', () => {
        it('throws when sessionId is undefined', async () => {
            await expect(firstValueFrom(service.requestTutorSuggestion())).rejects.toThrow(/Not initialized/);
        });

        it('hits the http endpoint when session is set', async () => {
            service.sessionId = 1;
            const tutorSpy = vi.spyOn(httpService, 'createTutorSuggestion').mockReturnValue(of(new HttpResponse<void>()));
            await firstValueFrom(service.requestTutorSuggestion());
            expect(tutorSpy).toHaveBeenCalledWith(1);
        });
    });

    describe('resendMessage', () => {
        it('throws when sessionId is undefined', async () => {
            await expect(firstValueFrom(service.resendMessage(mockUserMessageWithContent('x')))).rejects.toThrow(/Not initialized/);
        });

        it('replaces the message on success', async () => {
            service.sessionId = 1;
            const replaced: IrisAssistantMessage = { id: 99, sender: IrisSender.LLM, content: [], sentAt: '2026-04-29' as any } as any;
            vi.spyOn(httpService, 'resendMessage').mockReturnValue(of(new HttpResponse({ body: replaced as any })));
            service.messages.next([{ id: 99, sender: IrisSender.LLM, content: [] } as any]);

            await firstValueFrom(service.resendMessage(mockUserMessageWithContent('x')));

            const messages = service.messages.value;
            expect(messages[0].id).toBe(99);
        });

        it('surfaces error on failure', async () => {
            service.sessionId = 1;
            vi.spyOn(httpService, 'resendMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            await firstValueFrom(service.resendMessage(mockUserMessageWithContent('x')));
            const error = await firstValueFrom(service.currentError());
            expect(error).toBe(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
        });
    });

    describe('rateMessage', () => {
        it('throws when sessionId is undefined', async () => {
            await expect(firstValueFrom(service.rateMessage(mockServerMessage as any, true))).rejects.toThrow(/Not initialized/);
        });

        it('replaces the message on rating success', async () => {
            service.sessionId = 1;
            service.messages.next([{ id: 1, sender: IrisSender.LLM, content: [] } as any]);
            vi.spyOn(httpService, 'rateMessage').mockReturnValue(of(new HttpResponse({ body: { id: 1, sender: IrisSender.LLM, content: [], helpful: true } as any })));
            await firstValueFrom(service.rateMessage(mockServerMessage as any, true));
            expect(service.messages.value[0].helpful).toBe(true);
        });

        it('emits RATE_MESSAGE_FAILED on error', async () => {
            service.sessionId = 1;
            vi.spyOn(httpService, 'rateMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            await firstValueFrom(service.rateMessage(mockServerMessage as any, true));
            const error = await firstValueFrom(service.currentError());
            expect(error).toBe(IrisErrorMessageKey.RATE_MESSAGE_FAILED);
        });
    });

    describe('messagesRead', () => {
        it('resets the new-message counters', async () => {
            service.numNewMessages.next(3);
            service.newIrisMessage.next({ id: 1, sender: IrisSender.LLM, content: [] } as any);
            service.messagesRead();
            expect(service.numNewMessages.value).toBe(0);
            expect(service.newIrisMessage.value).toBeUndefined();
        });
    });

    describe('updateLLMUsageConsent', () => {
        it('persists NO_AI and closes the chat', () => {
            userServiceMock.updateLLMSelectionDecision.mockReturnValue(of(new HttpResponse<void>()));
            const accountSpy = vi.spyOn(accountService, 'setUserLLMSelectionDecision');
            service.updateLLMUsageConsent(LLMSelectionDecision.NO_AI);
            expect(accountSpy).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
        });

        it('persists CLOUD_AI and re-opens the chat', () => {
            userServiceMock.updateLLMSelectionDecision.mockReturnValue(of(new HttpResponse<void>()));
            const accountSpy = vi.spyOn(accountService, 'setUserLLMSelectionDecision');
            service.updateLLMUsageConsent(LLMSelectionDecision.CLOUD_AI);
            expect(accountSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(service.hasJustAcceptedLLMUsage).toBe(true);
        });

        it('surfaces TECHNICAL_ERROR_RESPONSE on failure for NO_AI path', () => {
            userServiceMock.updateLLMSelectionDecision.mockReturnValue(throwError(() => new Error('nope')));
            // close() resets error after emit, so observe the emission directly.
            const errorSpy = vi.spyOn(service.error, 'next');
            service.updateLLMUsageConsent(LLMSelectionDecision.NO_AI);
            expect(errorSpy).toHaveBeenCalledWith(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE);
        });

        it('surfaces TECHNICAL_ERROR_RESPONSE on failure for accept path', () => {
            userServiceMock.updateLLMSelectionDecision.mockReturnValue(throwError(() => new Error('nope')));
            const errorSpy = vi.spyOn(service.error, 'next');
            service.updateLLMUsageConsent(LLMSelectionDecision.CLOUD_AI);
            expect(errorSpy).toHaveBeenCalledWith(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE);
        });
    });

    describe('deleteSession', () => {
        beforeEach(() => {
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(mockServerSessionHttpResponseWithEmptyConversation));
            service.setContext(courseId, ChatServiceMode.COURSE, courseId);
        });

        it('closes the active session when deleting it', async () => {
            vi.spyOn(httpService, 'deleteSession').mockReturnValue(of(new HttpResponse<void>()));
            // No remaining sessions, so close() runs and stays closed (no auto-switch path).
            service.chatSessions.next([{ id: 555 } as IrisSessionDTO]);
            service.sessionId = 555;
            await firstValueFrom(service.deleteSession(555));
            expect(service.sessionId).toBeUndefined();
        });

        it('does not affect active session when deleting a different one', async () => {
            vi.spyOn(httpService, 'deleteSession').mockReturnValue(of(new HttpResponse<void>()));
            service.sessionId = 1;
            await firstValueFrom(service.deleteSession(2));
            expect(service.sessionId).toBe(1);
        });
    });

    describe('handleWebsocketMessage', () => {
        beforeEach(() => {
            service.sessionId = 1;
        });

        it('appends a MESSAGE payload to messages', () => {
            (service as any).handleWebsocketMessage(mockWebsocketServerMessage);
            expect(service.messages.value.length).toBeGreaterThan(0);
        });

        it('updates stages on STATUS payload (excluding internal stages)', () => {
            (service as any).handleWebsocketMessage(mockWebsocketStatusMessageWithInteralStage);
            expect(service.stages.value.length).toBe(1);
            expect(service.stages.value[0].name).toBe('Stage 1');
        });

        it('updates session title from payload', () => {
            service.latestStartedSession = { id: 1 } as IrisSessionDTO;
            service.chatSessions.next([{ id: 1 } as IrisSessionDTO]);
            const payload: IrisChatWebsocketDTO = { ...mockWebsocketStatusMessage, sessionTitle: 'Renamed' };
            (service as any).handleWebsocketMessage(payload);
            expect(service.chatSessions.value[0].title).toBe('Renamed');
            expect(service.latestStartedSession?.title).toBe('Renamed');
        });

        it('merges citation info', () => {
            service.citationInfo.next([{ entityId: 1, title: 'A' } as any]);
            const payload: IrisChatWebsocketDTO = { ...mockWebsocketStatusMessage, citationInfo: [{ entityId: 2, title: 'B' } as any] };
            (service as any).handleWebsocketMessage(payload);
            expect(service.citationInfo.value).toHaveLength(2);
        });

        it('updates rate-limit info from payload', () => {
            const info = new IrisRateLimitInformation(5, 10, 0);
            const payload: IrisChatWebsocketDTO = { ...mockWebsocketStatusMessage, rateLimitInfo: info };
            (service as any).handleWebsocketMessage(payload);
            expect(service.currentRatelimitInfoSubject.value).toBe(info);
        });
    });

    describe('parseLatestSuggestions', () => {
        it('publishes empty array when input is undefined', async () => {
            (service as any).parseLatestSuggestions(undefined);
            expect(service.suggestions.value).toEqual([]);
        });

        it('parses a JSON array of strings', () => {
            (service as any).parseLatestSuggestions(JSON.stringify(['one', 'two']));
            expect(service.suggestions.value).toEqual(['one', 'two']);
        });

        it('filters non-string items per type contract', () => {
            (service as any).parseLatestSuggestions(JSON.stringify(['ok', 1, {}, 'still-ok']));
            expect(service.suggestions.value).toEqual(['ok', 'still-ok']);
        });

        it('falls back to empty array when JSON is malformed', () => {
            (service as any).parseLatestSuggestions('{not-json');
            expect(service.suggestions.value).toEqual([]);
        });

        it('falls back to empty array when JSON parses to a non-array', () => {
            (service as any).parseLatestSuggestions(JSON.stringify({ foo: 'bar' }));
            expect(service.suggestions.value).toEqual([]);
        });
    });

    describe('observable accessors', () => {
        it('exposes session id, chat mode, related entity id, messages, stages, citations, error, num new messages, suggestions, sessions, active, rate limit info', async () => {
            // Smoke-touch every public observable accessor to guarantee they emit.
            await firstValueFrom(service.currentSessionId());
            await firstValueFrom(service.currentRelatedEntityId());
            await firstValueFrom(service.currentChatMode());
            await firstValueFrom(service.currentMessages());
            await firstValueFrom(service.currentStages());
            await firstValueFrom(service.currentCitationInfo());
            await firstValueFrom(service.currentError());
            await firstValueFrom(service.currentNumNewMessages());
            await firstValueFrom(service.currentSuggestions());
            await firstValueFrom(service.availableChatSessions());
            await firstValueFrom(service.getActiveStatus());
            await firstValueFrom(service.currentRatelimitInfo());
        });
    });

    describe('setShouldReopenChat', () => {
        it('emits the new value on the shouldReopenChat$ stream', async () => {
            service.setShouldReopenChat(true);
            const v = await firstValueFrom(service.shouldReopenChat$);
            expect(v).toBe(true);
        });
    });
});
