import { Injectable, OnDestroy } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, Observer, Subscription } from 'rxjs/Rx';

import { CSRFService } from 'app/core/auth/csrf.service';
import { WindowRef } from './window.service';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';

import * as SockJS from 'sockjs-client';
import * as Stomp from 'webstomp-client';

@Injectable({ providedIn: 'root' })
export class JhiWebsocketService implements OnDestroy {
    stompClient: Stomp.Client;
    subscribers: { [key: string]: Stomp.Subscription } = {};
    connection: Promise<void>;
    connectedPromise: Function;
    myListeners: { [key: string]: Observable<any> } = {};
    listenerObservers: { [key: string]: Observer<any> } = {};
    alreadyConnectedOnce = false;
    private subscription: Subscription;
    shouldReconnect = false;
    connectListeners: { (): void }[] = [];
    disconnectListeners: { (): void }[] = [];
    consecutiveFailedAttempts = 0;
    connecting = false;

    constructor(
        private router: Router,
        private authServerProvider: AuthServerProvider,
        private $window: WindowRef,
        // tslint:disable-next-line: no-unused-variable
        private csrfService: CSRFService
    ) {}

    stompFailureCallback() {
        this.connecting = false;
        this.consecutiveFailedAttempts++;
        this.disconnectListeners.forEach(listener => {
            listener();
        });
        if (this.shouldReconnect) {
            // NOTE: after 5 failed attempts in row, increase the timeout to 5 seconds,
            // after 10 failed attempts in row, increase the timeout to 10 seconds
            // after 20 failed attempts in row, increase the timeout to 20 seconds
            // after 30 failed attempts in row, increase the timeout to 60 seconds
            let waitUntilReconnectAttempt;
            if (this.consecutiveFailedAttempts > 30) {
                waitUntilReconnectAttempt = 60;
            } else if (this.consecutiveFailedAttempts > 20) {
                waitUntilReconnectAttempt = 20;
            } else if (this.consecutiveFailedAttempts > 10) {
                waitUntilReconnectAttempt = 10;
            } else if (this.consecutiveFailedAttempts > 5) {
                waitUntilReconnectAttempt = 5;
            } else {
                waitUntilReconnectAttempt = 1;
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
        const socket = new SockJS(url);
        this.stompClient = Stomp.over(socket, { debug: false });
        // deactivate websocket debugging
        this.stompClient.debug = function(str) {};
        const headers = <Stomp.ConnectionHeaders>{};
        headers['X-CSRFToken'] = this.csrfService.getCSRF('csrftoken');

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
                    if (Object.keys(this.myListeners).length !== 0) {
                        for (const channel in this.myListeners) {
                            if (this.myListeners.hasOwnProperty(channel)) {
                                this.subscribers[channel] = this.stompClient.subscribe(channel, data => {
                                    this.listenerObservers[channel].next(JSON.parse(data.body));
                                });
                            }
                        }
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
            },
            this.stompFailureCallback.bind(this)
        );
    }

    disconnect() {
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

    receive(channel?: string): Observable<any> {
        if (channel != null && (!Object.keys(this.myListeners).length || !this.myListeners.hasOwnProperty(channel))) {
            this.myListeners[channel] = this.createListener(channel);
        }
        return this.myListeners[channel];
    }

    sendActivity() {
        if (this.stompClient !== null && this.stompClient.connected) {
            this.stompClient.send(
                '/topic/activity', // destination
                JSON.stringify({ page: this.router.routerState.snapshot.url }), // body
                {} // header
            );
        }
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

    subscribe(channel?: string) {
        this.connection.then(() => {
            if (channel != null && (!Object.keys(this.myListeners).length || !this.myListeners.hasOwnProperty(channel))) {
                this.myListeners[channel] = this.createListener(channel);
            }
            this.subscribers[channel] = this.stompClient.subscribe(channel, data => {
                this.listenerObservers[channel].next(JSON.parse(data.body));
            });
        });
    }

    unsubscribe(channel?: string) {
        if (this && this.subscribers && this.subscribers[channel]) {
            this.subscribers[channel].unsubscribe();
        }
        if (
            this &&
            channel != null &&
            this.myListeners != null &&
            (!Object.keys(this.myListeners).length || this.myListeners.hasOwnProperty(channel))
        ) {
            this.myListeners[channel] = this.createListener(channel);
        }
    }

    private createListener<T>(channel: string): Observable<T> {
        return Observable.create((observer: Observer<T>) => {
            this.listenerObservers[channel] = observer;
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
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
    }
}
