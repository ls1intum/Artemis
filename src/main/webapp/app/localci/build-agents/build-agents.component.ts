import { Component, OnDestroy, OnInit } from '@angular/core';
import { BuildAgent } from 'app/entities/build-agent.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { BuildAgentsService } from 'app/localci/build-agents/build-agents.service';
import { BuildJob } from 'app/entities/build-job.model';

@Component({
    selector: 'jhi-build-agents',
    templateUrl: './build-agents.component.html',
    styleUrl: './build-agents.component.scss',
})
export class BuildAgentsComponent implements OnInit, OnDestroy {
    buildAgents: BuildAgent[];
    channel: string = '/topic/admin/build-agents';

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
    }

    /**
     * This method is used to initialize the websocket subscription for the build agents. It subscribes to the channel for the build agents.
     */
    initWebsocketSubscription() {
        this.websocketService.subscribe(this.channel);
        this.websocketService.receive(this.channel).subscribe((buildAgents) => {
            this.buildAgents = buildAgents;
        });
    }

    /**
     * This method is used to load the build agents.
     */
    load() {
        this.buildAgentsService.getBuildAgents().subscribe((buildAgents) => {
            this.buildAgents = buildAgents;
        });
    }

    /**
     * This method is used to get the build job IDs from the given build jobs.
     * @param buildJobs The build jobs to get the IDs from.
     */
    getBuildJobIds(buildJobs: BuildJob[]): string {
        if (!buildJobs || buildJobs.length === 0) {
            return '';
        }

        // Extract and concatenate build IDs
        return buildJobs.map((job) => job.id).join(', ');
    }
}
