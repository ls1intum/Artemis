import { Component, OnInit, inject, signal } from '@angular/core';
import { WebsocketAdminService } from 'app/core/admin/websocket/websocket-admin.service';
import { WebsocketNode } from 'app/core/admin/websocket/websocket-node.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { AlertService } from 'app/shared/service/alert.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPlug, faRedo, faSync } from '@fortawesome/free-solid-svg-icons';
import { NgIf } from '@angular/common';

@Component({
    selector: 'jhi-websocket-admin',
    templateUrl: './websocket-admin.component.html',
    standalone: true,
    imports: [TranslateDirective, FormsModule, FaIconComponent, NgIf],
})
export class WebsocketAdminComponent implements OnInit {
    protected readonly faPlug = faPlug;
    protected readonly faSync = faSync;
    protected readonly faRedo = faRedo;

    nodes = signal<WebsocketNode[]>([]);
    selectedNodeId = signal<string | undefined>(undefined);
    loading = signal(false);
    reconnecting = signal(false);

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
                const local = nodes.find((node) => node.local);
                this.selectedNodeId.set(local?.memberId);
                this.loading.set(false);
            },
            error: () => {
                this.loading.set(false);
                this.alertService.error('artemisApp.websocketAdmin.loadError');
            },
        });
    }

    reconnect(target?: string) {
        this.reconnecting.set(true);
        this.websocketAdminService.triggerReconnect(target).subscribe({
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
}
