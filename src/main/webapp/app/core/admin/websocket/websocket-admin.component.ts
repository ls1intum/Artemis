import { NgClass } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { WebsocketAdminService } from 'app/core/admin/websocket/websocket-admin.service';
import { WebsocketNode } from 'app/core/admin/websocket/websocket-node.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { AlertService } from 'app/shared/service/alert.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPlug, faPowerOff, faSync } from '@fortawesome/free-solid-svg-icons';
import { forkJoin } from 'rxjs';
import { map } from 'rxjs/operators';

@Component({
    selector: 'jhi-websocket-admin',
    templateUrl: './websocket-admin.component.html',
    standalone: true,
    imports: [TranslateDirective, FormsModule, FaIconComponent, NgClass],
})
export class WebsocketAdminComponent implements OnInit {
    protected readonly faPlug = faPlug;
    protected readonly faSync = faSync;
    protected readonly faPowerOff = faPowerOff;

    nodes = signal<WebsocketNode[]>([]);
    loading = signal(false);
    reconnecting = signal(false);
    coreNodes = computed(() => this.nodes().filter((node) => !node.liteMember));

    private websocketAdminService = inject(WebsocketAdminService);
    private alertService = inject(AlertService);

    ngOnInit(): void {
        this.loadNodes();
    }

    loadNodes() {
        this.loading.set(true);
        this.websocketAdminService.getNodes().subscribe({
            next: (nodes) => {
                this.nodes.set(nodes);
                this.loading.set(false);
            },
            error: () => {
                this.loading.set(false);
                this.alertService.error('artemisApp.websocketAdmin.loadError');
            },
        });
    }

    reconnect(target?: string) {
        const availableTargets = target
            ? this.coreNodes()
                  .filter((node) => node.memberId === target)
                  .map((node) => node.memberId)
            : this.coreNodes().map((node) => node.memberId);

        if (availableTargets.length === 0) {
            this.alertService.error('artemisApp.websocketAdmin.noCoreNodes');
            return;
        }

        this.reconnecting.set(true);
        const request$ =
            availableTargets.length === 1
                ? this.websocketAdminService.triggerAction('RECONNECT', availableTargets[0])
                : forkJoin(availableTargets.map((nodeId) => this.websocketAdminService.triggerAction('RECONNECT', nodeId))).pipe(map(() => void 0));

        request$.subscribe({
            next: () => {
                this.reconnecting.set(false);
                this.alertService.success('artemisApp.websocketAdmin.reconnectRequested');
            },
            error: () => {
                this.reconnecting.set(false);
                this.alertService.error('artemisApp.websocketAdmin.reconnectFailed');
            },
        });
    }

    disconnect(target?: string) {
        const availableTargets = target
            ? this.coreNodes()
                  .filter((node) => node.memberId === target)
                  .map((node) => node.memberId)
            : this.coreNodes().map((node) => node.memberId);

        if (availableTargets.length === 0) {
            this.alertService.error('artemisApp.websocketAdmin.noCoreNodes');
            return;
        }

        this.reconnecting.set(true);
        const request$ =
            availableTargets.length === 1
                ? this.websocketAdminService.triggerAction('DISCONNECT', availableTargets[0])
                : forkJoin(availableTargets.map((nodeId) => this.websocketAdminService.triggerAction('DISCONNECT', nodeId))).pipe(map(() => void 0));

        request$.subscribe({
            next: () => {
                this.reconnecting.set(false);
                this.alertService.success('artemisApp.websocketAdmin.disconnectRequested');
            },
            error: () => {
                this.reconnecting.set(false);
                this.alertService.error('artemisApp.websocketAdmin.disconnectFailed');
            },
        });
    }

    connect(target?: string) {
        const availableTargets = target
            ? this.coreNodes()
                  .filter((node) => node.memberId === target)
                  .map((node) => node.memberId)
            : this.coreNodes().map((node) => node.memberId);

        if (availableTargets.length === 0) {
            this.alertService.error('artemisApp.websocketAdmin.noCoreNodes');
            return;
        }

        this.reconnecting.set(true);
        const request$ =
            availableTargets.length === 1
                ? this.websocketAdminService.triggerAction('CONNECT', availableTargets[0])
                : forkJoin(availableTargets.map((nodeId) => this.websocketAdminService.triggerAction('CONNECT', nodeId))).pipe(map(() => void 0));

        request$.subscribe({
            next: () => {
                this.reconnecting.set(false);
                this.alertService.success('artemisApp.websocketAdmin.connectRequested');
            },
            error: () => {
                this.reconnecting.set(false);
                this.alertService.error('artemisApp.websocketAdmin.connectFailed');
            },
        });
    }
}
