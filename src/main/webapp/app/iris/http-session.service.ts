import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisSession } from 'app/entities/iris/iris-session.model';

export type Response<T> = Observable<HttpResponse<T>>;

/**
 * The `IrisHttpSessionService` provides methods for retrieving existing or creating new Iris sessions.
 * It interacts with the server-side API to perform session-related operations.
 */
@Injectable({ providedIn: 'root' })
export abstract class IrisHttpSessionService {
    protected readonly apiPrefix = 'api/iris';

    protected constructor(protected http: HttpClient) {}

    /**
     * gets the current session by the id of the owning entity (course or exercise)
     * @param {number} id of the entity (course or exercise)
     * @return {Observable<EntityResponseType>} an Observable of the HTTP response
     */
    abstract getCurrentSession(id: number): Response<IrisSession>;

    /**
     * creates a session for a course or exercise
     * @param {number} id of the entity (course or exercise) in which the message should be created
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */
    abstract createSession(id: number): Observable<IrisSession>;
}
