import { ConnectionState, IWebsocketService } from 'app/core/websocket/websocket.service';
import { Observable, of } from 'rxjs';

export class MockWebsocketService implements IWebsocketService {
    connect = () => {};

    disableReconnect(): void {}

    disconnect(): void {}

    enableReconnect(): void {}

    receive(): Observable<any> {
        return of();
    }

    send(path: string, data: any): void {}

    stompFailureCallback(): void {}

    subscribe(): void {}

    unsubscribe(): void {}

    state = of(new ConnectionState(true, true, false));
    get connectionState(): Observable<ConnectionState> {
        return this.state;
    }
}
