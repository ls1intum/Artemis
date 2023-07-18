import { TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisWebsocketService } from 'app/iris/websocket.service';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ActiveConversationMessageLoadedAction, ConversationErrorOccurredAction, SessionReceivedAction, StudentMessageSentAction } from 'app/iris/state-store.model';
import {
    mockServerMessage,
    mockWebsocketClientMessage,
    mockWebsocketKnownError,
    mockWebsocketServerMessage,
    mockWebsocketUnknownError,
} from '../../helpers/sample/iris-sample-data';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { IrisClientMessage } from 'app/entities/iris/iris-message.model';

describe('IrisWebsocketService', () => {
    let irisWebsocketService: IrisWebsocketService;
    let jhiWebsocketService: JhiWebsocketService;
    let irisStateStore: IrisStateStore;

    const channel = '/user/topic/iris/sessions/0';
    const newMessageObservable = of(mockWebsocketServerMessage);

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [IrisWebsocketService, MockProvider(JhiWebsocketService), IrisStateStore, { provide: AccountService, useClass: MockAccountService }],
        });
        irisWebsocketService = TestBed.inject(IrisWebsocketService);
        jhiWebsocketService = TestBed.inject(JhiWebsocketService);
        irisStateStore = TestBed.inject(IrisStateStore);
    });

    afterEach(() => {
        irisWebsocketService.ngOnDestroy();
        jest.restoreAllMocks();
    });

    it('should subscribe to a channel, get session updates and notify store about new messages', fakeAsync(() => {
        const websocketSubscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
        const websocketReceiveMock = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(newMessageObservable);
        const dispatchSpy = jest.spyOn(irisStateStore, 'dispatch');

        irisStateStore.dispatch(new SessionReceivedAction(0, []));

        tick();

        expect(websocketSubscribeSpy).toHaveBeenCalledOnce();
        expect(websocketSubscribeSpy).toHaveBeenCalledWith(channel);

        expect(websocketReceiveMock).toHaveBeenCalledOnce();
        expect(websocketReceiveMock).toHaveBeenCalledWith(channel);

        expect(dispatchSpy).toHaveBeenCalledWith(new ActiveConversationMessageLoadedAction(mockServerMessage));
        expect(dispatchSpy).toHaveBeenCalledTimes(2);
    }));

    it('should unsubscribe from a channel', fakeAsync(() => {
        jest.spyOn(jhiWebsocketService, 'subscribe');
        jest.spyOn(jhiWebsocketService, 'unsubscribe');
        jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(newMessageObservable);
        irisStateStore.dispatch(new SessionReceivedAction(0, []));
        tick();
        irisStateStore.dispatch(new SessionReceivedAction(2, []));
        tick();
        expect(jhiWebsocketService.unsubscribe).toHaveBeenCalledWith(channel);
    }));

    it('should receive an unknown error type from the server and dispatch a technical error', fakeAsync(() => {
        const websocketReceiveMock = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(mockWebsocketUnknownError));
        const dispatchSpy = jest.spyOn(irisStateStore, 'dispatch');

        irisStateStore.dispatch(new SessionReceivedAction(0, []));

        tick();

        expect(websocketReceiveMock).toHaveBeenCalledOnce();
        expect(websocketReceiveMock).toHaveBeenCalledWith(channel);

        expect(dispatchSpy).toHaveBeenCalledWith(new ConversationErrorOccurredAction(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE));
        expect(dispatchSpy).toHaveBeenCalledTimes(2); // Include the existing dispatch count
    }));

    it('should receive a new server exception message and dispatch corresponding action', fakeAsync(() => {
        const websocketReceiveMock = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(mockWebsocketKnownError));
        const dispatchSpy = jest.spyOn(irisStateStore, 'dispatch');

        irisStateStore.dispatch(new SessionReceivedAction(0, []));

        tick();

        expect(websocketReceiveMock).toHaveBeenCalledOnce();
        expect(websocketReceiveMock).toHaveBeenCalledWith(channel);

        const map = new Map<string, any>();
        map.set('model', 'gpt-4');

        expect(dispatchSpy).toHaveBeenCalledWith(new ConversationErrorOccurredAction(IrisErrorMessageKey.NO_MODEL_AVAILABLE, map));
        expect(dispatchSpy).toHaveBeenCalledTimes(2); // Include the existing dispatch count
    }));

    it('should receive a new server response message and add it to the store', fakeAsync(() => {
        const websocketReceiveMock = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(mockWebsocketServerMessage));
        const dispatchSpy = jest.spyOn(irisStateStore, 'dispatch');

        irisStateStore.dispatch(new SessionReceivedAction(0, []));

        tick();

        expect(websocketReceiveMock).toHaveBeenCalledOnce();
        expect(websocketReceiveMock).toHaveBeenCalledWith(channel);

        expect(dispatchSpy).toHaveBeenNthCalledWith(2, new ActiveConversationMessageLoadedAction(mockWebsocketServerMessage.message!));
        expect(dispatchSpy).toHaveBeenCalledTimes(2);
        flush();
    }));

    it('should receive a new student message and emit an action', fakeAsync(() => {
        const websocketReceiveMock = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(mockWebsocketClientMessage));
        const dispatchSpy = jest.spyOn(irisStateStore, 'dispatch');

        irisStateStore.dispatch(new SessionReceivedAction(0, []));

        tick();

        expect(websocketReceiveMock).toHaveBeenCalledOnce();
        expect(websocketReceiveMock).toHaveBeenCalledWith(channel);

        expect(dispatchSpy).toHaveBeenNthCalledWith(2, {
            ...new StudentMessageSentAction(
                mockWebsocketClientMessage.message as IrisClientMessage,
                setTimeout(() => {}),
            ),
            timeoutId: expect.any(Number),
        });
        flush();
    }));
});
