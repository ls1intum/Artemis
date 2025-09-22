import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { BuildJob, BuildJobStatistics, FinishedBuildJob, SpanType } from 'app/buildagent/shared/entities/build-job.model';
import { createNestedRequestOption } from 'app/shared/util/request.util';
import { HttpResponse } from '@angular/common/http';
import { FinishedBuildJobFilter } from 'app/buildagent/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';

@Injectable({ providedIn: 'root' })
export class BuildOverviewService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/programming';
    public adminResourceUrl = 'api/core/admin';
    nestedDtoKey = 'pageable';

    /**
     * Get all build jobs of a course in the queue
     * @param courseId
     */
    getQueuedBuildJobsByCourseId(courseId: number): Observable<BuildJob[]> {
        return this.http.get<BuildJob[]>(`${this.resourceUrl}/courses/${courseId}/queued-jobs`);
    }

    /**
     * Get all running build jobs of a course
     * @param courseId
     */
    getRunningBuildJobsByCourseId(courseId: number): Observable<BuildJob[]> {
        return this.http.get<BuildJob[]>(`${this.resourceUrl}/courses/${courseId}/running-jobs`);
    }

    /**
     * Get all build jobs in the queue
     */
    getQueuedBuildJobs(): Observable<BuildJob[]> {
        return this.http.get<BuildJob[]>(`${this.adminResourceUrl}/queued-jobs`);
    }

    /**
     * Get all running build jobs, optionally filtered by agent name
     * @param agentName the name of the agent (optional)
     */
    getRunningBuildJobs(agentName?: string): Observable<BuildJob[]> {
        const params: { agentName?: string } = agentName ? { agentName } : {};
        return this.http.get<BuildJob[]>(`${this.adminResourceUrl}/running-jobs`, { params });
    }

    /**
     * Cancel a specific build job associated with the build job id
     * @param courseId the id of the course
     * @param buildJobId the id of the build job to cancel
     */
    cancelBuildJobInCourse(courseId: number, buildJobId: string): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/courses/${courseId}/cancel-job/${buildJobId}`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel build job ${buildJobId} in course ${courseId}\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel a specific build job associated with the build job id
     * @param buildJobId the id of the build job to cancel
     */
    cancelBuildJob(buildJobId: string): Observable<void> {
        return this.http.delete<void>(`${this.adminResourceUrl}/cancel-job/${buildJobId}`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel build job ${buildJobId}\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel all queued build jobs
     */
    cancelAllQueuedBuildJobs(): Observable<void> {
        return this.http.delete<void>(`${this.adminResourceUrl}/cancel-all-queued-jobs`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel all queued build jobs\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel all queued build jobs associated with a course
     */
    cancelAllQueuedBuildJobsInCourse(courseId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/courses/${courseId}/cancel-all-queued-jobs`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel all queued build jobs in course ${courseId}\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel all running build jobs
     */
    cancelAllRunningBuildJobs(): Observable<void> {
        return this.http.delete<void>(`${this.adminResourceUrl}/cancel-all-running-jobs`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel all running build jobs\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel all running build jobs for a specific agent
     * @param agentName the name of the agent
     */
    cancelAllRunningBuildJobsForAgent(agentName: string): Observable<void> {
        return this.http.delete<void>(`${this.adminResourceUrl}/cancel-all-running-jobs-for-agent`, { params: { agentName } }).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel all running build jobs for agent ${agentName}\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel all running build jobs associated with a course
     */
    cancelAllRunningBuildJobsInCourse(courseId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/courses/${courseId}/cancel-all-running-jobs`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel all running build jobs in course ${courseId}\n${err.message}`));
            }),
        );
    }

    /**
     * Get all finished build jobs
     * @param req The query request
     * @param filter The filter to apply
     */
    getFinishedBuildJobs(req?: any, filter?: FinishedBuildJobFilter): Observable<HttpResponse<FinishedBuildJob[]>> {
        let options = createNestedRequestOption(req, this.nestedDtoKey);
        if (filter) {
            options = filter.addHttpParams(options);
        }
        return this.http.get<FinishedBuildJob[]>(`${this.adminResourceUrl}/finished-jobs`, { params: options, observe: 'response' }).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to get all finished build jobs\n${err.message}`));
            }),
        );
    }

    /**
     * Get all finished build jobs associated with a course
     * @param courseId the id of the course
     * @param req The query request
     * @param filter The filter to apply
     */
    getFinishedBuildJobsByCourseId(courseId: number, req?: any, filter?: FinishedBuildJobFilter): Observable<HttpResponse<FinishedBuildJob[]>> {
        let options = createNestedRequestOption(req, this.nestedDtoKey);
        if (filter) {
            options = filter.addHttpParams(options);
        }
        return this.http.get<FinishedBuildJob[]>(`${this.resourceUrl}/courses/${courseId}/finished-jobs`, { params: options, observe: 'response' }).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to get all finished build jobs in course ${courseId}\n${err.message}`));
            }),
        );
    }

    /**
     * Get BuildJobStatistics
     *
     * @param span the time span
     * @returns BuildJobStatistics
     */
    getBuildJobStatistics(span: SpanType): Observable<BuildJobStatistics> {
        return this.http.get<BuildJobStatistics>(`${this.adminResourceUrl}/build-job-statistics`, { params: { span } }).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to get build job statistics\n${err.message}`));
            }),
        );
    }

    /**
     * Get BuildJobStatistics for a specific course
     * @param courseId the id of the course
     * @param span the time span
     * @returns BuildJobStatistics
     */
    getBuildJobStatisticsForCourse(courseId: number, span: SpanType): Observable<BuildJobStatistics> {
        return this.http.get<BuildJobStatistics>(`${this.resourceUrl}/courses/${courseId}/build-job-statistics`, { params: { span } }).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to get build job statistics for course ${courseId}\n${err.message}`));
            }),
        );
    }

    /**
     * Get all build jobs of a course in the queue
     * @param buildJobId
     */
    getBuildJobLogs(buildJobId: string): Observable<string> {
        return this.http.get(`${this.resourceUrl}/build-log/${buildJobId}`, { responseType: 'text' }).pipe(
            catchError(() => {
                return throwError(() => new Error('artemisApp.buildQueue.logs.errorFetchingLogs'));
            }),
        );
    }
}
