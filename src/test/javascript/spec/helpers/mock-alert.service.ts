import { SpyObject } from './spyobject';
import { JhiAlert } from 'ng-jhipster';
import { AlertService } from 'app/core/alert/alert.service';

export class MockAlertService extends SpyObject {
    constructor() {
        super(AlertService);
    }
    addAlert(alertOptions: JhiAlert) {
        return alertOptions;
    }
}
