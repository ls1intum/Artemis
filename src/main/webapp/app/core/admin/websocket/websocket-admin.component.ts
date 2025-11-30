import { DatePipe, NgClass } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { WebsocketAdminService } from 'app/core/admin/websocket/websocket-admin.service';
import { WebsocketNode } from 'app/core/admin/websocket/websocket-node.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { AlertService } from 'app/shared/service/alert.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPlug, faPowerOff, faSync } from '@fortawesome/free-solid-svg-icons';
import { Subscription, forkJoin, interval } from 'rxjs';
import { map } from 'rxjs/operators';

/**
 * Admin view to monitor and control websocket broker connectivity across Hazelcast nodes.
 * <p>
 * Features:
 * - Auto-refresh every 5 seconds with last-success/failed indicator.
 * - Distinguishes core nodes from build agents (lite members).
 * - Per-node connect/disconnect controls and bulk actions for all core nodes.
 */
@Component({
    selector: 'jhi-websocket-admin',
    templateUrl: './websocket-admin.component.html',
    standalone: true,
    imports: [TranslateDirective, FormsModule, FaIconComponent, NgClass, DatePipe],
})
export class WebsocketAdminComponent implements OnInit, OnDestroy {
    protected readonly faPlug = faPlug;
    protected readonly faSync = faSync;
    protected readonly faPowerOff = faPowerOff;

    nodes = signal<WebsocketNode[]>([]);
    loading = signal(false);
    reconnecting = signal(false);
    coreNodes = computed(() => this.nodes().filter((node) => !node.liteMember));
    sortedNodes = computed(() => {
        const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });
        return [...this.nodes()].sort((a, b) => {
            if (a.liteMember !== b.liteMember) {
                return a.liteMember ? 1 : -1; // core nodes first
            }
            const nameA = (a.instanceId ?? a.host ?? a.memberId).trim();
            const nameB = (b.instanceId ?? b.host ?? b.memberId).trim();
            return collator.compare(nameA, nameB);
        });
    });
    lastUpdated = signal<Date | undefined>(undefined);
    lastUpdateFailed = signal(false);

    private websocketAdminService = inject(WebsocketAdminService);
    private alertService = inject(AlertService);
    private refreshSubscription?: Subscription;

    ngOnInit(): void {
        this.loadNodes();
        this.startAutoRefresh();
    }

    ngOnDestroy(): void {
        this.refreshSubscription?.unsubscribe();
    }

    /**
     * Fetch current node metadata from the server and update the UI status.
     * Records the timestamp on success and marks the last fetch as failed otherwise.
     */
    loadNodes() {
        this.loading.set(true);
        this.websocketAdminService.getNodes().subscribe({
            next: (nodes) => {
                this.nodes.set(nodes);
                this.loading.set(false);
                this.lastUpdated.set(new Date());
                this.lastUpdateFailed.set(false);
            },
            error: () => {
                this.loading.set(false);
                this.lastUpdateFailed.set(true);
            },
        });
    }

    /**
     * Periodically reload the node list (every 5 seconds) while avoiding overlap with an ongoing request.
     */
    private startAutoRefresh() {
        this.refreshSubscription = interval(5000).subscribe(() => {
            if (!this.loading()) {
                this.loadNodes();
            }
        });
    }

    /**
     * Trigger a reconnect on the target node(s). If no target is passed, all core nodes are addressed.
     */
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

    /**
     * Trigger a disconnect on the target node(s). If no target is passed, all core nodes are addressed.
     */
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

    /**
     * Trigger a connect on the target node(s). If no target is passed, all core nodes are addressed.
     */
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
