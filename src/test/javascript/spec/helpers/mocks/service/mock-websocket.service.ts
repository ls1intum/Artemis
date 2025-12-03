import { Observable, of } from 'rxjs';
import { ConnectionState, IWebsocketService } from 'app/shared/service/websocket.service';

export class MockWebsocketService implements IWebsocketService {
    connect = () => {};

    disconnect(): void {}

    isConnected(): boolean {
        return true;
    }

    receive(): Observable<any> {
        return of();
    }

    send(path: string, data: any): void {}

    stompFailureCallback(): void {}

    subscribe(): IWebsocketService {
        return this;
    }

    unsubscribe(): void {}

    state = of(new ConnectionState(true, true, false));
    get connectionState(): Observable<ConnectionState> {
        return this.state;
    }
}
