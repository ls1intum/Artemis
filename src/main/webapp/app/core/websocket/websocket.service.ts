import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subscriber, Subscription, first } from 'rxjs';
import SockJS from 'sockjs-client';
import Stomp, { Client, ConnectionHeaders, Message, Subscription as StompSubscription } from 'webstomp-client';
import { gzip, ungzip } from 'pako';
import { captureException } from '@sentry/angular';

interface SockJSExtended extends WebSocket {
    _transport?: {
        url?: string;
    };
}

// must be the same as in GzipMessageConverter.java
export const COMPRESSION_HEADER_KEY = 'X-Compressed';
export const COMPRESSION_HEADER: Record<string, string> = { [COMPRESSION_HEADER_KEY]: 'true' };

export interface IWebsocketService {
    /**
     * Callback function managing the amount of failed connection attempts and the timeout until the next reconnect attempt.
     */
    stompFailureCallback(): void;

    /**
     * Set up the websocket connection.
     */
    connect(): void;

    /**
     * Close the connection to the websocket and unsubscribe als observables.
     */
    disconnect(): void;

    /**
     * Creates a new observable (with subscriber) to receive websocket messages from the server
     * @param channel The channel the observable listens on
     */
    receive(channel: string): Observable<any>;

    /**
     * Send data through the websocket connection
     * @param path the path for the websocket connection
     * @param data the data to send through the websocket connection
     */
    send<T>(path: string, data: T): void;

    /**
     * Subscribe to a channel.
     * @param channel topic
     */
    subscribe(channel: string): IWebsocketService;

    /**
     * Unsubscribe a channel.
     * @param channel topic
     */
    unsubscribe(channel: string): void;

    /**
     * Enable automatic reconnect
     */
    enableReconnect(): void;

    /**
     * Disable automatic reconnect
     */
    disableReconnect(): void;

    /**
     * Get updates on current connection status
     */
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

    // we store the STOMP subscriptions per channel so that we can unsubscribe in case we are not interested any more
    private stompSubscriptions = new Map<string, StompSubscription>();
    // we store the observables per channel to make sure we can resubscribe them in case of connection issues
    private observables = new Map<string, Observable<any>>();
    // we store the subscribers (represent the components who want to receive messages) per channel so that we can notify them in case a message was received from the server
    private subscribers = new Map<string, Subscriber<any>>();
    // we store the subscription that waits for a connection before subscribing to a channel for the edge case: a component subscribes to a channel, but it already unsubscribes before a connection takes place
    private waitUntilConnectionSubscriptions = new Map<string, Subscription>();

    private alreadyConnectedOnce = false;
    private shouldReconnect = false;
    private readonly connectionStateInternal: BehaviorSubject<ConnectionState>;
    private consecutiveFailedAttempts = 0;
    private connecting = false;
    private socket: SockJSExtended | undefined = undefined;
    private subscriptionCounter = 0;

    constructor() {
        this.connectionStateInternal = new BehaviorSubject<ConnectionState>(new ConnectionState(false, false, true));
    }

    get connectionState(): Observable<ConnectionState> {
        return this.connectionStateInternal.asObservable();
    }

    /**
     * Callback function managing the amount of failed connection attempts to the websocket and the timeout until the next reconnect attempt.
     *
     * Wait 5 seconds before reconnecting in case the connection does not work or the client is disconnected,
     * after  2 failed attempts in row, increase the timeout to 10 seconds,
     * after  4 failed attempts in row, increase the timeout to 20 seconds
     * after  8 failed attempts in row, increase the timeout to 60 seconds
     * after 12 failed attempts in row, increase the timeout to 120 seconds
     * after 16 failed attempts in row, increase the timeout to 300 seconds
     * after 20 failed attempts in row, increase the timeout to 600 seconds
     */
    stompFailureCallback() {
        this.connecting = false;
        this.consecutiveFailedAttempts++;
        if (this.connectionStateInternal.getValue().connected) {
            this.connectionStateInternal.next(new ConnectionState(false, this.alreadyConnectedOnce, false));
        }
        if (this.shouldReconnect) {
            let waitUntilReconnectAttempt;
            if (this.consecutiveFailedAttempts > 20) {
                // NOTE: normally a user would reload here anyway
                waitUntilReconnectAttempt = 600;
            } else if (this.consecutiveFailedAttempts > 16) {
                // NOTE: normally a user would reload here anyway
                waitUntilReconnectAttempt = 300;
            } else if (this.consecutiveFailedAttempts > 12) {
                waitUntilReconnectAttempt = 120;
            } else if (this.consecutiveFailedAttempts > 8) {
                waitUntilReconnectAttempt = 60;
            } else if (this.consecutiveFailedAttempts > 4) {
                waitUntilReconnectAttempt = 20;
            } else if (this.consecutiveFailedAttempts > 2) {
                waitUntilReconnectAttempt = 10;
            } else {
                waitUntilReconnectAttempt = 5;
            }
            setTimeout(this.connect.bind(this), waitUntilReconnectAttempt * 1000);
        }
    }

