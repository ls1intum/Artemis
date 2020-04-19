import { SpyObject } from './spyobject';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

export class MockTrackerService extends SpyObject {
    constructor() {
        super(JhiWebsocketService);
    }

    connect() {}
}
