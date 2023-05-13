import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import { IrisMessageStore } from 'app/iris/message-store.service';
import { IrisWebsocketService } from 'app/iris/websocket.service';
import { MockProvider } from 'ng-mocks';
import { IrisMessageContent, IrisMessageContentType, IrisSender, IrisServerMessageDescriptor } from 'app/entities/iris/iris.model';
import dayjs from 'dayjs';
import { firstValueFrom, of, skip, take } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ActiveConversationMessageLoadedAction, MessageStoreState } from 'app/iris/message-store.model';

describe('IrisWebsocketService', () => {
    const mockServerMessage: IrisServerMessageDescriptor = {
        sender: IrisSender.SERVER,
        messageId: 1,
        sentAt: dayjs(),
    };

    let irisWebsocketService: IrisWebsocketService;
    let jhiWebsocketService: JhiWebsocketService;
    let irisMessageStore: IrisMessageStore;

    const channel = 'test-channel';
    const newMessageObservable = of(mockServerMessage);

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                IrisWebsocketService,
                MockProvider(JhiWebsocketService),
                MockProvider(IrisHttpMessageService),
                MockProvider(IrisMessageStore),
                { provide: AccountService, useClass: MockAccountService },
            ],
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
        const dispatchMock = jest.spyOn(irisMessageStore, 'dispatch');

        irisWebsocketService.changeWebsocketSubscription(channel);

        tick();

        expect(websocketSubscribeSpy).toHaveBeenCalledOnce();
        expect(websocketSubscribeSpy).toHaveBeenCalledWith(channel);

        expect(websocketReceiveMock).toHaveBeenCalledOnce();
        expect(websocketReceiveMock).toHaveBeenCalledWith(channel);

        expect(dispatchMock).toHaveBeenCalledOnce();
        expect(dispatchMock).toHaveBeenCalledWith(new ActiveConversationMessageLoadedAction(mockServerMessage));
    }));

    it('should unsubscribe from a channel', fakeAsync(() => {
        const channel = 'test-channel';
        jest.spyOn(jhiWebsocketService, 'subscribe');
        jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(newMessageObservable);

        jest.spyOn(jhiWebsocketService, 'unsubscribe');
        irisWebsocketService.changeWebsocketSubscription(channel);
        tick();
        irisWebsocketService.changeWebsocketSubscription(null);
        tick();
        expect(jhiWebsocketService.unsubscribe).toHaveBeenCalledWith(channel);
    }));
});
