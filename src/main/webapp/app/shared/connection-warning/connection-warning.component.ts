import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { faExclamationCircle, faWifi } from '@fortawesome/free-solid-svg-icons';
import { Subscription, filter } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { NavigationEnd, Router } from '@angular/router';

@Component({
    selector: 'jhi-connection-warning',
    templateUrl: './connection-warning.component.html',
    styleUrls: ['./connection-warning.component.scss'],
})
export class JhiConnectionWarningComponent implements OnInit, OnDestroy {
    @ViewChild('popover') popover: NgbPopover;

    disconnected = false;
    isOnExamParticipationPage = false;
    websocketStatusSubscription: Subscription;
    routerSubscription: Subscription;
    openTimeout: any;

    // Icons
    faExclamationCircle = faExclamationCircle;
    faWifi = faWifi;

    constructor(private websocketService: JhiWebsocketService, private router: Router) {
        this.routerSubscription = router.events
            .pipe(filter((event) => event instanceof NavigationEnd))
            .subscribe((event: NavigationEnd) => (this.isOnExamParticipationPage = !!event.url.match('^/courses/\\d+/exams/\\d+')));
    }

    ngOnInit() {
        this.websocketStatusSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.disconnected = !status.connected && !status.intendedDisconnect && status.wasEverConnectedBefore;

            if (this.disconnected) {
                this.openTimeout = setTimeout(() => this.popover?.open(), 300);
            } else {
                clearTimeout(this.openTimeout);
                this.popover?.close();
            }
        });
    }

    ngOnDestroy() {
        this.websocketStatusSubscription.unsubscribe();
        this.routerSubscription.unsubscribe();
    }
}
