import { Component, ContentChild, ElementRef, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { faCircle, faExclamation, faTowerBroadcast } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-connection-status',
    templateUrl: './connection-status.component.html',
    styleUrls: ['./connection-status.component.scss'],
    imports: [NgClass, FaIconComponent, TranslateDirective],
})
export class JhiConnectionStatusComponent implements OnInit, OnDestroy {
    private websocketService = inject(WebsocketService);

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
