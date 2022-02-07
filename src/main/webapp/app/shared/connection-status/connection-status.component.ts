import { Component, OnInit, OnDestroy, ContentChild, ElementRef } from '@angular/core';
import { faCircle } from '@fortawesome/free-solid-svg-icons';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Component({
    selector: 'jhi-connection-status',
    templateUrl: './connection-status.component.html',
    styleUrls: ['./connection-status.component.scss'],
})
export class JhiConnectionStatusComponent implements OnInit, OnDestroy {
    @ContentChild('innerContent', { static: false }) innerContent: ElementRef;

    disconnected = true;
    onConnected: () => void;
    onDisconnected: () => void;

    // Icons
    faCircle = faCircle;

    constructor(private jhiWebsocketService: JhiWebsocketService) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        // listen to connect / disconnect events
        this.onConnected = () => {
            this.disconnected = false;
        };
        this.jhiWebsocketService.bind('connect', () => {
            this.onConnected();
        });
        this.onDisconnected = () => {
            this.disconnected = true;
        };
        this.jhiWebsocketService.bind('disconnect', () => {
            this.onDisconnected();
        });
    }

    /**
     * Life cycle hook called by Angular for cleanup just before Angular destroys the component
     */
    ngOnDestroy() {
        if (this.onConnected) {
            this.jhiWebsocketService.unbind('connect', this.onConnected);
        }
        if (this.onDisconnected) {
            this.jhiWebsocketService.unbind('disconnect', this.onDisconnected);
        }
    }
}
