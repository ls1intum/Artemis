import { Component, OnDestroy, OnInit, inject, signal, viewChild } from '@angular/core';
import { faExclamationCircle, faWifi } from '@fortawesome/free-solid-svg-icons';
import { Subscription, distinctUntilChanged, filter, map } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { NavigationEnd, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CloseCircleComponent } from '../close-circle/close-circle.component';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-connection-warning',
    templateUrl: './connection-warning.component.html',
    styleUrls: ['./connection-warning.component.scss'],
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, CloseCircleComponent, NgClass, NgbPopover],
})
export class JhiConnectionWarningComponent implements OnInit, OnDestroy {
    private websocketService = inject(WebsocketService);
    private router = inject(Router);

    private readonly popoverRef = viewChild<NgbPopover>('popover');

    get popover(): NgbPopover {
        return this.popoverRef()!;
    }

    readonly disconnected = signal(false);
    readonly isOnExamParticipationPage = signal(false);
    websocketStatusSubscription?: Subscription;
    // Initialise with an empty Subscription so ngOnDestroy can unsubscribe safely even if the
    // constructor never assigned a real subscription (e.g. component torn down mid-construction).
    routerSubscription: Subscription = new Subscription();
    openTimeout?: ReturnType<typeof setTimeout>;

    // Icons
    faExclamationCircle = faExclamationCircle;
    faWifi = faWifi;

    constructor() {
        this.routerSubscription = this.router.events
            .pipe(filter((event) => event instanceof NavigationEnd))
            .subscribe((event: NavigationEnd) => this.isOnExamParticipationPage.set(!!event.url.match('^/courses/\\d+/exams/\\d+')));
    }

    ngOnInit() {
        this.websocketStatusSubscription = this.websocketService.connectionState
            .pipe(
                map((status) => !status.connected && status.wasEverConnectedBefore),
                distinctUntilChanged(),
            )
            .subscribe((disconnected) => {
                this.disconnected.set(disconnected);

                if (this.disconnected()) {
                    this.openTimeout = setTimeout(() => this.popoverRef()?.open(), 300);
                } else {
                    clearTimeout(this.openTimeout);
                    this.popoverRef()?.close();
                }
            });
    }

    /** Manual interaction takes precedence over the delayed automatic warning. */
    protected togglePopover(): void {
        clearTimeout(this.openTimeout);
        this.popoverRef()?.toggle();
    }

    ngOnDestroy() {
        this.websocketStatusSubscription?.unsubscribe();
        this.routerSubscription.unsubscribe();
        clearTimeout(this.openTimeout);
    }
}
