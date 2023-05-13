import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisConversation } from 'app/entities/iris/iris.model';

type EntityResponseType = HttpResponse<IrisConversation>;

@Injectable({ providedIn: 'root' })
export class SessionService {
    public resourceUrl = SERVER_API_URL + 'api/iris/';

    constructor(protected http: HttpClient) {
        this.http = http;
    }

    /**
     * gets the current session by the exerciseId
     * @param {number} exerciseId of the exercise
     * @return {Observable<EntityResponseType>} an Observable of the HTTP response
     */
    getCurrentSession(exerciseId: number): Observable<EntityResponseType> {
        return this.http.get<IrisConversation>(`${this.resourceUrl}/programming-exercises/${exerciseId}/sessions`, { observe: 'response' });
    }

    /**
     * creates a session for a programming exercise
     * @param {number} exerciseId of the session in which the message should be created
     * @param {IrisConversation} session the iris session to be created
     * @return {Observable<EntityResponseType>} an Observable of the HTTP responses
     */
    createSessionForProgrammingExercise(exerciseId: number, session: IrisConversation): Observable<EntityResponseType> {
        return this.http.post<IrisConversation>(`${this.resourceUrl}/programming-exercises/${exerciseId}/sessions`, session, { observe: 'response' });
    }
}
