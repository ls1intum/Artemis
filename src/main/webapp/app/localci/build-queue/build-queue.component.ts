import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BuildJob } from 'app/entities/build-job.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';

@Component({
    selector: 'jhi-build-queue',
    templateUrl: './build-queue.component.html',
    styleUrl: './build-queue.component.scss',
})
export class BuildQueueComponent implements OnInit, OnDestroy {
    queuedBuildJobs: BuildJob[];
    runningBuildJobs: BuildJob[];
    courseChannels: string[] = [];

    constructor(
        private route: ActivatedRoute,
        private websocketService: JhiWebsocketService,
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
        this.websocketService.unsubscribe(`/topic/admin/queued-jobs`);
        this.websocketService.unsubscribe(`/topic/admin/running-jobs`);
        this.courseChannels.forEach((channel) => {
            this.websocketService.unsubscribe(channel);
        });
    }

    /**
     * This method is used to initialize the websocket subscription for the build jobs. It subscribes to the channels for the queued and running build jobs.
     */
    initWebsocketSubscription() {
        this.route.paramMap.subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                this.websocketService.subscribe(`/topic/courses/${courseId}/queued-jobs`);
                this.websocketService.subscribe(`/topic/courses/${courseId}/running-jobs`);
                this.websocketService.receive(`/topic/courses/${courseId}/queued-jobs`).subscribe((queuedBuildJobs) => {
                    this.queuedBuildJobs = queuedBuildJobs;
                });
                this.websocketService.receive(`/topic/courses/${courseId}/running-jobs`).subscribe((runningBuildJobs) => {
                    this.runningBuildJobs = runningBuildJobs;
                });
                this.courseChannels.push(`/topic/courses/${courseId}/queued-jobs`);
                this.courseChannels.push(`/topic/courses/${courseId}/running-jobs`);
            } else {
                this.websocketService.subscribe(`/topic/admin/queued-jobs`);
                this.websocketService.subscribe(`/topic/admin/running-jobs`);
                this.websocketService.receive(`/topic/admin/queued-jobs`).subscribe((queuedBuildJobs) => {
                    this.queuedBuildJobs = queuedBuildJobs;
                });
                this.websocketService.receive(`/topic/admin/running-jobs`).subscribe((runningBuildJobs) => {
                    this.runningBuildJobs = runningBuildJobs;
                });
            }
        });
    }

    /**
     * This method is used to load the build jobs from the backend when the component is initialized.
     * This ensures that the table is filled with data when the page is loaded or refreshed otherwise the user needs to
     * wait until the websocket subscription receives the data.
     */
    load() {
        this.route.paramMap.subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                this.buildQueueService.getQueuedBuildJobsByCourseId(courseId).subscribe((queuedBuildJobs) => {
                    this.queuedBuildJobs = queuedBuildJobs;
                });
                this.buildQueueService.getRunningBuildJobsByCourseId(courseId).subscribe((runningBuildJobs) => {
                    this.runningBuildJobs = runningBuildJobs;
                });
            } else {
                this.buildQueueService.getQueuedBuildJobs().subscribe((queuedBuildJobs) => {
                    this.queuedBuildJobs = queuedBuildJobs;
                });
                this.buildQueueService.getRunningBuildJobs().subscribe((runningBuildJobs) => {
                    this.runningBuildJobs = runningBuildJobs;
                });
            }
        });
    }
}
