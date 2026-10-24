import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { WebsocketAdminService } from 'app/admin/websocket/websocket-admin.service';
import { WebsocketNode } from 'app/admin/websocket/websocket-node.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPlug, faPowerOff, faSync } from '@fortawesome/free-solid-svg-icons';
import { Subscription, forkJoin, interval } from 'rxjs';
import { map } from 'rxjs/operators';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumAetUiButtonComponent, TumAetUiButtonGroupComponent, TumAetUiMessageComponent, TumAetUiTableDirective, TumAetUiTagComponent } from '@tumaet/ui-angular';
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
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        DatePipe,
        AdminTitleBarTitleDirective,
        TumAetUiButtonComponent,
        TumAetUiButtonGroupComponent,
        TumAetUiTagComponent,
        TumAetUiMessageComponent,
        TumAetUiTableDirective,
    ],
})
export class WebsocketAdminComponent implements OnInit, OnDestroy {
    private websocketAdminService = inject(WebsocketAdminService);
    private alertService = inject(AlertService);

    protected readonly faPlug = faPlug;
    protected readonly faSync = faSync;
    protected readonly faPowerOff = faPowerOff;

    nodes = signal<WebsocketNode[]>([]);
    loading = signal(false);
    reconnecting = signal(false);
    coreNodes = computed(() => this.nodes());
    sortedNodes = computed(() => {
        const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });
        return [...this.nodes()].sort((a, b) => {
            const nameA = (a.instanceId ?? a.host ?? a.memberId).trim();
            const nameB = (b.instanceId ?? b.host ?? b.memberId).trim();
            return collator.compare(nameA, nameB);
        });
    });
    lastUpdated = signal<Date | undefined>(undefined);
    lastUpdateFailed = signal(false);

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

    reconnect(target?: string) {
        this.triggerAction('RECONNECT', 'reconnect', target);
    }

    disconnect(target?: string) {
        this.triggerAction('DISCONNECT', 'disconnect', target);
    }

    connect(target?: string) {
        this.triggerAction('CONNECT', 'connect', target);
    }

    /** Execute one broker action for the selected node, or for every available core node. */
    private triggerAction(action: 'RECONNECT' | 'DISCONNECT' | 'CONNECT', translation: string, target?: string) {
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
                ? this.websocketAdminService.triggerAction(action, availableTargets[0])
                : forkJoin(availableTargets.map((nodeId) => this.websocketAdminService.triggerAction(action, nodeId))).pipe(map(() => void 0));

        request$.subscribe({
            next: () => {
                this.reconnecting.set(false);
                this.alertService.success(`artemisApp.websocketAdmin.${translation}Requested`);
            },
            error: () => {
                this.reconnecting.set(false);
                this.alertService.error(`artemisApp.websocketAdmin.${translation}Failed`);
            },
        });
    }
}
