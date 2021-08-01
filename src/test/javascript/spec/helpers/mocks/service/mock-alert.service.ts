import { SpyObject } from '../../spyobject';
import { Alert, AlertService } from 'app/core/util/alert.service';

export class MockAlertService extends SpyObject {
    constructor() {
        super(AlertService);
    }
    addAlert(alertOptions: Alert) {
        return alertOptions;
    }
    // clear = () => {};
    error = () => {};
}
