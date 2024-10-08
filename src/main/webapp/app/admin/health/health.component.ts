import { Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { HealthService } from './health.service';
import { HealthModalComponent } from './health-modal.component';
import { Health, HealthDetails, HealthStatus } from 'app/admin/health/health.model';
import { faEye, faSync } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-health',
    templateUrl: './health.component.html',
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
