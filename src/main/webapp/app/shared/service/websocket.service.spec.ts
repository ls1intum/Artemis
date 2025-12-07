import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { COMPRESSION_HEADER, COMPRESSION_HEADER_KEY, ConnectionState, WebsocketService } from 'app/shared/service/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { RxStompState } from '@stomp/rx-stomp';
import { BehaviorSubject, EMPTY, filter, firstValueFrom, of } from 'rxjs';
import { IMessage } from '@stomp/stompjs';

const constructedRxStompClients: any[] = [];
const watchMock = jest.fn();

jest.mock('@stomp/rx-stomp', () => {
    const { RxStompState: ActualRxStompState } = jest.requireActual('@stomp/rx-stomp');
    return {
        TickerStrategy: { Worker: 'worker-ticker' },
        RxStompState: ActualRxStompState,
        RxStomp: jest.fn().mockImplementation(() => {
            const client = {
                configure: jest.fn(),
                activate: jest.fn(),
                deactivate: jest.fn(),
                publish: jest.fn(),
                watch: watchMock,
                connected: jest.fn().mockReturnValue(true),
                connectionState$: new BehaviorSubject<RxStompState>(ActualRxStompState.CLOSED),
                stompClient: {
                    unsubscribe: jest.fn(),
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

const captureExceptionMock = jest.fn();
jest.mock('@sentry/angular', () => ({
    captureException: (...args: any[]) => captureExceptionMock(...args),
}));

const baseMessage: IMessage = {
    body: '',
    headers: {},
    ack: jest.fn(),
    nack: jest.fn(),
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
        jest.restoreAllMocks();
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
        rxStomp.connected = jest.fn().mockReturnValue(true);

        let latestState: ConnectionState | undefined;
        websocketService.connectionState.subscribe((state) => (latestState = state));

        websocketService.disconnect();

        expect(rxStomp.deactivate).toHaveBeenCalled();
        expect((websocketService as any).rxStomp).toBeUndefined();
        expect(latestState).toEqual(new ConnectionState(false, false));
    });

    it('reports connection status via isConnected', () => {
        expect(websocketService.isConnected()).toBeFalse();
        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];
        rxStomp.connected = jest.fn().mockReturnValue(true);
        expect(websocketService.isConnected()).toBeTrue();
        rxStomp.connected = jest.fn().mockReturnValue(false);
        expect(websocketService.isConnected()).toBeFalse();
    });

    it('returns EMPTY observable when subscribing without a channel', async () => {
        const obs = websocketService.subscribe('');
        let completed = false;
        await new Promise<void>((resolve) => {
            obs.subscribe({ complete: () => (completed = true) }).add(() => resolve());
        });
        expect(completed).toBeTrue();
        expect(watchMock).not.toHaveBeenCalled();
    });

    it('returns EMPTY when no client is available after connect attempt', () => {
        jest.spyOn(websocketService, 'connect').mockImplementation(() => {
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
        expect(watchMock).toHaveBeenCalledWith({ destination: '/topic/test', subHeaders: { id: '/topic/test' } });
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
        const decodeSpy = jest.spyOn(WebsocketService as any, 'decodeAndDecompress').mockImplementation(() => {
            throw new Error('boom');
        });
        const message: IMessage = { ...baseMessage, body: '"raw"', headers: { [COMPRESSION_HEADER_KEY]: 'true' } };
        watchMock.mockReturnValue(of(message));

        const resultPromise = firstValueFrom(websocketService.subscribe('/topic/test')!);
        await expect(resultPromise).rejects.toThrow('boom');
        expect(decodeSpy).toHaveBeenCalled();
        expect(captureExceptionMock).toHaveBeenCalledWith('Failed to decompress message', expect.any(Error));
    });

    it('send does nothing when disconnected', () => {
        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];
        rxStomp.connected = jest.fn().mockReturnValue(false);
        websocketService.send('/test', { data: 'test' });
        expect(rxStomp.publish).not.toHaveBeenCalled();
    });

    it('sends uncompressed payloads when below threshold', () => {
        websocketService.connect();
        const rxStomp = constructedRxStompClients[0];
        rxStomp.connected = jest.fn().mockReturnValue(true);

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
        rxStomp.connected = jest.fn().mockReturnValue(true);

        const compressSpy = jest.spyOn(WebsocketService as any, 'compressAndEncode').mockReturnValue('compressed');
        websocketService.send('/test', { data: 'x'.repeat(2000) });
        expect(compressSpy).toHaveBeenCalled();
        expect(rxStomp.publish).toHaveBeenCalledWith({
            destination: '/test',
            body: 'compressed',
            headers: COMPRESSION_HEADER,
        });

        const errorSpy = jest.spyOn(WebsocketService as any, 'compressAndEncode').mockImplementation(() => {
            throw new Error('compress fail');
        });
        websocketService.send('/test', { data: 'x'.repeat(2000) });
        expect(errorSpy).toHaveBeenCalled();
        expect(captureExceptionMock).toHaveBeenCalledWith('Failed to compress websocket message', expect.any(Error));
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
        const disconnectSpy = jest.spyOn(websocketService, 'disconnect');
        websocketService.ngOnDestroy();
        expect(disconnectSpy).toHaveBeenCalled();
    });
});
