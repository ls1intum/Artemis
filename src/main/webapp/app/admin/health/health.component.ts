import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { HealthService, HealthStatus, Health, HealthKey, HealthDetails } from './health.service';
import { HealthModalComponent } from './health-modal.component';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Component({
    selector: 'jhi-health',
    templateUrl: './health.component.html',
})
export class HealthComponent implements OnInit {
    health?: Health;
    disconnected = true;
    onConnected: () => void;
    onDisconnected: () => void;

    constructor(private modalService: NgbModal, private healthService: HealthService, private trackerService: JhiWebsocketService) {}

    ngOnInit() {
        this.refresh();

        // listen to connect / disconnect events
        this.onConnected = () => {
            this.disconnected = false;
        };
        this.trackerService.bind('connect', () => {
            this.onConnected();
        });
        this.onDisconnected = () => {
            this.disconnected = true;
        };
        this.trackerService.bind('disconnect', () => {
            this.onDisconnected();
        });
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
            health => (this.health = health),
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
