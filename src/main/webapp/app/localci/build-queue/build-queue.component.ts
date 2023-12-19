import { Component, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { AccountService } from 'app/core/auth/account.service';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { BuildJob } from 'app/entities/build-job.model';
import { faRefresh } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-build-queue',
    templateUrl: './build-queue.component.html',
    styleUrl: './build-queue.component.scss',
})
export class BuildQueueComponent {
    @ViewChild(DataTableComponent) dataTable: DataTableComponent;

    rowClass: string | undefined = undefined;

    queuedBuildJobs: BuildJob[];
    runningBuildJobs: BuildJob[];

    //icons
    faRefresh = faRefresh;

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
                    console.log('queuedBuildJobs1: ', queuedBuildJobs);
                });
                this.buildQueueService.getRunningBuildJobs().subscribe((runningBuildJobs) => {
                    this.runningBuildJobs = runningBuildJobs;
                });
            }
        });
    }
    /**
     * Computes the row class that is being added to all rows of the datatable
     */
    dataTableRowClass = () => {
        return this.rowClass;
    };

    refresh() {
        this.ngOnInit();
    }
}
