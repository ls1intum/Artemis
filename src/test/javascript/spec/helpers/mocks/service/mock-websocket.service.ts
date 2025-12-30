import { BehaviorSubject, EMPTY, Observable, Subject } from 'rxjs';
import { ConnectionState, IWebsocketService } from 'app/shared/service/websocket.service';

export class MockWebsocketService implements IWebsocketService {
    private channels = new Map<string, Subject<any>>();
    private connectionStateSubject = new BehaviorSubject<ConnectionState>(new ConnectionState(true, true));

    connect(): void {
        // noop
    }

    disconnect(): void {
        this.connectionStateSubject.next(new ConnectionState(false, true));
    }

    isConnected(): boolean {
        return true;
    }

    /**
     * Returns a shared subject per channel so multiple subscribers receive the same values.
     */
    subscribe<T>(channel: string): Observable<T> {
        if (!channel) {
            return EMPTY;
        }
        let subject = this.channels.get(channel) as Subject<T> | undefined;
        if (!subject) {
            subject = new Subject<T>();
            this.channels.set(channel, subject);
        }
        return subject.asObservable();
    }

    // only required for testing purposes to simulate incoming messages
    emit<T>(channel: string, payload: T): void {
        this.channels.get(channel)?.next(payload);
    }

    // only available for testing purposes
    setConnectionState(state: ConnectionState): void {
        this.connectionStateSubject.next(state);
    }

    send(_path: string, _data: any): void {
        // noop
    }

    get connectionState(): Observable<ConnectionState> {
        return this.connectionStateSubject.asObservable();
    }
}
