import { Injectable, OnDestroy } from '@angular/core';
import { captureException } from '@sentry/angular';
import { IWatchParams, RxStomp, RxStompConfig, TickerStrategy } from '@stomp/rx-stomp';
import { IMessage, StompHeaders } from '@stomp/stompjs';
import { gzip, ungzip } from 'pako';
import { BehaviorSubject, EMPTY, Observable } from 'rxjs';
import { map } from 'rxjs/operators';

/**
 * Name of the STOMP header that indicates whether a message payload is compressed.
 *
 * This header must be consistent with the corresponding server-side implementation
 * (see {@code GzipMessageConverter.java} on the server).
 */
export const COMPRESSION_HEADER_KEY = 'X-Compressed';

/**
 * STOMP headers used to mark a message as compressed.
 *
 * When this header is present with value `'true'`, the receiver must treat
 * the message body as a Base64-encoded, GZIP-compressed JSON payload.
 */
export const COMPRESSION_HEADER: Record<string, string> = { [COMPRESSION_HEADER_KEY]: 'true' };

/**
 * Public API for the WebSocket service used in Angular components.
 *
 * This abstraction allows components and services to:
 * - establish and tear down a single shared WebSocket/STOMP connection, and
 * - publish and subscribe to STOMP topics (channels).
 *
 * ## Important usage note regarding subscriptions
 *
 * Whenever a component calls {@link IWebsocketService.subscribe}, it **must**
 * eventually unsubscribe from the returned {@link Observable}:
 *
 * - If using the `async` pipe in an Angular template, make sure that the
 *   observable completes or is torn down when the component is destroyed
 *   (e.g. by using `takeUntil(this.destroy$)` or similar).
 * - If subscribing manually via `.subscribe(...)`, always hold on to the
 *   returned `Subscription` and call `subscription.unsubscribe()` in
 *   `ngOnDestroy`.
 *
 * Failing to unsubscribe will keep the underlying STOMP subscription and
 * WebSocket resources alive, even if the component is no longer in use.
 */
export interface IWebsocketService {
    /**
     * Set up the WebSocket/STOMP connection.
     *
     * This initializes an {@link RxStomp} client and connects it to the server.
     * If a client instance already exists, it is deactivated first and then
     * re-created with fresh configuration.
     *
     * Typically, the application calls this once during startup (e.g. in a
     * root service or app initializer). Consumers usually do not need to call
     * this directly, because {@link subscribe} will trigger a lazy connection
     * if none exists yet.
     */
    connect(): void;

    /**
     * Close the WebSocket/STOMP connection.
     *
     * This method:
     * - deactivates the underlying {@link RxStomp} client,
     * - clears the internal reference to it, and
     * - updates the {@link connectionState} to reflect that the connection is
     *   intentionally closed.
     *
     * Use this method for explicit disconnects such as user logout, application
     * shutdown, or when you are sure the application should no longer maintain
     * an active WebSocket connection.
     */
    disconnect(): void;

    /**
     * Send data through the WebSocket/STOMP connection.
     *
     * The payload is JSON-stringified, and if it exceeds 1 KB, it is compressed
     * using GZIP and then Base64-encoded. A compression header
     * {@link COMPRESSION_HEADER} is set accordingly to indicate that the
     * message body is compressed.
     *
     * @typeParam T Type of the payload being sent (typically a DTO or plain object).
     * @param path STOMP destination (topic or queue) to publish to (e.g. `/topic/example`).
     * @param data Data to send through the WebSocket connection.
     *
     * @remarks
     * If the connection is not currently active (see {@link isConnected}),
     * the method does nothing. It will **not** automatically reconnect.
     */
    send<T>(path: string, data: T): void;

