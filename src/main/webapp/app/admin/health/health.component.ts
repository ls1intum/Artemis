import { Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { HealthService } from './health.service';
import { HealthModalComponent } from './health-modal.component';
import { Health, HealthDetails, HealthStatus } from 'app/admin/health/health.model';
import { faEye, faSync } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { KeyValuePipe, NgClass } from '@angular/common';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-health',
    templateUrl: './health.component.html',
    imports: [TranslateDirective, FaIconComponent, NgClass, JhiConnectionStatusComponent, KeyValuePipe, ArtemisTranslatePipe],
})
export class HealthComponent implements OnInit {
    private modalService = inject(NgbModal);
    private healthService = inject(HealthService);

    health?: Health;

    // Icons
    faSync = faSync;
    faEye = faEye;

    ngOnInit() {
        this.refresh();
    }

    getBadgeClass(statusState: HealthStatus) {
        if (statusState === 'UP') {
            return 'bg-success';
        }
        return 'bg-danger';
    }

    refresh(): void {
        this.healthService.checkHealth().subscribe({
            next: (health) => (this.health = health),
            error: (error: HttpErrorResponse) => {
                if (error.status === 503) {
                    this.health = error.error;
                }
            },
        });
    }

    showHealth(health: { key: string; value: HealthDetails }): void {
        const modalRef = this.modalService.open(HealthModalComponent);
        modalRef.componentInstance.health = health;
    }
}
