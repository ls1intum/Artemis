import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { IrisChatHttpService } from 'app/iris/iris-chat-http.service';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';
import { IrisExerciseChatService } from 'app/iris/iris-exercise-chat.service';

describe('IrisExerciseChatService', () => {
    let service: IrisExerciseChatService;
    let httpService: jest.Mocked<IrisChatHttpService>;
    let wsMock: jest.Mocked<IrisWebsocketService>;
    let statusMock: jest.Mocked<IrisStatusService>;
    let userMock: jest.Mocked<UserService>;
    let accountMock: jest.Mocked<AccountService>;

    const exerciseId = 123;

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
                IrisExerciseChatService,
                MockProvider(IrisChatHttpService),
                MockProvider(IrisWebsocketService),
                { provide: IrisStatusService, useValue: statusMock },
                { provide: UserService, useValue: userMock },
                { provide: AccountService, useValue: accountMock },
            ],
        });

        service = TestBed.inject(IrisExerciseChatService);
        httpService = TestBed.inject(IrisChatHttpService) as jest.Mocked<IrisChatHttpService>;
        wsMock = TestBed.inject(IrisWebsocketService) as jest.Mocked<IrisWebsocketService>;
    });

    it('should start a session', () => {
        const mockSession = { body: { id: exerciseId, messages: [] } };
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockSession));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.exerciseId = exerciseId;
        service.start();

        expect(httpStub).toHaveBeenCalledWith('exercise-chat/' + exerciseId);
        expect(wsStub).toHaveBeenCalledWith(exerciseId);
    });

    it('initAfterAccept should start a session', () => {
        const mockSession = { body: { id: exerciseId, messages: [] } };
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockSession));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.exerciseId = exerciseId;
        service.initAfterAccept();

        expect(httpStub).toHaveBeenCalledWith('exercise-chat/' + exerciseId);
        expect(wsStub).toHaveBeenCalledWith(exerciseId);
    });

    it('should change to course and start new session', () => {
        const mockSession = { body: { id: exerciseId, messages: [] } };
        const httpStub = jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockSession));
        const wsStub = jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of());
        service.changeToExercise(exerciseId);

        expect(httpStub).toHaveBeenCalledWith('exercise-chat/' + exerciseId);
        expect(wsStub).toHaveBeenCalledWith(exerciseId);
    });

    it('should send a message', () => {
        const message = 'test message';
        const mockSession = { body: { id: exerciseId, messages: [] } };
        jest.spyOn(httpService, 'getCurrentSessionOrCreateIfNotExists').mockReturnValueOnce(of(mockSession));
        jest.spyOn(wsMock, 'subscribeToSession').mockReturnValueOnce(of(null));
        jest.spyOn(httpService, 'createMessage').mockReturnValueOnce(of({}));
        service.exerciseId = exerciseId;
        service.start();
        service.sendMessage(message);

        expect(httpService.createMessage).toHaveBeenCalledWith(exerciseId, expect.anything());
    });
});
