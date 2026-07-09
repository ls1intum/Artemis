import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
} from 'test/helpers/sample/iris-sample-data';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import 'app/foundation/util/array.extension';
import { Router } from '@angular/router';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { IrisChatWebsocketDTO, IrisChatWebsocketPayloadType } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { IrisActivityItem, IrisActivityKind, IrisActivityState, IrisRunState } from 'app/iris/shared/entities/iris-activity.model';

describe('IrisChatService', () => {
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
    const waitForCurrentChatMode = () => firstValueFrom(service.currentChatMode().pipe(filter((value): value is ChatServiceMode => value !== undefined)));
    const waitForCurrentRelatedEntityId = () => firstValueFrom(service.currentRelatedEntityId().pipe(filter((value): value is number => value !== undefined)));
    const startSessionWithWebsocket = async (websocketSubject: Subject<IrisChatWebsocketDTO>, chatSessions: IrisSessionDTO[] = []) => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of(chatSessions));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(websocketSubject.asObservable());

        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();
    };

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

        expect(httpStub).toHaveBeenCalledWith(ChatServiceMode.COURSE, id);
        expect(wsStub).toHaveBeenCalledWith(id);
    });

    it('should change to tutor chat and start new session', async () => {
        const httpStub = vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        const wsStub = vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id);

        expect(httpStub).toHaveBeenCalledWith(ChatServiceMode.PROGRAMMING_EXERCISE, id);
        expect(wsStub).toHaveBeenCalledWith(id);
    });

    describe('initialLoadComplete$', () => {
        const collectInitialLoadValues = (): boolean[] => {
            const values: boolean[] = [];
            service.initialLoadComplete$.subscribe((value) => values.push(value));
            return values;
        };

        it('should start false, flip to true after a successful session load, and reset on close-induced switch', async () => {
            const values = collectInitialLoadValues();
            expect(values).toEqual([false]);

            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists')
                .mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation))
                .mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            vi.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            service.switchTo(ChatServiceMode.COURSE, id);
            await waitForSessionId();

            expect(values.at(-1)).toBe(true);

            // Switching to a different context closes the previous session and rearms the gate.
            service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id + 1);

            expect(values).toContain(false);
            // The new load completes synchronously via the mocked observable, so the latest value
            // should be true again by the time we observe.
            expect(values.at(-1)).toBe(true);
        });

        it('should still flip to true when the session load fails so consumers do not deadlock', async () => {
            const values = collectInitialLoadValues();
            expect(values).toEqual([false]);

            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
            vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            service.switchTo(ChatServiceMode.COURSE, id);

            expect(values.at(-1)).toBe(true);
        });
    });

    it('should initialize current chat context from newly loaded session', async () => {
        const relatedEntityId = 77;
        const newSession: IrisSession = { ...mockConversationWithNoMessages, id: 333, mode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: relatedEntityId };
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: newSession } as HttpResponse<IrisSession>));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, relatedEntityId);

        expect(await waitForCurrentChatMode()).toBe(ChatServiceMode.PROGRAMMING_EXERCISE);
        expect(await waitForCurrentRelatedEntityId()).toBe(relatedEntityId);
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

        service.switchTo(ChatServiceMode.LECTURE, relatedEntityId);

        expect(await waitForCurrentChatMode()).toBe(ChatServiceMode.LECTURE);
        expect(await waitForCurrentRelatedEntityId()).toBe(relatedEntityId);
    });

    it('should send a message', async () => {
        const message = 'test message';
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const createdMessage = mockUserMessageWithContent(message);
        const stub = vi.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisMessageResponseDTO>));
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
        vi.spyOn(httpService, 'rateMessage').mockReturnValueOnce(of({ body: updatedMessage } as unknown as HttpResponse<IrisMessageResponseDTO>));
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
        vi.spyOn(httpService, 'resendMessage').mockReturnValueOnce(of({ body: message } as HttpResponse<IrisMessageResponseDTO>));

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

    it('should apply STATUS run state and activity snapshots', async () => {
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        vi.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id);
        await waitForSessionId();
        expect(service.runInfo.getValue()).toEqual({ runId: 'run-1', state: IrisRunState.RUNNING });
        expect(service.activities.getValue()).toEqual(mockWebsocketStatusMessage.activities);
    });

    it('should update session title from websocket STATUS payload', async () => {
        const myTitle = 'My new session title';
        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        vi.spyOn(httpService, 'getChatSessions').mockReturnValue(of([{ id, creationDate: new Date(), mode: ChatServiceMode.COURSE, entityId: 1 } as IrisSessionDTO]));

        const wsPayloadWithTitle = {
            type: IrisChatWebsocketPayloadType.STATUS,
            runId: 'run-1',
            runState: IrisRunState.RUNNING,
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
        const lastMessage = messages.last();
        expect(lastMessage).toMatchObject({ sender: message.sender, id: message.id, content: message.content });
    });

    it('should set live assistant draft from websocket partial without incrementing new message counter', async () => {
        const websocketSubject = new Subject<IrisChatWebsocketDTO>();
        await startSessionWithWebsocket(websocketSubject);

        websocketSubject.next({ type: IrisChatWebsocketPayloadType.PARTIAL, runId: 'run-1', partialResult: 'Hel', partialSeq: 1 });

        const draft = await firstValueFrom(service.currentLiveAssistantDraft());
        expect(draft).toEqual({ runId: 'run-1', text: 'Hel' });
        expect(await firstValueFrom(service.currentNumNewMessages())).toBe(0);
    });

    it('should ignore stale websocket partial sequence numbers', async () => {
        const websocketSubject = new Subject<IrisChatWebsocketDTO>();
        await startSessionWithWebsocket(websocketSubject);

        websocketSubject.next({ type: IrisChatWebsocketPayloadType.PARTIAL, runId: 'run-1', partialResult: 'Hello', partialSeq: 2 });
        websocketSubject.next({ type: IrisChatWebsocketPayloadType.PARTIAL, runId: 'run-1', partialResult: 'Hel', partialSeq: 1 });

        expect(await firstValueFrom(service.currentLiveAssistantDraft())).toEqual({ runId: 'run-1', text: 'Hello' });
    });

    it('should clear live assistant draft on final websocket message and ignore later partials for the same run', async () => {
        const websocketSubject = new Subject<IrisChatWebsocketDTO>();
        await startSessionWithWebsocket(websocketSubject);

        websocketSubject.next({ type: IrisChatWebsocketPayloadType.PARTIAL, runId: 'run-1', partialResult: 'Hello', partialSeq: 2 });
        websocketSubject.next({ ...mockWebsocketServerMessage, runId: 'run-1' });
        websocketSubject.next({ type: IrisChatWebsocketPayloadType.PARTIAL, runId: 'run-1', partialResult: 'late', partialSeq: 3 });

        expect(await firstValueFrom(service.currentLiveAssistantDraft())).toBeUndefined();
    });

    it('should clear live assistant draft on session switch', async () => {
        const websocketSubject = new Subject<IrisChatWebsocketDTO>();
        await startSessionWithWebsocket(websocketSubject);
        websocketSubject.next({ type: IrisChatWebsocketPayloadType.PARTIAL, runId: 'run-1', partialResult: 'Hello', partialSeq: 2 });

        vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(mockServerSessionHttpResponseWithId(id + 1)));
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id + 1);

        expect(await firstValueFrom(service.currentLiveAssistantDraft())).toBeUndefined();
    });

    describe('run-state frame policy', () => {
        const runningActivity = (idValue: string, name = 'lecture_content_retrieval'): IrisActivityItem => ({
            id: idValue,
            kind: IrisActivityKind.TOOL as IrisActivityItem['kind'],
            name,
            state: IrisActivityState.RUNNING,
            detail: 'Searching lecture content',
        });

        const finishedActivity = (idValue: string, name = 'lecture_content_retrieval'): IrisActivityItem => ({
            ...runningActivity(idValue, name),
            state: IrisActivityState.FINISHED,
            result: '3 sections',
            durationMillis: 3100,
        });

        const statusFrame = (runId: string, runState: IrisRunState, extra: Partial<IrisChatWebsocketDTO> = {}): IrisChatWebsocketDTO => ({
            type: IrisChatWebsocketPayloadType.STATUS,
            runId,
            runState,
            ...extra,
        });

        const messageFrame = (runId: string, extra: Partial<IrisChatWebsocketDTO> = {}): IrisChatWebsocketDTO => ({
            ...mockWebsocketServerMessage,
            runId,
            ...extra,
        });

        it('guard: awaitingAnswer opens on local send and survives the user-message HTTP response', async () => {
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject);
            const inFlight = new Subject<HttpResponse<IrisMessageResponseDTO>>();
            vi.spyOn(httpService, 'createMessage').mockReturnValue(inFlight.asObservable());

            const result = firstValueFrom(service.sendMessage('new question'));

            expect(service.awaitingAnswer()).toBe(true);

            inFlight.next({ body: mockUserMessageWithContent('new question') } as HttpResponse<IrisMessageResponseDTO>);
            inFlight.complete();
            await result;

            expect(service.awaitingAnswer()).toBe(true);
        });

        it('guard: awaitingAnswer clears on assistant MESSAGE and the persisted trail stays on the message', async () => {
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject);
            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities: [runningActivity('act-1')], activitySeq: 1 }));

            websocketSubject.next(
                messageFrame('run-1', {
                    message: { ...mockWebsocketServerMessage.message!, activities: [finishedActivity('act-1')] },
                }),
            );

            expect(service.awaitingAnswer()).toBe(false);
            expect(service.activities.getValue()).toEqual([]);
            expect(service.messages.getValue().last()).toMatchObject({ activities: [finishedActivity('act-1')] });
        });

        it('guard: intermediate MESSAGE clears the draft without finalizing the run or unread state', async () => {
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject);
            const activities = [runningActivity('act-1')];

            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities, activitySeq: 1 }));
            websocketSubject.next({ type: IrisChatWebsocketPayloadType.PARTIAL, runId: 'run-1', partialResult: 'Let me check', partialSeq: 2 });
            websocketSubject.next(
                messageFrame('run-1', {
                    final: false,
                    message: {
                        ...mockWebsocketServerMessage.message!,
                        id: 40,
                        final: false,
                        content: [{ type: 'text', textContent: 'Let me check first' }],
                    },
                }),
            );
            websocketSubject.next({ type: IrisChatWebsocketPayloadType.PARTIAL, runId: 'run-1', partialResult: 'Continuing after tool', partialSeq: 3 });

            expect(await firstValueFrom(service.currentLiveAssistantDraft())).toEqual({ runId: 'run-1', text: 'Continuing after tool' });
            expect(service.awaitingAnswer()).toBe(true);
            expect(service.activities.getValue()).toEqual(activities);
            expect(service.messages.getValue().last()).toMatchObject({ id: 40, final: false });
            expect(service.numNewMessages.getValue()).toBe(0);
        });

        it('guard: awaitingAnswer clears on FAILED while keeping the last live activities', async () => {
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject);
            const activities = [runningActivity('act-1')];

            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities, activitySeq: 1 }));
            websocketSubject.next(statusFrame('run-1', IrisRunState.FAILED, { error: { message: 'Suggestion failed', code: 'IRIS_FAILED' } }));

            expect(service.awaitingAnswer()).toBe(false);
            expect(service.runInfo.getValue()).toEqual({ runId: 'run-1', state: IrisRunState.FAILED, error: { message: 'Suggestion failed', code: 'IRIS_FAILED' } });
            expect(service.activities.getValue()).toEqual(activities);
        });

        it('guard: awaitingAnswer clears on HTTP send error', async () => {
            await startSessionWithWebsocket(new Subject<IrisChatWebsocketDTO>());
            vi.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            await firstValueFrom(service.sendMessage('new question'));

            expect(service.awaitingAnswer()).toBe(false);
        });

        it('guard: activitySeq applies only strictly newer snapshots', async () => {
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject);
            const newest = [finishedActivity('act-1')];

            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities: [runningActivity('act-1')], activitySeq: 2 }));
            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities: [runningActivity('act-stale')], activitySeq: 2 }));
            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities: [runningActivity('act-older')], activitySeq: 1 }));
            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities: newest, activitySeq: 3 }));

            expect(service.activities.getValue()).toEqual(newest);
        });

        it('guard: stale-run STATUS cannot mutate suggestions, runInfo, error, or activities', async () => {
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject);
            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities: [runningActivity('act-1')], activitySeq: 1 }));
            websocketSubject.next(statusFrame('run-2', IrisRunState.RUNNING, { activities: [runningActivity('act-2')], activitySeq: 1 }));

            websocketSubject.next(
                statusFrame('run-1', IrisRunState.FAILED, {
                    activities: [finishedActivity('act-old')],
                    activitySeq: 2,
                    suggestions: ['stale suggestion'],
                    error: { message: 'old failed' },
                    rateLimitInfo: { currentMessageCount: 1, rateLimit: 5 } as IrisRateLimitInformation,
                }),
            );

            expect(statusMock.handleRateLimitInfo).toHaveBeenCalledWith({ currentMessageCount: 1, rateLimit: 5 });
            expect(service.runInfo.getValue()).toEqual({ runId: 'run-2', state: IrisRunState.RUNNING });
            expect(service.activities.getValue()).toEqual([runningActivity('act-2')]);
            expect(service.suggestions.getValue()).toEqual([]);
        });

        it('guard: pending-window ignores already-known run frames until a new run binds', async () => {
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject);
            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities: [runningActivity('act-1')], activitySeq: 1 }));

            const inFlight = new Subject<HttpResponse<IrisMessageResponseDTO>>();
            vi.spyOn(httpService, 'createMessage').mockReturnValue(inFlight.asObservable());
            const result = firstValueFrom(service.sendMessage('follow up'));

            websocketSubject.next(statusFrame('run-1', IrisRunState.FAILED, { suggestions: ['old suggestion'], error: { message: 'old failed' } }));
            expect(service.runInfo.getValue()).toBeUndefined();
            expect(service.suggestions.getValue()).toEqual([]);
            expect(service.awaitingAnswer()).toBe(true);

            websocketSubject.next(statusFrame('run-2', IrisRunState.RUNNING, { activities: [runningActivity('act-2')], activitySeq: 1 }));
            expect(service.runInfo.getValue()).toEqual({ runId: 'run-2', state: IrisRunState.RUNNING });
            expect(service.activities.getValue()).toEqual([runningActivity('act-2')]);

            inFlight.next({ body: mockUserMessageWithContent('follow up') } as HttpResponse<IrisMessageResponseDTO>);
            inFlight.complete();
            await result;
        });

        it('guard: stale known-run STATUS cannot update the session title while a new run is pending', async () => {
            const originalTitle = 'Current session title';
            const staleTitle = 'Stale previous-run title';
            const freshTitle = 'Fresh current-run title';
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject, [
                { id, title: originalTitle, creationDate: new Date(), mode: ChatServiceMode.COURSE, entityId: id } as IrisSessionDTO,
            ]);
            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING));

            const inFlight = new Subject<HttpResponse<IrisMessageResponseDTO>>();
            vi.spyOn(httpService, 'createMessage').mockReturnValue(inFlight.asObservable());
            const result = firstValueFrom(service.sendMessage('follow up'));

            websocketSubject.next(
                statusFrame('run-1', IrisRunState.FAILED, {
                    sessionTitle: staleTitle,
                    rateLimitInfo: { currentMessageCount: 1, rateLimit: 5 } as IrisRateLimitInformation,
                }),
            );

            expect(statusMock.handleRateLimitInfo).toHaveBeenCalledWith({ currentMessageCount: 1, rateLimit: 5 });
            expect((await firstValueFrom(service.availableChatSessions())).find((session) => session.id === id)?.title).toBe(originalTitle);

            websocketSubject.next(statusFrame('run-2', IrisRunState.RUNNING, { sessionTitle: freshTitle }));
            expect((await firstValueFrom(service.availableChatSessions())).find((session) => session.id === id)?.title).toBe(freshTitle);

            inFlight.next({ body: mockUserMessageWithContent('follow up') } as HttpResponse<IrisMessageResponseDTO>);
            inFlight.complete();
            await result;
        });

        it('guard: RUNNING after terminal state is ignored for the run', async () => {
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject);

            websocketSubject.next(statusFrame('run-1', IrisRunState.FINISHED));
            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities: [runningActivity('act-late')], activitySeq: 1 }));

            expect(service.runInfo.getValue()).toEqual({ runId: 'run-1', state: IrisRunState.FINISHED });
            expect(service.activities.getValue()).toEqual([]);
        });

        it('guard: post-MESSAGE frames keep transitions and suggestions but drop partials and activity snapshots', async () => {
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject);

            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities: [runningActivity('act-1')], activitySeq: 1 }));
            websocketSubject.next(messageFrame('run-1'));
            websocketSubject.next({ type: IrisChatWebsocketPayloadType.PARTIAL, runId: 'run-1', partialResult: 'late', partialSeq: 3 });
            websocketSubject.next(
                statusFrame('run-1', IrisRunState.FINISHED, {
                    activities: [finishedActivity('act-1')],
                    activitySeq: 2,
                    suggestions: ['follow-up'],
                }),
            );

            expect(service.runInfo.getValue()).toEqual({ runId: 'run-1', state: IrisRunState.FINISHED });
            expect(service.suggestions.getValue()).toEqual(['follow-up']);
            expect(service.activities.getValue()).toEqual([]);
            expect(await firstValueFrom(service.currentLiveAssistantDraft())).toBeUndefined();
        });

        it('guard: run state and activity tracking reset on session switch', async () => {
            const websocketSubject = new Subject<IrisChatWebsocketDTO>();
            await startSessionWithWebsocket(websocketSubject);
            websocketSubject.next(statusFrame('run-1', IrisRunState.RUNNING, { activities: [runningActivity('act-1')], activitySeq: 1 }));

            vi.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValue(of(mockServerSessionHttpResponseWithId(id + 1)));
            service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id + 1);

            expect(service.runInfo.getValue()).toBeUndefined();
            expect(service.activities.getValue()).toEqual([]);
            expect(service.awaitingAnswer()).toBe(false);
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
            service['sessionContext'] = { mode: ChatServiceMode.TUTOR_SUGGESTION, entityId: 1 };

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
            service['sessionContext'] = { mode: ChatServiceMode.COURSE, entityId: 1 };

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
            const clearChatSpy = vi.spyOn(service, 'clearChat');
            const switchSpy = vi.spyOn(service, 'switchToSession');

            await firstValueFrom(service.deleteSession(1));

            expect(clearChatSpy).not.toHaveBeenCalled();
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
            scopedService['sessionContext'] = { mode: ChatServiceMode.COURSE, entityId: 1 };
            scopedService.hasJustAcceptedLLMUsage = true;
            scopedService.rateLimitInfo = { rateLimitTimeframeHours: 1 } as IrisRateLimitInformation;

            authState.next(undefined);

            expect(scopedService.sessionId).toBeUndefined();
            expect(scopedService.messages.getValue()).toEqual([]);
            expect(scopedService.chatSessions.getValue()).toEqual([]);
            expect(scopedService.latestStartedSession).toBeUndefined();
            expect(scopedService['sessionContext']).toBeUndefined();
            expect(scopedService.hasJustAcceptedLLMUsage).toBe(false);
            expect(scopedService.rateLimitInfo).toBeUndefined();
            // courseId is route-derived, not user-private — it is intentionally preserved so the next
            // user's session in the same course can still locate it without a route change.
            expect(scopedService.getCourseId()).toBe(courseId);
        });

        it('should clear messages even when sessionId was never set (resetState must not depend on close)', () => {
            // Populate subjects without going through handleNewSession (e.g. via direct manipulation).
            scopedService.messages.next([mockServerMessage]);
            scopedService.activities.next([{ id: 'act-1', kind: IrisActivityKind.TOOL, name: 'lecture_content_retrieval', state: IrisActivityState.RUNNING }]);
            scopedService.runInfo.next({ runId: 'run-1', state: IrisRunState.RUNNING });
            scopedService.chatSessions.next([{ id: 1 } as IrisSessionDTO]);
            expect(scopedService.sessionId).toBeUndefined();

            authState.next(undefined);

            expect(scopedService.messages.getValue()).toEqual([]);
            expect(scopedService.activities.getValue()).toEqual([]);
            expect(scopedService.runInfo.getValue()).toBeUndefined();
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

            scopedService.switchToNewSession(ChatServiceMode.COURSE, 1);
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
