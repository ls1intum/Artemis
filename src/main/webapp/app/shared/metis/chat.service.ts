import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { ChatSession } from 'app/entities/metis/chat.session/chat.session.model';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';

type EntityResponseType = HttpResponse<ChatSession>;
type EntityArrayResponseType = HttpResponse<ChatSession[]>;

@Injectable({ providedIn: 'root' })
export class ChatService {
    public resourceUrl = SERVER_API_URL + '/api/courses/';

    constructor(protected http: HttpClient) {}

    /**
     * creates a chatSession
     * @param {number} courseId
     * @param {ChatSession} chatSession
     * @return {Observable<EntityResponseType>}
     */
    create(courseId: number, chatSession: ChatSession): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(chatSession);
        return this.http.post<ChatSession>(`${this.resourceUrl}${courseId}/chatSessions`, copy, { observe: 'response' }).pipe(map(this.convertDateFromServer));
    }

    /**
     * gets all chatSessions for user within course by courseId
     * @param {number} courseId
     * @return {Observable<EntityArrayResponseType>}
     */
    getChatSessionsOfUser(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<ChatSession[]>(`${this.resourceUrl}${courseId}/chatSessions`, {
                observe: 'response',
            })
            .pipe(map(this.convertDateArrayFromServer));
    }

    /**
     * takes a chatSession and converts the date from the client
     * @param   {ChatSession} chatSession
     * @return  {ChatSession}
     */
    private convertDateFromClient(chatSession: ChatSession) {
        return {
            ...chatSession,
            creationDate: chatSession.creationDate && dayjs(chatSession.creationDate).isValid() ? dayjs(chatSession.creationDate).toJSON() : undefined,
            lastMessageDate: chatSession.lastMessageDate && dayjs(chatSession.lastMessageDate).isValid() ? dayjs(chatSession.lastMessageDate).toJSON() : undefined,
        };
    }

    /**
     * takes a chatSession and converts the date from the server
     * @param   {HttpResponse<ChatSession>} res
     * @return  {HttpResponse<ChatSession>}
     */
    private convertDateFromServer(res: HttpResponse<ChatSession>): HttpResponse<ChatSession> {
        if (res.body) {
            res.body.creationDate = res.body.creationDate ? dayjs(res.body.creationDate) : undefined;
            res.body.lastMessageDate = res.body.lastMessageDate ? dayjs(res.body.lastMessageDate) : undefined;
        }
        return res;
    }

    /**
     * takes an array of chatSessions and converts the date from the server
     * @param   {HttpResponse<ChatSession[]>} res
     * @return  {HttpResponse<ChatSession[]>}
     */
    protected convertDateArrayFromServer(res: HttpResponse<ChatSession[]>): HttpResponse<ChatSession[]> {
        if (res.body) {
            res.body.forEach((chatSession: ChatSession) => {
                chatSession.creationDate = chatSession.creationDate ? dayjs(chatSession.creationDate) : undefined;
                chatSession.lastMessageDate = chatSession.lastMessageDate ? dayjs(chatSession.lastMessageDate) : undefined;
            });
        }
        return res;
    }
}
