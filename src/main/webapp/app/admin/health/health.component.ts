import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';

import { HealthService } from './health.service';
import { HealthModalComponent } from './health-modal.component';
import { Health, HealthDetails, HealthKey, HealthStatus } from 'app/admin/health/health.model';
import { faExclamation, faEye, faSync, faTowerBroadcast, faUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { KeyValuePipe } from '@angular/common';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { TumUiButtonComponent, TumUiTagComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
/**
 * Component for displaying system health status.
 * Shows health of various system components like database, mail, etc.
 */
@Component({
    selector: 'jhi-health',
    templateUrl: './health.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        FaIconComponent,
        KeyValuePipe,
        ArtemisTranslatePipe,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        HealthModalComponent,
        TumUiTagComponent,
        TumUiButtonComponent,
        TumUiTooltipDirective,
    ],
})
export class HealthComponent implements OnInit, OnDestroy {
    private readonly healthService = inject(HealthService);
    private readonly websocketService = inject(WebsocketService);

    readonly health = signal<Health | undefined>(undefined);

    readonly websocketConnected = signal<boolean>(false);
    private websocketStatusSubscription?: Subscription;

    showHealthModal = signal(false);
    /** Drives the refresh button's loading spinner while a health check is in flight. */
    readonly isRefreshing = signal(false);
    selectedHealth = signal<{ key: HealthKey; value: HealthDetails } | undefined>(undefined);

    protected readonly faSync = faSync;
    protected readonly faEye = faEye;
    protected readonly faTowerBroadcast = faTowerBroadcast;
    protected readonly faExclamation = faExclamation;
    protected readonly faUpRightFromSquare = faUpRightFromSquare;

    ngOnInit() {
        this.refresh();
        this.websocketStatusSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.websocketConnected.set(status.connected);
        });
    }

    ngOnDestroy() {
        this.websocketStatusSubscription?.unsubscribe();
    }

    getBadgeSeverity(statusState: HealthStatus): 'success' | 'danger' {
        if (statusState === 'UP') {
            return 'success';
        }
        return 'danger';
    }

    refresh(): void {
        this.isRefreshing.set(true);
        this.healthService.checkHealth().subscribe({
            next: (health) => {
                this.health.set(health);
                this.isRefreshing.set(false);
            },
            error: (error: HttpErrorResponse) => {
                if (error.status === 503 && this.isHealth(error.error)) {
                    this.health.set(error.error);
                }
                this.isRefreshing.set(false);
            },
        });
    }

    showHealth(health: { key: string; value: HealthDetails }): void {
        this.selectedHealth.set(health as { key: HealthKey; value: HealthDetails });
        this.showHealthModal.set(true);
    }

    private isHealth(value: unknown): value is Health {
        if (!value || typeof value !== 'object') {
            return false;
        }
        const candidate = value as Partial<Health>;
        return typeof candidate.status === 'string' && !!candidate.components && typeof candidate.components === 'object';
    }
}