    /**
     * Subscribe to a STOMP destination (topic/channel).
     *
     * @typeParam T Expected type of the decoded message payload, after optional
     *             decompression and JSON parsing.
     * @param channel STOMP destination to subscribe to (e.g. `/topic/my-channel`).
     * @returns An {@link Observable} that emits messages of type `T` whenever
     *          the server sends a message on the given `channel`.
     *
     * @remarks
     * - If `channel` is falsy (empty string, `null`, etc.), {@link EMPTY} is returned.
     * - If there is no active {@link RxStomp} client, {@link connect} is called
     *   lazily. If connection setup fails, {@link EMPTY} is returned.
     * - Each subscription to the returned `Observable` creates an underlying
     *   STOMP subscription via `RxStomp.watch(...)`. When the observable
     *   subscription is **unsubscribed**, the STOMP subscription is cleaned up
     *   automatically by `RxStomp`.
     *
     * ## Responsibility to unsubscribe
     *
     * The **caller** (component/service) is responsible for unsubscribing from
     * the `Observable`. Typical patterns include:
     *
     * ```ts
     * // 1. Manual subscription + teardown
     * private subscription?: Subscription;
     *
     * ngOnInit() {
     *   this.subscription = this.websocketService
     *     .subscribe<MyDto>('/topic/my-channel')
     *     .subscribe((message) => { ... });
     * }
     *
     * ngOnDestroy() {
     *   this.subscription?.unsubscribe();
     * }
     *
     * // 2. Using takeUntil with a destroy$ subject
     * private readonly destroy$ = new Subject<void>();
     *
     * ngOnInit() {
     *   this.websocketService
     *     .subscribe<MyDto>('/topic/my-channel')
     *     .pipe(takeUntil(this.destroy$))
     *     .subscribe((message) => { ... });
     * }
     *
     * ngOnDestroy() {
     *   this.destroy$.next();
     *   this.destroy$.complete();
     * }
     * ```
     *
     * Failing to unsubscribe keeps the WebSocket/STOMP subscription active and
     * may lead to memory leaks and unnecessary server load.
     */
    subscribe<T>(channel: string): Observable<T>;

    /**
     * Observe the current WebSocket connection state.
     *
     * @returns An {@link Observable} of {@link ConnectionState}, emitting
     *          whenever the internal connection state changes.
     *
     * @remarks
     * This is useful for components that need to react to connection changes,
     * e.g. showing a "disconnected" banner or blocking certain actions until
     * the connection is established.
     */
    get connectionState(): Observable<ConnectionState>;
}

/**
 * Immutable representation of the WebSocket/STOMP connection state.
 *
 * Instances of this class are emitted by {@link WebsocketService.connectionState}
 * to allow observers to react to:
 * - whether a connection is currently established,
 * - whether there has ever been a successful connection before, and
 * - whether the current disconnected state is intentional or due to an error.
 */
export class ConnectionState {
    /**
     * Indicates whether the WebSocket/STOMP connection is currently active.
     */
    readonly connected: boolean;

    /**
     * Indicates whether a successful connection has been established at least once
     * since service initialization or the last intentional disconnect.
     */
    readonly wasEverConnectedBefore: boolean;

    /**
     * Indicates whether the current disconnected state is intentional.
     *
     * Typically `true` after an explicit call to {@link WebsocketService.disconnect}
     * and `false` when the connection has dropped unexpectedly (e.g. network issues).
     */
    readonly intendedDisconnect: boolean;

    /**
     * Create a new {@link ConnectionState} instance.
     *
     * @param connected Whether the WebSocket/STOMP connection is currently active.
     * @param wasEverConnectedBefore Whether a successful connection has been established at least once.
     * @param intendedDisconnect Whether the current disconnected state is intentional.
     */
    constructor(connected: boolean, wasEverConnectedBefore: boolean, intendedDisconnect: boolean) {
        this.connected = connected;
        this.wasEverConnectedBefore = wasEverConnectedBefore;
        this.intendedDisconnect = intendedDisconnect;
    }
}

/**
 * WebSocket service managing a single shared STOMP connection.
 *
 * Relation overview:
 *
 * - **Server** &lt;1—1&gt; **STOMP** &lt;1—1&gt; **WebsocketService** &lt;1—n·m&gt; **Angular components** × **channel topics**
 *
 * Responsibilities:
 * - Configure and manage a single {@link RxStomp} client instance.
 * - Expose methods to connect, disconnect, send messages, and subscribe to topics.
 * - Handle optional compression/decompression of message payloads.
 *
 * ## Subscription lifecycle and responsibility
 *
 * Each call to {@link subscribe} returns an {@link Observable} bound to an
 * underlying STOMP subscription created by `RxStomp.watch(...)`.
 *
 * - When the consumer **subscribes** to the observable, the STOMP subscription
 *   is created (or reused internally by `RxStomp`).
 * - When the consumer **unsubscribes** from the observable subscription, the
 *   STOMP subscription is cleaned up by `RxStomp`.
 *
 * Therefore, any component or service that calls `subscribe` **must** call
 * `unsubscribe` on the observable subscription when it is no longer needed,
 * usually in `ngOnDestroy`. This is essential to:
 *
 * - free client-side resources,
 * - properly unsubscribe from server-side topics, and
 * - prevent memory leaks and unnecessary server traffic.
 */
@Injectable({ providedIn: 'root' })
export class WebsocketService implements IWebsocketService, OnDestroy {
    /**
     * Underlying RxStomp client instance managing the STOMP/WebSocket connection.
     *
     * This is lazily created in {@link connect} and cleared in {@link disconnect}.
     */
    private rxStomp?: RxStomp;

