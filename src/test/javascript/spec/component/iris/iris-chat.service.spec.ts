import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';
import { IrisChatHttpService } from 'app/iris/iris-chat-http.service';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';
import { HttpErrorResponse } from '@angular/common/http';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import dayjs from 'dayjs/esm';
import {
    mockConversation,
    mockConversationWithNoMessages,
    mockServerMessage,
    mockServerMessageWithContent,
    mockWebsocketStatusMessage,
} from '../../helpers/sample/iris-sample-data';

describe('IrisChatService', () => {
    let service: IrisChatService;
    let httpService: jest.Mocked<IrisChatHttpService>;
    let wsMock: jest.Mocked<IrisWebsocketService>;
    let statusMock: jest.Mocked<IrisStatusService>;
    let userMock: jest.Mocked<UserService>;
    let accountMock: jest.Mocked<AccountService>;

    const id = 123;

    statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
    };
    userMock = {
        acceptIris: jest.fn(),
    };
    accountMock = {
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

    it('should change to an course chat and start new session', fakeAsync(() => {
        const mockSession = { body: { id: id, messages: [] } };
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockSession));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.COURSE, id);

        expect(httpStub).toHaveBeenCalledWith('course-chat/' + id);
        expect(wsStub).toHaveBeenCalledWith(id);
    }));

    it('should change to tutor chat and start new session', fakeAsync(() => {
        const mockSession = { body: { id: id, messages: [] } };
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockSession));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.TUTOR, id);

        expect(httpStub).toHaveBeenCalledWith('tutor-chat/' + id);
        expect(wsStub).toHaveBeenCalledWith(id);
    }));

    it('should send a message', fakeAsync(() => {
        const message = 'test message';
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation, id: id } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        const createdMessage = mockServerMessageWithContent(message);
        const stub = jest.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({ body: createdMessage }));
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
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation, id: id } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(mockWebsocketStatusMessage));
        const stub = jest.spyOn(httpService, 'createMessage').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        service.switchTo(ChatServiceMode.COURSE, 123);
        service.sendMessage(message).subscribe();

        expect(stub).toHaveBeenCalledWith(id, expect.anything());
        service.currentError().subscribe((error) => {
            expect(error).toEqual(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
        });
        tick();
    }));

    it('should load existing messages on session creation', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
        jest.spyOn(httpService, 'createSession').mockReturnValueOnce(of({ body: { ...mockConversationWithNoMessages, id: 2 } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.switchTo(ChatServiceMode.COURSE, 123);
        service.currentMessages().subscribe((messages) => {
            expect(messages).toHaveLength(mockConversation.messages!.length);
        });
        tick();
    }));

    it('should clear chat', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation } }));
        jest.spyOn(httpService, 'createSession').mockReturnValueOnce(of({ body: { ...mockConversationWithNoMessages, id: 2 } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValue(of());
        service.switchTo(ChatServiceMode.COURSE, 123);
        service.clearChat();
        service.currentMessages().subscribe((messages) => {
            expect(messages).toHaveLength(mockConversationWithNoMessages.messages!.length);
        });
        tick();
    }));

    it('should rate a message', fakeAsync(() => {
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of({ body: { ...mockConversation, id: id } }));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        jest.spyOn(httpService, 'rateMessage').mockReturnValueOnce(of({}));
        service.switchTo(ChatServiceMode.COURSE, 123);
        const message = mockServerMessage;
        service.rateMessage(message, true).subscribe();

        expect(httpService.rateMessage).toHaveBeenCalledWith(id, message.id, true);
        tick();
    }));
});
