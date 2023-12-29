import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { BuildJob } from 'app/entities/build-job.model';

@Injectable({ providedIn: 'root' })
export class BuildQueueService {
    public resourceUrl = 'api/build-job-queue';
    public adminResourceUrl = 'api/admin/build-job-queue';

    constructor(private http: HttpClient) {}
    /**
     * Get all build jobs of a course in the queue
     * @param courseId
     */
    getQueuedBuildJobsByCourseId(courseId: number): Observable<BuildJob[]> {
        return this.http.get<BuildJob[]>(`${this.resourceUrl}/queued/${courseId}`);
    }

    /**
     * Get all running build jobs of a course
     * @param courseId
     */
    getRunningBuildJobsByCourseId(courseId: number): Observable<BuildJob[]> {
        return this.http.get<BuildJob[]>(`${this.resourceUrl}/running/${courseId}`);
    }

    /**
     * Get all build jobs in the queue
     */
    getQueuedBuildJobs(): Observable<BuildJob[]> {
        return this.http.get<BuildJob[]>(`${this.adminResourceUrl}/queued`);
    }

    /**
     * Get all running build jobs
     */
    getRunningBuildJobs(): Observable<BuildJob[]> {
        return this.http.get<BuildJob[]>(`${this.adminResourceUrl}/running`);
    }
}
