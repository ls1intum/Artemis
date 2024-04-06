import { Component, OnDestroy, OnInit } from '@angular/core';
import { BuildAgent } from 'app/entities/build-agent.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { BuildAgentsService } from 'app/localci/build-agents/build-agents.service';
import { Subscription } from 'rxjs';
import { faCircleCheck, faExclamationCircle, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { TriggeredByPushTo } from 'app/entities/repository-info.model';

@Component({
    selector: 'jhi-build-agents',
    templateUrl: './build-agents.component.html',
    styleUrl: './build-agents.component.scss',
})
export class BuildAgentsComponent implements OnInit, OnDestroy {
    protected readonly TriggeredByPushTo = TriggeredByPushTo;
    buildAgents: BuildAgent[] = [];
    buildCapacity = 0;
    currentBuilds = 0;
    channel: string = '/topic/admin/build-agents';
    websocketSubscription: Subscription;
    restSubscription: Subscription;

    //icons
    faCircleCheck = faCircleCheck;
    faExclamationCircle = faExclamationCircle;
    faExclamationTriangle = faExclamationTriangle;
    faTimes = faTimes;

    constructor(
        private websocketService: JhiWebsocketService,
        private buildAgentsService: BuildAgentsService,
        private buildQueueService: BuildQueueService,
    ) {}

    ngOnInit() {
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

    private updateBuildAgents(buildAgents: BuildAgent[]) {
        this.buildAgents = buildAgents;
        this.setRecentBuildJobsDuration(buildAgents);
        this.buildCapacity = this.buildAgents.reduce((sum, agent) => sum + (agent.maxNumberOfConcurrentBuildJobs || 0), 0);
        this.currentBuilds = this.buildAgents.reduce((sum, agent) => sum + (agent.numberOfCurrentBuildJobs || 0), 0);
    }

    /**
     * This method is used to load the build agents.
     */
    load() {
        this.restSubscription = this.buildAgentsService.getBuildAgents().subscribe((buildAgents) => {
            this.updateBuildAgents(buildAgents);
        });
    }

    setRecentBuildJobsDuration(buildAgents: BuildAgent[]) {
        for (const buildAgent of buildAgents) {
            const recentBuildJobs = buildAgent.recentBuildJobs;
            if (recentBuildJobs) {
                for (const buildJob of recentBuildJobs) {
                    if (buildJob.jobTimingInfo?.buildStartDate && buildJob.jobTimingInfo?.buildCompletionDate) {
                        const start = dayjs(buildJob.jobTimingInfo.buildStartDate);
                        const end = dayjs(buildJob.jobTimingInfo.buildCompletionDate);
                        buildJob.jobTimingInfo.buildDuration = end.diff(start, 'milliseconds') / 1000;
                    }
                }
            }
        }
    }

    cancelBuildJob(buildJobId: string) {
        this.buildQueueService.cancelBuildJob(buildJobId).subscribe();
    }

    cancelAllBuildJobs(buildAgentName: string) {
        const buildAgent = this.buildAgents.find((agent) => agent.name === buildAgentName);
        if (buildAgent && buildAgent.name) {
            this.buildQueueService.cancelAllRunningBuildJobsForAgent(buildAgent.name).subscribe();
        }
    }

    viewBuildLogs(resultId: number): void {
        const url = `/api/build-log/${resultId}`;
        window.open(url, '_blank');
    }
}
