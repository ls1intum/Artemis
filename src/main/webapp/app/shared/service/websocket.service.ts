import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subscriber, Subscription, first } from 'rxjs';
import Stomp, { Client, ConnectionHeaders, Frame, Message, Subscription as StompSubscription } from 'webstomp-client';
import { gzip, ungzip } from 'pako';
import { captureException } from '@sentry/angular';

// must be the same as in GzipMessageConverter.java
export const COMPRESSION_HEADER_KEY = 'X-Compressed';
export const COMPRESSION_HEADER: Record<string, string> = { [COMPRESSION_HEADER_KEY]: 'true' };

export interface IWebsocketService {
    stompFailureCallback(): void;
    connect(): void;
    disconnect(): void;
    receive(channel: string): Observable<any>;
    send<T>(path: string, data: T): void;
    subscribe(channel: string): IWebsocketService;
    unsubscribe(channel: string): void;
    enableReconnect(): void;
    disableReconnect(): void;
    get connectionState(): Observable<ConnectionState>;
}

export class ConnectionState {
    readonly connected: boolean;
    readonly wasEverConnectedBefore: boolean;
    readonly intendedDisconnect: boolean;

    constructor(connected: boolean, wasEverConnectedBefore: boolean, intendedDisconnect: boolean) {
        this.connected = connected;
        this.wasEverConnectedBefore = wasEverConnectedBefore;
        this.intendedDisconnect = intendedDisconnect;
    }
}

/**
 * Server <1--1> Stomp <1--1> websocket.service.ts <1--n*m> Angular components * channel topic
 */
@Injectable({ providedIn: 'root' })
export class WebsocketService implements IWebsocketService, OnDestroy {
    private stompClient?: Client;

    // STOMP + observable plumbing
    private stompSubscriptions = new Map<string, StompSubscription>();
    private observables = new Map<string, Observable<any>>();
    private subscribers = new Map<string, Subscriber<any>>();
    private waitUntilConnectionSubscriptions = new Map<string, Subscription>();

    // connection/reconnect state
    private alreadyConnectedOnce = false;
    private shouldReconnect = false;
    private readonly connectionStateInternal: BehaviorSubject<ConnectionState>;
    private consecutiveFailedAttempts = 0;
    private connecting = false;
    private subscriptionCounter = 0;
    private sessionId = '';

    // fast-fail/health
    private watchdogTimer?: number;
    private lastActivityTs = 0;

    constructor() {
        this.connectionStateInternal = new BehaviorSubject<ConnectionState>(new ConnectionState(false, false, true));

        // React instantly to network changes
        window.addEventListener('offline', () => {
            try {
                this.getNativeWS()?.close(4001, 'browser-offline');
            } catch {
                /* empty */
            }
            this.stompFailureCallback();
        });

        window.addEventListener('online', () => {
            if (this.shouldReconnect && !this.isConnected() && !this.connecting) {
                this.connect();
            }
        });
    }

    get connectionState(): Observable<ConnectionState> {
        return this.connectionStateInternal.asObservable();
    }

    /**
     * Callback managing failed attempts and backoff.
     */
    stompFailureCallback() {
        // eslint-disable-next-line no-undef
        console.log('stompFailureCallback');
        this.connecting = false;
        this.consecutiveFailedAttempts++;
        // eslint-disable-next-line no-undef
        console.log('failed attempts: ', this.consecutiveFailedAttempts);
        if (this.connectionStateInternal.getValue().connected) {
            this.connectionStateInternal.next(new ConnectionState(false, this.alreadyConnectedOnce, false));
        }
        // eslint-disable-next-line no-undef
        console.log('should reconnect: ', this.shouldReconnect);
        if (this.shouldReconnect) {
            let waitUntilReconnectAttempt: number;
            if (this.consecutiveFailedAttempts > 20) waitUntilReconnectAttempt = 600;
            else if (this.consecutiveFailedAttempts > 16) waitUntilReconnectAttempt = 300;
            else if (this.consecutiveFailedAttempts > 12) waitUntilReconnectAttempt = 120;
            else if (this.consecutiveFailedAttempts > 8) waitUntilReconnectAttempt = 60;
            else if (this.consecutiveFailedAttempts > 4) waitUntilReconnectAttempt = 20;
            else if (this.consecutiveFailedAttempts > 2) waitUntilReconnectAttempt = 10;
            else waitUntilReconnectAttempt = 5;

            setTimeout(this.connect.bind(this), waitUntilReconnectAttempt * 1000);
        }
    }

