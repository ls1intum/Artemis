import { Injectable } from '@angular/core';
import { Alert, AlertService, AlertType } from 'app/core/util/alert.service';
import { ConnectionState, JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Injectable({ providedIn: 'root' })
export class ConnectionNotificationService {
    alert?: Alert;

    constructor(private websocketService: JhiWebsocketService, private alertService: AlertService) {}

    init() {
        console.log('Subscribed');
        this.websocketService.connectionState.subscribe(this.onChange);
    }

    private onChange(change: ConnectionState) {
        console.log(change);
        console.log('Alert service defined: ' + !!this.alertService);
        if (!change.wasEverConnectedBefore || !this.alertService) {
            return;
        }
        this.alert?.close();
        if (change.connected) {
            this.alert = this.alertService.addAlert({ type: AlertType.SUCCESS, message: 'artemisApp.connectionAlert.reconnected', timeout: 10000 });
        } else if (!change.intendedDisconnect) {
            this.alert = this.alertService.addAlert({ type: AlertType.DANGER, message: 'artemisApp.connectionAlert.disconnected', timeout: 30, dismissible: true });
        }
    }
}
