import { Injectable, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, Observer, Subscription } from 'rxjs';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';
import SockJS from 'sockjs-client';
import Stomp, { Client, ConnectionHeaders, Subscription as StompSubscription } from 'webstomp-client';

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
     * Close the connection to the websocket and unsubscribe als listeners.
     */
    disconnect(): void;

    /**
     * Add a new listener.
     * @param channel The channel the listener listens on
     */
    receive(channel: string): Observable<any>;

    /**
     * Send data through the websocket connection
     * @param path {string} the path for the websocket connection
     * @param data {object} the data to send through the websocket connection
     */
    send(path: string, data: any): void;

    /**
     * Subscribe to a channel.
     * @param channel
     */
    subscribe(channel: string): void;

    /**
     * Unsubscribe a channel.
     * @param channel
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

@Injectable({ providedIn: 'root' })
export class JhiWebsocketService implements IWebsocketService, OnDestroy {
    stompClient: Client | null;
    connection: Promise<void>;
    connectedPromise: Function;
    subscribers = new Map<string, StompSubscription>();
    myListeners = new Map<string, Observable<any>>();
    listenerObservers = new Map<string, Observer<any>>();
    alreadyConnectedOnce = false;
    private subscription: Subscription | null;
    shouldReconnect = false;
    private readonly connectionStateInternal: BehaviorSubject<ConnectionState>;
    consecutiveFailedAttempts = 0;
    connecting = false;

    private socket: any = undefined;
    private subscriptionCounter = 0;

    constructor(private router: Router, private authServerProvider: AuthServerProvider) {
        this.connectionStateInternal = new BehaviorSubject<ConnectionState>(new ConnectionState(false, false, true));
        this.connection = this.createConnection();
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
        if (!this.connectedPromise) {
            this.connection = this.createConnection();
        }
        let url = `//${window.location.host}/websocket/tracker`;
        const authToken = this.authServerProvider.getToken();
        if (authToken) {
            url += '?access_token=' + authToken;
        }
        // NOTE: only support real websockets transports and disable http poll, http stream and other exotic workarounds.
        // nowadays, all modern browsers support websockets and workarounds are not necessary anymore and might only lead to problems
        this.socket = new SockJS(url, undefined, { transports: 'websocket' });
        const options = {
            heartbeat: { outgoing: 10000, incoming: 10000 },
            debug: false,
            protocols: ['v12.stomp'],
        };
        this.stompClient = Stomp.over(this.socket, options);
        // Note: at the moment, debugging is deactivated to prevent console log statements
        this.stompClient.debug = function () {};
        const headers = <ConnectionHeaders>{};

        this.stompClient.connect(
            headers,
            () => {
                this.connectedPromise('success');
                this.connecting = false;
                if (!this.connectionStateInternal.getValue().connected) {
                    this.connectionStateInternal.next(new ConnectionState(true, this.alreadyConnectedOnce, false));
                }
                this.consecutiveFailedAttempts = 0;
                if (this.alreadyConnectedOnce) {
                    // (re)connect to all existing channels
                    if (this.myListeners.size !== 0) {
                        this.myListeners.forEach((listener, channel) => {
                            this.subscribers.set(
                                channel,
                                this.stompClient!.subscribe(
                                    channel,
                                    (data) => {
                                        if (this.listenerObservers.has(channel)) {
                                            this.listenerObservers.get(channel)!.next(JSON.parse(data.body));
                                        }
                                    },
                                    {
                                        id: this.getSessionId() + '-' + this.subscriptionCounter++,
                                    },
                                ),
                            );
                        });
                    }
                } else {
                    this.alreadyConnectedOnce = true;
                }
            },
            this.stompFailureCallback.bind(this),
        );
    }

    private isConnected(): boolean {
        return !!this.stompClient?.connected;
    }

    /**
     * Close the connection to the websocket, unsubscribe all listeners and distribute "intended disconnect" state.
     */
    disconnect() {
        this.connection = this.createConnection();
        Object.keys(this.myListeners).forEach((listener) => this.unsubscribe(listener), this);
        if (this.stompClient) {
            this.stompClient.disconnect();
            this.stompClient = null;
            if (this.connectionStateInternal.getValue().connected || !this.connectionStateInternal.getValue().intendedDisconnect) {
                this.connectionStateInternal.next(new ConnectionState(false, this.alreadyConnectedOnce, true));
            }
        }
        if (this.subscription) {
            this.subscription.unsubscribe();
            this.subscription = null;
        }
        this.alreadyConnectedOnce = false;
    }

    /**
     * Add a new listener.
     * @param channel The channel the listener listens on
     */
    receive(channel: string): Observable<any> {
        if (channel != undefined && (this.myListeners.size === 0 || !this.myListeners.has(channel))) {
            this.myListeners.set(channel, this.createListener(channel));
        }
        return this.myListeners.get(channel)!;
    }

    /**
     * Send data through the websocket connection
     * @param path {string} the path for the websocket connection
     * @param data {object} the data to send through the websocket connection
     */
    send(path: string, data: any): void {
        if (this.isConnected()) {
            this.stompClient!.send(path, JSON.stringify(data), {});
        }
    }

    /**
     * Subscribe to a channel.
     * @param channel
     */
    subscribe(channel: string) {
        this.connection.then(() => {
            if (channel != undefined && (this.myListeners.size === 0 || !this.myListeners.has(channel))) {
                this.myListeners.set(channel, this.createListener(channel));
            }
            if (!this.subscribers.has(channel)) {
                this.subscribers.set(
                    channel,
                    this.stompClient!.subscribe(
                        channel,
                        (data) => {
                            if (this.listenerObservers.has(channel)) {
                                this.listenerObservers.get(channel)!.next(JhiWebsocketService.parseJSON(data.body));
                            }
                        },
                        {
                            id: this.getSessionId() + '-' + this.subscriptionCounter++,
                        },
                    ),
                );
            }
        });
    }

    /**
     * Unsubscribe a channel.
     * @param channel
     */
    unsubscribe(channel: string) {
        if (this && this.subscribers && this.subscribers.has(channel)) {
            this.subscribers.get(channel)!.unsubscribe();
            this.subscribers.delete(channel);
            this.myListeners.delete(channel);
            this.listenerObservers.delete(channel);
        }
    }

    /**
     * Create a new listener.
     * @param channel The channel to listen on.
     */
    private createListener<T>(channel: string): Observable<T> {
        return new Observable((observer: Observer<T>) => {
            this.listenerObservers.set(channel, observer);
        });
    }

    /**
     * Create a new connection.
     */
    private createConnection(): Promise<void> {
        return new Promise((resolve: Function) => (this.connectedPromise = resolve));
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

    private static parseJSON(response: any): any {
        try {
            return JSON.parse(response);
        } catch {
            return response;
        }
    }

    // https://stackoverflow.com/a/35651029/3802758
    private getSessionId(): string {
        if (this.socket && this.socket._transport && this.socket._transport.url) {
            return this.socket._transport.url.match('.*\\/websocket\\/tracker\\/\\d*\\/(.*)\\/websocket.*')[1];
        } else {
            return 'unsubscribed';
        }
    }
}
