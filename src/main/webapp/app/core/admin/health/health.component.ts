import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { HealthService } from './health.service';
import { HealthModalComponent } from './health-modal.component';
import { Health, HealthDetails, HealthStatus } from 'app/core/admin/health/health.model';
import { faEye, faSync } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { KeyValuePipe, NgClass } from '@angular/common';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Component for displaying system health status.
 * Shows health of various system components like database, mail, etc.
 */
@Component({
    selector: 'jhi-health',
    templateUrl: './health.component.html',
    imports: [TranslateDirective, FaIconComponent, NgClass, JhiConnectionStatusComponent, KeyValuePipe, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HealthComponent implements OnInit {
    private readonly modalService = inject(NgbModal);
    private readonly healthService = inject(HealthService);

    /** Current system health status */
    readonly health = signal<Health | undefined>(undefined);

    /** Icons */
    protected readonly faSync = faSync;
    protected readonly faEye = faEye;

    ngOnInit() {
        this.refresh();
    }

    getBadgeClass(statusState: HealthStatus) {
        if (statusState === 'UP') {
            return 'bg-success';
        }
        return 'bg-danger';
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
        const modalRef = this.modalService.open(HealthModalComponent);
        modalRef.componentInstance.health = health;
    }
}
