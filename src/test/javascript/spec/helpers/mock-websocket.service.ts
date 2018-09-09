import { SpyObject } from './spyobject';
import { JhiWebsocketService } from '../../../../main/webapp/app/core/websocket/websocket.service';

export class MockTrackerService extends SpyObject {
    constructor() {
        super(JhiWebsocketService);
    }

    connect() {}
}
