import { Component, OnDestroy, OnInit, inject, input } from '@angular/core';
import { faCircle, faExclamation, faTowerBroadcast } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-connection-status',
    templateUrl: './connection-status.component.html',
    styleUrls: ['./connection-status.component.scss'],
    imports: [NgClass, FaIconComponent, TranslateDirective],
})
export class JhiConnectionStatusComponent implements OnInit, OnDestroy {
    private websocketService = inject(WebsocketService);

    isExamMode = input(false);
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
