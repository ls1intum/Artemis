import { Component, ElementRef, OnDestroy, OnInit, TemplateRef, ViewContainerRef, inject, signal, viewChild } from '@angular/core';
import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { faExclamationCircle } from '@fortawesome/free-solid-svg-icons/faExclamationCircle';
import { faWifi } from '@fortawesome/free-solid-svg-icons/faWifi';
import { Subscription, distinctUntilChanged, filter, map } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { NavigationEnd, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CloseCircleComponent } from '../close-circle/close-circle.component';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-connection-warning',
    templateUrl: './connection-warning.component.html',
    styleUrls: ['./connection-warning.component.css'],
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, CloseCircleComponent, NgClass],
})
export class JhiConnectionWarningComponent implements OnInit, OnDestroy {
    private websocketService = inject(WebsocketService);
    private router = inject(Router);
    private readonly overlay = inject(Overlay);
    private readonly viewContainerRef = inject(ViewContainerRef);

    private readonly warningRef = viewChild.required<ElementRef<HTMLElement>>('warning');
    private readonly panelRef = viewChild.required('panel', { read: TemplateRef });
    private overlayRef?: OverlayRef;

    /** Whether the warning panel is shown. */
    readonly isOpen = signal(false);
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
                    this.openTimeout = setTimeout(() => this.open(), 300);
                } else {
                    clearTimeout(this.openTimeout);
                    this.close();
                }
            });
    }

    /** Shows the warning below the indicator. Nothing happens while it is already shown. */
    open(): void {
        if (this.isOpen()) {
            return;
        }
        const strategy = this.overlay
            .position()
            .flexibleConnectedTo(this.warningRef())
            .withPositions([
                { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 12 },
                { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -12 },
            ])
            .withPush(true);
        this.overlayRef = this.overlay.create({ positionStrategy: strategy, scrollStrategy: this.overlay.scrollStrategies.reposition(), hasBackdrop: false });
        this.overlayRef.attach(new TemplatePortal(this.panelRef(), this.viewContainerRef));
        this.isOpen.set(true);
    }

    close(): void {
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.isOpen.set(false);
    }

    /** Manual interaction takes precedence over the delayed automatic warning. */
    protected toggle(): void {
        clearTimeout(this.openTimeout);
        if (this.isOpen()) {
            this.close();
        } else {
            this.open();
        }
    }

    ngOnDestroy() {
        this.websocketStatusSubscription?.unsubscribe();
        this.routerSubscription.unsubscribe();
        clearTimeout(this.openTimeout);
        this.close();
    }
}
