import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';

import { BuildJob } from 'app/entities/build-job.model';

@Injectable({ providedIn: 'root' })
export class BuildQueueService {
    public resourceUrl = 'api/courses';
    public adminResourceUrl = 'api/admin';

    constructor(private http: HttpClient) {}
    /**
     * Get all build jobs of a course in the queue
     * @param courseId
     */
    getQueuedBuildJobsByCourseId(courseId: number): Observable<BuildJob[]> {
        return this.http.get<BuildJob[]>(`${this.resourceUrl}/${courseId}/queued-jobs`);
    }

    /**
     * Get all running build jobs of a course
     * @param courseId
     */
    getRunningBuildJobsByCourseId(courseId: number): Observable<BuildJob[]> {
        return this.http.get<BuildJob[]>(`${this.resourceUrl}/${courseId}/running-jobs`);
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
    cancelBuildJobInCourse(courseId: number, buildJobId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/cancel/${courseId}/${buildJobId}`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel build job ${buildJobId} in course ${courseId}\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel a specific build job associated with the build job id
     * @param buildJobId the id of the build job to cancel
     */
    cancelBuildJob(buildJobId: number): Observable<void> {
        return this.http.delete<void>(`${this.adminResourceUrl}/cancel/${buildJobId}`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel build job ${buildJobId}\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel all running build jobs
     */
    cancelAllRunningBuildJobs(): Observable<void> {
        return this.http.delete<void>(`${this.adminResourceUrl}/cancel-all-running`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel all running build jobs\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel all running build jobs associated with a course
     */
    cancelAllRunningBuildJobsInCourse(courseId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/cancel-all-running/${courseId}`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel all running build jobs in course ${courseId}\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel all queued build jobs
     */
    cancelAllQueuedBuildJobs(): Observable<void> {
        return this.http.delete<void>(`${this.adminResourceUrl}/cancel-all-queued`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel all queued build jobs\n${err.message}`));
            }),
        );
    }

    /**
     * Cancel all queued build jobs associated with a course
     */
    cancelAllQueuedBuildJobsInCourse(courseId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/cancel-all-queued/${courseId}`).pipe(
            catchError((err) => {
                return throwError(() => new Error(`Failed to cancel all queued build jobs in course ${courseId}\n${err.message}`));
            }),
        );
    }
}
