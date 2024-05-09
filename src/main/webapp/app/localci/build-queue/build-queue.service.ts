import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { BuildJob, FinishedBuildJob } from 'app/entities/build-job.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { HttpResponse } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class BuildQueueService {
    public resourceUrl = 'api';
    public adminResourceUrl = 'api/admin';

    constructor(private http: HttpClient) {}
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
     * Get all running build jobs
     */
    getRunningBuildJobs(): Observable<BuildJob[]> {
        return this.http.get<BuildJob[]>(`${this.adminResourceUrl}/running-jobs`);
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
     */
    getFinishedBuildJobs(req?: any): Observable<HttpResponse<FinishedBuildJob[]>> {
        const options = createRequestOption(req);
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
     */
    getFinishedBuildJobsByCourseId(courseId: number, req?: any): Observable<HttpResponse<FinishedBuildJob[]>> {
        const options = createRequestOption(req);
        return this.http.get<FinishedBuildJob[]>(`${this.resourceUrl}/courses/${courseId}/finished-jobs`, { params: options, observe: 'response' }).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to get all finished build jobs in course ${courseId}\n${err.message}`));
            }),
        );
    }
}
