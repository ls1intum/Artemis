import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { COMPRESSION_HEADER, COMPRESSION_HEADER_KEY, ConnectionState, WebsocketService } from 'app/foundation/service/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { RxStompState } from '@stomp/rx-stomp';
import { BehaviorSubject, EMPTY, filter, firstValueFrom, of } from 'rxjs';
import { IMessage } from '@stomp/stompjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

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

vi.mock('@sentry/angular', () => ({
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
    setupTestBed({ zoneless: true });

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

    it('subscribes and parses compressed messages', async () => {
        // @ts-ignore
        const payload = (WebsocketService as any).compressAndEncode(JSON.stringify({ data: 'test' }));
        const message: IMessage = { ...baseMessage, body: payload, headers: { [COMPRESSION_HEADER_KEY]: 'true' } };
        watchMock.mockReturnValue(of(message));

        const result = await firstValueFrom(websocketService.subscribe('/topic/test')!);
        expect(result).toEqual({ data: 'test' });
    });

    it('reports decompression errors and propagates them', async () => {
        const decodeSpy = vi.spyOn(WebsocketService as any, 'decodeAndDecompress').mockImplementation(() => {
            throw new Error('boom');
        });
        const message: IMessage = { ...baseMessage, body: '"raw"', headers: { [COMPRESSION_HEADER_KEY]: 'true' } };
        watchMock.mockReturnValue(of(message));

        const resultPromise = firstValueFrom(websocketService.subscribe('/topic/test')!);
        await expect(resultPromise).rejects.toThrow('boom');
        expect(decodeSpy).toHaveBeenCalled();
        expect(captureExceptionMock).toHaveBeenCalledWith(expect.any(Error), {
            mechanism: { handled: true, type: 'websocket-decompression', data: { message: 'Failed to decompress message' } },
        });
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
            mechanism: { handled: true, type: 'websocket-compression', data: { message: 'Failed to compress message' } },
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
