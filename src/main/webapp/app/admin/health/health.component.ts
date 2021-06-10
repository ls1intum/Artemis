import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { Health, HealthDetails, HealthService } from './health.service';
import { HealthModalComponent } from './health-modal.component';
import { KeyValue } from '@angular/common';

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

    getBadgeClass(statusState: string | undefined) {
        if (statusState === 'UP') {
            return 'bg-success';
        } else {
            return 'bg-danger';
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

    showHealth(health: KeyValue<string, HealthDetails | undefined>): void {
        const modalRef = this.modalService.open(HealthModalComponent);
        modalRef.componentInstance.health = health;
    }
}
