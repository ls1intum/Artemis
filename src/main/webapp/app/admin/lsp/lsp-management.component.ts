import { Component, OnInit } from '@angular/core';
import { CodeEditorMonacoService } from 'app/exercises/programming/shared/code-editor/service/code-editor-monaco.service';
import { LspStatus } from 'app/entities/lsp-status.model';
import { AlertService } from 'app/core/util/alert.service';
import { faPause, faPlay, faPlus, faSync } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AddServerModalComponent } from 'app/admin/lsp/add-server-modal.component';

@Component({
    selector: 'jhi-lsp',
    templateUrl: './lsp-management.component.html',
    styleUrls: ['./lsp-management.component.scss'],
})
export class LspManagementComponent implements OnInit {
    serversStatus: Array<LspStatus>;
    isLoading = false;

    // Icons
    faSync = faSync;
    faPlus = faPlus;
    faPause = faPause;
    faPlay = faPlay;

    constructor(private monacoService: CodeEditorMonacoService, private alertService: AlertService, private modalService: NgbModal) {}

    ngOnInit(): void {
        this.refresh(true);
    }

    refresh(updateMetrics: boolean) {
        this.isLoading = true;
        this.monacoService.getLspServersStatus(updateMetrics).subscribe({
            next: (servers) => {
                this.serversStatus = servers;
                this.serversStatus.map((status) => (status.memUsage = Math.round(((status.totalMem - status.freeMem) / status.totalMem) * 100) || 0));
                this.isLoading = false;
            },
            error: (e) => {
                this.alertService.error(e);
                this.isLoading = false;
            },
        });
    }

    /**
     * Opens a modal and requests the adding of a new LSP server
     */
    addServer() {
        const modalRef = this.modalService.open(AddServerModalComponent, {
            size: 'lg',
        });
        modalRef.componentInstance.title = 'artemisApp.lsp.modals.addServerTitle';
        modalRef.componentInstance.text = 'artemisApp.lsp.modals.addServerText';
        modalRef.closed.subscribe((serverUrl: string) => {
            if (serverUrl && serverUrl.length > 0) {
                if (this.serversStatus.filter((status) => status.url!.localeCompare(serverUrl) === 0).length > 0) {
                    this.alertService.warning('artemisApp.lsp.alerts.serverExisting');
                    return;
                }
                this.monacoService.addServer(serverUrl).subscribe({
                    next: (state) => this.serversStatus.push(state),
                    error: () => this.alertService.error('artemisApp.lsp.alerts.errorServerAdd'),
                });
            }
        });
    }

    /**
     * Pauses a given server and ensures that no new sessions are
     * started on it
     * @param serverStatus of the server to stop
     */
    pauseServer(serverStatus: LspStatus) {
        this.monacoService.pauseServer(serverStatus.url!).subscribe((pauseState) => (serverStatus.paused = pauseState));
    }
}
