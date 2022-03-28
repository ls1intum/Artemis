import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { faTriangleExclamation, faWifi } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-connection-warning',
    templateUrl: './connection-warning.component.html',
    styleUrls: ['./connection-warning.component.scss'],
})
export class JhiConnectionWarningComponent implements OnInit, OnDestroy {
    @ViewChild('t') tooltip: NgbTooltip;

    display = false;
    disconnected = false;
    websocketStatusSubscription: Subscription;

    // Icons
    faTriangleExclamation = faTriangleExclamation;
    faWifi = faWifi;

    constructor(private websocketService: JhiWebsocketService) {}

    ngOnInit() {
        this.websocketStatusSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.disconnected = !status.connected;
            this.display = !status.intendedDisconnect && status.wasEverConnectedBefore;

            if (this.display && this.disconnected) {
                this.tooltip?.open();
            } else {
                this.tooltip?.close();
            }
        });
    }

    ngOnDestroy() {
        this.websocketStatusSubscription.unsubscribe();
    }
}
