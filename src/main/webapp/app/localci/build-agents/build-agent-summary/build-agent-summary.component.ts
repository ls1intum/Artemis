import { Component, OnDestroy, OnInit } from '@angular/core';
import { BuildAgentInformation, BuildAgentStatus } from 'app/entities/programming/build-agent-information.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { BuildAgentsService } from 'app/localci/build-agents/build-agents.service';
import { Subscription } from 'rxjs';
import { faPause, faPlay, faTimes } from '@fortawesome/free-solid-svg-icons';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { Router } from '@angular/router';
import { BuildAgent } from 'app/entities/programming/build-agent.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService, AlertType } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-build-agents',
    templateUrl: './build-agent-summary.component.html',
    styleUrl: './build-agent-summary.component.scss',
})
export class BuildAgentSummaryComponent implements OnInit, OnDestroy {
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

    constructor(
        private websocketService: JhiWebsocketService,
        private buildAgentsService: BuildAgentsService,
        private buildQueueService: BuildQueueService,
        private router: Router,
        private modalService: NgbModal,
        private alertService: AlertService,
    ) {}

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
            .filter((agent) => agent.status !== BuildAgentStatus.PAUSED)
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

    displayPauseBuildAgentModal(modal: any) {
        this.modalService.open(modal);
    }

    pauseAllBuildAgents(modal?: any) {
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
        if (modal) {
            modal.close();
        }
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
}
