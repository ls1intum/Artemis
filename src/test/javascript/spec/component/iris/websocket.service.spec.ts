import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { defer, of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';

describe('IrisWebsocketService', () => {
    let irisWebsocketService: IrisWebsocketService;
    let jhiWebsocketService: JhiWebsocketService;

    const sessionId = 1;
    const channel = `/user/topic/iris/${sessionId}`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                IrisWebsocketService,
                MockProvider(JhiWebsocketService),
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        irisWebsocketService = TestBed.inject(IrisWebsocketService);
        jhiWebsocketService = TestBed.inject(JhiWebsocketService);
    });

    afterEach(() => {
        irisWebsocketService.ngOnDestroy();
        jest.restoreAllMocks();
    });

    it('should subscribe to a channel', fakeAsync(() => {
        const subscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe').mockReturnValue(jhiWebsocketService);
        const receiveSpy = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(null));

        irisWebsocketService.subscribeToSession(sessionId);

        expect(subscribeSpy).toHaveBeenCalledWith(channel);
        expect(receiveSpy).toHaveBeenCalledWith(channel);
        expect(irisWebsocketService['subscribedChannels'].has(sessionId)).toBeTrue();
    }));

    it('should return an existing channel', fakeAsync(() => {
        // Spy on the JhiWebsocketService's subscribe and receive methods
        const subscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe').mockReturnValue(jhiWebsocketService);
        const receiveSpy = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(null));

        // Call subscribeToSession for the first time
        const firstObservable = irisWebsocketService.subscribeToSession(sessionId);

        // Call subscribeToSession for the second time
        const secondObservable = irisWebsocketService.subscribeToSession(sessionId);

        // Check that subscribe and receive were called only once
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(receiveSpy).toHaveBeenCalledOnce();

        // Check that the same observable was returned both times
        expect(firstObservable).toStrictEqual(secondObservable);
    }));

    it('should emit a message', fakeAsync(() => {
        const testMessage = 'Test message';

        // Spy on the JhiWebsocketService's subscribe and receive methods
        const subscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe').mockReturnValue(jhiWebsocketService);
        const receiveSpy = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(defer(() => Promise.resolve(testMessage)));

        // Call subscribeToSession and subscribe to the returned observable
        const observable = irisWebsocketService.subscribeToSession(sessionId);
        let receivedMessage: any;
        observable.subscribe((message) => {
            // Store the message emitted by the observable
            receivedMessage = message;
        });
        tick();
        expect(receivedMessage).toEqual(testMessage);
        // Check that subscribe and receive were called with the correct channel
        expect(subscribeSpy).toHaveBeenCalledWith(channel);
        expect(receiveSpy).toHaveBeenCalledWith(channel);
    }));

    it('should unsubscribe from a channel', fakeAsync(() => {
        jest.spyOn(jhiWebsocketService, 'subscribe').mockReturnValue(jhiWebsocketService);
        jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(null));
        const unsubscribeSpy = jest.spyOn(jhiWebsocketService, 'unsubscribe');

        irisWebsocketService.subscribeToSession(sessionId);
        expect(irisWebsocketService['subscribedChannels'].has(sessionId)).toBeTrue();

        const result = irisWebsocketService.unsubscribeFromSession(sessionId);

        expect(unsubscribeSpy).toHaveBeenCalledWith(channel);

        // Check that the sessionId was removed from the subscribedChannels map
        expect(irisWebsocketService['subscribedChannels'].has(sessionId)).toBeFalse();

        // Check that the method returned true
        expect(result).toBeTrue();
    }));
});
