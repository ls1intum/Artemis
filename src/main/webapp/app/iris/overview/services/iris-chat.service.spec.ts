import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { firstValueFrom, of, filter } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { UserService } from 'app/core/user/shared/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { mockConversation, mockConversationWithNoMessages, mockServerSessionHttpResponse, mockServerSessionHttpResponseWithId } from 'test/helpers/sample/iris-sample-data';
import 'app/shared/util/array.extension';
import { Router } from '@angular/router';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';

describe('IrisChatService', () => {
    let service: IrisChatService;
    let httpService: IrisChatHttpService;
    let wsMock: IrisWebsocketService;
    let routerMock: { url: string };
    let accountService: AccountService;

    const id = 123;
    const courseId = 234;

    const statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
        setCurrentCourse: jest.fn(),
    };
    const userMock = {
        acceptLLMUsage: jest.fn(),
    };

    const waitForSessionId = () => firstValueFrom(service.currentSessionId().pipe(filter((value): value is number => value !== undefined)));

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

        accountService.userIdentity.set({ selectedLLMUsageTimestamp: dayjs() } as User);

        service.setCourseId(courseId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should clear chat', fakeAsync(async () => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponse));
        jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
        jest.spyOn(httpService, 'createSession').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(2, true)));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
        service.switchTo(ChatServiceMode.COURSE, id);
        await waitForSessionId();
        service.clearChat();
        service.currentMessages().subscribe((messages) => {
            expect(messages).toHaveLength(mockConversationWithNoMessages.messages!.length);
        });
        tick();
    }));

    it('should emit sessionId when set', async () => {
        const expectedId = 456;
        service.sessionId = expectedId;
        const sessionId = await firstValueFrom(service.currentSessionId());
        expect(sessionId).toBe(expectedId);
    });

    it('should request tutor suggestion if sessionId is set', async () => {
        service.sessionId = id;
        const httpStub = jest.spyOn(httpService, 'createTutorSuggestion').mockReturnValueOnce(of(new HttpResponse<void>()));

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
            const closeSpy = jest.spyOn(service as any, 'close');
            jest.spyOn(httpService, 'getChatSessionById').mockReturnValue(of());
            const session = { id: id } as IrisSessionDTO;
            service.sessionId = id;

            service.switchToSession(session);

            expect(closeSpy).not.toHaveBeenCalled();
        });

        it('should switch to a different session if llm usage is accepted', async () => {
            const newSession = { ...mockConversation, id: 456, chatMode: ChatServiceMode.COURSE, entityName: 'Course 1' };

            const closeSpy = jest.spyOn(service as any, 'close');
            jest.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSession));

            const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());

            service.sessionId = id;

            service.switchToSession(newSession);

            // Wait for async operations
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(closeSpy).toHaveBeenCalled();
            const messages = await firstValueFrom(service.currentMessages());
            expect(messages).toEqual(newSession.messages);
            expect(wsStub).toHaveBeenCalledWith(newSession.id);
        });

        it('should switch if LLM usage is not required for the mode', fakeAsync(async () => {
            accountService.userIdentity.set({ selectedLLMUsageTimestamp: undefined } as User);
            service['hasJustAcceptedLLMUsage'] = false;
            service['sessionCreationIdentifier'] = 'tutor-suggestion/1';

            const newSession = { id: 12, chatMode: ChatServiceMode.TUTOR_SUGGESTION, creationDate: new Date(), entityId: 1 } as IrisSessionDTO;

            const closeSpy = jest.spyOn(service as any, 'close');
            const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            jest.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSession));

            service.sessionId = id;

            service.switchToSession(newSession);

            // Wait for async operations
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(closeSpy).toHaveBeenCalled();
            expect(wsStub).toHaveBeenCalledWith(newSession.id);
        }));

        it('should switch if user has just accepted LLM usage', fakeAsync(async () => {
            accountService.userIdentity.set({ selectedLLMUsageTimestamp: undefined } as User);
            service['hasJustAcceptedLLMUsage'] = true;
            service['sessionCreationIdentifier'] = 'course/1';

            const newSession = { id: 12, chatMode: ChatServiceMode.COURSE, creationDate: new Date(), entityId: 1 } as IrisSessionDTO;

            const closeSpy = jest.spyOn(service as any, 'close');
            const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
            jest.spyOn(httpService, 'getChatSessions').mockReturnValue(of([]));
            jest.spyOn(httpService, 'getChatSessionById').mockReturnValue(of(newSession));

            service.sessionId = id;

            service.switchToSession(newSession);

            // Wait for async operations
            await new Promise((resolve) => setTimeout(resolve, 0));

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