    /**
     * Generates an 8-character secure session id.
     */
    private generateSecureSessionId() {
        const byteArray = window.crypto.getRandomValues(new Uint8Array(4));
        const hexValues = Array.from(byteArray, (byte) => byte.toString(16).padStart(2, '0'));
        return hexValues.join('');
    }

    /**
     * Reach the native WebSocket created by webstomp-client.
     */
    private getNativeWS(): WebSocket | undefined {
        return (this.stompClient as any)?.ws as WebSocket | undefined;
    }

    /**
     * Start a watchdog that forces a reconnect on silent drops.
     */
    private startWatchdog() {
        this.stopWatchdog();
        this.lastActivityTs = Date.now();

        const CHECK_EVERY_MS = 3000;
        const EXPECTED_BEAT_MS = 10000; // matches heartbeat.incoming
        const GRACE_MS = 5000;

        this.watchdogTimer = window.setInterval(() => {
            const ws = this.getNativeWS();
            if (!ws || ws.readyState !== WebSocket.OPEN) return;

            const idle = Date.now() - this.lastActivityTs;
            if (idle > EXPECTED_BEAT_MS + GRACE_MS) {
                try {
                    ws.close(4000, 'watchdog-timeout');
                } catch {
                    /* empty */
                }
                this.stompFailureCallback();
            }
        }, CHECK_EVERY_MS);
    }

    private stopWatchdog() {
        if (this.watchdogTimer) {
            window.clearInterval(this.watchdogTimer);
            this.watchdogTimer = undefined;
        }
    }

    /**
     * Set up the websocket connection.
     */
    connect() {
        if (this.isConnected() || this.connecting) {
            return; // already connected or connecting
        }
        this.connecting = true;

        // NOTE: we add 'websocket' twice to use STOMP without SockJS
        const url = `//${window.location.host}/websocket/websocket`;
        const options = {
            heartbeat: { outgoing: 10000, incoming: 10000 },
            debug: false,
            protocols: ['v12.stomp'],
        } as const;

        // TODO: consider to switch to RxStomp (like in the latest jhipster version)
        this.stompClient = Stomp.client(url, options);
        this.stompClient.debug = () => {};
        const headers = {} as ConnectionHeaders;

        // DIY connect timeout (10s)
        const connectTimeout = window.setTimeout(() => {
            if (this.connecting) {
                try {
                    this.getNativeWS()?.close(4002, 'connect-timeout');
                } catch {
                    /* empty */
                }
                this.stompFailureCallback();
            }
        }, 10000);

        this.stompClient.connect(
            headers,
            (frame?: Frame) => {
                window.clearTimeout(connectTimeout);
                // check if a session id is part of the frame, otherwise use a random string
                this.sessionId = frame?.headers['session'] || this.generateSecureSessionId();
                this.connecting = false;
                if (!this.connectionStateInternal.getValue().connected) {
                    this.connectionStateInternal.next(new ConnectionState(true, this.alreadyConnectedOnce, false));
                }
                this.consecutiveFailedAttempts = 0;
                this.lastActivityTs = Date.now();
                this.startWatchdog();

                if (this.alreadyConnectedOnce) {
                    // (re)connect to all existing channels
                    if (this.observables.size !== 0) {
                        this.observables.forEach((_observable, channel) => this.addSubscription(channel));
                    }
                } else {
                    this.alreadyConnectedOnce = true;
                }
            },
            (_err?: Frame | CloseEvent) => {
                window.clearTimeout(connectTimeout);
                this.stompFailureCallback();
            },
        );
    }

    /**
     * Adds a STOMP subscription for a channel.
     */
    private addSubscription(channel: string) {
        const subscription = this.stompClient!.subscribe(channel, this.handleIncomingMessage(channel), {
            id: this.sessionId + '-' + this.subscriptionCounter++,
        });
        this.stompSubscriptions.set(channel, subscription);
    }

    /**
     * Handle incoming messages (supports optional gzip payloads).
     */
    private handleIncomingMessage(channel: string) {
        return (message: Message) => {
            this.lastActivityTs = Date.now(); // activity bump for watchdog

            if (this.subscribers.has(channel)) {
                const isCompressed = message.headers[COMPRESSION_HEADER_KEY] === 'true';
                let payload = message.body;

                if (isCompressed) {
                    try {
                        payload = WebsocketService.decodeAndDecompress(payload);
                    } catch (error) {
                        captureException('Failed to decompress message', error);
                    }
                }

                this.subscribers.get(channel)!.next(WebsocketService.parseJSON<object>(payload));
            }
        };
    }

    /**
     * Compresses a given string payload using GZIP and encodes to Base64.
     */
    private static compressAndEncode(payload: string): string {
        const compressedPayload = gzip(payload);
        return window.btoa(
            Array.from(compressedPayload)
                .map((byte) => String.fromCharCode(byte))
                .join(''),
        );
    }

