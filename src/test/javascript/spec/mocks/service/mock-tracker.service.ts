import { SpyObject } from '../../helpers/spyobject';
import { JhiTrackerService } from 'app/core/tracker/tracker.service';

export class MockTrackerService extends SpyObject {
    constructor() {
        super(JhiTrackerService);
    }

    connect() {}
}