    /**
     * Internal {@link BehaviorSubject} holding the current {@link ConnectionState}.
     * Exposed as an observable via {@link connectionState}.
     */
    private readonly connectionStateInternal: BehaviorSubject<ConnectionState>;

    /**
     * Creates a new instance of {@link WebsocketService}.
     *
     * The initial connection state is:
     * - `connected = false`
     * - `wasEverConnectedBefore = false`
     * - `intendedDisconnect = true` (no connection has been attempted yet)
     */
    constructor() {
        this.connectionStateInternal = new BehaviorSubject<ConnectionState>(new ConnectionState(false, false, true));
    }

    /**
     * Observable view of the current WebSocket connection state.
     *
     * Consumers can subscribe to this observable to react to connection changes.
     * The observable never completes as long as the service is alive.
     */
    get connectionState(): Observable<ConnectionState> {
        return this.connectionStateInternal.asObservable();
    }

    /**
     * Set up the WebSocket/STOMP connection.
     *
     * This method:
     * 1. Deactivates any existing {@link RxStomp} instance.
     * 2. Constructs a new `RxStomp` client with:
     *    - broker URL based on the current `window.location.host`
     *    - configured heartbeats and reconnect behavior
     *    - native `WebSocket` usage (no SockJS)
     * 3. Registers a connection callback to update {@link connectionStateInternal}
     *    when a connection is established.
     * 4. Activates the client.
     *
     * @remarks
     * - This method does **not** return a promise or observable for connection
     *   completion. Consumers who need to react to connection establishment
     *   should subscribe to {@link connectionState}.
     */
    connect() {
        if (this.rxStomp) {
            void this.rxStomp.deactivate();
        }
        // NOTE: we add 'websocket' twice to use STOMP without SockJS
        const url = `//${window.location.host}/websocket/websocket`;
        const config: RxStompConfig = {
            brokerURL: url,
            connectHeaders: {} as StompHeaders,
            heartbeatOutgoing: 10000,
            heartbeatIncoming: 10000,
            reconnectDelay: 1000, // backoff handled manually via stompFailureCallback
            connectionTimeout: 10000,
            maxReconnectDelay: 10000,
            heartbeatStrategy: TickerStrategy.Worker, // use Web Worker for heartbeats so that browser tabs stay connected when in background
            discardWebsocketOnCommFailure: true,
            debug: () => {},
            webSocketFactory: () => new WebSocket(url, ['v12.stomp']),
        };
        this.rxStomp = new RxStomp();
        this.rxStomp.configure(config);
        const rawClient = this.rxStomp.stompClient;
        rawClient.onConnect = () => {
            // check if a session id is part of the frame, otherwise use a random string
            if (!this.connectionStateInternal.getValue().connected) {
                this.connectionStateInternal.next(new ConnectionState(true, false, false));
            }
        };
        this.rxStomp.activate();
    }

