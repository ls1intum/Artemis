import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { COMPRESSION_HEADER, COMPRESSION_HEADER_KEY, ConnectionState, WebsocketService, buildWebsocketUrl } from 'app/foundation/service/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { RxStompState } from '@stomp/rx-stomp';
import { BehaviorSubject, EMPTY, Observable, Subject, filter, firstValueFrom, of } from 'rxjs';
import { IMessage } from '@stomp/stompjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// vi.mock is hoisted above imports, so any value its factory references must be created via vi.hoisted().
const { constructedRxStompClients, watchMock, captureExceptionMock } = vi.hoisted(() => ({
    constructedRxStompClients: [] as any[],
    watchMock: vi.fn(),
    captureExceptionMock: vi.fn(),
}));

vi.mock('@stomp/rx-stomp', async () => {
    const { RxStompState: ActualRxStompState, ReconnectionTimeMode: ActualReconnectionTimeMode } = await vi.importActual<typeof import('@stomp/rx-stomp')>('@stomp/rx-stomp');
    return {
        TickerStrategy: { Worker: 'worker-ticker' },
        RxStompState: ActualRxStompState,
        ReconnectionTimeMode: ActualReconnectionTimeMode,
        RxStomp: vi.fn().mockImplementation(function () {
            const client = {
                configure: vi.fn(),
                activate: vi.fn(),
                deactivate: vi.fn(),
                publish: vi.fn(),
                watch: watchMock,
                connected: vi.fn().mockReturnValue(true),
                connectionState$: new BehaviorSubject<RxStompState>(ActualRxStompState.CLOSED),
                stompClient: {
                    unsubscribe: vi.fn(),
                    onConnect: undefined as (() => void) | undefined,
                    onStompError: undefined,
                    onWebSocketClose: undefined,
                    onWebSocketError: undefined,
                },
            };
            constructedRxStompClients.push(client);
            return client;
        }),
    };
});

vi.mock('app/core/error-reporting/capture-exception', () => ({
    captureException: (...args: any[]) => captureExceptionMock(...args),
}));

const baseMessage: IMessage = {
    body: '',
    headers: {},
    ack: vi.fn(),
    nack: vi.fn(),
    command: '',
    binaryBody: new Uint8Array(),
    isBinaryBody: false,
};

