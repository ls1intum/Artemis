import { Observable, of } from 'rxjs';
import { IWebsocketService } from 'app/core/websocket/websocket.service';

export class MockWebsocketService implements IWebsocketService {
    bind(event: string, callback: () => void): void {}

    connect = () => {};

    disableReconnect(): void {}

    disconnect(): void {}

    enableReconnect(): void {}

    receive(): Observable<any> {
        return of();
    }

    stompFailureCallback(): void {}

    subscribe(): void {}

    unbind(event: string, callback: () => void): void {}

    unsubscribe(): void {}
}