    /**
     * Decodes Base64 and ungzips.
     */
    private static decodeAndDecompress(payload: string): string {
        const binaryData = Uint8Array.from(window.atob(payload), (char) => char.charCodeAt(0));
        return ungzip(binaryData, { to: 'string' });
    }

    /**
     * Are we truly connected? (stomp + native WS OPEN)
     */
    public isConnected(): boolean {
        const ws = this.getNativeWS();
        const stompOk = !!this.stompClient?.connected;
        const wsOk = ws?.readyState === WebSocket.OPEN;
        return stompOk && wsOk;
    }

    /**
     * Close the connection and clean up.
     */
    disconnect() {
        this.stopWatchdog();

        this.observables.forEach((_observable, channel) => this.unsubscribe(channel));
        this.waitUntilConnectionSubscriptions.forEach((subscription) => subscription.unsubscribe());

        if (this.stompClient) {
            try {
                this.getNativeWS()?.close(4003, 'manual-disconnect');
            } catch {
                /* empty */
            }
            this.stompClient.disconnect();
            this.stompClient = undefined;
            if (this.connectionStateInternal.getValue().connected || !this.connectionStateInternal.getValue().intendedDisconnect) {
                this.connectionStateInternal.next(new ConnectionState(false, this.alreadyConnectedOnce, true));
            }
        }
        this.alreadyConnectedOnce = false;
    }

    /**
     * Observable factory per channel.
     */
    receive(channel: string): Observable<any> {
        if (channel != undefined && (this.observables.size === 0 || !this.observables.has(channel))) {
            this.observables.set(channel, this.createObservable(channel));
        }
        return this.observables.get(channel)!;
    }

    /**
     * Safe send with native readyState guard + optional compression.
     */
    send<T>(path: string, data: T): void {
        const ws = this.getNativeWS();
        if (!this.stompClient?.connected || !ws || ws.readyState !== WebSocket.OPEN) {
            // Optionally: buffer for later or log
            return;
        }

        const jsonPayload = JSON.stringify(data);
        const payloadSize = new Blob([jsonPayload]).size;

        if (payloadSize > 1024) {
            try {
                const base64StringPayload = WebsocketService.compressAndEncode(jsonPayload);
                this.stompClient!.send(path, base64StringPayload, COMPRESSION_HEADER);
            } catch (error) {
                captureException('Failed to compress websocket message', error);
                this.stompClient!.send(path, jsonPayload, {});
            }
        } else {
            this.stompClient!.send(path, jsonPayload, {});
        }
    }

    /**
     * Subscribe to a channel.
     */
    subscribe(channel: string): IWebsocketService {
        if (channel == undefined) {
            return this;
        }
        const subscription = this.connectionState.pipe(first((connectionState) => connectionState.connected)).subscribe(() => {
            if (!this.observables.has(channel)) {
                this.observables.set(channel, this.createObservable(channel));
            }
            if (!this.stompSubscriptions.has(channel)) {
                this.addSubscription(channel);
            }
        });
        this.waitUntilConnectionSubscriptions.set(channel, subscription);
        return this;
    }

    /**
     * Unsubscribe a channel.
     */
    unsubscribe(channel: string) {
        if (this && this.stompSubscriptions && this.stompSubscriptions.has(channel)) {
            this.stompSubscriptions.get(channel)!.unsubscribe();
            this.stompSubscriptions.delete(channel);
            this.observables.delete(channel);
            this.subscribers.delete(channel);
            if (this.waitUntilConnectionSubscriptions.has(channel)) {
                this.waitUntilConnectionSubscriptions.get(channel)!.unsubscribe();
                this.waitUntilConnectionSubscriptions.delete(channel);
            }
        }
    }

    /**
     * Create a new observable and store its subscriber.
     */
    private createObservable<T>(channel: string): Observable<T> {
        return new Observable((subscriber: Subscriber<T>) => {
            this.subscribers.set(channel, subscriber);
        });
    }

    /**
     * Enable automatic reconnect.
     */
    enableReconnect() {
        if (this.stompClient && !this.stompClient.connected) {
            this.connect();
        }
        this.shouldReconnect = true;
    }

    /**
     * Disable automatic reconnect.
     */
    disableReconnect() {
        this.shouldReconnect = false;
    }

    /**
     * On destroy disconnect.
     */
    ngOnDestroy(): void {
        this.disconnect();
    }

    /**
     * Safe JSON parse.
     */
    private static parseJSON<T>(response: string): T {
        try {
            return JSON.parse(response);
        } catch {
            return response as T;
        }
    }
}
