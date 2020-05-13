import { Component, OnInit, OnDestroy, ContentChild, ElementRef } from '@angular/core';
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

    constructor(private jhiWebsocketService: JhiWebsocketService) {}

    /**
     * Life cycle hook, called on initialisation.
     * Binds {@link onConnected} | {@link onDisconnected} to {@link jhiWebsocketService~connect} | {@link jhiWebsocketService~disconnect} events.
     * See: {@link jhiWebsocketService~bind}
     * @listens jhiWebsocketService~disconnect
     * @listens jhiWebsocketService~connect
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
     * Life cycle hook called by Angular for cleanup just before Angular destroys the component.
     * Unbinds {@link onConnected} | {@link onDisconnected} from {@link jhiWebsocketService~connect} | {@link jhiWebsocketService~disconnect} events.
     * See: {@link jhiWebsocketService~unbind}
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
