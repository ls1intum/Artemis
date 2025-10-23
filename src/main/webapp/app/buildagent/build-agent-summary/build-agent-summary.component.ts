import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BuildAgentInformation, BuildAgentStatus } from 'app/buildagent/shared/entities/build-agent-information.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { Subscription } from 'rxjs';
import { faPause, faPlay, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { Router, RouterModule } from '@angular/router';
import { BuildAgent } from 'app/buildagent/shared/entities/build-agent.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { BuildAgentPauseAllModalComponent } from 'app/buildagent/build-agent-summary/build-agent-pause-all-modal/build-agent-pause-all-modal.component';
import { BuildAgentClearDistributedDataComponent } from 'app/buildagent/build-agent-summary/build-agent-clear-distributed-data/build-agent-clear-distributed-data.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { BuildAgentsService } from 'app/buildagent/build-agents.service';

@Component({
    selector: 'jhi-build-agents',
    templateUrl: './build-agent-summary.component.html',
    styleUrl: './build-agent-summary.component.scss',
    imports: [TranslateDirective, NgxDatatableModule, DataTableComponent, FontAwesomeModule, RouterModule],
})
export class BuildAgentSummaryComponent implements OnInit, OnDestroy {
    private readonly websocketService = inject(WebsocketService);
    private readonly buildAgentsService = inject(BuildAgentsService);
    private readonly buildQueueService = inject(BuildOverviewService);
    private readonly router = inject(Router);
    private readonly modalService = inject(NgbModal);
    private readonly alertService = inject(AlertService);

    buildAgents: BuildAgentInformation[] = [];
    buildCapacity = 0;
    currentBuilds = 0;
    channel: string = '/topic/admin/build-agents';
    websocketSubscription: Subscription;
    restSubscription: Subscription;
    routerLink: string;

    //icons
    protected readonly faTimes = faTimes;
    protected readonly faPause = faPause;
    protected readonly faPlay = faPlay;
    protected readonly faTrash = faTrash;

    ngOnInit() {
        this.routerLink = this.router.url;
        this.load();
        this.initWebsocketSubscription();
    }

    /**
     * This method is used to unsubscribe from the websocket channels when the component is destroyed.
     */
    ngOnDestroy() {
        this.websocketService.unsubscribe(this.channel);
        this.websocketSubscription?.unsubscribe();
        this.restSubscription?.unsubscribe();
    }

    /**
     * This method is used to initialize the websocket subscription for the build agents. It subscribes to the channel for the build agents.
     */
    initWebsocketSubscription() {
        this.websocketService.subscribe(this.channel);
        this.websocketSubscription = this.websocketService.receive(this.channel).subscribe((buildAgents) => {
            this.updateBuildAgents(buildAgents);
        });
    }

    private updateBuildAgents(buildAgents: BuildAgentInformation[]) {
        this.buildAgents = buildAgents;
        this.buildCapacity = this.buildAgents
            .filter((agent) => agent.status !== BuildAgentStatus.PAUSED && agent.status !== BuildAgentStatus.SELF_PAUSED)
            .reduce((sum, agent) => sum + (agent.maxNumberOfConcurrentBuildJobs || 0), 0);
        this.currentBuilds = this.buildAgents.reduce((sum, agent) => sum + (agent.numberOfCurrentBuildJobs || 0), 0);
    }

    /**
     * This method is used to load the build agents.
     */
    load() {
        this.restSubscription = this.buildAgentsService.getBuildAgentSummary().subscribe((buildAgents) => {
            this.updateBuildAgents(buildAgents);
        });
    }

    cancelBuildJob(buildJobId: string) {
        this.buildQueueService.cancelBuildJob(buildJobId).subscribe();
    }

    cancelAllBuildJobs(buildAgent?: BuildAgent) {
        if (!buildAgent?.name) {
            return;
        }

        const buildAgentToCancel = this.buildAgents.find((agent) => agent.buildAgent?.name === buildAgent.name);
        if (buildAgentToCancel?.buildAgent?.name) {
            this.buildQueueService.cancelAllRunningBuildJobsForAgent(buildAgentToCancel.buildAgent?.name).subscribe();
        }
    }

    displayPauseBuildAgentModal() {
        const modalRef: NgbModalRef = this.modalService.open(BuildAgentPauseAllModalComponent as Component);
        modalRef.result.then((result) => {
            if (result) {
                this.pauseAllBuildAgents();
            }
        });
    }

    displayClearDistributedDataModal() {
        const modalRef: NgbModalRef = this.modalService.open(BuildAgentClearDistributedDataComponent as Component, { size: 'lg' });
        modalRef.result.then((result) => {
            if (result) {
                this.clearDistributedData();
            }
        });
    }

    pauseAllBuildAgents() {
        this.buildAgentsService.pauseAllBuildAgents().subscribe({
            next: () => {
                this.load();
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.buildAgents.alerts.buildAgentsPaused',
                });
            },
            error: () => {
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: 'artemisApp.buildAgents.alerts.buildAgentPauseFailed',
                });
            },
        });
    }

    resumeAllBuildAgents() {
        this.buildAgentsService.resumeAllBuildAgents().subscribe({
            next: () => {
                this.load();
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.buildAgents.alerts.buildAgentsResumed',
                });
            },
            error: () => {
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: 'artemisApp.buildAgents.alerts.buildAgentResumeFailed',
                });
            },
        });
    }

    clearDistributedData() {
        this.buildAgentsService.clearDistributedData().subscribe({
            next: () => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.buildAgents.alerts.distributedDataCleared',
                });
            },
            error: () => {
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: 'artemisApp.buildAgents.alerts.distributedDataClearFailed',
                });
            },
        });
    }
}
