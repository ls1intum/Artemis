import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';
import { IrisChatHttpService } from 'app/iris/iris-chat-http.service';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
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
} from '../../helpers/sample/iris-sample-data';
import { IrisMessage, IrisUserMessage } from 'app/entities/iris/iris-message.model';

describe('IrisChatService', () => {
    let service: IrisChatService;
    let httpService: jest.Mocked<IrisChatHttpService>;
    let wsMock: jest.Mocked<IrisWebsocketService>;

    const id = 123;

    const statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
    };
    const userMock = {
        acceptIris: jest.fn(),
    };
    const accountMock = {
        userIdentity: { irisAccepted: dayjs() },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                IrisChatService,
                MockProvider(IrisChatHttpService),
                MockProvider(IrisWebsocketService),
                { provide: IrisStatusService, useValue: statusMock },
                { provide: UserService, useValue: userMock },
                { provide: AccountService, useValue: accountMock },
            ],
        });

        service = TestBed.inject(IrisChatService);
        httpService = TestBed.inject(IrisChatHttpService) as jest.Mocked<IrisChatHttpService>;
        wsMock = TestBed.inject(IrisWebsocketService) as jest.Mocked<IrisWebsocketService>;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should change to an course chat and start new session', fakeAsync(() => {
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.COURSE, id);

        expect(httpStub).toHaveBeenCalledWith(ChatServiceMode.COURSE + '/' + id);
        expect(wsStub).toHaveBeenCalledWith(id);
    }));

    it('should change to tutor chat and start new session', fakeAsync(() => {
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithEmptyConversation));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.TUTOR, id);

        expect(httpStub).toHaveBeenCalledWith(ChatServiceMode.TUTOR + '/' + id);
        expect(wsStub).toHaveBeenCalledWith(id);
    }));

    it('should send a message', fakeAsync(() => {
        const message = 'test message';
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
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
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        service.switchTo(ChatServiceMode.TUTOR, id);

        service.currentStages().subscribe((stages) => {
            expect(stages).toEqual(mockWebsocketStatusMessage.stages);
        });
        tick();
    }));

    it('should handle websocket message', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockServerSessionHttpResponseWithId(id)));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketServerMessage));
        const message = mockServerMessage2;
        service.switchTo(ChatServiceMode.TUTOR, id);

        service.currentMessages().subscribe((messages) => {
            expect(messages).toHaveLength(mockConversation.messages!.length + 1);
            expect(messages.last()).toEqual(message);
        });
        tick();
    }));
});
