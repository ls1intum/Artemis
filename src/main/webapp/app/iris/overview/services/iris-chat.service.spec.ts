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
import { IrisChatWebsocketDTO, IrisChatWebsocketPayloadType } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { IrisSlidesContextDTO } from 'app/iris/shared/entities/iris-message-context-dto.model';
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

    it('should commit the course context and subscribe to its session via openChat', async () => {
        const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.openChat(ChatServiceMode.COURSE, id);

        expect(httpStub).toHaveBeenCalledWith(ChatServiceMode.COURSE, id);
        expect(wsStub).toHaveBeenCalledWith(id);
    });

    it('should commit a non-course context and subscribe to its session via openChat', async () => {
        const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.openChat(ChatServiceMode.PROGRAMMING_EXERCISE, id);

        expect(httpStub).toHaveBeenCalledWith(ChatServiceMode.PROGRAMMING_EXERCISE, id);
        expect(wsStub).toHaveBeenCalledWith(id);
    });

    it('should initialize current chat context from newly loaded session', async () => {
        const relatedEntityId = 77;
        const newSession: IrisSession = { ...mockConversationWithNoMessages, id: 333, mode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: relatedEntityId };
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: newSession } as HttpResponse<IrisSession>));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        service.openChat(ChatServiceMode.PROGRAMMING_EXERCISE, relatedEntityId);

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

        service.openChat(ChatServiceMode.LECTURE, relatedEntityId);

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
        service.openChat(ChatServiceMode.COURSE, id);
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

        service.openChat(ChatServiceMode.COURSE, id);
        await waitForSessionId();
        await firstValueFrom(service.sendMessage(message));

        expect(stub).toHaveBeenCalledWith(id, expect.objectContaining({ pendingContext: undefined }));
        const error = await firstValueFrom(service.currentError());
        expect(error).toEqual(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
    });

    it('should load existing messages on session creation', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(httpService, 'createCourseSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2)));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.openChat(ChatServiceMode.COURSE, id);
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

            service.openChat(ChatServiceMode.COURSE, id);
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

            service.openChat(ChatServiceMode.COURSE, id);
            await waitForSessionId();

            const pendingEntityId = 42;
            service.stagePendingContext(ChatServiceMode.LECTURE, pendingEntityId);
            await firstValueFrom(service.sendMessage('hi'));

            expect(createMessageSpy).toHaveBeenCalledWith(id, expect.objectContaining({ pendingContext: { mode: ChatServiceMode.LECTURE, entityId: pendingEntityId } }));
            // After the send commits the switch, the override is cleared and committed is updated
            expect(service['contextService']['_pending']()).toBeUndefined();
            expect(service['contextService']['_committed']()).toEqual({ mode: ChatServiceMode.LECTURE, entityId: pendingEntityId });
        });

        it('should forward page context together with pendingContext', async () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
            const createdMessage = mockUserMessageWithContent('hi');
            const createMessageSpy = vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisMessageResponseDTO>));
            const context: IrisSlidesContextDTO[] = [{ type: 'slides', lectureUnitId: 7, page: 3 }];

            service.openChat(ChatServiceMode.COURSE, id);
            await waitForSessionId();

            service.stagePendingContext(ChatServiceMode.LECTURE, 42);
            await firstValueFrom(service.sendMessage('hi', {}, context));

            expect(createMessageSpy).toHaveBeenCalledWith(
                id,
                expect.objectContaining({ pendingContext: { mode: ChatServiceMode.LECTURE, entityId: 42 }, context }),
            );
        });

        it('should not include pendingContext when user reverts to session current context before sending', async () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
            const createdMessage = mockUserMessageWithContent('hi');
            const createMessageSpy = vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisMessageResponseDTO>));

            service.openChat(ChatServiceMode.COURSE, id);
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
            vi.spyOn(httpService, 'createCourseSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2, true)));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            service.openChat(ChatServiceMode.COURSE, id);
            await waitForSessionId();
            service.startFreshChat();
            await waitForSessionIdValue(2);
            const messages = await firstValueFrom(service.currentMessages());
            expect(messages).toHaveLength(mockConversationWithNoMessages.messages!.length);
        });

        it('should be a no-op when the current session is already an empty COURSE session for the same course', async () => {
            // Session entityId must match the course id so isFreshCourseSession evaluates to true:
            // adoptServerContext overwrites the committed context with the session's entityId.
            const courseSession = { body: { ...mockConversationWithNoMessages, id: 999, entityId: courseId } } as HttpResponse<IrisSession>;
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(courseSession));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            const createCourseSessionSpy = vi.spyOn(httpService, 'createCourseSession');

            service.openChat(ChatServiceMode.COURSE, courseId);
            await waitForSessionId();
            expect(service.messages.getValue()).toEqual([]);

            service.startFreshChat();

            // No new session is created: the dashboard re-mount of a fresh empty chat must not churn.
            expect(createCourseSessionSpy).not.toHaveBeenCalled();
        });

        it('should be a no-op when courseId is undefined', () => {
            service.setCourseId(undefined);
            routerMock.url = '/invalid-url';
            const createCourseSessionSpy = vi.spyOn(httpService, 'createCourseSession');
            const closeSpy = vi.spyOn(service as any, 'close');

            service.startFreshChat();

            expect(createCourseSessionSpy).not.toHaveBeenCalled();
            expect(closeSpy).not.toHaveBeenCalled();
        });
    });

    describe('openChat (course)', () => {
        it('should skip reload when the course context is already committed', async () => {
            // The session response must carry entityId === courseId so adoptServerContext keeps the committed
            // context aligned with the requested course — otherwise the second call would treat
            // the context as different and re-trigger closeAndStart.
            const courseSession = { body: { ...mockConversationWithNoMessages, id: 999, entityId: courseId } } as HttpResponse<IrisSession>;
            const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(courseSession));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());

            service.openChat(ChatServiceMode.COURSE, courseId);
            expect(httpStub).toHaveBeenCalledTimes(1);

            service.openChat(ChatServiceMode.COURSE, courseId);
            // Same (mode, entityId) → openChat's sameSessionContext guard short-circuits closeAndStart.
            expect(httpStub).toHaveBeenCalledTimes(1);
        });

        it('should set the pageContext to the COURSE entry', () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

            service.openChat(ChatServiceMode.COURSE, courseId);

            expect(service['contextService'].page()).toEqual({ mode: ChatServiceMode.COURSE, entityId: courseId });
        });
    });

    describe('openChat', () => {
        const lectureId = 555;

        it('should set the page context to the requested (mode, entityId)', () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());

            service.openChat(ChatServiceMode.LECTURE, lectureId);

            expect(service['contextService'].page()).toEqual({ mode: ChatServiceMode.LECTURE, entityId: lectureId });
        });

        it('should resolve the session server-side via getCurrentSessionOrCreateIfNotExists', () => {
            const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());

            service.openChat(ChatServiceMode.LECTURE, lectureId);

            // History matching now happens on the server: the client just forwards (mode, entityId) and the
            // server returns the matching lecture chat or an empty course-session fallback.
            expect(httpStub).toHaveBeenCalledWith(ChatServiceMode.LECTURE, lectureId);
        });

        it('should be a no-op when the page context is unchanged', () => {
            const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(mockServerSessionHttpResponseWithEmptyConversation));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());

            service.openChat(ChatServiceMode.LECTURE, lectureId);
            service.openChat(ChatServiceMode.LECTURE, lectureId);

            // Re-opening the same page context short-circuits before re-resolving the session.
            expect(httpStub).toHaveBeenCalledTimes(1);
        });
    });

    describe('close', () => {
        it('should leave the staged context to the context service (close only tears down session state)', () => {
            // A lecture page can stage a pending context before any session has been loaded. close() clears
            // session/message state but no longer owns the context lifecycle: the pending override is only
            // discarded once the next session's server context is adopted.
            service.stagePendingContext(ChatServiceMode.LECTURE, 7, 'Lecture 7');
            expect(service.displayContext()).toBeDefined();

            service['close']();
            expect(service['contextService']['_pending']()).toEqual({ mode: ChatServiceMode.LECTURE, entityId: 7, entityName: 'Lecture 7' });

            service['contextService'].adoptServerContext({ mode: ChatServiceMode.COURSE, entityId: courseId });
            expect(service['contextService']['_pending']()).toBeUndefined();
        });
    });

    it('should rate a message', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const message = mockServerMessage;
        const updatedMessage = Object.assign({}, message, { helpful: true });
        vi.spyOn(httpService, 'rateMessage').mockReturnValueOnce(of({ body: updatedMessage } as unknown as HttpResponse<IrisMessageResponseDTO>));
        service.openChat(ChatServiceMode.COURSE, id);
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

        service.openChat(ChatServiceMode.COURSE, id);
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

        service.openChat(ChatServiceMode.COURSE, id);
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

        service.openChat(ChatServiceMode.COURSE, id);
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
        service.openChat(ChatServiceMode.PROGRAMMING_EXERCISE, id);
        await waitForSessionId();
        const stages = await firstValueFrom(service.currentStages());
        expect(stages).toEqual(mockWebsocketStatusMessage.stages);
    });

    it('should handle websocket status message with internal stages', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessageWithInteralStage));
        service.openChat(ChatServiceMode.PROGRAMMING_EXERCISE, id);
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
        service.openChat(ChatServiceMode.COURSE, id);
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
        service.openChat(ChatServiceMode.PROGRAMMING_EXERCISE, id);
        await waitForSessionId();
        const messages = await firstValueFrom(service.currentMessages());
        expect(messages).toHaveLength(mockConversation.messages!.length + 1);
        const lastMessage = messages.last();
        expect(lastMessage).toMatchObject({ sender: message.sender, id: message.id, content: message.content });
    });

    describe('message ordering', () => {
        const sessionWithNoMessages = () => {
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id, true)));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(websocketSubject.asObservable());
            return websocketSubject;
        };

        const websocketMessage = (messageId: number, sentAt: string): IrisChatWebsocketDTO =>
            ({
                type: IrisChatWebsocketPayloadType.MESSAGE,
                message: {
                    sender: IrisSender.LLM,
                    id: messageId,
                    content: [{ type: 'text', textContent: 'x' }],
                    sentAt,
                },
                stages: [],
            }) as IrisChatWebsocketDTO;

        it('should order messages by sentAt regardless of which channel delivered them', async () => {
            // A message can reach the client over the websocket and via the sendMessage HTTP response, so
            // arrival order is racy. Here the HTTP-response message is processed first but carries the later
            // sentAt, while the websocket message arrives afterwards with an earlier sentAt: it must end up
            // first. (This is what guarantees a CTXSWAP divider renders before the message that triggered it.)
            const websocketSubject = sessionWithNoMessages();
            const httpMessage: IrisMessageResponseDTO = {
                sender: IrisSender.USER,
                id: 51,
                content: [{ type: 'text', textContent: 'second' }],
                sentAt: '2024-01-01T10:00:01Z',
            };
            vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: httpMessage } as HttpResponse<IrisMessageResponseDTO>));

            service.openChat(ChatServiceMode.COURSE, id);
            await waitForSessionId();
            await firstValueFrom(service.sendMessage('second'));
            websocketSubject.next(websocketMessage(50, '2024-01-01T10:00:00Z'));

            const messages = await firstValueFrom(service.currentMessages());
            expect(messages.map((message) => message.id)).toEqual([50, 51]);
        });

        it('should break ties on equal sentAt by id so the earlier-saved message comes first', async () => {
            const websocketSubject = sessionWithNoMessages();
            const sentAt = '2024-01-01T10:00:00Z';
            const httpMessage: IrisMessageResponseDTO = {
                sender: IrisSender.USER,
                id: 61,
                content: [{ type: 'text', textContent: 'tie' }],
                sentAt,
            };
            vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: httpMessage } as HttpResponse<IrisMessageResponseDTO>));

            service.openChat(ChatServiceMode.COURSE, id);
            await waitForSessionId();
            await firstValueFrom(service.sendMessage('tie'));
            websocketSubject.next(websocketMessage(60, sentAt));

            const messages = await firstValueFrom(service.currentMessages());
            expect(messages.map((message) => message.id)).toEqual([60, 61]);
        });
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
            service['contextService']['_committed'].set({ mode: ChatServiceMode.TUTOR_SUGGESTION, entityId: 1 });

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
            service['contextService']['_committed'].set({ mode: ChatServiceMode.COURSE, entityId: 1 });

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
            // Accepting consent runs closeAndStart(); give it a page context and stubbed session-loading deps
            // so start() resolves quietly instead of throwing "Page context not set".
            service['contextService'].setPageContext({ mode: ChatServiceMode.COURSE, entityId: courseId });
            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(mockServerSessionHttpResponseWithEmptyConversation));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
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
            scopedService['contextService']['_committed'].set({ mode: ChatServiceMode.COURSE, entityId: 1 });
            scopedService.hasJustAcceptedLLMUsage = true;
            scopedService.rateLimitInfo = { rateLimitTimeframeHours: 1 } as IrisRateLimitInformation;

            authState.next(undefined);

            expect(scopedService.sessionId).toBeUndefined();
            expect(scopedService.messages.getValue()).toEqual([]);
            expect(scopedService.chatSessions.getValue()).toEqual([]);
            expect(scopedService.latestStartedSession).toBeUndefined();
            expect(scopedService['contextService']['_committed']()).toBeUndefined();
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
            vi.spyOn(httpServiceMock, 'createCourseSession').mockReturnValue(inFlight.asObservable());
            vi.spyOn(httpServiceMock, 'getChatSessions').mockReturnValue(of([]));

            // startFreshChat only spins up a new session-loading subscription when the current session is non-empty.
            scopedService.messages.next([mockServerMessage]);
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

        it('should ignore an in-flight sendMessage response after switching to another session', async () => {
            const inFlight = new Subject<HttpResponse<IrisMessageResponseDTO>>();
            const httpServiceMock = TestBed.inject(IrisChatHttpService);
            vi.spyOn(httpServiceMock, 'createMessage').mockReturnValue(inFlight.asObservable());

            scopedService.sessionId = 1;
            scopedService.messages.next([mockServerMessage]);
            scopedService.chatSessions.next([
                { id: 1, mode: ChatServiceMode.COURSE, entityId: 1 } as IrisSessionDTO,
                { id: 2, mode: ChatServiceMode.COURSE, entityId: 2 } as IrisSessionDTO,
            ]);
            scopedService['contextService'].adoptServerContext({ mode: ChatServiceMode.COURSE, entityId: 1 });
            scopedService.stagePendingContext(ChatServiceMode.LECTURE, 42);

            const callerResult = firstValueFrom(scopedService.sendMessage('hi'));

            scopedService.sessionId = 2;
            scopedService.messages.next([mockServerMessage2]);
            scopedService['contextService'].adoptServerContext({ mode: ChatServiceMode.COURSE, entityId: 2 });

            inFlight.next({ body: mockServerMessage } as unknown as HttpResponse<IrisMessageResponseDTO>);
            inFlight.complete();
            await callerResult;

            expect(scopedService.messages.getValue()).toEqual([mockServerMessage2]);
            expect(scopedService.chatSessions.getValue()).toEqual([
                { id: 1, mode: ChatServiceMode.COURSE, entityId: 1 },
                { id: 2, mode: ChatServiceMode.COURSE, entityId: 2 },
            ]);
            expect(scopedService['contextService']['_committed']()).toEqual({ mode: ChatServiceMode.COURSE, entityId: 2 });
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
