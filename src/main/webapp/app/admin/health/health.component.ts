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

    /** refresh health component on init */
    ngOnInit() {
        this.refresh();
    }

    /** define badges according to status state */
    getBadgeClass(statusState: string) {
        if (statusState === 'UP') {
            return 'badge-success';
        } else {
            return 'badge-danger';
        }
    }

    /**
     * Handles the subscription to the health service. It will update the component's health state when something is received.
     */
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

    /**
     * Creates a modal with the given health aspect if the user wants to see more details.
     * @param health
     */
    showHealth(health: { key: HealthKey; value: HealthDetails }): void {
        const modalRef = this.modalService.open(HealthModalComponent);
        modalRef.componentInstance.health = health;
    }
}
