import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GocastBindingWithApproval, GocastCourse, GocastPlaybackToken, GocastStream } from './gocast.model';

/**
 * Angular service for the gocast (TUM Live) integration.
 *
 * Wraps the six server-side endpoints exposed by GocastIntegrationResource:
 *  - GET  api/videosource/courses/{courseId}/tumlive-courses    → list administered TUM Live courses (EP1)
 *  - GET  api/videosource/courses/{courseId}/tumlive-streams    → list bound course's streams (EP8)
 *  - POST api/videosource/courses/{courseId}/binding            → create PENDING binding
 *  - GET  api/videosource/courses/{courseId}/binding            → get/verify binding status (triggers EP7)
 *  - DELETE api/videosource/courses/{courseId}/binding          → revoke binding
 *  - POST api/videosource/courses/{courseId}/streams/{streamId}/playback-tokens → get signed playback token (EP2)
 */
@Injectable({
    providedIn: 'root',
})
export class GocastService {
    private readonly http = inject(HttpClient);

    private readonly baseUrl = 'api/videosource/courses';

    /**
     * Fetch the list of TUM Live courses administered by the current instructor for the given Artemis course.
     * Backed by EP1 (ListAdministeredCourses).
     */
    listAdministeredTumLiveCourses(courseId: number): Observable<GocastCourse[]> {
        return this.http.get<GocastCourse[]>(`${this.baseUrl}/${courseId}/tumlive-courses`);
    }

    /**
     * Fetch the streams for the course that is actively bound to the given Artemis course.
     * Only available when the binding status is ACTIVE.
     * Backed by EP8 (ListCourseStreams).
     */
    listTumLiveStreams(courseId: number): Observable<GocastStream[]> {
        return this.http.get<GocastStream[]>(`${this.baseUrl}/${courseId}/tumlive-streams`);
    }

    /**
     * Create a PENDING binding for the given Artemis course with the specified TUM Live course.
     * Returns GocastBindingWithApproval (mirrors GocastBindingWithApprovalDTO) containing the nested
     * binding object and the TUM Live approval URL the instructor must visit.
     */
    createBinding(courseId: number, gocastCourseId: number, gocastCourseSlug: string): Observable<GocastBindingWithApproval> {
        return this.http.post<GocastBindingWithApproval>(`${this.baseUrl}/${courseId}/binding`, { gocastCourseId, gocastCourseSlug });
    }

    /**
     * Get the current binding status for the Artemis course.
     * This triggers an EP7 server-to-server verification; the server flips PENDING→ACTIVE when gocast confirms.
     * Returns GocastBindingWithApproval; the approvalUrl is present only for PENDING bindings.
     */
    getBinding(courseId: number): Observable<GocastBindingWithApproval> {
        return this.http.get<GocastBindingWithApproval>(`${this.baseUrl}/${courseId}/binding`);
    }

    /**
     * Revoke the current binding for the given Artemis course.
     */
    deleteBinding(courseId: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${courseId}/binding`);
    }

    /**
     * Obtain a signed playback token for the given stream in the bound TUM Live course.
     * Backed by EP2 (GetPlaybackToken). Only available when the course has an ACTIVE binding.
     *
     * @param courseId   Artemis course id
     * @param streamId   TUM Live stream id
     */
    getPlaybackToken(courseId: number, streamId: number): Observable<GocastPlaybackToken> {
        return this.http.post<GocastPlaybackToken>(`${this.baseUrl}/${courseId}/streams/${streamId}/playback-tokens`, {});
    }
}
