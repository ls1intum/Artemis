import { Component, ContentChild, ElementRef, OnDestroy, OnInit } from '@angular/core';
import { faCircle } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Component({
    selector: 'jhi-connection-status',
    templateUrl: './connection-status.component.html',
    styleUrls: ['./connection-status.component.scss'],
})
export class JhiConnectionStatusComponent implements OnInit, OnDestroy {
    @ContentChild('innerContent', { static: false }) innerContent: ElementRef;

    disconnected = true;
    websocketStatusSubscription: Subscription;

    // Icons
    faCircle = faCircle;

    constructor(private websocketService: JhiWebsocketService) {}

    ngOnInit() {
        // listen to connect / disconnect events
        this.websocketStatusSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.disconnected = !status.connected;
        });
    }

    /**
     * Life cycle hook called by Angular for cleanup just before Angular destroys the component
     */
    ngOnDestroy() {
        this.websocketStatusSubscription.unsubscribe();
    }
}
