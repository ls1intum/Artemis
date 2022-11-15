import { Injectable, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, Subscriber, Subscription } from 'rxjs';
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
     * @param path {string} the path for the websocket connection
     * @param data {object} the data to send through the websocket connection
     */
    send(path: string, data: any): void;

    /**
     * Subscribe to a channel.
     * @param channel
     */
    subscribe(channel: string): IWebsocketService;

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
    stompClient?: Client;
    connection: Promise<void>;
    connectedPromise: Function;
    // we store the STOMP subscriptions per channel so that we can unsubscribe in case we are not interested any more
    stompSubscriptions = new Map<string, StompSubscription>();
    // we store the observables per channel to make sure we can resubscribe them in case of connection issues
    observables = new Map<string, Observable<any>>();
    // we store the subscribers (represent the components who want to receive messages) per channel so that we can notify them in case a message was received from the server
    subscribers = new Map<string, Subscriber<any>>();
    alreadyConnectedOnce = false;
    private subscription?: Subscription;
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
        let url = `//${window.location.host}/websocket`;
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
                    if (this.observables.size !== 0) {
                        this.observables.forEach((observable, channel) => this.addSubscription(channel));
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
     * @private
     */
    private addSubscription(channel: string) {
        const subscription = this.stompClient!.subscribe(
            channel,
            (message) => {
                // this code is invoked if a new websocket message was received from the server
                // we pass the message to the subscriber (e.g. a component who will be notified and can handle the message)
                if (this.subscribers.has(channel)) {
                    this.subscribers.get(channel)!.next(JhiWebsocketService.parseJSON(message.body));
                }
            },
            {
                id: this.getSessionId() + '-' + this.subscriptionCounter++,
            },
        );
        this.stompSubscriptions.set(channel, subscription);
    }

    private isConnected(): boolean {
        return !!this.stompClient?.connected;
    }

    /**
     * Close the connection to the websocket, unsubscribe all observables and distribute "intended disconnect" state.
     */
    disconnect() {
        this.connection = this.createConnection();
        this.observables.forEach((observable, channel) => this.unsubscribe(channel));
        if (this.stompClient) {
            this.stompClient.disconnect();
            this.stompClient = undefined;
            if (this.connectionStateInternal.getValue().connected || !this.connectionStateInternal.getValue().intendedDisconnect) {
                this.connectionStateInternal.next(new ConnectionState(false, this.alreadyConnectedOnce, true));
            }
        }
        if (this.subscription) {
            this.subscription.unsubscribe();
            this.subscription = undefined;
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
     * Subscribe to a channel: add the channel to the observables and create a STOMP subscription for the channel if this has not been done before
     * @param channel
     */
    subscribe(channel: string): IWebsocketService {
        this.connection.then(() => {
            if (channel != undefined && (this.observables.size === 0 || !this.observables.has(channel))) {
                this.observables.set(channel, this.createObservable(channel));
            }
            if (!this.stompSubscriptions.has(channel)) {
                this.addSubscription(channel);
            }
        });
        return this;
    }

    /**
     * Unsubscribe a channel.
     * @param channel
     */
    unsubscribe(channel: string) {
        if (this && this.stompSubscriptions && this.stompSubscriptions.has(channel)) {
            this.stompSubscriptions.get(channel)!.unsubscribe();
            this.stompSubscriptions.delete(channel);
            this.observables.delete(channel);
            this.subscribers.delete(channel);
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

    private static parseJSON(response: string): any {
        try {
            return JSON.parse(response);
        } catch {
            return response;
        }
    }

    // https://stackoverflow.com/a/35651029/3802758
    private getSessionId(): string {
        if (this.socket && this.socket._transport && this.socket._transport.url) {
            return this.socket._transport.url.match('.*\\/websocket\\/\\d*\\/(.*)\\/websocket.*')[1];
        } else {
            return 'unsubscribed';
        }
    }
}
