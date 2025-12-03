import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { COMPRESSION_HEADER, ConnectionState, WebsocketService } from 'app/shared/service/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { IrisWebsocketService } from 'app/iris/overview/services/iris-websocket.service';
import { defer, of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { Message, Subscription as StompSubscription } from 'webstomp-client';

jest.mock('webstomp-client', () => ({
    client: jest.fn().mockReturnValue({
        connect: jest.fn(),
        subscribe: jest.fn(),
        send: jest.fn(),
        disconnect: jest.fn(),
        connected: false,
    }),
}));

describe('WebsocketService', () => {
    let irisWebsocketService: IrisWebsocketService;
    let websocketService: WebsocketService;

    const sessionId = 1;
    const channel = `/user/topic/iris/${sessionId}`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisWebsocketService, WebsocketService, { provide: AccountService, useClass: MockAccountService }],
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

    it('should fall back to SockJS on initial connection failure', () => {
        const connectSpy = jest.spyOn(websocketService, 'connect').mockImplementation(() => {});
        websocketService['alreadyConnectedOnce'] = false;

        websocketService.stompFailureCallback();

        expect(websocketService['sockJSFallbackAttempted']).toBeTrue();
        expect(connectSpy).toHaveBeenCalledWith(true);
        expect(websocketService['consecutiveFailedAttempts']).toBe(1);
    });

    it('should fall back to SockJS after repeated failures post-connect', () => {
        const connectSpy = jest.spyOn(websocketService, 'connect').mockImplementation(() => {});
        websocketService['alreadyConnectedOnce'] = true;
        websocketService['consecutiveFailedAttempts'] = 0;

        websocketService.stompFailureCallback();
        expect(websocketService['sockJSFallbackAttempted']).toBeFalse();
        expect(connectSpy).not.toHaveBeenCalledWith(true);
        expect(websocketService['consecutiveFailedAttempts']).toBe(1);

        websocketService.stompFailureCallback();
        expect(websocketService['sockJSFallbackAttempted']).toBeTrue();
        expect(connectSpy).toHaveBeenCalledWith(true);
        expect(websocketService['consecutiveFailedAttempts']).toBe(2);
    });

    it('should handle reconnection with backoff', fakeAsync(() => {
        jest.useFakeTimers();
        const timeoutSpy = jest.spyOn(global, 'setTimeout');

        websocketService['usingSockJS'] = true;
        websocketService['sockJSFallbackAttempted'] = true;
        websocketService['consecutiveFailedAttempts'] = 0;

        websocketService.stompFailureCallback();
        expect(websocketService['consecutiveFailedAttempts']).toBe(1);
        expect(timeoutSpy).toHaveBeenCalledWith(expect.any(Function), 5000);

        websocketService.stompFailureCallback();
        expect(websocketService['consecutiveFailedAttempts']).toBe(2);
        expect(timeoutSpy).toHaveBeenCalledWith(expect.any(Function), 5000);

        websocketService.stompFailureCallback();
        expect(websocketService['consecutiveFailedAttempts']).toBe(3);
        expect(timeoutSpy).toHaveBeenCalledWith(expect.any(Function), 5000);

        websocketService['consecutiveFailedAttempts'] = 4;
        websocketService.stompFailureCallback();
        expect(timeoutSpy).toHaveBeenCalledWith(expect.any(Function), 5000);

        websocketService['consecutiveFailedAttempts'] = 5;
        websocketService.stompFailureCallback();
        expect(timeoutSpy).toHaveBeenCalledWith(expect.any(Function), 10000);

        websocketService['consecutiveFailedAttempts'] = 9;
        websocketService.stompFailureCallback();
        expect(timeoutSpy).toHaveBeenCalledWith(expect.any(Function), 15000);

        websocketService['consecutiveFailedAttempts'] = 17;
        websocketService.stompFailureCallback();
        expect(timeoutSpy).toHaveBeenCalledWith(expect.any(Function), 20000);

        jest.useRealTimers();
    }));

    it('should not reconnect when reconnect is disabled', fakeAsync(() => {
        jest.useFakeTimers();
        const connectSpy = jest.spyOn(websocketService, 'connect');
        websocketService['usingSockJS'] = true;
        websocketService['sockJSFallbackAttempted'] = true;
        websocketService.stompFailureCallback();
        jest.runAllTimers();
        expect(connectSpy).not.toHaveBeenCalled();
        jest.useRealTimers();
    }));

    it('should handle sending message when disconnected', () => {
        websocketService.connect();
        const sendSpy = jest.spyOn(websocketService['stompClient']!, 'send');
        websocketService['stompClient']!.connected = false;

        websocketService.send('/test', { data: 'test' });
        expect(sendSpy).not.toHaveBeenCalled();
    });

    it('should handle large messages with compression', () => {
        websocketService.connect();
        websocketService['stompClient']!.connected = true;
        const sendSpy = jest.spyOn(websocketService['stompClient']!, 'send');
        const largeData = { data: 'x'.repeat(2000) };

        websocketService.send('/test', largeData);

        expect(sendSpy).toHaveBeenCalledWith('/test', expect.any(String), { 'X-Compressed': 'true' });
    });

    it('should handle undefined channel subscription', () => {
        // This test case is simply to hit the initial check in the function
        const result = websocketService.subscribe(undefined!);
        expect(result).toBe(websocketService);
    });

    it('should handle multiple subscriptions to same channel', fakeAsync(() => {
        websocketService.connect();
        websocketService['connectionStateInternal'].next(new ConnectionState(true, true, false));

        const channel = '/test/channel';
        websocketService.subscribe(channel);
        websocketService.subscribe(channel);

        tick();

        expect(websocketService['stompSubscriptions'].size).toBe(1);
        websocketService['stompSubscriptions'] = new Map<string, StompSubscription>();
    }));

    it('should handle unsubscribe from non-existent channel', () => {
        const channel = '/non-existent';
        expect(() => websocketService.unsubscribe(channel)).not.toThrow();
    });

    it('should handle multiple connect calls', () => {
        const connectSpy = jest.spyOn(websocketService, 'connect');
        websocketService.connect();
        websocketService.connect();

        expect(connectSpy).toHaveBeenCalledTimes(2);
        expect(websocketService['connecting']).toBeTruthy();
    });

    it('should handle JSON parsing errors', () => {
        const invalidJson = 'invalid-json';
        // @ts-ignore
        const result = WebsocketService.parseJSON(invalidJson);
        expect(result).toBe(invalidJson);
    });

    it('should handle incoming message with no compression', () => {
        const channel = '/topic/test';
        const message: Message = {
            body: JSON.stringify({ data: 'test' }),
            headers: {},
            ack: jest.fn(),
            nack: jest.fn(),
            command: '',
        };
        const subscriber = jest.fn();
        // @ts-ignore
        websocketService['subscribers'].set(channel, { next: subscriber });

        websocketService['handleIncomingMessage'](channel)(message);

        expect(subscriber).toHaveBeenCalledWith({ data: 'test' });
    });

    it('should handle incoming message with compression', () => {
        const channel = '/topic/test';
        // @ts-ignore
        const messageBody = WebsocketService.compressAndEncode(JSON.stringify({ data: 'test' }));
        const message: Message = {
            body: messageBody,
            headers: COMPRESSION_HEADER,
            ack: jest.fn(),
            nack: jest.fn(),
            command: '',
        };
        const subscriber = jest.fn();
        // @ts-ignore
        websocketService['subscribers'].set(channel, { next: subscriber });

        websocketService['handleIncomingMessage'](channel)(message);

        expect(subscriber).toHaveBeenCalledWith({ data: 'test' });
    });

    it('should update observables when calling receive', () => {
        expect(websocketService['observables'].size).toBe(0);
        websocketService.receive('/test/topic');
        expect(websocketService['observables'].size).toBe(1);
        websocketService.receive('/test/topic');
        expect(websocketService['observables'].size).toBe(1);
        websocketService.receive('/test/topictwo');
        expect(websocketService['observables'].size).toBe(2);
    });
});
