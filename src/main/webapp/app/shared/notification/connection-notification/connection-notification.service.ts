import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { User } from 'app/core/user/user.model';
import { Alert, AlertService, AlertType } from 'app/core/util/alert.service';

@Injectable({ providedIn: 'root' })
export class ConnectionNotificationService {
    alert?: Alert;
    connected?: boolean;

    constructor(private accountService: AccountService, private jhiWebsocketService: JhiWebsocketService, private alertService: AlertService) {}

    init() {
        this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
            if (user) {
                // listen to connect / disconnect events
                this.jhiWebsocketService.enableReconnect();
                this.jhiWebsocketService.bind('connect', this.onConnect);
                this.jhiWebsocketService.bind('disconnect', this.onDisconnect);
            } else {
                // On logout, reset component
                this.connected = undefined;
                this.alert?.close();
                this.alert = undefined;
                this.jhiWebsocketService.disableReconnect();
                this.jhiWebsocketService.unbind('connect', this.onConnect);
                this.jhiWebsocketService.unbind('disconnect', this.onDisconnect);
            }
        });
    }

    /**
     * Only update on connect if there is not already an active connection.
     * This alert is temporary and disappears after 10 seconds.
     */
    onConnect = () => {
        if (this.connected === false) {
            this.alert?.close();
            this.alert = this.alertService.addAlert({ type: AlertType.SUCCESS, message: 'artemisApp.connectionAlert.reconnected', timeout: 10000 });
        }
        this.connected = true;
    };

    /**
     * Only update on disconnect if the connection was active before.
     * This needs to be checked because the websocket service triggers a disconnect before the connect.
     */
    onDisconnect = () => {
        if (this.connected === true) {
            this.alert?.close();
            this.alert = this.alertService.addAlert({ type: AlertType.DANGER, message: 'artemisApp.connectionAlert.disconnected', timeout: 0, dismissible: false });
            this.connected = false;
        }
    };
}
