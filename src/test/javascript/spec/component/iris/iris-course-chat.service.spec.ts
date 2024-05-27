import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { IrisCourseChatService } from 'app/iris/iris-course-chat.service';
import { IrisChatHttpService } from 'app/iris/iris-chat-http.service';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';

describe('IrisCourseChatService', () => {
    let service: IrisCourseChatService;
    let httpService: jest.Mocked<IrisChatHttpService>;
    let wsMock: jest.Mocked<IrisWebsocketService>;
    let statusMock: jest.Mocked<IrisStatusService>;
    let userMock: jest.Mocked<UserService>;
    let accountMock: jest.Mocked<AccountService>;

    const courseId = 123;

    statusMock = {
        currentRatelimitInfo: jest.fn().mockReturnValue(of({})),
        handleRateLimitInfo: jest.fn(),
    };
    userMock = {
        acceptIris: jest.fn(),
    };
    accountMock = {
        userIdentity: { irisAccepted: true },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                IrisCourseChatService,
                MockProvider(IrisChatHttpService),
                MockProvider(IrisWebsocketService),
                { provide: IrisStatusService, useValue: statusMock },
                { provide: UserService, useValue: userMock },
                { provide: AccountService, useValue: accountMock },
            ],
        });

        service = TestBed.inject(IrisCourseChatService);
        httpService = TestBed.inject(IrisChatHttpService) as jest.Mocked<IrisChatHttpService>;
        wsMock = TestBed.inject(IrisWebsocketService) as jest.Mocked<IrisWebsocketService>;
    });

    it('should start a session', () => {
        const mockSession = { body: { id: courseId, messages: [] } };
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockSession));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.courseId = courseId;
        service.start();

        expect(httpStub).toHaveBeenCalledWith('course-chat/' + courseId);
        expect(wsStub).toHaveBeenCalledWith(courseId);
    });

    it('initAfterAccept should start a session', () => {
        const mockSession = { body: { id: courseId, messages: [] } };
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockSession));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.courseId = courseId;
        service.initAfterAccept();

        expect(httpStub).toHaveBeenCalledWith('course-chat/' + courseId);
        expect(wsStub).toHaveBeenCalledWith(courseId);
    });

    it('should change to course and start new session', () => {
        const mockSession = { body: { id: courseId, messages: [] } };
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockSession));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.changeToCourse(courseId);

        expect(httpStub).toHaveBeenCalledWith('course-chat/' + courseId);
        expect(wsStub).toHaveBeenCalledWith(courseId);
    });

    it('should send a message', () => {
        const courseId = 123;
        const message = 'test message';
        const mockSession = { body: { id: courseId, messages: [] } };
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockSession));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(null));
        jest.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({}));
        service.courseId = courseId;
        service.start();
        service.sendMessage(message);

        expect(httpService.createMessage).toHaveBeenCalledWith(courseId, expect.anything());
    });
});
