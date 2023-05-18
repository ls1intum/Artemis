import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisMessageStore } from 'app/iris/message-store.service';
import { IrisWebsocketService } from 'app/iris/websocket.service';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ActiveConversationMessageLoadedAction, SessionIdReceivedAction } from 'app/iris/message-store.model';
import { mockServerMessage } from '../../helpers/sample/iris-sample-data';

describe('IrisWebsocketService', () => {
    let irisWebsocketService: IrisWebsocketService;
    let jhiWebsocketService: JhiWebsocketService;
    let irisMessageStore: IrisMessageStore;

    const channel = 'topic/iris/sessions/0';
    const newMessageObservable = of(mockServerMessage);

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [IrisWebsocketService, MockProvider(JhiWebsocketService), IrisMessageStore, { provide: AccountService, useClass: MockAccountService }],
        });
        irisWebsocketService = TestBed.inject(IrisWebsocketService);
        jhiWebsocketService = TestBed.inject(JhiWebsocketService);
        irisMessageStore = TestBed.inject(IrisMessageStore);
    });

    afterEach(() => {
        irisWebsocketService.ngOnDestroy();
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(irisWebsocketService).toBeTruthy();
    });

    it('should subscribe to a channel', fakeAsync(() => {
        const websocketSubscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
        const websocketReceiveMock = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(newMessageObservable);
        const dispatchSpy = jest.spyOn(irisMessageStore, 'dispatch');

        irisMessageStore.dispatch(new SessionIdReceivedAction(0));

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
        irisMessageStore.dispatch(new SessionIdReceivedAction(0));
        tick();
        irisMessageStore.dispatch(new SessionIdReceivedAction(null));
        tick();
        expect(jhiWebsocketService.unsubscribe).toHaveBeenCalledWith(channel);
    }));
});
