import { Component, OnDestroy, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { User } from 'app/core/user/user.model';
import { ConnectionNotification, ConnectionNotificationType } from 'app/shared/notification/connection-notification/connection-notification.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCheckCircle, faTimesCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-connection-notification',
    templateUrl: './connection-notification.component.html',
    styleUrls: ['connection-notification.scss'],
})
export class ConnectionNotificationComponent implements OnInit, OnDestroy {
    notification = new ConnectionNotification();
    alert?: { class: string; icon: IconProp; text: string };
    connected?: boolean;

    constructor(private accountService: AccountService, private jhiWebsocketService: JhiWebsocketService) {}

    ngOnInit() {
        this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
            if (user) {
                // listen to connect / disconnect events
                this.jhiWebsocketService.enableReconnect();
                this.jhiWebsocketService.bind('connect', this.onConnect);
                this.jhiWebsocketService.bind('disconnect', this.onDisconnect);
            } else {
                // On logout, reset component
                this.connected = undefined;
                this.alert = undefined;
                this.notification.type = undefined;
                this.jhiWebsocketService.disableReconnect();
                this.jhiWebsocketService.unbind('connect', this.onConnect);
                this.jhiWebsocketService.unbind('disconnect', this.onDisconnect);
            }
        });
    }

    ngOnDestroy() {
        this.jhiWebsocketService.unbind('connect', this.onConnect);
        this.jhiWebsocketService.unbind('disconnect', this.onDisconnect);
    }

    /**
     * Only update on connect if there is not already an active connection.
     * This alert is temporary and disappears after 5 seconds.
     **/

    onConnect = () => {
        if (this.connected === false) {
            this.notification.type = ConnectionNotificationType.RECONNECTED;
            this.updateAlert();
            // The reconnect alert should only be displayed temporarily
            setTimeout(() => {
                this.notification.type = ConnectionNotificationType.CONNECTED;
                this.updateAlert();
            }, 5000);
        }
        this.connected = true;
    };

    /**
     * Only update on disconnect if the connection was active before.
     * This needs to be checked because the websocket service triggers a disconnect before the connect.
     **/

    onDisconnect = () => {
        if (this.connected === true) {
            this.notification.type = ConnectionNotificationType.DISCONNECTED;
            this.updateAlert();
            this.connected = false;
        }
    };

    /**
     * Update the alert to fit the state of the notification.
     **/

    updateAlert(): void {
        if (this.notification) {
            if (this.notification.type === ConnectionNotificationType.DISCONNECTED) {
                this.alert = { class: 'alert-danger', icon: faTimesCircle, text: 'artemisApp.connectionAlert.disconnected' };
            } else if (this.notification.type === ConnectionNotificationType.RECONNECTED) {
                this.alert = { class: 'alert-success', icon: faCheckCircle, text: 'artemisApp.connectionAlert.reconnected' };
            } else if (this.notification.type === ConnectionNotificationType.CONNECTED) {
                this.alert = undefined;
            }
        }
    }
}
