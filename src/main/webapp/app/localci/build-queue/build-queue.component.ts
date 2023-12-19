import { Component, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { AccountService } from 'app/core/auth/account.service';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { BuildJob } from 'app/entities/build-job.model';

@Component({
    selector: 'jhi-build-queue',
    templateUrl: './build-queue.component.html',
    styleUrl: './build-queue.component.scss',
})
export class BuildQueueComponent {
    @ViewChild(DataTableComponent) dataTable: DataTableComponent;

    isLoading = false;
    hasExamStarted = false;
    hasExamEnded = false;
    isSearching = false;
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;
    rowClass: string | undefined = undefined;

    name?: string;
    participationId?: number;
    repositoryType?: string;
    commitHash?: string;
    submissionDate?: number;
    buildStartDate?: number;
    courseId?: number;
    priority?: number;

    isAdmin = false;

    queuedBuildJobs: BuildJob[];
    runningBuildJobs: BuildJob[];

    constructor(
        private route: ActivatedRoute,
        private accountService: AccountService,
        private buildQueueService: BuildQueueService,
    ) {}

    ngOnInit() {
        this.route.paramMap.subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                console.log('courseId: ' + courseId);
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
            console.log('queuedBuildJobs: ' + this.queuedBuildJobs);
            console.log('kakcer ' + this.queuedBuildJobs[0].name);
        });
    }
    /**
     * Computes the row class that is being added to all rows of the datatable
     */
    dataTableRowClass = () => {
        return this.rowClass;
    };
}
