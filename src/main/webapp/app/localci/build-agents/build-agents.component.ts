import { Component, OnDestroy, OnInit } from '@angular/core';
import { BuildAgent } from 'app/entities/build-agent.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { BuildAgentsService } from 'app/localci/build-agents/build-agents.service';
import { BuildJob } from 'app/entities/build-job.model';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-build-agents',
    templateUrl: './build-agents.component.html',
    styleUrl: './build-agents.component.scss',
})
export class BuildAgentsComponent implements OnInit, OnDestroy {
    buildAgents: BuildAgent[];
    channel: string = '/topic/admin/build-agents';
    websocketSubscription: Subscription;
    restSubscription: Subscription;

    constructor(
        private websocketService: JhiWebsocketService,
        private buildAgentsService: BuildAgentsService,
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
            this.buildAgents = buildAgents;
            this.setBuildAgentBuildJobIds(buildAgents);
        });
    }

    /**
     * This method is used to load the build agents.
     */
    load() {
        this.restSubscription = this.buildAgentsService.getBuildAgents().subscribe((buildAgents) => {
            this.buildAgents = buildAgents;
            this.setBuildAgentBuildJobIds(buildAgents);
        });
    }

    /**
     * This method is used to set the build job ids string for each build agent.
     * @param buildAgents the build agents for which the build job ids string should be set
     */
    setBuildAgentBuildJobIds(buildAgents: BuildAgent[]) {
        for (const buildAgent of buildAgents) {
            if (buildAgent.runningBuildJobs) {
                buildAgent.runningBuildJobsIds = buildAgent.runningBuildJobs.map((buildJob: BuildJob) => buildJob.id).join(', ');
            } else {
                buildAgent.runningBuildJobsIds = '';
            }
        }
    }
}
