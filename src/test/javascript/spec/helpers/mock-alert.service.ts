import { SpyObject } from './spyobject';
import { JhiAlert, JhiAlertService } from 'ng-jhipster';

export class MockAlertService extends SpyObject {
    constructor() {
        super(JhiAlertService);
    }
    addAlert(alertOptions: JhiAlert) {
        return alertOptions;
    }
}
