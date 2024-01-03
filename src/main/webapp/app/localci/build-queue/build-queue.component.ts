import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { BuildJob } from 'app/entities/build-job.model';
import { faRefresh, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-build-queue',
    templateUrl: './build-queue.component.html',
    styleUrl: './build-queue.component.scss',
})
export class BuildQueueComponent implements OnInit {
    queuedBuildJobs: BuildJob[];
    runningBuildJobs: BuildJob[];

    //icons
    faRefresh = faRefresh;
    faTimes = faTimes;

    constructor(
        private route: ActivatedRoute,
        private accountService: AccountService,
        private buildQueueService: BuildQueueService,
    ) {}

    ngOnInit() {
        this.load();
    }

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

    cancelBuildJob(commitHash: string) {
        this.route.paramMap.subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                this.buildQueueService.cancelBuildJobInCourse(courseId, commitHash).subscribe(() => this.load());
            } else {
                this.buildQueueService.cancelBuildJob(commitHash).subscribe(() => this.load());
            }
        });
    }
}
