import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { Observable, filter, firstValueFrom, of, throwError } from 'rxjs';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { UserService } from 'app/core/user/shared/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import {
    mockClientMessage,
    mockConversation,
    mockConversationWithNoMessages,
    mockServerMessage,
    mockServerMessage2,
    mockServerSessionHttpResponse,
    mockServerSessionHttpResponseWithEmptyConversation,
    mockServerSessionHttpResponseWithId,
    mockUserMessageWithContent,
    mockWebsocketServerMessage,
    mockWebsocketStatusMessage,
    mockWebsocketStatusMessageWithInteralStage,
} from 'test/helpers/sample/iris-sample-data';
import { IrisMessage, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import 'app/shared/util/array.extension';
import { Router } from '@angular/router';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { IrisChatWebsocketPayloadType } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

describe('IrisChatService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisChatService;
    let httpService: IrisChatHttpService;
    let wsMock: IrisWebsocketService;
    let routerMock: { url: string };
    let accountService: AccountService;

    const id = 123;
    const courseId = 234;

    const statusMock = {
        currentRatelimitInfo: vi.fn().mockReturnValue(of({})),
        handleRateLimitInfo: vi.fn(),
        setCurrentCourse: vi.fn(),
    };
    const userMock = {
        acceptExternalLLMUsage: vi.fn(),
    };

    const waitForSessionId = () => firstValueFrom(service.currentSessionId().pipe(filter((value): value is number => value !== undefined)));

    const waitForSessionIdValue = (expectedId: number) => firstValueFrom(service.currentSessionId().pipe(filter((value): value is number => value === expectedId)));

    beforeEach(() => {
        routerMock = { url: '' };

        TestBed.configureTestingModule({
            providers: [
                IrisChatService,
                MockProvider(IrisChatHttpService),
                MockProvider(IrisWebsocketService),
                { provide: IrisStatusService, useValue: statusMock },
                { provide: UserService, useValue: userMock },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useValue: routerMock },
            ],
        });

        service = TestBed.inject(IrisChatService);
        httpService = TestBed.inject(IrisChatHttpService);
        wsMock = TestBed.inject(IrisWebsocketService);
        accountService = TestBed.inject(AccountService);

        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);

        service.setCourseId(courseId);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should change to an course chat and start new session', async () => {
        const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.COURSE, id);

        expect(httpStub).toHaveBeenCalledWith('course-chat/' + id);
        expect(wsStub).toHaveBeenCalledWith(id);
    });

    it('should change to tutor chat and start new session', async () => {
        const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id);

        expect(httpStub).toHaveBeenCalledWith('programming-exercise-chat/' + id);
        expect(wsStub).toHaveBeenCalledWith(id);
    });

    it('should send a message', async () => {
        const message = 'test message';
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const createdMessage = mockUserMessageWithContent(message);
        const stub = vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisUserMessage>));
        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();
        await firstValueFrom(service.sendMessage(message));

        expect(stub).toHaveBeenCalledWith(id, expect.anything());
        const messages = await firstValueFrom(service.currentMessages());
        expect(messages).toHaveLength(mockConversation.messages!.length + 1);
        expect(messages.last()).toEqual(createdMessage);
    });

    it('should handle error when sending a message', async () => {
        const message = 'test message';
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        const stub = vi.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();
        await firstValueFrom(service.sendMessage(message));

        expect(stub).toHaveBeenCalledWith(id, expect.anything());
        const error = await firstValueFrom(service.currentError());
        expect(error).toEqual(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
    });

    it('should load existing messages on session creation', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(httpService, 'createSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2)));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();
        const messages = await firstValueFrom(service.currentMessages());
        expect(messages).toHaveLength(mockConversation.messages!.length);
    });

    it('should clear chat', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(httpService, 'createSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2, true)));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();
        service.clearChat();
        await waitForSessionIdValue(2);
        const messages = await firstValueFrom(service.currentMessages());
        expect(messages).toHaveLength(mockConversationWithNoMessages.messages!.length);
    });

    it('should rate a message', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const message = mockServerMessage;
        const updatedMessage = Object.assign({}, message, { helpful: true });
        vi.spyOn(httpService, 'rateMessage').mockReturnValueOnce(of({ body: updatedMessage } as HttpResponse<IrisMessage>));
        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();
        await firstValueFrom(service.rateMessage(message, true));

        expect(httpService.rateMessage).toHaveBeenCalledWith(id, message.id, true);
    });

    it('should resend a message', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        const message = mockUserMessageWithContent('resend message');
        message.id = mockClientMessage.id;
        vi.spyOn(httpService, 'resendMessage').mockReturnValueOnce(of({ body: message } as HttpResponse<IrisMessage>));

        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();
        await firstValueFrom(service.resendMessage(message));

        expect(httpService.resendMessage).toHaveBeenCalledWith(mockConversation.id, message);
        const messages = await firstValueFrom(service.currentMessages());
        expect(messages).toHaveLength(mockConversation.messages!.length);
        expect(messages.first()).toEqual(message);
    });

    it('should handle error when rate limited', async () => {
        const message = 'test message';
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        const stub = vi.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 429 })));

        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();
        await firstValueFrom(service.sendMessage(message));

        expect(stub).toHaveBeenCalledWith(id, expect.anything());
        const error = await firstValueFrom(service.currentError());
        expect(error).toEqual(IrisErrorMessageKey.RATE_LIMIT_EXCEEDED);
    });

    it('should handle error when iris is disabled', async () => {
        const message = 'test message';
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        const stub = vi.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();
        await firstValueFrom(service.sendMessage(message));

        expect(stub).toHaveBeenCalledWith(id, expect.anything());
        const error = await firstValueFrom(service.currentError());
        expect(error).toEqual(IrisErrorMessageKey.IRIS_DISABLED);
    });

    it('should handle websocket status message', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id);
        await waitForSessionId();
        const stages = await firstValueFrom(service.currentStages());
        expect(stages).toEqual(mockWebsocketStatusMessage.stages);
    });

    it('should handle websocket status message with internal stages', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessageWithInteralStage));
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id);
        await waitForSessionId();
        const stages = await firstValueFrom(service.currentStages());
        expect(stages).toEqual(mockWebsocketStatusMessageWithInteralStage.stages?.filter((stage: IrisStageDTO) => !stage.internal));
    });

    it('should update session title from websocket STATUS payload', async () => {
        const myTitle = 'My new session title';
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([{ id, creationDate: new Date(), chatMode: ChatServiceMode.COURSE, entityId: 1 } as IrisSessionDTO]));

        const wsPayloadWithTitle = {
            type: IrisChatWebsocketPayloadType.STATUS,
            stages: [],
            sessionTitle: myTitle,
        };
        const wsSpy = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(
            new Observable((subscriber) => {
                setTimeout(() => {
                    subscriber.next(wsPayloadWithTitle);
                    subscriber.complete();
                }, 0);
            }),
        );
        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();

        expect(wsSpy).toHaveBeenCalledWith(id);

        // Wait for the async setTimeout in the Observable
        await new Promise((resolve) => setTimeout(resolve, 10));

        const sessions = await firstValueFrom(service.availableChatSessions());
        const current = sessions.find((s) => s.id === id);
        expect(current?.title).toBe(myTitle);
    });

    it('should handle websocket message', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        const message = mockServerMessage2;
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id);
        await waitForSessionId();
        const messages = await firstValueFrom(service.currentMessages());
        expect(messages).toHaveLength(mockConversation.messages!.length + 1);
        expect(messages.last()).toEqual(message);
    });

    it('should emit sessionId when set', async () => {
        const expectedId = 456;
        service.sessionId = expectedId;
        const sessionId = await firstValueFrom(service.currentSessionId());
        expect(sessionId).toBe(expectedId);
    });

    it('should request tutor suggestion if sessionId is set', async () => {
        service.sessionId = id;
        const httpStub = vi.spyOn(httpService, 'createTutorSuggestion').mockReturnValueOnce(of(new HttpResponse<void>()));

        const res = await firstValueFrom(service.requestTutorSuggestion());
        expect(res).toBeUndefined();

        expect(httpStub).toHaveBeenCalledWith(id);
    });

    it('should throw error if sessionId is undefined on tutor suggestion', async () => {
        service.sessionId = undefined;
        await expect(firstValueFrom(service.requestTutorSuggestion())).rejects.toThrow('Not initialized');
    });

    describe('switchToSession', () => {
        it('should not switch if session id is the same', () => {
            const closeSpy = vi.spyOn(service as any, 'close');
            vi.spyOn(httpService, 'getChatSessionById').mockReturnValue(of());
            const session = { id: id } as IrisSessionDTO;
            service.sessionId = id;

            service.switchToSession(session);

            expect(closeSpy).not.toHaveBeenCalled();
        });

        it('should switch to a different session if llm usage is accepted', async () => {
            const newSession = { ...mockConversation, id: 456, chatMode: ChatServiceMode.COURSE, entityName: 'Course 1' };

            const closeSpy = vi.spyOn(service as any, 'close');
            vi.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSession));

            const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());

            service.sessionId = id;

            service.switchToSession(newSession);

            // Wait for async operations
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(closeSpy).toHaveBeenCalled();
            const messages = await firstValueFrom(service.currentMessages());
            expect(messages).toEqual(newSession.messages);
            expect(wsStub).toHaveBeenCalledWith(newSession.id);
        });

        it('should switch if LLM usage is not required for the mode', async () => {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
            service['hasJustAcceptedLLMUsage'] = false;
            service['sessionCreationIdentifier'] = 'tutor-suggestion/1';

            const newSession = { id: 12, chatMode: ChatServiceMode.TUTOR_SUGGESTION, creationDate: new Date(), entityId: 1 } as IrisSessionDTO;

            const closeSpy = vi.spyOn(service as any, 'close');
            const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSession));

            service.sessionId = id;

            service.switchToSession(newSession);

            // Wait for async operations
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(closeSpy).toHaveBeenCalled();
            expect(wsStub).toHaveBeenCalledWith(newSession.id);
        });

        it('should switch if user has just accepted LLM usage', async () => {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
            service['hasJustAcceptedLLMUsage'] = true;
            service['sessionCreationIdentifier'] = 'course/1';

            const newSession = { id: 12, chatMode: ChatServiceMode.COURSE, creationDate: new Date(), entityId: 1 } as IrisSessionDTO;

            const closeSpy = vi.spyOn(service as any, 'close');
            const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSession));

            service.sessionId = id;

            service.switchToSession(newSession);

            // Wait for async operations
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(closeSpy).toHaveBeenCalled();
            expect(wsStub).toHaveBeenCalledWith(newSession.id);
        });
    });

    describe('loadChatSessions', () => {
        it('should load chat sessions and update the behavior subject', () => {
            const sessions = [{ id: 1 }, { id: 2 }] as IrisSessionDTO[];
            const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of(sessions));
            const nextSpy = vi.spyOn(service.chatSessions, 'next');

            service['loadChatSessions']();

            expect(getChatSessionsSpy).toHaveBeenCalledWith(courseId);
            expect(nextSpy).toHaveBeenCalledWith(sessions);
        });

        it('should handle an empty array of sessions', () => {
            const sessions: IrisSessionDTO[] = [];
            const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of(sessions));
            const nextSpy = vi.spyOn(service.chatSessions, 'next');

            service['loadChatSessions']();

            expect(getChatSessionsSpy).toHaveBeenCalledWith(courseId);
            expect(nextSpy).toHaveBeenCalledWith([]);
        });

        it('should handle an invalid response from the server', () => {
            const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of(null as any));
            const nextSpy = vi.spyOn(service.chatSessions, 'next');

            service['loadChatSessions']();

            expect(getChatSessionsSpy).toHaveBeenCalledWith(courseId);
            expect(nextSpy).toHaveBeenCalledWith([]);
        });
    });

    describe('deleteSession', () => {
        it('should delete a non-active session and remove it from the list', async () => {
            const sessions = [
                { id: 1, creationDate: new Date(), chatMode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO,
                { id: 2, creationDate: new Date(), chatMode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO,
            ];
            service.chatSessions.next(sessions);
            service.sessionId = 1;

            vi.spyOn(httpService, 'deleteSession').mockReturnValue(of(new HttpResponse<void>({ status: 204 })));
            const closeSpy = vi.spyOn(service as any, 'close');

            await firstValueFrom(service.deleteSession(2));

            const remaining = service.chatSessions.getValue();
            expect(remaining).toHaveLength(1);
            expect(remaining[0].id).toBe(1);
            expect(closeSpy).not.toHaveBeenCalled();
        });

        it('should delete the active session and switch to the next available session', async () => {
            const sessions = [
                { id: 1, creationDate: new Date(), chatMode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO,
                { id: 2, creationDate: new Date(), chatMode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO,
            ];
            service.chatSessions.next(sessions);
            service.sessionId = 1;

            vi.spyOn(httpService, 'deleteSession').mockReturnValue(of(new HttpResponse<void>({ status: 204 })));
            // switchToSession internally calls getChatSessionById, so we need to mock it
            vi.spyOn(httpService, 'getChatSessionById').mockReturnValue(of({ ...mockConversation, id: 2 }));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            const switchSpy = vi.spyOn(service, 'switchToSession');

            await firstValueFrom(service.deleteSession(1));

            expect(switchSpy).toHaveBeenCalledWith(expect.objectContaining({ id: 2 }));
        });

        it('should delete the last remaining session and stay in closed state', async () => {
            const sessions = [{ id: 1, creationDate: new Date(), chatMode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO];
            service.chatSessions.next(sessions);
            service.sessionId = 1;

            vi.spyOn(httpService, 'deleteSession').mockReturnValue(of(new HttpResponse<void>({ status: 204 })));
            const clearChatSpy = vi.spyOn(service, 'clearChat');
            const switchSpy = vi.spyOn(service, 'switchToSession');

            await firstValueFrom(service.deleteSession(1));

            expect(clearChatSpy).not.toHaveBeenCalled();
            expect(switchSpy).not.toHaveBeenCalled();
            expect(service.chatSessions.getValue()).toHaveLength(0);
            expect(service.sessionId).toBeUndefined();
        });

        it('should clear latestStartedSession if the deleted session matches', async () => {
            const session = { id: 5, creationDate: new Date(), chatMode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO;
            service.chatSessions.next([session]);
            service.latestStartedSession = session;
            service.sessionId = 99; // different from 5

            vi.spyOn(httpService, 'deleteSession').mockReturnValue(of(new HttpResponse<void>({ status: 204 })));

            await firstValueFrom(service.deleteSession(5));

            expect(service.latestStartedSession).toBeUndefined();
        });
    });

    describe('getCourseId', () => {
        /**
         * It can be the case that courseId is undefined when loading a page directly from a URL or via browser page reload.
         */
        it('should extract course ID from the current URL when courseId is undefined', () => {
            service.setCourseId(undefined); // courseId must be undefined so it is retrieved from the URL
            routerMock.url = '/courses/19/lectures/27';

            const courseId = service.getCourseId();

            expect(courseId).toBe(19);
        });

        it('should return undefined when courseId is undefined and the URL does not match the expected structure', () => {
            service.setCourseId(undefined); // courseId must be undefined so it is retrieved from the URL
            routerMock.url = '/invalid-url';

            const courseId = service.getCourseId();

            expect(courseId).toBeUndefined();
        });
    });
});
