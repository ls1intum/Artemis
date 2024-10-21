import { Component, OnDestroy, OnInit } from '@angular/core';
import { BuildAgentInformation } from 'app/entities/programming/build-agent.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { BuildAgentsService } from 'app/localci/build-agents/build-agents.service';
import { Subscription } from 'rxjs';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { Router } from '@angular/router';

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
    faTimes = faTimes;

    constructor(
        private websocketService: JhiWebsocketService,
        private buildAgentsService: BuildAgentsService,
        private buildQueueService: BuildQueueService,
        private router: Router,
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
        this.buildCapacity = this.buildAgents.reduce((sum, agent) => sum + (agent.maxNumberOfConcurrentBuildJobs || 0), 0);
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

    cancelAllBuildJobs(buildAgentName: string) {
        const buildAgent = this.buildAgents.find((agent) => agent.buildAgent?.name === buildAgentName);
        if (buildAgent && buildAgent.buildAgent?.name) {
            this.buildQueueService.cancelAllRunningBuildJobsForAgent(buildAgent.buildAgent?.name).subscribe();
        }
    }
}
