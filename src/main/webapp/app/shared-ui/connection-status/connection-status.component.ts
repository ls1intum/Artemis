import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { faCircle } from '@fortawesome/free-solid-svg-icons/faCircle';
import { faExclamation } from '@fortawesome/free-solid-svg-icons/faExclamation';
import { faTowerBroadcast } from '@fortawesome/free-solid-svg-icons/faTowerBroadcast';
import { Subscription } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiTagComponent } from '@tumaet/ui-angular/tag';

@Component({
    selector: 'jhi-connection-status',
    templateUrl: './connection-status.component.html',
    styleUrls: ['./connection-status.component.css'],
    imports: [NgClass, FaIconComponent, TranslateDirective, TumAetUiTagComponent],
})
export class JhiConnectionStatusComponent implements OnInit, OnDestroy {
    private websocketService = inject(WebsocketService);

    isExamMode = input(false);
    readonly disconnected = signal(true);
    websocketStatusSubscription?: Subscription;

    // Icons
    readonly faCircle = faCircle;
    readonly faTowerBroadcast = faTowerBroadcast;
    readonly faExclamation = faExclamation;

    ngOnInit() {
        // listen to connect / disconnect events
        this.websocketStatusSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.disconnected.set(!status.connected);
        });
    }

    /**
     * Life cycle hook called by Angular for cleanup just before Angular destroys the component
     */
    ngOnDestroy() {
        this.websocketStatusSubscription?.unsubscribe();
    }
}
