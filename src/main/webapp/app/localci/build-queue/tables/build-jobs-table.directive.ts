import { Directive, input, output } from '@angular/core';
import { BuildJob } from 'app/localci/shared/entities/build-job.model';

/** Common inputs and interaction semantics for queued and running build jobs. */
@Directive()
export abstract class BuildJobsTableDirective {
    /** List of queued build jobs to display */
    buildJobs = input<BuildJob[]>([]);

    /** Whether to show the course ID column */
    showCourseId = input<boolean>(false);

    /** Course ID for building navigation routes (undefined for admin view) */
    courseId = input<number | undefined>(undefined);

    /** Emits the job ID when a job should be cancelled */
    cancelJob = output<string>();

    /** Emits the job ID when a job row is clicked for navigation */
    jobClick = output<string>();

    /**
     * Handles the cancel button click for a single job.
     * Stops event propagation to prevent row click navigation.
     * @param jobId The ID of the job to cancel
     * @param event The click event
     */
    onCancelJob(jobId: string, event: Event): void {
        event.stopPropagation();
        this.cancelJob.emit(jobId);
    }

    /**
     * Handles row click for navigation to job details.
     * @param jobId The ID of the clicked job
     */
    onJobClick(jobId: string | undefined): void {
        if (jobId) {
            this.jobClick.emit(jobId);
        }
    }

    /**
     * Stops event propagation for links within the table row.
     * @param event The click event
     */
    onLinkClick(event: Event): void {
        event.stopPropagation();
    }

    /**
     * Builds the router link array for navigating to job details.
     * Uses course-specific route when courseId is provided, otherwise admin route.
     * @param jobId The ID of the build job
     */
    getJobDetailRoute(jobId: string | undefined): string[] {
        if (!jobId) {
            return [];
        }
        const courseId = this.courseId();
        if (courseId) {
            return ['/course-management', String(courseId), 'build-overview', jobId, 'job-details'];
        }
        return ['/admin', 'build-overview', jobId, 'job-details'];
    }
}
