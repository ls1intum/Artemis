import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
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

/**
 * Component that displays a summary of all build agents in the system.
 * Shows the status of each agent, current build capacity, and allows
 * administrators to pause/resume agents and clear distributed data.
 *
 * Uses OnPush change detection with signals for optimal performance.
 */
@Component({
    selector: 'jhi-build-agents',
    templateUrl: './build-agent-summary.component.html',
    styleUrl: './build-agent-summary.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, NgxDatatableModule, DataTableComponent, FontAwesomeModule, RouterModule],
})
export class BuildAgentSummaryComponent implements OnInit, OnDestroy {
    private readonly websocketService = inject(WebsocketService);
    private readonly buildAgentsService = inject(BuildAgentsService);
    private readonly buildQueueService = inject(BuildOverviewService);
    private readonly router = inject(Router);
    private readonly modalService = inject(NgbModal);
    private readonly alertService = inject(AlertService);

    /** Signal containing the list of all build agents with their current status */
    readonly buildAgents = signal<BuildAgentInformation[]>([]);

    /**
     * Computed signal that calculates the total build capacity across all active agents.
     * Excludes paused agents from the calculation since they cannot accept new builds.
     */
    readonly buildCapacity = computed(() =>
        this.buildAgents()
            .filter((agent) => agent.status !== BuildAgentStatus.PAUSED && agent.status !== BuildAgentStatus.SELF_PAUSED)
            .reduce((totalCapacity, agent) => totalCapacity + (agent.maxNumberOfConcurrentBuildJobs || 0), 0),
    );

    /**
     * Computed signal that calculates the total number of currently running builds
     * across all agents.
     */
    readonly currentBuilds = computed(() => this.buildAgents().reduce((totalBuilds, agent) => totalBuilds + (agent.numberOfCurrentBuildJobs || 0), 0));

    /** WebSocket topic for receiving real-time build agent updates */
    readonly buildAgentsWebsocketTopic = '/topic/admin/build-agents';

    /**
     * Subscription for WebSocket updates.
     * @internal Exposed for testing purposes only.
     */
    buildAgentsWebsocketSubscription?: Subscription;

    /**
     * Subscription for initial REST API load.
     * @internal Exposed for testing purposes only.
     */
    initialLoadSubscription?: Subscription;

    /** Current router URL used for navigation */
    routerLink: string;

    // Font Awesome icons for the UI
    protected readonly faTimes = faTimes;
    protected readonly faPause = faPause;
    protected readonly faPlay = faPlay;
    protected readonly faTrash = faTrash;

    ngOnInit(): void {
        this.routerLink = this.router.url;
        this.loadBuildAgents();
        this.subscribeToWebsocketUpdates();
    }

    ngOnDestroy(): void {
        this.buildAgentsWebsocketSubscription?.unsubscribe();
        this.initialLoadSubscription?.unsubscribe();
    }

    /**
     * Subscribes to the WebSocket topic to receive real-time updates
     * when build agent status changes.
     */
    private subscribeToWebsocketUpdates(): void {
        this.buildAgentsWebsocketSubscription = this.websocketService.subscribe<BuildAgentInformation[]>(this.buildAgentsWebsocketTopic).subscribe((updatedBuildAgents) => {
            this.buildAgents.set(updatedBuildAgents);
        });
    }

    /**
     * Loads the initial list of build agents from the REST API.
     * This provides immediate data while WebSocket connection is established.
     */
    private loadBuildAgents(): void {
        this.initialLoadSubscription = this.buildAgentsService.getBuildAgentSummary().subscribe((buildAgentsList) => {
            this.buildAgents.set(buildAgentsList);
        });
    }

    /**
     * Cancels a specific build job by its ID.
     * @param buildJobId The unique identifier of the build job to cancel
     */
    cancelBuildJob(buildJobId: string): void {
        this.buildQueueService.cancelBuildJob(buildJobId).subscribe();
    }

    /**
     * Cancels all running build jobs for a specific build agent.
     * @param buildAgent The build agent whose jobs should be cancelled
     */
    cancelAllBuildJobs(buildAgent?: BuildAgent): void {
        if (!buildAgent?.name) {
            return;
        }

        const matchingAgent = this.buildAgents().find((agent) => agent.buildAgent?.name === buildAgent.name);
        if (matchingAgent?.buildAgent?.name) {
            this.buildQueueService.cancelAllRunningBuildJobsForAgent(matchingAgent.buildAgent.name).subscribe();
        }
    }

    /**
     * Opens a confirmation modal for pausing all build agents.
     * If confirmed, triggers the pause operation.
     */
    displayPauseBuildAgentModal(): void {
        const modalRef: NgbModalRef = this.modalService.open(BuildAgentPauseAllModalComponent as Component);
        modalRef.result.then((confirmed) => {
            if (confirmed) {
                this.pauseAllBuildAgents();
            }
        });
    }

    /**
     * Opens a confirmation modal for clearing distributed data.
     * If confirmed, triggers the clear operation.
     */
    displayClearDistributedDataModal(): void {
        const modalRef: NgbModalRef = this.modalService.open(BuildAgentClearDistributedDataComponent as Component, { size: 'lg' });
        modalRef.result.then((confirmed) => {
            if (confirmed) {
                this.clearDistributedData();
            }
        });
    }

    /**
     * Pauses all build agents in the system.
     * Shows success/error alerts based on the operation result.
     */
    pauseAllBuildAgents(): void {
        this.buildAgentsService.pauseAllBuildAgents().subscribe({
            next: () => {
                this.loadBuildAgents();
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

    /**
     * Resumes all paused build agents in the system.
     * Shows success/error alerts based on the operation result.
     */
    resumeAllBuildAgents(): void {
        this.buildAgentsService.resumeAllBuildAgents().subscribe({
            next: () => {
                this.loadBuildAgents();
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

    /**
     * Clears all distributed data (caches, queues) across the build agent cluster.
     * This is a maintenance operation that should be used with caution.
     * Shows success/error alerts based on the operation result.
     */
    clearDistributedData(): void {
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