describe('WebsocketService', () => {
    let websocketService: WebsocketService;

    beforeEach(() => {
        constructedRxStompClients.length = 0;
        watchMock.mockReset();
        captureExceptionMock.mockReset();
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), WebsocketService, { provide: AccountService, useClass: MockAccountService }],
        });
        websocketService = TestBed.inject(WebsocketService);
    });

    afterEach(() => {
        websocketService.ngOnDestroy();
        vi.restoreAllMocks();
    });

    it('connects, configures, activates, and emits connection state on successful connect', async () => {
        const openStatePromise = firstValueFrom(websocketService.connectionState.pipe(filter((state) => state.connected)));

        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];
        expect(rxStomp.configure).toHaveBeenCalled();
        expect(rxStomp.activate).toHaveBeenCalled();

        rxStomp.connectionState$.next(RxStompState.OPEN);
        rxStomp.stompClient.onConnect?.();
        expect(await openStatePromise).toEqual(new ConnectionState(true, false));
    });

    it('connects to an absolute ws URL matching the protocol of the page', () => {
        websocketService.connect();

        const config = constructedRxStompClients[0].configure.mock.calls[0][0];
        expect(config.brokerURL).toBe(`ws://${window.location.host}/websocket/websocket`);
    });

    it.each([
        ['https:', 'wss://artemis.example.org/websocket/websocket'],
        ['http:', 'ws://artemis.example.org/websocket/websocket'],
    ])('builds the websocket URL for a page served over %s', (protocol, expected) => {
        expect(buildWebsocketUrl({ protocol, host: 'artemis.example.org' })).toBe(expected);
    });

    it('keeps the port in the websocket URL', () => {
        expect(buildWebsocketUrl({ protocol: 'https:', host: 'localhost:9000' })).toBe('wss://localhost:9000/websocket/websocket');
    });

    it('disconnects gracefully and updates connection state', () => {
        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];
        rxStomp.connected = vi.fn().mockReturnValue(true);

        let latestState: ConnectionState | undefined;
        websocketService.connectionState.subscribe((state) => (latestState = state));

        websocketService.disconnect();

        expect(rxStomp.deactivate).toHaveBeenCalled();
        expect((websocketService as any).rxStomp).toBeUndefined();
        expect(latestState).toEqual(new ConnectionState(false, false));
    });

    it('reports connection status via isConnected', () => {
        expect(websocketService.isConnected()).toBe(false);
        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];
        rxStomp.connected = vi.fn().mockReturnValue(true);
        expect(websocketService.isConnected()).toBe(true);
        rxStomp.connected = vi.fn().mockReturnValue(false);
        expect(websocketService.isConnected()).toBe(false);
    });

    it('returns EMPTY observable when subscribing without a channel', async () => {
        const obs = websocketService.subscribe('');
        let completed = false;
        await new Promise<void>((resolve) => {
            obs.subscribe({ complete: () => (completed = true) }).add(() => resolve());
        });
        expect(completed).toBe(true);
        expect(watchMock).not.toHaveBeenCalled();
    });

    it('returns EMPTY when no client is available after connect attempt', () => {
        vi.spyOn(websocketService, 'connect').mockImplementation(() => {
            (websocketService as any).rxStomp = undefined;
        });
        const result = websocketService.subscribe('/topic/test');
        expect(result).toBe(EMPTY);
    });

    it('subscribes and parses uncompressed messages', async () => {
        const message: IMessage = { ...baseMessage, body: JSON.stringify({ data: 'test' }) };
        watchMock.mockReturnValue(of(message));

        const result = await firstValueFrom(websocketService.subscribe('/topic/test')!);

        expect(result).toEqual({ data: 'test' });
        expect(watchMock).toHaveBeenCalledWith(
            expect.objectContaining({
                destination: '/topic/test',
                subHeaders: expect.objectContaining({
                    id: expect.any(String),
                }),
            }),
        );
    });

    it('creates only one STOMP subscription when several callers watch the same destination', () => {
        watchMock.mockReturnValue(new Subject<IMessage>());

        const first = websocketService.subscribe('/topic/shared').subscribe();
        const second = websocketService.subscribe('/topic/shared').subscribe();

        // The broker rejects a second subscription to a destination already subscribed on the same session,
        // so both callers have to share one underlying watch.
        expect(watchMock).toHaveBeenCalledOnce();

        first.unsubscribe();
        second.unsubscribe();
    });

    it('delivers messages of a shared destination to every caller', async () => {
        const messages = new Subject<IMessage>();
        watchMock.mockReturnValue(messages);

        const received: unknown[] = [];
        const first = websocketService.subscribe('/topic/shared').subscribe((value) => received.push(value));
        const second = websocketService.subscribe('/topic/shared').subscribe((value) => received.push(value));

        messages.next({ ...baseMessage, body: JSON.stringify({ data: 'test' }) });

        expect(received).toEqual([{ data: 'test' }, { data: 'test' }]);

        first.unsubscribe();
        second.unsubscribe();
    });

    it('drops an undecodable message and keeps the shared stream alive for all callers', () => {
        const messages = new Subject<IMessage>();
        watchMock.mockReturnValue(messages);

        const firstReceived: unknown[] = [];
        const secondReceived: unknown[] = [];
        let errored = false;
        const first = websocketService.subscribe('/topic/shared').subscribe({ next: (value) => firstReceived.push(value), error: () => (errored = true) });
        const second = websocketService.subscribe('/topic/shared').subscribe({ next: (value) => secondReceived.push(value), error: () => (errored = true) });

        // Claims to be compressed but is not, so decoding throws. Since every consumer of a destination shares one
        // subscription, this must not tear the stream down for any of them.
        messages.next({ ...baseMessage, body: 'not actually compressed', headers: { [COMPRESSION_HEADER_KEY]: 'true' } });
        messages.next({ ...baseMessage, body: JSON.stringify({ data: 'after the bad frame' }) });

        expect(errored).toBe(false);
        expect(captureExceptionMock).toHaveBeenCalled();
        expect(firstReceived).toEqual([{ data: 'after the bad frame' }]);
        expect(secondReceived).toEqual([{ data: 'after the bad frame' }]);
        expect(first.closed).toBe(false);
        expect(second.closed).toBe(false);

        first.unsubscribe();
        second.unsubscribe();
    });

    it('does not reuse a cached destination stream after the client was replaced by a reconnect', () => {
        watchMock.mockReturnValue(new Subject<IMessage>());

        const beforeReconnect = websocketService.subscribe('/topic/shared').subscribe();
        expect(watchMock).toHaveBeenCalledOnce();

        // connect() deactivates and replaces rxStomp. A cached stream belongs to the old client, so handing it out
        // again would leave the caller watching a dead client and receiving nothing.
        websocketService.connect();
        const afterReconnect = websocketService.subscribe('/topic/shared').subscribe();

        expect(watchMock).toHaveBeenCalledTimes(2);

        beforeReconnect.unsubscribe();
        afterReconnect.unsubscribe();
    });

    it('does not let a stale stream evict the cache entry of its replacement', () => {
        watchMock.mockReturnValue(new Subject<IMessage>());

        const stale = websocketService.subscribe('/topic/shared').subscribe();
        websocketService.connect();
        const current = websocketService.subscribe('/topic/shared').subscribe();
        expect(watchMock).toHaveBeenCalledTimes(2);

        // Tearing the old stream down must not remove the entry that now belongs to the new one, otherwise the next
        // caller opens a second subscription to a destination that is already subscribed.
        stale.unsubscribe();
        const later = websocketService.subscribe('/topic/shared').subscribe();

        expect(watchMock).toHaveBeenCalledTimes(2);

        current.unsubscribe();
        later.unsubscribe();
    });

    it('keeps serving one destination stream after the last caller unsubscribed', () => {
        const messages = new Subject<IMessage>();
        watchMock.mockReturnValue(messages);

        websocketService.subscribe('/topic/shared').subscribe().unsubscribe();

        // The entry survives an idle period. A caller that retained the observable (IrisSearchAnswerService does)
        // can resubscribe, and a concurrent fresh subscribe() must not open a second watch for the destination.
        const received: unknown[] = [];
        const later = websocketService.subscribe('/topic/shared').subscribe((value) => received.push(value));
        const concurrent = websocketService.subscribe('/topic/shared').subscribe();
        // one watch per period in which the destination has consumers
        expect(watchMock).toHaveBeenCalledTimes(2);

        messages.next({ ...baseMessage, body: JSON.stringify({ data: 'still flowing' }) });
        expect(received).toEqual([{ data: 'still flowing' }]);

        later.unsubscribe();
        concurrent.unsubscribe();
    });

    it('does not open a second live watch when a retained observable is resubscribed', () => {
        let liveWatches = 0;
        watchMock.mockImplementation(
            () =>
                new Observable<IMessage>(() => {
                    liveWatches++;
                    return () => liveWatches--;
                }),
        );

        // A service holding the observable as a field, resubscribing after its refcount dropped to zero.
        const retained = websocketService.subscribe('/topic/shared');
        retained.subscribe().unsubscribe();
        expect(liveWatches).toBe(0);
        const viaRetained = retained.subscribe();
        const viaFresh = websocketService.subscribe('/topic/shared').subscribe();

        expect(liveWatches).toBe(1);

        viaRetained.unsubscribe();
        viaFresh.unsubscribe();
        expect(liveWatches).toBe(0);
    });

    describe('STOMP subscription ids', () => {
        const subscriptionIds = () => watchMock.mock.calls.map(([params]) => params.subHeaders.id as string);

        beforeEach(() => {
            watchMock.mockReturnValue(new Subject<IMessage>());
        });

        it('uses a fresh id when a destination is subscribed again after its last caller left', () => {
            // The server handles the frames of one session concurrently, so the UNSUBSCRIBE of the first subscription can overtake the
            // SUBSCRIBE of the second one. With one id for both, the broker rejects the second as a duplicate and closes the connection.
            websocketService.subscribe('/topic/room').subscribe().unsubscribe();
            websocketService.subscribe('/topic/room').subscribe().unsubscribe();
            websocketService.subscribe('/topic/room').subscribe();

            const ids = subscriptionIds();
            expect(ids).toHaveLength(3);
            expect(new Set(ids).size).toBe(3);
        });

        it('uses a fresh id when a retained observable is resubscribed', () => {
            const retained = websocketService.subscribe('/topic/room');
            retained.subscribe().unsubscribe();
            retained.subscribe();

            const ids = subscriptionIds();
            expect(ids).toHaveLength(2);
            expect(ids[0]).not.toBe(ids[1]);
        });

        it('does not repeat an id across destinations or after a reconnect', () => {
            websocketService.subscribe('/topic/a').subscribe();
            websocketService.subscribe('/topic/b').subscribe();
            websocketService.connect();
            websocketService.subscribe('/topic/a').subscribe();
            websocketService.subscribe('/topic/b').subscribe();

            const ids = subscriptionIds();
            expect(ids).toHaveLength(4);
            expect(new Set(ids).size).toBe(4);
        });

        it('opens no watch before the first caller subscribes', () => {
            websocketService.subscribe('/topic/room');

            expect(watchMock).not.toHaveBeenCalled();
        });

        it('subscribes again with a fresh id after a disconnect and a new connection', () => {
            const beforeDisconnect = websocketService.subscribe('/topic/room').subscribe();
            websocketService.disconnect();
            beforeDisconnect.unsubscribe();
            websocketService.subscribe('/topic/room').subscribe();

            const ids = subscriptionIds();
            expect(ids).toHaveLength(2);
            expect(ids[0]).not.toBe(ids[1]);
            expect(constructedRxStompClients).toHaveLength(2);
        });
    });

    it('subscribes and parses compressed messages', async () => {
        // @ts-ignore
        const payload = (WebsocketService as any).compressAndEncode(JSON.stringify({ data: 'test' }));
        const message: IMessage = { ...baseMessage, body: payload, headers: { [COMPRESSION_HEADER_KEY]: 'true' } };
        watchMock.mockReturnValue(of(message));

        const result = await firstValueFrom(websocketService.subscribe('/topic/test')!);
        expect(result).toEqual({ data: 'test' });
    });

    it('reports decompression errors and drops the offending message without erroring the stream', () => {
        const decodeSpy = vi.spyOn(WebsocketService as any, 'decodeAndDecompress').mockImplementation(() => {
            throw new Error('boom');
        });
        const messages = new Subject<IMessage>();
        watchMock.mockReturnValue(messages);

        const received: unknown[] = [];
        let errored: unknown;
        const subscription = websocketService.subscribe('/topic/test').subscribe({ next: (value) => received.push(value), error: (error) => (errored = error) });

        messages.next({ ...baseMessage, body: '"raw"', headers: { [COMPRESSION_HEADER_KEY]: 'true' } });

        // The error used to be propagated to the subscriber, which terminated the stream. Consumers of a destination
        // now share a single subscription, so one bad frame would disconnect all of them. It is reported and dropped.
        expect(decodeSpy).toHaveBeenCalled();
        expect(captureExceptionMock).toHaveBeenCalledWith(expect.any(Error), {
            extra: { mechanism: 'websocket-decompression', message: 'Failed to decompress message' },
        });
        expect(errored).toBeUndefined();
        expect(received).toHaveLength(0);
        expect(subscription.closed).toBe(false);

        subscription.unsubscribe();
    });

    it('send does nothing when disconnected', () => {
        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];
        rxStomp.connected = vi.fn().mockReturnValue(false);
        websocketService.send('/test', { data: 'test' });
        expect(rxStomp.publish).not.toHaveBeenCalled();
    });

    it('sends uncompressed payloads when below threshold', () => {
        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];
        rxStomp.connected = vi.fn().mockReturnValue(true);

        websocketService.send('/test', { data: 'abc' });

        expect(rxStomp.publish).toHaveBeenCalledWith({
            destination: '/test',
            body: JSON.stringify({ data: 'abc' }),
            headers: {},
        });
    });

    it('sends compressed payloads and falls back if compression fails', () => {
        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];
        rxStomp.connected = vi.fn().mockReturnValue(true);

        const compressSpy = vi.spyOn(WebsocketService as any, 'compressAndEncode').mockReturnValue('compressed');
        websocketService.send('/test', { data: 'x'.repeat(2000) });
        expect(compressSpy).toHaveBeenCalled();
        expect(rxStomp.publish).toHaveBeenCalledWith({
            destination: '/test',
            body: 'compressed',
            headers: COMPRESSION_HEADER,
        });

        const errorSpy = vi.spyOn(WebsocketService as any, 'compressAndEncode').mockImplementation(() => {
            throw new Error('compress fail');
        });
        websocketService.send('/test', { data: 'x'.repeat(2000) });
        expect(errorSpy).toHaveBeenCalled();
        expect(captureExceptionMock).toHaveBeenCalledWith(expect.any(Error), {
            extra: { mechanism: 'websocket-compression', message: 'Failed to compress message' },
        });
        expect(rxStomp.publish).toHaveBeenCalledWith({
            destination: '/test',
            body: JSON.stringify({ data: 'x'.repeat(2000) }),
            headers: {},
        });
    });

    it('compressAndEncode and decodeAndDecompress form a reversible pair', () => {
        // @ts-ignore
        const encoded = (WebsocketService as any).compressAndEncode('hello world');
        // @ts-ignore
        const decoded = (WebsocketService as any).decodeAndDecompress(encoded);
        expect(decoded).toBe('hello world');
    });

    it('parseJSON returns raw payload on invalid JSON', () => {
        // @ts-ignore
        expect((WebsocketService as any).parseJSON('invalid-json')).toBe('invalid-json');
    });

    it('delegates ngOnDestroy to disconnect', () => {
        const disconnectSpy = vi.spyOn(websocketService, 'disconnect');
        websocketService.ngOnDestroy();
        expect(disconnectSpy).toHaveBeenCalled();
    });

    it('delays non-OPEN connection states by 5 seconds', () => {
        vi.useFakeTimers();
        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];

        // First connect
        rxStomp.connectionState$.next(RxStompState.OPEN);

        const stateChanges: ConnectionState[] = [];
        websocketService.connectionState.subscribe((state) => stateChanges.push(state));

        // Simulate connection loss
        stateChanges.length = 0;
        rxStomp.connectionState$.next(RxStompState.CLOSED);

        // No state change should be emitted immediately for non-OPEN states
        expect(stateChanges).toHaveLength(0);

        // After 4 seconds, still no change
        vi.advanceTimersByTime(4000);
        expect(stateChanges).toHaveLength(0);

        // After 5 seconds total, the CLOSED state should be emitted
        vi.advanceTimersByTime(1000);
        expect(stateChanges).toHaveLength(1);
        expect(stateChanges[0]).toEqual(new ConnectionState(false, true));

        vi.useRealTimers();
    });

    it('cancels delayed non-OPEN state if connection recovers within 5 seconds', () => {
        vi.useFakeTimers();
        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];

        // First connect
        rxStomp.connectionState$.next(RxStompState.OPEN);

        const stateChanges: ConnectionState[] = [];
        websocketService.connectionState.subscribe((state) => stateChanges.push(state));

        // Simulate connection loss
        stateChanges.length = 0;
        rxStomp.connectionState$.next(RxStompState.CLOSED);

        // No state change should be emitted immediately
        expect(stateChanges).toHaveLength(0);

        // After 3 seconds, connection recovers
        vi.advanceTimersByTime(3000);
        expect(stateChanges).toHaveLength(0);

        rxStomp.connectionState$.next(RxStompState.OPEN);

        // OPEN state should be emitted immediately
        expect(stateChanges).toHaveLength(1);
        expect(stateChanges[0]).toEqual(new ConnectionState(true, true));

        // Even after 5 more seconds, no CLOSED state should have been emitted
        vi.advanceTimersByTime(5000);
        expect(stateChanges).toHaveLength(1);

        vi.useRealTimers();
    });
});
