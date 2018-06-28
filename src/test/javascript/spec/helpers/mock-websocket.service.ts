import { SpyObject } from './spyobject';
import { JhiWebsocketService } from '../../../../main/webapp/app/shared/websocket/websocket.service';

export class MockTrackerService extends SpyObject {

    constructor() {
        super(JhiWebsocketService);
    }

    connect() {}
}
