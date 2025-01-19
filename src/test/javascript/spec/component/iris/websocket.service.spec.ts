import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { WebsocketService } from 'app/core/websocket/websocket.service';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { defer, of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';

describe('WebsocketService', () => {
    let irisWebsocketService: IrisWebsocketService;
    let websocketService: WebsocketService;

    const sessionId = 1;
    const channel = `/user/topic/iris/${sessionId}`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                IrisWebsocketService,
                MockProvider(WebsocketService),
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        irisWebsocketService = TestBed.inject(IrisWebsocketService);
        websocketService = TestBed.inject(WebsocketService);
    });

    afterEach(() => {
        websocketService.ngOnDestroy();
        irisWebsocketService.ngOnDestroy();
        jest.restoreAllMocks();
    });

    const createMockMessage = (message: string) => {
        return defer(() => Promise.resolve(message));
    };

    it('should subscribe to a channel', fakeAsync(() => {
        const subscribeSpy = jest.spyOn(websocketService, 'subscribe').mockReturnValue(websocketService);
        const receiveSpy = jest.spyOn(websocketService, 'receive').mockReturnValue(of(null));

        irisWebsocketService.subscribeToSession(sessionId);

        expect(subscribeSpy).toHaveBeenCalledWith(channel);
        expect(receiveSpy).toHaveBeenCalledWith(channel);
        expect(irisWebsocketService['subscribedChannels'].has(sessionId)).toBeTrue();
    }));

    it('should return an existing channel', fakeAsync(() => {
        // Spy on the WebsocketService's subscribe and receive methods
        const subscribeSpy = jest.spyOn(websocketService, 'subscribe').mockReturnValue(websocketService);
        const receiveSpy = jest.spyOn(websocketService, 'receive').mockReturnValue(of(null));

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

        // Spy on the WebsocketService's subscribe and receive methods
        const subscribeSpy = jest.spyOn(websocketService, 'subscribe').mockReturnValue(websocketService);
        const receiveSpy = jest.spyOn(websocketService, 'receive').mockReturnValue(defer(() => Promise.resolve(testMessage)));

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

    it('should emit and decode a message', fakeAsync(() => {
        const testMessage = 'Test message';
        const encodedMessage = window.btoa(testMessage);
        const subscribeSpy = jest.spyOn(websocketService, 'subscribe').mockReturnValue(websocketService);
        const receiveSpy = jest.spyOn(websocketService, 'receive').mockReturnValue(createMockMessage(encodedMessage));

        const observable = irisWebsocketService.subscribeToSession(sessionId);
        let receivedMessage: any;

        observable.subscribe((message) => {
            receivedMessage = window.atob(message); // Decode the Base64 message
        });

        tick();

        expect(receivedMessage).toEqual(testMessage);
        expect(subscribeSpy).toHaveBeenCalledWith(channel);
        expect(receiveSpy).toHaveBeenCalledWith(channel);
    }));

    it('should unsubscribe from a channel', fakeAsync(() => {
        jest.spyOn(websocketService, 'subscribe').mockReturnValue(websocketService);
        jest.spyOn(websocketService, 'receive').mockReturnValue(of(null));
        const unsubscribeSpy = jest.spyOn(websocketService, 'unsubscribe');

        irisWebsocketService.subscribeToSession(sessionId);
        expect(irisWebsocketService['subscribedChannels'].has(sessionId)).toBeTrue();

        const result = irisWebsocketService.unsubscribeFromSession(sessionId);

        expect(unsubscribeSpy).toHaveBeenCalledWith(channel);

        // Check that the sessionId was removed from the subscribedChannels map
        expect(irisWebsocketService['subscribedChannels'].has(sessionId)).toBeFalse();

        // Check that the method returned true
        expect(result).toBeTrue();
    }));

    it('should handle invalid Base64 messages gracefully', fakeAsync(() => {
        const invalidBase64 = 'InvalidMessage$$'; // Not a valid Base64 string
        jest.spyOn(websocketService, 'subscribe').mockReturnValue(websocketService);
        jest.spyOn(websocketService, 'receive').mockReturnValue(defer(() => Promise.resolve(invalidBase64)));

        const observable = irisWebsocketService.subscribeToSession(sessionId);
        let receivedMessage: any;

        observable.subscribe({
            next: (message) => {
                try {
                    // Attempt to decode the invalid Base64
                    receivedMessage = window.atob(message);
                } catch (error) {
                    receivedMessage = null; // Handle decoding error
                }
            },
        });

        tick();

        // Ensure the message was handled gracefully
        expect(receivedMessage).toBeNull(); // Expect null because decoding should fail
    }));

    it('should compress and decompress correctly', () => {
        // Arrange
        const largePayload = { data: 'x'.repeat(2000) }; // Creates a large JSON payload
        const jsonPayload = JSON.stringify(largePayload);
        // @ts-ignore
        const compressedAndEncodedPayload = WebsocketService.compressAndEncode(jsonPayload);
        // @ts-ignore
        const originalPayload = WebsocketService.decodeAndDecompress(compressedAndEncodedPayload);
        expect(originalPayload).toEqual(jsonPayload);
    });
});
