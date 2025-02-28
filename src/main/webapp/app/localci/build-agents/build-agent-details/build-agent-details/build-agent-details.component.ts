import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BuildAgentInformation } from 'app/entities/programming/build-agent-information.model';
import { BuildAgentsService } from 'app/localci/build-agents/build-agents.service';
import { Subscription } from 'rxjs';
import { faCircleCheck, faExclamationCircle, faExclamationTriangle, faPause, faPlay, faTimes } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { TriggeredByPushTo } from 'app/entities/programming/repository-info.model';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { WebsocketService } from 'app/core/websocket/websocket.service';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-build-agent-details',
    templateUrl: './build-agent-details.component.html',
    styleUrl: './build-agent-details.component.scss',
    imports: [
        NgxDatatableModule,
        DataTableComponent,
        ArtemisDurationFromSecondsPipe,
        ArtemisDatePipe,
        FontAwesomeModule,
        RouterModule,
        CommonModule,
        ResultComponent,
        TranslateDirective,
    ],
})
export class BuildAgentDetailsComponent implements OnInit, OnDestroy {
    private readonly websocketService = inject(WebsocketService);
    private readonly buildAgentsService = inject(BuildAgentsService);
    private readonly route = inject(ActivatedRoute);
    private readonly buildQueueService = inject(BuildQueueService);
    private readonly alertService = inject(AlertService);

    protected readonly TriggeredByPushTo = TriggeredByPushTo;
    buildAgent: BuildAgentInformation;
    agentName: string;
    websocketSubscription: Subscription;
    restSubscription: Subscription;
    paramSub: Subscription;
    channel: string;

    //icons
    faCircleCheck = faCircleCheck;
    faExclamationCircle = faExclamationCircle;
    faExclamationTriangle = faExclamationTriangle;
    faTimes = faTimes;
    readonly faPause = faPause;
    readonly faPlay = faPlay;

    ngOnInit() {
        this.paramSub = this.route.queryParams.subscribe((params) => {
            this.agentName = params['agentName'];
            this.channel = `/topic/admin/build-agent/${this.agentName}`;
            this.load();
            this.initWebsocketSubscription();
        });
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
        this.websocketSubscription = this.websocketService.receive(this.channel).subscribe((buildAgent) => {
            this.updateBuildAgent(buildAgent);
        });
    }

    /**
     * This method is used to load the build agents.
     */
    load() {
        this.restSubscription = this.buildAgentsService.getBuildAgentDetails(this.agentName).subscribe((buildAgent) => {
            this.updateBuildAgent(buildAgent);
        });
    }

    private updateBuildAgent(buildAgent: BuildAgentInformation) {
        this.buildAgent = buildAgent;
        this.setRecentBuildJobsDuration();
    }

    setRecentBuildJobsDuration() {
        const recentBuildJobs = this.buildAgent.recentBuildJobs;
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

    cancelBuildJob(buildJobId: string) {
        this.buildQueueService.cancelBuildJob(buildJobId).subscribe();
    }

    cancelAllBuildJobs() {
        if (this.buildAgent.buildAgent?.name) {
            this.buildQueueService.cancelAllRunningBuildJobsForAgent(this.buildAgent.buildAgent?.name).subscribe();
        }
    }

    viewBuildLogs(resultId: number): void {
        const url = `/api/programming/build-log/${resultId}`;
        window.open(url, '_blank');
    }

    pauseBuildAgent(): void {
        if (this.buildAgent.buildAgent?.name) {
            this.buildAgentsService.pauseBuildAgent(this.buildAgent.buildAgent.name).subscribe({
                next: () => {
                    this.alertService.addAlert({
                        type: AlertType.SUCCESS,
                        message: 'artemisApp.buildAgents.alerts.buildAgentPaused',
                    });
                },
                error: () => {
                    this.alertService.addAlert({
                        type: AlertType.DANGER,
                        message: 'artemisApp.buildAgents.alerts.buildAgentPauseFailed',
                    });
                },
            });
        } else {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'artemisApp.buildAgents.alerts.buildAgentWithoutName',
            });
        }
    }

    resumeBuildAgent(): void {
        if (this.buildAgent.buildAgent?.name) {
            this.buildAgentsService.resumeBuildAgent(this.buildAgent.buildAgent.name).subscribe({
                next: () => {
                    this.alertService.addAlert({
                        type: AlertType.SUCCESS,
                        message: 'artemisApp.buildAgents.alerts.buildAgentResumed',
                    });
                },
                error: () => {
                    this.alertService.addAlert({
                        type: AlertType.DANGER,
                        message: 'artemisApp.buildAgents.alerts.buildAgentResumeFailed',
                    });
                },
            });
        } else {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'artemisApp.buildAgents.alerts.buildAgentWithoutName',
            });
        }
    }
}
