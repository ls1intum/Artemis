import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';

import { HealthService } from './health.service';
import { HealthModalComponent } from './health-modal.component';
import { Health, HealthDetails, HealthKey, HealthStatus } from 'app/admin/health/health.model';
import { faExclamation, faEye, faSync, faTowerBroadcast } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { KeyValuePipe } from '@angular/common';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';

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
        TagModule,
        ButtonModule,
        TooltipModule,
    ],
})
export class HealthComponent implements OnInit, OnDestroy {
    private readonly healthService = inject(HealthService);
    private readonly websocketService = inject(WebsocketService);

    /** Current system health status */
    readonly health = signal<Health | undefined>(undefined);

    /** Whether the client is currently connected to the server via websocket */
    readonly websocketConnected = signal<boolean>(false);
    private websocketStatusSubscription?: Subscription;

    /** Health modal visibility and data */
    showHealthModal = signal(false);
    selectedHealth = signal<{ key: HealthKey; value: HealthDetails } | undefined>(undefined);

    /** Icons */
    protected readonly faSync = faSync;
    protected readonly faEye = faEye;
    protected readonly faTowerBroadcast = faTowerBroadcast;
    protected readonly faExclamation = faExclamation;

    ngOnInit() {
        this.refresh();
        // listen to connect / disconnect events (mirrors JhiConnectionStatusComponent)
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

    /**
     * Refreshes the health status by fetching from the server.
     */
    refresh(): void {
        this.healthService.checkHealth().subscribe({
            next: (health) => {
                this.health.set(health);
            },
            error: (error: HttpErrorResponse) => {
                if (error.status === 503) {
                    this.health.set(error.error);
                }
            },
        });
    }

    showHealth(health: { key: string; value: HealthDetails }): void {
        this.selectedHealth.set(health as { key: HealthKey; value: HealthDetails });
        this.showHealthModal.set(true);
    }
}
