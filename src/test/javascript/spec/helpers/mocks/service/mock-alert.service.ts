import { SpyObject } from '../../spyobject';
import { Alert, AlertService } from 'app/core/util/alert.service';

export class MockAlertService extends SpyObject {
    constructor() {
        super(AlertService);
    }
    addAlert(alert: Alert) {
        return alert;
    }
    success(message: string, translationParams?: { [key: string]: unknown }, position?: string): Alert {
        return { type: 'success', message, translationParams, position };
    }
    error(message: string, translationParams?: { [key: string]: unknown }, position?: string): Alert {
        return { type: 'danger', message, translationParams, position };
    }
    warning(message: string, translationParams?: { [key: string]: unknown }, position?: string): Alert {
        return { type: 'warning', message, translationParams, position };
    }
    info(message: string, translationParams?: { [key: string]: unknown }, position?: string): Alert {
        return { type: 'info', message, translationParams, position };
    }
    isToast(): boolean {
        return true;
    }
    clear = () => {};
}
