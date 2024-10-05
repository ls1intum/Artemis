import { Component, ContentChild, ElementRef, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { faCircle, faExclamation, faTowerBroadcast } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Component({
    selector: 'jhi-connection-status',
    templateUrl: './connection-status.component.html',
    styleUrls: ['./connection-status.component.scss'],
})
export class JhiConnectionStatusComponent implements OnInit, OnDestroy {
    private websocketService = inject(JhiWebsocketService);

    @ContentChild('innerContent', { static: false }) innerContent: ElementRef;
    @Input() isExamMode = false;
    disconnected = true;
    websocketStatusSubscription: Subscription;

    // Icons
    readonly faCircle = faCircle;
    readonly faTowerBroadcast = faTowerBroadcast;
    readonly faExclamation = faExclamation;

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
