import { Injectable, OnDestroy } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, Observer, Subscription } from 'rxjs/Rx';

import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { CSRFService } from 'app/core/auth/csrf.service';

import { Client, Subscription as StompSubscription, over, VERSIONS, ConnectionHeaders } from 'webstomp-client';
import { WindowRef } from 'app/core/websocket/window.service';
import * as SockJS from 'sockjs-client';
import { timer } from 'rxjs';

export interface IWebsocketService {
    stompFailureCallback(): void;
    connect(): void;
    disconnect(): void;
    receive(channel: string): Observable<any>;
    subscribe(channel: string): void;
    unsubscribe(channel: string): void;
    bind(event: string, callback: () => void): void;
    unbind(event: string, callback: () => void): void;
    enableReconnect(): void;
    disableReconnect(): void;
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
    connectListeners: { (): void }[] = [];
    disconnectListeners: { (): void }[] = [];
    consecutiveFailedAttempts = 0;
    connecting = false;

    private logTimers: Subscription[] = [];

    constructor(private router: Router, private authServerProvider: AuthServerProvider, private $window: WindowRef, private csrfService: CSRFService) {
        this.connection = this.createConnection();
    }

    stompFailureCallback() {
        this.connecting = false;
        this.consecutiveFailedAttempts++;
        this.disconnectListeners.forEach(listener => {
            listener();
        });
        if (this.shouldReconnect) {
            // wait 5 seconds before reconnecting in case the connection does not work or the client is disconnected
            // after  2 failed attempts in row, increase the timeout to 10 seconds,
            // after  4 failed attempts in row, increase the timeout to 20 seconds
            // after  8 failed attempts in row, increase the timeout to 60 seconds
            // after 12 failed attempts in row, increase the timeout to 120 seconds
            // after 16 failed attempts in row, increase the timeout to 300 seconds
            // after 20 failed attempts in row, increase the timeout to 600 seconds
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
            // console.log('Websocket: Try to reconnect in ' + waitUntilReconnectAttempt + ' seconds...');
        }
    }

    connect() {
        if ((this.stompClient && this.stompClient.connected) || this.connecting) {
            return; // don't connect, if already connected or connecting
        }
        this.connecting = true;
        if (!this.connectedPromise) {
            this.connection = this.createConnection();
        }
        // building absolute path so that websocket doesn't fail when deploying with a context path
        const loc = this.$window.nativeWindow.location;
        let url = '//' + loc.host + loc.pathname + 'websocket/tracker';
        const authToken = this.authServerProvider.getToken();
        if (authToken) {
            url += '?access_token=' + authToken;
        }
        // NOTE: only support real websockets transports and disable http poll, http stream and other exotic workarounds.
        // nowadays all modern browsers support websockets and workarounds are not necessary any more and might only lead to problems
        const socket = new SockJS(url, undefined, { transports: 'websocket' });
        const options = {
            heartbeat: { outgoing: 10000, incoming: 20000 },
            debug: false,
            protocols: ['v12.stomp'],
        };
        this.stompClient = over(socket, options);
        // Note: at the moment, debugging is deactivated to prevent console log statements
        this.stompClient.debug = function(str) {};
        const headers = <ConnectionHeaders>{};
        headers['X-CSRF-TOKEN'] = this.csrfService.getCSRF();

        this.stompClient.connect(
            headers,
            () => {
                this.connectedPromise('success');
                this.connecting = false;
                this.connectListeners.forEach(listener => {
                    listener();
                });
                this.consecutiveFailedAttempts = 0;
                if (this.alreadyConnectedOnce) {
                    // (re)connect to all existing channels
                    if (this.myListeners.size !== 0) {
                        this.myListeners.forEach((listener, channel) => {
                            this.subscribers.set(
                                channel,
                                this.stompClient!.subscribe(channel, data => {
                                    if (this.listenerObservers.has(channel)) {
                                        this.listenerObservers.get(channel)!.next(JSON.parse(data.body));
                                    }
                                }),
                            );
                        });
                    }
                } else {
                    this.alreadyConnectedOnce = true;
                }
                if (!this.subscription) {
                    this.subscription = this.router.events.subscribe(event => {
                        if (event instanceof NavigationEnd) {
                            this.sendActivity();
                        }
                    });
                }
                this.sendActivity();

                // Setup periodic logs of websocket connection numbers
                this.logTimers.push(
                    timer(0, 10000).subscribe(x => {
                        console.log('\n\n');
                        console.log(`${this.subscribers.size} websocket subscriptions: `, this.subscribers.keys());
                        // this.subscribers.forEach((sub, topic) => console.log(topic));

                        // console.log(`Listeners (${this.myListeners.size}): `, this.myListeners.values());
                        // this.myListeners.forEach((sub, topic) => console.log(topic));

                        // console.log(`Observers (${this.listenerObservers.size}): `, this.listenerObservers.values());
                        // this.listenerObservers.forEach((sub, topic) => console.log(topic));
                    }),
                );
            },
            this.stompFailureCallback.bind(this),
        );
    }