    /**
     * Set up the websocket connection.
     */
    connect() {
        if (this.isConnected() || this.connecting) {
            return; // don't connect, if already connected or connecting
        }
        this.connecting = true;
        const url = `//${window.location.host}/websocket`;
        // NOTE: only support real websockets transports and disable http poll, http stream and other exotic workarounds.
        // nowadays, all modern browsers support websockets and workarounds are not necessary anymore and might only lead to problems
        this.socket = new SockJS(url, undefined, { transports: 'websocket' });
        const options = {
            heartbeat: { outgoing: 10000, incoming: 10000 },
            debug: false,
            protocols: ['v12.stomp'],
        };
        // TODO: consider to switch to RxStomp (like in the latest jhipster version)
        this.stompClient = Stomp.over(this.socket, options);
        // Note: at the moment, debugging is deactivated to prevent console log statements
        this.stompClient.debug = () => {};
        const headers = {} as ConnectionHeaders;

        this.stompClient.connect(
            headers,
            () => {
                this.connecting = false;
                if (!this.connectionStateInternal.getValue().connected) {
                    this.connectionStateInternal.next(new ConnectionState(true, this.alreadyConnectedOnce, false));
                }
                this.consecutiveFailedAttempts = 0;
                if (this.alreadyConnectedOnce) {
                    // (re)connect to all existing channels
                    if (this.observables.size !== 0) {
                        this.observables.forEach((_observable, channel) => this.addSubscription(channel));
                    }
                } else {
                    this.alreadyConnectedOnce = true;
                }
            },
            this.stompFailureCallback.bind(this),
        );
    }

    /**
     * Adds a STOMP subscription to the subscribers to receive messages for specific channels
     * @param channel the path (e.g. '/courses/5/exercises/10') that should be subscribed
     */
    private addSubscription(channel: string) {
        const subscription = this.stompClient!.subscribe(channel, this.handleIncomingMessage(channel), {
            id: this.getSessionId() + '-' + this.subscriptionCounter++,
        });
        this.stompSubscriptions.set(channel, subscription);
    }