    /**
     * Create a message handler that processes incoming STOMP messages.
     *
     * The returned function:
     * 1. Checks whether the incoming message is marked as compressed via the {@link COMPRESSION_HEADER_KEY}.
     * 2. If compressed, attempts to decode and decompress the payload.
     * 3. Parses the resulting string as JSON into type `T`.
     *
     * @typeParam T Expected type of the parsed message payload.
     * @returns A function that takes an {@link IMessage} and returns a decoded and parsed payload of type `T`.
     *
     * @internal
     */
    private handleIncomingMessage<T>() {
        return (message: IMessage): T => {
            // this code is invoked if a new websocket message was received from the server
            // we pass the message to the subscriber (e.g. a component who will be notified and can handle the message)
            const isCompressed = message.headers[COMPRESSION_HEADER_KEY] === 'true';
            let payload = message.body;

            if (isCompressed) {
                try {
                    payload = WebsocketService.decodeAndDecompress(payload);
                } catch (error) {
                    captureException('Failed to decompress message', error);
                }
            }
            return WebsocketService.parseJSON<T>(payload);
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
     *
     * @internal
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
     *
     * @internal
     */
    private static decodeAndDecompress(payload: string): string {
        // 1. Decode the Base64 string to binary (ArrayBuffer) and convert to Uint8Array
        const binaryData = Uint8Array.from(window.atob(payload), (char) => char.charCodeAt(0));
        // 2. Decompress using pako
        return ungzip(binaryData, { to: 'string' });
    }

    /**
     * Checks whether the WebSocket/STOMP connection is currently established.
     *
     * @returns `true` if the connection is active; otherwise, `false`.
     */
    public isConnected(): boolean {
        return this.rxStomp?.connected() ?? false;
    }

    /**
     * Close the WebSocket/STOMP connection.
     *
     * This method:
     * - deactivates the underlying {@link RxStomp} client (if any),
     * - clears the internal `rxStomp` reference, and
     * - updates {@link connectionStateInternal} to reflect an intentional disconnect.
     *
     * @remarks
     * This is typically invoked for use cases such as logout or application
     * teardown. After calling this, any further {@link send} or {@link subscribe}
     * calls will behave as if no connection exists. A subsequent call to
     * {@link connect} or a lazy `subscribe` will create a new connection.
     */
    disconnect() {
        if (this.rxStomp) {
            void this.rxStomp.deactivate();
            this.rxStomp = undefined;
            if (this.connectionStateInternal.getValue().connected || !this.connectionStateInternal.getValue().intendedDisconnect) {
                this.connectionStateInternal.next(new ConnectionState(false, false, true));
            }
        }
    }

    /**
     * Send data through the WebSocket connection, potentially compressing the payload.
     *
     * Steps:
     * 1. Convert the data into JSON.
     * 2. Compute the payload size.
     * 3. If the payload is larger than 1 KB:
     *    - compress it using GZIP,
     *    - convert it to a Base64 string,
     *    - send it with {@link COMPRESSION_HEADER}.
     * 4. Otherwise, send the uncompressed JSON payload.
     *
     * @typeParam T Type of the payload being sent.
     * @param path STOMP destination (e.g. `/app/some-endpoint`).
     * @param data Data to send through the WebSocket connection.
     *
     * @remarks
     * - If compression fails, the method logs the error via Sentry and falls back to sending the uncompressed payload.
     * - If the connection is not active, nothing is sent.
     */
    send<T>(path: string, data: T): void {
        if (this.isConnected()) {
            const jsonPayload = JSON.stringify(data);
            const payloadSize = new Blob([jsonPayload]).size; // Measure payload size

            if (payloadSize > 1024) {
                try {
                    const base64StringPayload = WebsocketService.compressAndEncode(jsonPayload);
                    this.rxStomp!.publish({ destination: path, body: base64StringPayload, headers: COMPRESSION_HEADER });
                } catch (error) {
                    captureException('Failed to compress websocket message', error);
                    // Send uncompressed payload if an error occurs
                    this.rxStomp!.publish({ destination: path, body: jsonPayload, headers: {} });
                }
            } else {
                // Send uncompressed payload
                this.rxStomp!.publish({ destination: path, body: jsonPayload, headers: {} });
            }
        }
    }

    /**
     * Subscribe to a channel and receive decoded message payloads as an observable stream.
     *
     * @typeParam T Expected type of the decoded message payload.
     * @param channel STOMP destination (topic) to subscribe to (e.g. `/topic/example`).
     * @returns An {@link Observable} that emits values of type `T`. Returns
     *          {@link EMPTY} if `channel` is falsy or if a connection cannot
     *          be established.
     *
     * @remarks
     * - Each subscription to the returned observable uses `RxStomp.watch(...)`
     *   with specific {@link IWatchParams}, including `subHeaders: { id: channel }`
     *   to identify the subscription.
     * - When the observable subscription is **unsubscribed**, `RxStomp` will
     *   unsubscribe from the corresponding STOMP topic.
     *
     * ## Caller must unsubscribe
     *
     * Components/services that call `subscribe` must ensure they unsubscribe
     * from the observable when it is no longer needed. This is crucial to:
     *
     * - avoid accumulating STOMP subscriptions on the server,
     * - prevent memory leaks,
     * - prevent messages being delivered to components that no longer exist.
     *
     * See the examples in {@link IWebsocketService.subscribe}.
     */
    subscribe<T = any>(channel: string): Observable<T> {
        if (!channel) {
            return EMPTY;
        }
        if (!this.rxStomp) {
            this.connect();
        }
        if (!this.rxStomp) {
            return EMPTY;
        }
        const params: IWatchParams = { destination: channel, subHeaders: { id: channel } };
        return this.rxStomp.watch(params).pipe(map(this.handleIncomingMessage<T>()));
    }

    /**
     * Lifecycle hook of {@link OnDestroy}.
     *
     * Called by Angular when the service is destroyed (e.g. during application teardown).
     * Delegates to {@link disconnect} to cleanly close the WebSocket/STOMP connection.
     */
    ngOnDestroy(): void {
        this.disconnect();
    }

    /**
     * Parses a JSON string into an object of the specified generic type.
     *
     * This method attempts to parse the provided JSON string. If parsing fails,
     * it returns the input string cast to the specified type. This can be useful
     * for handling cases where the response might not always be a valid JSON string.
     *
     * @typeParam T The type of the object to return after parsing.
     * @param response The JSON string to be parsed.
     * @returns The parsed object of the specified type, or the input string cast to the type if parsing fails.
     */
    private static parseJSON<T>(response: string): T {
        try {
            return JSON.parse(response);
        } catch {
            return response as T;
        }
    }
}
