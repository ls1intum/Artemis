import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { IrisHttpSessionService, Response } from 'app/iris/http-session.service';

/**
 * The `IrisCourseHttpSessionService` provides methods for retrieving existing or creating new Iris sessions at the course level.
 * It interacts with the server-side API to perform session-related operations.
 */
@Injectable({ providedIn: 'root' })
export abstract class IrisCourseHttpSessionService extends IrisHttpSessionService {
    protected constructor(
        http: HttpClient,
        protected sessionType: string,
    ) {
        super(http);
    }

    /**
     * gets the current session by the courseId
     * @param {number} courseId of the exercise
     * @return {Observable<EntityResponseType>} an Observable of the HTTP response
     */
    getCurrentSession(courseId: number): Response<IrisSession> {
        return this.http.get<IrisSession>(`${this.apiPrefix}/courses/${courseId}/${this.sessionType}/current`, { observe: 'response' });
    }

    /**
     * creates a session for a course
     * @param {number} courseId of the session in which the message should be created
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */
    createSession(courseId: number): Observable<IrisSession> {
        return this.http.post<never>(`${this.apiPrefix}/courses/${courseId}/${this.sessionType}`, {});
    }
}