    disconnect() {
        this.logTimers.forEach(logTimer => logTimer.unsubscribe());
        this.connection = this.createConnection();
        Object.keys(this.myListeners).forEach(listener => this.unsubscribe(listener), this);
        if (this.stompClient) {
            this.stompClient.disconnect();
            this.stompClient = null;
        }
        if (this.subscription) {
            this.subscription.unsubscribe();
            this.subscription = null;
        }
        this.alreadyConnectedOnce = false;
    }

    receive(channel: string): Observable<any> {
        if (channel != null && (this.myListeners.size === 0 || !this.myListeners.has(channel))) {
            this.myListeners.set(channel, this.createListener(channel));
        }
        return this.myListeners.get(channel)!;
    }

    sendActivity() {
        // Note: this is temporarily deactivated for now to reduce server load on websocket connections
        // if (this.stompClient !== null && this.stompClient.connected) {
        //     this.stompClient.send(
        //         '/topic/activity', // destination
        //         JSON.stringify({ page: this.router.routerState.snapshot.url }), // body
        //         {}, // header
        //     );
        // }
    }

    /**
     * Send data through the websocket connection
     * @param path {string} the path for the websocket connection
     * @param data {object} the date to send through the websocket connection
     */
    send(path: string, data: any) {
        if (this.stompClient !== null && this.stompClient.connected) {
            this.stompClient.send(path, JSON.stringify(data), {});
        }
    }

    subscribe(channel: string) {
        this.connection.then(() => {
            if (channel != null && (this.myListeners.size === 0 || !this.myListeners.has(channel))) {
                this.myListeners.set(channel, this.createListener(channel));
            }
            this.subscribers.set(
                channel,
                this.stompClient!.subscribe(channel, data => {
                    if (this.listenerObservers.has(channel)) {
                        this.listenerObservers.get(channel)!.next(JSON.parse(data.body));
                    }
                }),
            );
        });
    }

    unsubscribe(channel: string) {
        if (this && this.subscribers && this.subscribers.has(channel)) {
            this.subscribers.get(channel)!.unsubscribe();
            this.subscribers.delete(channel);
            this.myListeners.delete(channel);
            this.listenerObservers.delete(channel);
        }
    }

    private createListener<T>(channel: string): Observable<T> {
        return new Observable((observer: Observer<T>) => {
            this.listenerObservers.set(channel, observer);
        });
    }

    private createConnection(): Promise<void> {
        return new Promise((resolve: Function) => (this.connectedPromise = resolve));
    }

    /**
     * bind the given callback function to the given event
     *
     * @param event {string} the event to be notified of
     * @param callback {function} the function to call when the event is triggered
     */
    bind(event: string, callback: () => void) {
        if (this.stompClient) {
            if (event === 'connect') {
                this.connectListeners.push(callback);
                if (this.stompClient.connected) {
                    callback();
                }
            } else if (event === 'disconnect') {
                this.disconnectListeners.push(callback);
                if (!this.stompClient.connected) {
                    callback();
                }
            }
        }
    }

    /**
     * unbind the given callback function from the given event
     *
     * @param event {string} the event to no longer be notified of
     * @param callback {function} the function to no longer call when the event is triggered
     */
    unbind(event: string, callback: () => void) {
        if (event === 'connect') {
            this.connectListeners = this.connectListeners.filter(listener => {
                return listener !== callback;
            });
        } else if (event === 'disconnect') {
            this.disconnectListeners = this.disconnectListeners.filter(listener => {
                return listener !== callback;
            });
        }
    }

    /**
     * enable automatic reconnect
     */
    enableReconnect() {
        if (this.stompClient && !this.stompClient.connected) {
            this.connect();
        }
        this.shouldReconnect = true;
    }

    /**
     * disable automatic reconnect
     */
    disableReconnect() {
        this.shouldReconnect = false;
    }

    ngOnDestroy(): void {
        this.disconnect();
    }
}
