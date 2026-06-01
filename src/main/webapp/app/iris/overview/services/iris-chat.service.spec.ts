import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Observable, Subject, distinctUntilChanged, filter, firstValueFrom, of, throwError } from 'rxjs';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { UserService } from 'app/account/user/shared/user.service';
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
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import 'app/foundation/util/array.extension';
import { Router } from '@angular/router';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { IrisChatWebsocketPayloadType } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';

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
        updateLLMSelectionDecision: vi.fn().mockReturnValue(of(new HttpResponse<void>())),
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

    it('should commit the course context and subscribe to its session via resumeOrCreateCourseChat', async () => {
        const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.resumeOrCreateCourseChat(id);

        expect(httpStub).toHaveBeenCalledWith(ChatServiceMode.COURSE, id);
        expect(wsStub).toHaveBeenCalledWith(id);
    });

    it('should commit a non-course context and subscribe to its session via resumeOrCreateChat', async () => {
        const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service['resumeOrCreateChat'](ChatServiceMode.PROGRAMMING_EXERCISE, id);

        expect(httpStub).toHaveBeenCalledWith(ChatServiceMode.PROGRAMMING_EXERCISE, id);
        expect(wsStub).toHaveBeenCalledWith(id);
    });

    it('should initialize current chat context from newly loaded session', async () => {
        const relatedEntityId = 77;
        const newSession: IrisSession = { ...mockConversationWithNoMessages, id: 333, mode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: relatedEntityId };
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: newSession } as HttpResponse<IrisSession>));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        service['resumeOrCreateChat'](ChatServiceMode.PROGRAMMING_EXERCISE, relatedEntityId);

        expect(service.displayContext()?.mode).toBe(ChatServiceMode.PROGRAMMING_EXERCISE);
        expect(service.displayContext()?.entityId).toBe(relatedEntityId);
    });

    it('should initialize current chat context from mode field', async () => {
        const relatedEntityId = 66;
        const newSession: IrisSession = {
            ...mockConversationWithNoMessages,
            id: 444,
            mode: ChatServiceMode.LECTURE,
            entityId: relatedEntityId,
        };
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: newSession } as HttpResponse<IrisSession>));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        service['resumeOrCreateChat'](ChatServiceMode.LECTURE, relatedEntityId);

        expect(service.displayContext()?.mode).toBe(ChatServiceMode.LECTURE);
        expect(service.displayContext()?.entityId).toBe(relatedEntityId);
    });

    it('should send a message', async () => {
        const message = 'test message';
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const createdMessage = mockUserMessageWithContent(message);
        const stub = vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisMessageResponseDTO>));
        service.resumeOrCreateCourseChat(id);
        await waitForSessionId();
        await firstValueFrom(service.sendMessage(message));

        expect(stub).toHaveBeenCalledWith(id, expect.objectContaining({ pendingContext: undefined }));
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

        service.resumeOrCreateCourseChat(id);
        await waitForSessionId();
        await firstValueFrom(service.sendMessage(message));

        expect(stub).toHaveBeenCalledWith(id, expect.objectContaining({ pendingContext: undefined }));
        const error = await firstValueFrom(service.currentError());
        expect(error).toEqual(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
    });

    it('should load existing messages on session creation', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(httpService, 'createSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2)));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.resumeOrCreateCourseChat(id);
        await waitForSessionId();
        const messages = await firstValueFrom(service.currentMessages());
        expect(messages).toHaveLength(mockConversation.messages!.length);
    });

    describe('stagePendingContext', () => {
        it('should not call HTTP when stagePendingContext is invoked, only update dropdown signals', async () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
            const createMessageSpy = vi.spyOn(httpService, 'createMessage');

            service.resumeOrCreateCourseChat(id);
            await waitForSessionId();

            const newEntityId = 42;
            service.stagePendingContext(ChatServiceMode.LECTURE, newEntityId);

            // No HTTP call: the change is purely local until the user sends a message
            expect(createMessageSpy).not.toHaveBeenCalled();

            // Dropdown reflects the new selection (committed-look)
            expect(service.displayContext()?.mode).toBe(ChatServiceMode.LECTURE);
            expect(service.displayContext()?.entityId).toBe(newEntityId);

            // Messages and citations are untouched
            expect(service.messages.getValue()).toEqual(mockConversation.messages);
        });

        it('should include pendingContext in the request DTO when context differs from session', async () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
            const createdMessage = mockUserMessageWithContent('hi');
            const createMessageSpy = vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisMessageResponseDTO>));

            service.resumeOrCreateCourseChat(id);
            await waitForSessionId();

            const pendingEntityId = 42;
            service.stagePendingContext(ChatServiceMode.LECTURE, pendingEntityId);
            await firstValueFrom(service.sendMessage('hi'));

            expect(createMessageSpy).toHaveBeenCalledWith(id, expect.objectContaining({ pendingContext: { mode: ChatServiceMode.LECTURE, entityId: pendingEntityId } }));
            // After the send commits the switch, the override is cleared and committed is updated
            expect(service['_pendingContext']()).toBeUndefined();
            expect(service['_committedContext']()).toEqual({ mode: ChatServiceMode.LECTURE, entityId: pendingEntityId });
        });

        it('should not include pendingContext when user reverts to session current context before sending', async () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
            const createdMessage = mockUserMessageWithContent('hi');
            const createMessageSpy = vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisMessageResponseDTO>));

            service.resumeOrCreateCourseChat(id);
            await waitForSessionId();

            // Pick a different context, then revert to the session's current context
            service.stagePendingContext(ChatServiceMode.LECTURE, 42);
            service.stagePendingContext(mockConversation.mode!, mockConversation.entityId!);

            await firstValueFrom(service.sendMessage('hi'));

            expect(createMessageSpy).toHaveBeenCalledWith(id, expect.objectContaining({ pendingContext: undefined }));
        });
    });

    describe('startFreshChat', () => {
        it('should close the active session and open a fresh COURSE session', async () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(httpService, 'createSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2, true)));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            service.resumeOrCreateCourseChat(id);
            await waitForSessionId();
            service.startFreshChat();
            await waitForSessionIdValue(2);
            const messages = await firstValueFrom(service.currentMessages());
            expect(messages).toHaveLength(mockConversationWithNoMessages.messages!.length);
        });

        it('should be a no-op when the current session is already an empty COURSE session for the same course', async () => {
            // Session entityId must match the course id so isFreshCourseSession evaluates to true:
            // updateCurrentSessionContext overwrites _committedContext with the session's entityId.
            const courseSession = { body: { ...mockConversationWithNoMessages, id: 999, entityId: courseId } } as HttpResponse<IrisSession>;
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(courseSession));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            const createSessionSpy = vi.spyOn(httpService, 'createSession');

            service.resumeOrCreateCourseChat(courseId);
            await waitForSessionId();
            expect(service.messages.getValue()).toEqual([]);

            service.startFreshChat();

            // No new session is created: the dashboard re-mount of a fresh empty chat must not churn.
            expect(createSessionSpy).not.toHaveBeenCalled();
        });

        it('should be a no-op when courseId is undefined', () => {
            service.setCourseId(undefined);
            routerMock.url = '/invalid-url';
            const createSessionSpy = vi.spyOn(httpService, 'createSession');
            const closeSpy = vi.spyOn(service as any, 'close');

            service.startFreshChat();

            expect(createSessionSpy).not.toHaveBeenCalled();
            expect(closeSpy).not.toHaveBeenCalled();
        });
    });

    describe('resumeOrCreateCourseChat', () => {
        it('should skip reload when the course context is already committed', async () => {
            // The session response must carry entityId === courseId so updateCurrentSessionContext keeps
            // committedContext aligned with the requested course — otherwise the second call would treat
            // the context as different and re-trigger closeAndStart.
            const courseSession = { body: { ...mockConversationWithNoMessages, id: 999, entityId: courseId } } as HttpResponse<IrisSession>;
            const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(courseSession));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());

            service.resumeOrCreateCourseChat(courseId);
            expect(httpStub).toHaveBeenCalledTimes(1);

            service.resumeOrCreateCourseChat(courseId);
            // Same (mode, entityId) → resumeOrCreateChat's isDifferent guard short-circuits closeAndStart.
            expect(httpStub).toHaveBeenCalledTimes(1);
        });

        it('should set the pageContext to the COURSE entry', () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

            service.resumeOrCreateCourseChat(courseId);

            expect(service.pageContext()).toEqual({ mode: ChatServiceMode.COURSE, entityId: courseId });
        });
    });

    describe('openChatForContext', () => {
        const lectureId = 555;

        it('should set the pageContext including entityName', () => {
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(httpService, 'createSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2, true)));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());

            service.openChatForContext(ChatServiceMode.LECTURE, lectureId, 'Intro Lecture');

            expect(service.pageContext()).toEqual({ mode: ChatServiceMode.LECTURE, entityId: lectureId, entityName: 'Intro Lecture' });
        });

        it('should return early when getCourseId returns undefined', () => {
            service.setCourseId(undefined);
            routerMock.url = '/invalid-url';
            const getChatSessionsSpy = vi.spyOn(httpService, 'getChatSessions');

            service.openChatForContext(ChatServiceMode.LECTURE, lectureId);

            expect(getChatSessionsSpy).not.toHaveBeenCalled();
        });

        it('should switch to a matching history session when one exists', async () => {
            const matchingSession: IrisSessionDTO = { id: 77, mode: ChatServiceMode.LECTURE, entityId: lectureId, creationDate: new Date() } as IrisSessionDTO;
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([matchingSession]));
            vi.spyOn(httpService, 'getChatSessionById').mockReturnValue(of({ ...mockConversation, id: 77, mode: ChatServiceMode.LECTURE, entityId: lectureId }));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            const switchSpy = vi.spyOn(service, 'switchToSession');
            const startFreshSpy = vi.spyOn(service, 'startFreshChat');
            const stagePendingSpy = vi.spyOn(service, 'stagePendingContext');

            service.openChatForContext(ChatServiceMode.LECTURE, lectureId);
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(switchSpy).toHaveBeenCalledWith(matchingSession);
            expect(startFreshSpy).not.toHaveBeenCalled();
            expect(stagePendingSpy).not.toHaveBeenCalled();
        });

        it('should open a fresh COURSE chat and stage the pending context when no matching session exists', async () => {
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(httpService, 'createSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2, true)));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            const startFreshSpy = vi.spyOn(service, 'startFreshChat');
            const stagePendingSpy = vi.spyOn(service, 'stagePendingContext');

            service.openChatForContext(ChatServiceMode.LECTURE, lectureId, 'Intro Lecture');
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(startFreshSpy).toHaveBeenCalledOnce();
            expect(stagePendingSpy).toHaveBeenCalledWith(ChatServiceMode.LECTURE, lectureId, 'Intro Lecture');
        });

        it('should cancel an in-flight session-loading subscription when called again before the first resolves', () => {
            const firstInFlight = new Subject<IrisSessionDTO[]>();
            const secondInFlight = new Subject<IrisSessionDTO[]>();
            vi.spyOn(httpService, 'getChatSessions').mockReturnValueOnce(firstInFlight.asObservable()).mockReturnValueOnce(secondInFlight.asObservable());
            const switchSpy = vi.spyOn(service, 'switchToSession').mockImplementation(() => {});

            service.openChatForContext(ChatServiceMode.LECTURE, lectureId);
            service.openChatForContext(ChatServiceMode.LECTURE, 999);

            // The first request resolves after the second was started. Its callback must short-circuit
            // because stateGeneration has advanced.
            firstInFlight.next([{ id: 7, mode: ChatServiceMode.LECTURE, entityId: lectureId, creationDate: new Date() } as IrisSessionDTO]);
            firstInFlight.complete();

            expect(switchSpy).not.toHaveBeenCalled();
        });
    });

    describe('close', () => {
        it('should clear the pending context even when no session existed', () => {
            // A lecture page can stage a pending context before any session has been loaded.
            // close() is invoked by start()/closeAndStart() and must wipe that stale pending state.
            service.stagePendingContext(ChatServiceMode.LECTURE, 7, 'Lecture 7');
            expect(service.displayContext()).toBeDefined();

            service['close']();

            expect(service['_pendingContext']()).toBeUndefined();
        });
    });

    it('should rate a message', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const message = mockServerMessage;
        const updatedMessage = Object.assign({}, message, { helpful: true });
        vi.spyOn(httpService, 'rateMessage').mockReturnValueOnce(of({ body: updatedMessage } as unknown as HttpResponse<IrisMessageResponseDTO>));
        service.resumeOrCreateCourseChat(id);
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
        vi.spyOn(httpService, 'resendMessage').mockReturnValueOnce(of({ body: message } as HttpResponse<IrisMessageResponseDTO>));

        service.resumeOrCreateCourseChat(id);
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

        service.resumeOrCreateCourseChat(id);
        await waitForSessionId();
        await firstValueFrom(service.sendMessage(message));

        expect(stub).toHaveBeenCalledWith(id, expect.objectContaining({ pendingContext: undefined }));
        const error = await firstValueFrom(service.currentError());
        expect(error).toEqual(IrisErrorMessageKey.RATE_LIMIT_EXCEEDED);
    });

    it('should handle error when iris is disabled', async () => {
        const message = 'test message';
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        const stub = vi.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

        service.resumeOrCreateCourseChat(id);
        await waitForSessionId();
        await firstValueFrom(service.sendMessage(message));

        expect(stub).toHaveBeenCalledWith(id, expect.objectContaining({ pendingContext: undefined }));
        const error = await firstValueFrom(service.currentError());
        expect(error).toEqual(IrisErrorMessageKey.IRIS_DISABLED);
    });

    it('should handle websocket status message', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        service['resumeOrCreateChat'](ChatServiceMode.PROGRAMMING_EXERCISE, id);
        await waitForSessionId();
        const stages = await firstValueFrom(service.currentStages());
        expect(stages).toEqual(mockWebsocketStatusMessage.stages);
    });

    it('should handle websocket status message with internal stages', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessageWithInteralStage));
        service['resumeOrCreateChat'](ChatServiceMode.PROGRAMMING_EXERCISE, id);
        await waitForSessionId();
        const stages = await firstValueFrom(service.currentStages());
        expect(stages).toEqual(mockWebsocketStatusMessageWithInteralStage.stages?.filter((stage: IrisStageDTO) => !stage.internal));
    });

    it('should update session title from websocket STATUS payload', async () => {
        const myTitle = 'My new session title';
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([{ id, creationDate: new Date(), mode: ChatServiceMode.COURSE, entityId: 1 } as IrisSessionDTO]));

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
        service.resumeOrCreateCourseChat(id);
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
        service['resumeOrCreateChat'](ChatServiceMode.PROGRAMMING_EXERCISE, id);
        await waitForSessionId();
        const messages = await firstValueFrom(service.currentMessages());
        expect(messages).toHaveLength(mockConversation.messages!.length + 1);
        const lastMessage = messages.last();
        expect(lastMessage).toMatchObject({ sender: message.sender, id: message.id, content: message.content });
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
            const newSession = { ...mockConversation, id: 456, mode: ChatServiceMode.COURSE, entityName: 'Course 1' };

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
            service['_committedContext'].set({ mode: ChatServiceMode.TUTOR_SUGGESTION, entityId: 1 });

            const newSession = { id: 12, mode: ChatServiceMode.TUTOR_SUGGESTION, creationDate: new Date(), entityId: 1 } as IrisSessionDTO;
            const newSessionFull = { id: 12, mode: ChatServiceMode.TUTOR_SUGGESTION, creationDate: new Date(), entityId: 1, userId: 1 } as IrisSession;

            const closeSpy = vi.spyOn(service as any, 'close');
            const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSessionFull));

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
            service['_committedContext'].set({ mode: ChatServiceMode.COURSE, entityId: 1 });

            const newSession = { id: 12, mode: ChatServiceMode.COURSE, creationDate: new Date(), entityId: 1 } as IrisSessionDTO;
            const newSessionFull = { id: 12, mode: ChatServiceMode.COURSE, creationDate: new Date(), entityId: 1, userId: 1 } as IrisSession;

            const closeSpy = vi.spyOn(service as any, 'close');
            const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSessionFull));

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
                { id: 1, creationDate: new Date(), mode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO,
                { id: 2, creationDate: new Date(), mode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO,
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
                { id: 1, creationDate: new Date(), mode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO,
                { id: 2, creationDate: new Date(), mode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO,
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
            const sessions = [{ id: 1, creationDate: new Date(), mode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO];
            service.chatSessions.next(sessions);
            service.sessionId = 1;

            vi.spyOn(httpService, 'deleteSession').mockReturnValue(of(new HttpResponse<void>({ status: 204 })));
            const startFreshChatSpy = vi.spyOn(service, 'startFreshChat');
            const switchSpy = vi.spyOn(service, 'switchToSession');

            await firstValueFrom(service.deleteSession(1));

            expect(startFreshChatSpy).not.toHaveBeenCalled();
            expect(switchSpy).not.toHaveBeenCalled();
            expect(service.chatSessions.getValue()).toHaveLength(0);
            expect(service.sessionId).toBeUndefined();
        });

        it('should clear latestStartedSession if the deleted session matches', async () => {
            const session = { id: 5, creationDate: new Date(), mode: ChatServiceMode.COURSE, entityId: 1, entityName: 'C1' } as IrisSessionDTO;
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

    describe('updateLLMUsageConsent', () => {
        beforeEach(() => {
            userMock.updateLLMSelectionDecision.mockReset();
            userMock.updateLLMSelectionDecision.mockReturnValue(of(new HttpResponse<void>()));
        });

        it('should emit llmOptedOut$ once after NO_AI is persisted successfully', () => {
            let emissions = 0;
            service.llmOptedOut$.subscribe(() => emissions++);

            service.updateLLMUsageConsent(LLMSelectionDecision.NO_AI);

            expect(emissions).toBe(1);
        });

        it('should not emit llmOptedOut$ when NO_AI persistence fails', () => {
            userMock.updateLLMSelectionDecision.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            let emissions = 0;
            service.llmOptedOut$.subscribe(() => emissions++);

            service.updateLLMUsageConsent(LLMSelectionDecision.NO_AI);

            expect(emissions).toBe(0);
        });

        it('should not emit llmOptedOut$ when the user accepts cloud AI', () => {
            let emissions = 0;
            service.llmOptedOut$.subscribe(() => emissions++);

            service.updateLLMUsageConsent(LLMSelectionDecision.CLOUD_AI);

            expect(emissions).toBe(0);
        });

        it('should cancel an in-flight NO_AI request when a second NO_AI call starts, emitting only once', () => {
            const inFlight = new Subject<HttpResponse<void>>();
            userMock.updateLLMSelectionDecision.mockReturnValueOnce(inFlight.asObservable()).mockReturnValueOnce(of(new HttpResponse<void>()));
            let emissions = 0;
            service.llmOptedOut$.subscribe(() => emissions++);

            service.updateLLMUsageConsent(LLMSelectionDecision.NO_AI);
            service.updateLLMUsageConsent(LLMSelectionDecision.NO_AI);
            // The first request completes after the second was started; its subscription must have been cancelled.
            inFlight.next(new HttpResponse<void>());
            inFlight.complete();

            expect(emissions).toBe(1);
        });
    });

    describe('authentication state changes', () => {
        let authState: BehaviorSubject<User | undefined>;
        let scopedService: IrisChatService;
        let customAccountService: MockAccountService;

        beforeEach(() => {
            authState = new BehaviorSubject<User | undefined>({ id: 99 } as User);
            customAccountService = new MockAccountService();
            customAccountService.userIdentity.set({ id: 99 } as User);
            // Mirror the production pipeline (AccountService.getAuthenticationState() applies distinctUntilChanged).
            customAccountService.getAuthenticationState = () => authState.asObservable().pipe(distinctUntilChanged());

            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                providers: [
                    IrisChatService,
                    MockProvider(IrisChatHttpService),
                    MockProvider(IrisWebsocketService),
                    { provide: IrisStatusService, useValue: statusMock },
                    { provide: UserService, useValue: userMock },
                    { provide: AccountService, useValue: customAccountService },
                    { provide: Router, useValue: routerMock },
                ],
            });
            scopedService = TestBed.inject(IrisChatService);
            scopedService.setCourseId(courseId);
        });

        it('should not reset state on the initial auth emission for the already-authenticated user', () => {
            // courseId was set after construction; the initial emission with the same user must not clear it.
            expect(scopedService.getCourseId()).toBe(courseId);
        });

        it('should clear all chat state when the user logs out', () => {
            scopedService.sessionId = id;
            scopedService.messages.next([mockServerMessage]);
            scopedService.chatSessions.next([{ id: 1 } as IrisSessionDTO]);
            scopedService.latestStartedSession = { id: 1 } as IrisSessionDTO;
            scopedService['_committedContext'].set({ mode: ChatServiceMode.COURSE, entityId: 1 });
            scopedService.hasJustAcceptedLLMUsage = true;
            scopedService.rateLimitInfo = { rateLimitTimeframeHours: 1 } as IrisRateLimitInformation;

            authState.next(undefined);

            expect(scopedService.sessionId).toBeUndefined();
            expect(scopedService.messages.getValue()).toEqual([]);
            expect(scopedService.chatSessions.getValue()).toEqual([]);
            expect(scopedService.latestStartedSession).toBeUndefined();
            expect(scopedService['_committedContext']()).toBeUndefined();
            expect(scopedService.hasJustAcceptedLLMUsage).toBe(false);
            expect(scopedService.rateLimitInfo).toBeUndefined();
            // courseId is route-derived, not user-private — it is intentionally preserved so the next
            // user's session in the same course can still locate it without a route change.
            expect(scopedService.getCourseId()).toBe(courseId);
        });

        it('should clear messages even when sessionId was never set (resetState must not depend on close)', () => {
            // Populate subjects without going through handleNewSession (e.g. via direct manipulation).
            scopedService.messages.next([mockServerMessage]);
            scopedService.stages.next([{ name: 'foo' } as IrisStageDTO]);
            scopedService.chatSessions.next([{ id: 1 } as IrisSessionDTO]);
            expect(scopedService.sessionId).toBeUndefined();

            authState.next(undefined);

            expect(scopedService.messages.getValue()).toEqual([]);
            expect(scopedService.stages.getValue()).toEqual([]);
            expect(scopedService.chatSessions.getValue()).toEqual([]);
        });

        it('should clear chat state when a different user logs in', () => {
            scopedService.sessionId = id;
            scopedService.messages.next([mockServerMessage]);
            scopedService.chatSessions.next([{ id: 1 } as IrisSessionDTO]);

            authState.next({ id: 42 } as User);

            expect(scopedService.sessionId).toBeUndefined();
            expect(scopedService.messages.getValue()).toEqual([]);
            expect(scopedService.chatSessions.getValue()).toEqual([]);
        });

        it('should not clear state when the same user re-emits', () => {
            scopedService.sessionId = id;
            scopedService.messages.next([mockServerMessage]);

            authState.next({ id: 99 } as User);

            expect(scopedService.sessionId).toBe(id);
            expect(scopedService.messages.getValue()).toEqual([mockServerMessage]);
        });

        it('should reset shouldReopenChat$ on logout', async () => {
            scopedService.setShouldReopenChat(true);

            authState.next(undefined);

            const value = await firstValueFrom(scopedService.shouldReopenChat$);
            expect(value).toBe(false);
        });

        it('should cancel an in-flight session-loading subscription on logout so it cannot repopulate state', () => {
            const inFlight = new Subject<HttpResponse<IrisSession>>();
            const httpServiceMock = TestBed.inject(IrisChatHttpService);
            vi.spyOn(httpServiceMock, 'createSession').mockReturnValue(inFlight.asObservable());
            vi.spyOn(httpServiceMock, 'getChatSessions').mockReturnValue(of([]));

            scopedService.startFreshChat();
            expect(scopedService['sessionLoadingSubscription']).toBeDefined();

            authState.next(undefined);

            // The in-flight HTTP completes after logout; tap operators must not run because the subscription was cancelled.
            inFlight.next({ body: { ...mockConversation, id: 999 } } as HttpResponse<IrisSession>);
            inFlight.complete();

            expect(scopedService.sessionId).toBeUndefined();
            expect(scopedService.messages.getValue()).toEqual([]);
        });

        it('should cancel an in-flight switchToSession HTTP request on logout', () => {
            const inFlight = new Subject<IrisSession>();
            const httpServiceMock = TestBed.inject(IrisChatHttpService);
            vi.spyOn(httpServiceMock, 'getChatSessionById').mockReturnValue(inFlight.asObservable());
            vi.spyOn(httpServiceMock, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());

            scopedService.switchToSession({ id: 7, mode: ChatServiceMode.COURSE, entityId: 1, creationDate: new Date() } as IrisSessionDTO);
            expect(scopedService['chatSessionByIdSubscription']).toBeDefined();

            authState.next(undefined);

            // The in-flight HTTP completes after logout; handleNewSession must not repopulate state.
            inFlight.next({ ...mockConversation, id: 7 } as IrisSession);
            inFlight.complete();

            expect(scopedService.sessionId).toBeUndefined();
            expect(scopedService.messages.getValue()).toEqual([]);
        });

        it('should cancel an in-flight loadChatSessions HTTP request on logout', () => {
            const inFlight = new Subject<IrisSessionDTO[]>();
            const httpServiceMock = TestBed.inject(IrisChatHttpService);
            vi.spyOn(httpServiceMock, 'getChatSessions').mockReturnValue(inFlight.asObservable());

            scopedService['loadChatSessions']();
            expect(scopedService['chatSessionSubscription']).toBeDefined();

            authState.next(undefined);

            inFlight.next([{ id: 99 } as IrisSessionDTO]);
            inFlight.complete();

            expect(scopedService.chatSessions.getValue()).toEqual([]);
        });

        it('should not allow an in-flight sendMessage tap to repopulate messages after logout', async () => {
            const inFlight = new Subject<HttpResponse<IrisMessageResponseDTO>>();
            const httpServiceMock = TestBed.inject(IrisChatHttpService);
            vi.spyOn(httpServiceMock, 'createMessage').mockReturnValue(inFlight.asObservable());

            scopedService.sessionId = 1;
            scopedService.messages.next([mockServerMessage]);

            // Caller subscribes (mirroring component behaviour) — no auto-cancel here.
            const callerResult = firstValueFrom(scopedService.sendMessage('hi'));

            authState.next(undefined);

            // HTTP eventually responds after the user has logged out and resetState ran.
            inFlight.next({ body: mockServerMessage2 } as unknown as HttpResponse<IrisMessageResponseDTO>);
            inFlight.complete();
            await callerResult;

            // The tap should have been gated by stateGeneration and therefore did NOT repopulate messages
            // for the previous user.
            expect(scopedService.messages.getValue()).toEqual([]);
        });

        it('should not allow an in-flight requestTutorSuggestion catchError to surface an error after logout', async () => {
            const inFlight = new Subject<HttpResponse<void>>();
            const httpServiceMock = TestBed.inject(IrisChatHttpService);
            vi.spyOn(httpServiceMock, 'createTutorSuggestion').mockReturnValue(inFlight.asObservable());

            scopedService.sessionId = 1;

            const callerResult = firstValueFrom(scopedService.requestTutorSuggestion());

            authState.next(undefined);

            // The HTTP request fails after logout. The catchError must short-circuit because the
            // generation has changed, so it does not write a stale error key to the next user's session.
            inFlight.error(new HttpErrorResponse({ status: 500 }));
            await callerResult;

            expect(scopedService.error.getValue()).toBeUndefined();
        });
    });
});