    /**
     * Handle incoming messages from the server, which are potentially compressed:
     * 1. Decode the Base64 string to binary data
     * 2. Decompress the binary data to a string payload (JSON)
     * 3. Parse the JSON payload and pass it to the subscribers
     * @param channel the channel the message was received on
     */
    private handleIncomingMessage(channel: string) {
        return (message: Message) => {
            // this code is invoked if a new websocket message was received from the server
            // we pass the message to the subscriber (e.g. a component who will be notified and can handle the message)
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
     * Compresses a given string payload using GZIP and encodes the compressed data into a Base64 string.
     *
     * <p>This method performs the following steps:
     * <ol>
     *   <li>Compresses the input string using GZIP.</li>
     *   <li>Converts the compressed binary data into a Base64-encoded string.</li>
     * </ol>
     *
     * @param payload The string payload to be compressed and encoded.
     * @returns A Base64-encoded string representing the compressed payload.
     * @throws Error If compression or Base64 encoding fails.
     */
    private static compressAndEncode(payload: string): string {
        // 1. Compress if larger than 1 KB
        const compressedPayload = gzip(payload);
        // 2. Convert binary data to base64 string
        return window.btoa(
            Array.from(compressedPayload)
                .map((byte) => String.fromCharCode(byte))
                .join(''),
        );
    }

    /**
     * Decodes a Base64-encoded string and decompresses the resulting binary data using GZIP.
     *
     * <p>This method performs the following steps:
     * <ol>
     *   <li>Decodes the Base64-encoded string into binary data.</li>
     *   <li>Decompresses the binary data using GZIP.</li>
     * </ol>
     *
     * @param payload The Base64-encoded string representing compressed data.
     * @returns The decompressed string.
     * @throws Error If decoding or decompression fails.
     */
    private static decodeAndDecompress(payload: string): string {
        // 1. Decode the Base64 string to binary (ArrayBuffer) and convert to Uint8Array
        const binaryData = Uint8Array.from(window.atob(payload), (char) => char.charCodeAt(0));
        // 2. Decompress using pako
        return ungzip(binaryData, { to: 'string' });
    }

    /**
     * Checks whether the WebSocket connection is currently established.
     *
     * @returns true if the WebSocket connection is active; otherwise, false.
     */
    public isConnected(): boolean {
        return this.stompClient?.connected || false;
    }

    /**
     * Close the connection to the websocket (e.g. due to logout), unsubscribe all observables and set alreadyConnectedOnce to false
     */
    disconnect() {
        this.observables.forEach((_observable, channel) => this.unsubscribe(channel));
        this.waitUntilConnectionSubscriptions.forEach((subscription) => subscription.unsubscribe());
        if (this.stompClient) {
            this.stompClient.disconnect();
            this.stompClient = undefined;
            if (this.connectionStateInternal.getValue().connected || !this.connectionStateInternal.getValue().intendedDisconnect) {
                this.connectionStateInternal.next(new ConnectionState(false, this.alreadyConnectedOnce, true));
            }
        }
        this.alreadyConnectedOnce = false;
    }

    /**
     * Creates a new observable  in case there is no observable for the passed channel yet.
     * Returns the Observable which is invoked when a new message is received
     * @param channel The channel the observable listens on
     */
    receive(channel: string): Observable<any> {
        if (channel != undefined && (this.observables.size === 0 || !this.observables.has(channel))) {
            this.observables.set(channel, this.createObservable(channel));
        }
        return this.observables.get(channel)!;
    }

    /**
     * Send data through the websocket connection, potentially compressing the payload.
     * Only compresses data if the JSON stringified payload size is larger than 1 KB.
     * 1. Convert the data into JSON
     * 2. Compress the JSON payload into binary data if it is larger than 1 KB
     * 3. Convert the binary data into a Base64 string
     *
     * @param path the path for the websocket connection
     * @param data the data to send through the websocket connection
     */
    send<T>(path: string, data: T): void {
        if (this.isConnected()) {
            const jsonPayload = JSON.stringify(data);
            const payloadSize = new Blob([jsonPayload]).size; // Measure payload size

            if (payloadSize > 1024) {
                try {
                    const base64StringPayload = WebsocketService.compressAndEncode(jsonPayload);
                    this.stompClient!.send(path, base64StringPayload, COMPRESSION_HEADER);
                } catch (error) {
                    captureException('Failed to compress websocket message', error);
                    // Send uncompressed payload if an error occurs
                    this.stompClient!.send(path, jsonPayload, {});
                }
            } else {
                // Send uncompressed payload
                this.stompClient!.send(path, jsonPayload, {});
            }
        }
    }

    /**
     * Subscribe to a channel: add the channel to the observables and create a STOMP subscription for the channel if this has not been done before
     * @param channel
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
     * Unsubscribe a channel if the component is not interested in the messages anymore
     * @param channel topic for which the component wants to unsubscribe
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
     * Create a new observable and store the corresponding subscriber so that we can invoke it when a new message was received
     * @param channel The channel to listen on.
     */
    private createObservable<T>(channel: string): Observable<T> {
        return new Observable((subscriber: Subscriber<T>) => {
            this.subscribers.set(channel, subscriber);
        });
    }

    /**
     * Enable automatic reconnect
     */
    enableReconnect() {
        if (this.stompClient && !this.stompClient.connected) {
            this.connect();
        }
        this.shouldReconnect = true;
    }

    /**
     * Disable automatic reconnect
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
     * Parses a JSON string into an object of the specified generic type.
     *
     * <p>This method attempts to parse the provided JSON string. If parsing fails,
     * it returns the input string cast to the specified type. This can be useful
     * for handling cases where the response might not always be a valid JSON string.</p>
     *
     * @param response The JSON string to be parsed.
     * @returns The parsed object of the specified type, or the input string cast to the type if parsing fails.
     * @template T The type of the object to return after parsing.
     * @throws Error If JSON parsing fails and the input is not a valid string cast to the specified type.
     */
    private static parseJSON<T>(response: string): T {
        try {
            return JSON.parse(response);
        } catch {
            return response as T;
        }
    }

    // see https://stackoverflow.com/a/35651029/3802758
    private getSessionId(): string {
        const url = this.socket?._transport?.url;
        const match = url?.match('.*\\/websocket\\/\\d*\\/(.*)\\/websocket.*');
        return match ? match[1] : 'unsubscribed';
    }
}
