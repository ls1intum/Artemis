import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { Health, HealthDetails, HealthKey, HealthService } from './health.service';
import { HealthModalComponent } from './health-modal.component';

@Component({
    selector: 'jhi-health',
    templateUrl: './health.component.html',
})
export class HealthComponent implements OnInit {
    health?: Health;

    constructor(private modalService: NgbModal, private healthService: HealthService) {}

    ngOnInit() {
        this.refresh();
    }

    getBadgeClass(statusState: string) {
        if (statusState === 'UP') {
            return 'badge-success';
        } else {
            return 'badge-danger';
        }
    }

    refresh(): void {
        this.healthService.checkHealth().subscribe(
            (health) => (this.health = health),
            (error: HttpErrorResponse) => {
                if (error.status === 503) {
                    this.health = error.error;
                }
            },
        );
    }

    showHealth(health: { key: HealthKey; value: HealthDetails }): void {
        const modalRef = this.modalService.open(HealthModalComponent);
        modalRef.componentInstance.health = health;
    }
}
