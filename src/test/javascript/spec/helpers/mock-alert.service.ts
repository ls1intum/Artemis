import { SpyObject } from './spyobject';
import { JhiAlert, AlertService } from 'ng-jhipster';

export class MockAlertService extends SpyObject {
    constructor() {
        super(AlertService);
    }
    addAlert(alertOptions: JhiAlert) {
        return alertOptions;
    }
}
