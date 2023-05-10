import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { IrisMessage } from 'app/entities/iris/message.model';

type EntityResponseType = HttpResponse<IrisMessage>;
type EntityArrayResponseType = HttpResponse<IrisMessage[]>;

@Injectable({ providedIn: 'root' })
export class MessageService {
    public resourceUrl = SERVER_API_URL + 'api/iris/sessions';

    constructor(protected http: HttpClient) {
        this.http = http;
    }

    /**
     * creates a message for a session
     * @param {number} sessionId
     * @param {IrisMessage} message
     * @return {Observable<EntityResponseType>}
     */
    create(sessionId: number, message: IrisMessage): Observable<EntityResponseType> {
        return this.http.post<IrisMessage>(`${this.resourceUrl}/${sessionId}/messages`, message, { observe: 'response' });
    }

    /**
     * gets all messages for a session by its id
     * @param {number} sessionId
     * @return {Observable<EntityArrayResponseType>}
     */
    getMessages(sessionId: number): Observable<EntityArrayResponseType> {
        return this.http.get<IrisMessage[]>(`${this.resourceUrl}${sessionId}/messages`, { observe: 'response' });
    }
}
