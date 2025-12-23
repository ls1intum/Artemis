import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { UserService } from 'app/core/user/shared/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import dayjs from 'dayjs/esm';
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

describe('IrisChatService', () => {
    let service: IrisChatService;
    let httpService: jest.Mocked<IrisChatHttpService>;
    let wsMock: jest.Mocked<IrisWebsocketService>;
    let routerMock: { url: string };
    let accountService: AccountService;

    const id = 123;
    const courseId = 234;

    const statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
    };
    const userMock = {
        acceptExternalLLMUsage: jest.fn(),
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
        httpService = TestBed.inject(IrisChatHttpService) as jest.Mocked<IrisChatHttpService>;
        wsMock = TestBed.inject(IrisWebsocketService) as jest.Mocked<IrisWebsocketService>;
        accountService = TestBed.inject(AccountService);

        accountService.userIdentity.set({ externalLLMUsageAccepted: dayjs() } as User);

        service.setCourseId(courseId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should change to an course chat and start new session', fakeAsync(() => {
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.COURSE, id);

        expect(httpStub).toHaveBeenCalledWith('course-chat/' + id);
        expect(wsStub).toHaveBeenCalledWith(id);
    }));

    it('should change to tutor chat and start new session', fakeAsync(() => {
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id);

        expect(httpStub).toHaveBeenCalledWith('programming-exercise-chat/' + id);
        expect(wsStub).toHaveBeenCalledWith(id);
    }));

    it('should send a message', fakeAsync(() => {
        const message = 'test message';
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const createdMessage = mockUserMessageWithContent(message);
        const stub = jest.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage } as HttpResponse<IrisUserMessage>));
        service.switchTo(ChatServiceMode.COURSE, id);
        service.sendMessage(message).subscribe();

        expect(stub).toHaveBeenCalledWith(id, expect.anything());
        service.currentMessages().subscribe((messages) => {
            expect(messages).toHaveLength(mockConversation.messages!.length + 1);
            expect(messages.last()).toEqual(createdMessage);
        });
        tick();
    }));

    it('should handle error when sending a message', fakeAsync(() => {
        const message = 'test message';
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        const stub = jest.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        service.switchTo(ChatServiceMode.COURSE, id);
        service.sendMessage(message).subscribe();

        expect(stub).toHaveBeenCalledWith(id, expect.anything());
        service.currentError().subscribe((error) => {
            expect(error).toEqual(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
        });
        tick();
    }));

    it('should load existing messages on session creation', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(httpService, 'createSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2)));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.COURSE, id);
        service.currentMessages().subscribe((messages) => {
            expect(messages).toHaveLength(mockConversation.messages!.length);
        });
        tick();
    }));

    it('should clear chat', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(httpService, 'createSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2, true)));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
        service.switchTo(ChatServiceMode.COURSE, id);
        service.clearChat();
        service.currentMessages().subscribe((messages) => {
            expect(messages).toHaveLength(mockConversationWithNoMessages.messages!.length);
        });
        tick();
    }));

    it('should rate a message', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        jest.spyOn(httpService, 'rateMessage').mockReturnValueOnce(of({} as HttpResponse<IrisMessage>));
        service.switchTo(ChatServiceMode.COURSE, id);
        const message = mockServerMessage;
        service.rateMessage(message, true).subscribe();

        expect(httpService.rateMessage).toHaveBeenCalledWith(id, message.id, true);
        tick();
    }));

    it('should resend a message', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());

        const message = mockUserMessageWithContent('resend message');
        message.id = mockClientMessage.id;
        jest.spyOn(httpService, 'resendMessage').mockReturnValueOnce(of({ body: message } as HttpResponse<IrisMessage>));

        service.switchTo(ChatServiceMode.COURSE, id);
        service.resendMessage(message).subscribe();

        expect(httpService.resendMessage).toHaveBeenCalledWith(mockConversation.id, message);
        service.currentMessages().subscribe((messages) => {
            expect(messages).toHaveLength(mockConversation.messages!.length);
            expect(messages.first()).toEqual(message);
        });
        tick();
    }));

    it('should handle error when rate limited', fakeAsync(() => {
        const message = 'test message';
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        const stub = jest.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 429 })));

        service.switchTo(ChatServiceMode.COURSE, id);
        service.sendMessage(message).subscribe();

        expect(stub).toHaveBeenCalledWith(id, expect.anything());
        service.currentError().subscribe((error) => {
            expect(error).toEqual(IrisErrorMessageKey.RATE_LIMIT_EXCEEDED);
        });
        tick();
    }));

    it('should handle error when iris is disabled', fakeAsync(() => {
        const message = 'test message';
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        const stub = jest.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

        service.switchTo(ChatServiceMode.COURSE, id);
        service.sendMessage(message).subscribe();

        expect(stub).toHaveBeenCalledWith(id, expect.anything());
        service.currentError().subscribe((error) => {
            expect(error).toEqual(IrisErrorMessageKey.IRIS_DISABLED);
        });
        tick();
    }));

    it('should handle websocket status message', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id);

        service.currentStages().subscribe((stages) => {
            expect(stages).toEqual(mockWebsocketStatusMessage.stages);
        });
        tick();
    }));

    it('should handle websocket status message with internal stages', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessageWithInteralStage));
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id);

        service.currentStages().subscribe((stages) => {
            expect(stages).toEqual(mockWebsocketStatusMessageWithInteralStage.stages?.filter((stage: IrisStageDTO) => !stage.internal));
        });
        tick();
    }));

    it('should update session title from websocket STATUS payload', fakeAsync(() => {
        const myTitle = 'My new session title';
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([{ id, creationDate: new Date(), chatMode: ChatServiceMode.COURSE, entityId: 1 } as IrisSessionDTO]));

        const wsPayloadWithTitle = {
            type: IrisChatWebsocketPayloadType.STATUS,
            stages: [],
            sessionTitle: myTitle,
        };
        const wsSpy = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(
            new Observable((subscriber) => {
                setTimeout(() => {
                    subscriber.next(wsPayloadWithTitle);
                    subscriber.complete();
                }, 0);
            }),
        );
        service.switchTo(ChatServiceMode.COURSE, id);

        expect(wsSpy).toHaveBeenCalledWith(id);
        tick();

        service.availableChatSessions().subscribe((sessions) => {
            const current = sessions.find((s) => s.id === id);
            expect(current?.title).toBe(myTitle);
        });
    }));

    it('should handle websocket message', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        const message = mockServerMessage2;
        service.switchTo(ChatServiceMode.PROGRAMMING_EXERCISE, id);

        service.currentMessages().subscribe((messages) => {
            expect(messages).toHaveLength(mockConversation.messages!.length + 1);
            expect(messages.last()).toEqual(message);
        });
        tick();
    }));

    it('should emit sessionId when set', () => {
        const expectedId = 456;
        service.currentSessionId().subscribe((id) => {
            expect(id).toBe(expectedId);
        });
        service.sessionId = expectedId;
    });

    it('should request tutor suggestion if sessionId is set', fakeAsync(() => {
        service.sessionId = id;
        const httpStub = jest.spyOn(httpService, 'createTutorSuggestion').mockReturnValueOnce(of());

        service.requestTutorSuggestion().subscribe((res) => {
            expect(res).toBeUndefined();
        });

        expect(httpStub).toHaveBeenCalledWith(id);
        tick();
    }));

    it('should throw error if sessionId is undefined on tutor suggestion', fakeAsync(() => {
        service.sessionId = undefined;

        service.requestTutorSuggestion().subscribe({
            error: (err) => {
                expect(err.message).toBe('Not initialized');
            },
        });

        tick();
    }));

    describe('switchToSession', () => {
        it('should not switch if session id is the same', () => {
            const closeSpy = jest.spyOn(service as any, 'close');
            jest.spyOn(httpService, 'getChatSessionById').mockReturnValue(of());
            const session = { id: id } as IrisSessionDTO;
            service.sessionId = id;

            service.switchToSession(session);

            expect(closeSpy).not.toHaveBeenCalled();
        });

        it('should switch to a different session if llm usage is accepted', fakeAsync(() => {
            const newSession = { ...mockConversation, id: 456, chatMode: ChatServiceMode.COURSE, entityName: 'Course 1' };

            const closeSpy = jest.spyOn(service as any, 'close');
            jest.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSession));

            const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());

            service.sessionId = id;

            service.switchToSession(newSession);
            tick();

            expect(closeSpy).toHaveBeenCalled();
            service.currentMessages().subscribe((messages) => {
                expect(messages).toEqual(newSession.messages);
            });
            expect(wsStub).toHaveBeenCalledWith(newSession.id);
        }));

        it('should switch if LLM usage is not required for the mode', fakeAsync(() => {
            accountService.userIdentity.set({ externalLLMUsageAccepted: undefined } as User);
            service['hasJustAcceptedExternalLLMUsage'] = false;
            service['sessionCreationIdentifier'] = 'tutor-suggestion/1';

            const newSession = { id: 12, chatMode: ChatServiceMode.TUTOR_SUGGESTION, creationDate: new Date(), entityId: 1 } as IrisSessionDTO;

            const closeSpy = jest.spyOn(service as any, 'close');
            const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            jest.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSession));

            service.sessionId = id;

            service.switchToSession(newSession);
            tick();

            expect(closeSpy).toHaveBeenCalled();
            expect(wsStub).toHaveBeenCalledWith(newSession.id);
        }));

        it('should switch if user has just accepted LLM usage', fakeAsync(() => {
            accountService.userIdentity.set({ externalLLMUsageAccepted: undefined } as User);
            service['hasJustAcceptedExternalLLMUsage'] = true;
            service['sessionCreationIdentifier'] = 'course/1';

            const newSession = { id: 12, chatMode: ChatServiceMode.COURSE, creationDate: new Date(), entityId: 1 } as IrisSessionDTO;

            const closeSpy = jest.spyOn(service as any, 'close');
            const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            jest.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSession));

            service.sessionId = id;

            service.switchToSession(newSession);
            tick();

            expect(closeSpy).toHaveBeenCalled();
            expect(wsStub).toHaveBeenCalledWith(newSession.id);
        }));
    });

    describe('loadChatSessions', () => {
        it('should load chat sessions and update the behavior subject', () => {
            const sessions = [{ id: 1 }, { id: 2 }] as IrisSessionDTO[];
            const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of(sessions));
            const nextSpy = jest.spyOn(service.chatSessions, 'next');

            service['loadChatSessions']();

            expect(getChatSessionsSpy).toHaveBeenCalledWith(courseId);
            expect(nextSpy).toHaveBeenCalledWith(sessions);
        });

        it('should handle an empty array of sessions', () => {
            const sessions: IrisSessionDTO[] = [];
            const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of(sessions));
            const nextSpy = jest.spyOn(service.chatSessions, 'next');

            service['loadChatSessions']();

            expect(getChatSessionsSpy).toHaveBeenCalledWith(courseId);
            expect(nextSpy).toHaveBeenCalledWith([]);
        });

        it('should handle an invalid response from the server', () => {
            const getChatSessionsSpy = jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of(null as any));
            const nextSpy = jest.spyOn(service.chatSessions, 'next');

            service['loadChatSessions']();

            expect(getChatSessionsSpy).toHaveBeenCalledWith(courseId);
            expect(nextSpy).toHaveBeenCalledWith([]);
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
