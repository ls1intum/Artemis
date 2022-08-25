import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

type EntityResponseType = HttpResponse<Conversation>;
type EntityArrayResponseType = HttpResponse<Conversation[]>;

@Injectable({ providedIn: 'root' })
export class ConversationService {
    public resourceUrl = SERVER_API_URL + '/api/courses/';

    constructor(protected http: HttpClient) {}

    /**
     * creates a conversation
     * @param {number} courseId                 ID of course the conversation will belong to
     * @param {Conversation} conversation       conversation to create
     * @return {Observable<EntityResponseType>} the created conversation
     */
    create(courseId: number, conversation: Conversation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(conversation);
        return this.http.post<Conversation>(`${this.resourceUrl}${courseId}/conversations`, copy, { observe: 'response' }).pipe(map(this.convertDateFromServer));
    }

    /**
     * gets all conversations for user within course by courseId
     * @param {number} courseId                      ID of course the conversations belong to
     * @return {Observable<EntityArrayResponseType>} conversations of user
     */
    getConversationsOfUser(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Conversation[]>(`${this.resourceUrl}${courseId}/conversations`, {
                observe: 'response',
            })
            .pipe(map(this.convertDateArrayFromServer));
    }

    /**
     * gets all conversations for user within course by courseId
     * @param {conversationId}
     */
    auditConversationReadTimeOfUser = (conversationId: number): void => {
        this.http.post<void>(`${this.resourceUrl}conversation`, conversationId).subscribe();
    };

    /**
     * takes a conversation and converts the date from the client
     * @param   {Conversation} conversation
     * @return  {Conversation}
     */
    private convertDateFromClient(conversation: Conversation) {
        return {
            ...conversation,
            creationDate: conversation.creationDate && dayjs(conversation.creationDate).isValid() ? dayjs(conversation.creationDate).toJSON() : undefined,
            lastMessageDate: conversation.lastMessageDate && dayjs(conversation.lastMessageDate).isValid() ? dayjs(conversation.lastMessageDate).toJSON() : undefined,
        };
    }

    /**
     * takes a conversation and converts the date from the server
     * @param   {HttpResponse<Conversation>} res
     * @return  {HttpResponse<Conversation>}
     */
    private convertDateFromServer(res: HttpResponse<Conversation>): HttpResponse<Conversation> {
        if (res.body) {
            res.body.creationDate = res.body.creationDate ? dayjs(res.body.creationDate) : undefined;
            res.body.lastMessageDate = res.body.lastMessageDate ? dayjs(res.body.lastMessageDate) : undefined;
        }
        return res;
    }

    /**
     * takes an array of conversations and converts the date from the server
     * @param   {HttpResponse<Conversation[]>} res
     * @return  {HttpResponse<Conversation[]>}
     */
    protected convertDateArrayFromServer(res: HttpResponse<Conversation[]>): HttpResponse<Conversation[]> {
        if (res.body) {
            res.body.forEach((conversation: Conversation) => {
                conversation.creationDate = conversation.creationDate ? dayjs(conversation.creationDate) : undefined;
                conversation.lastMessageDate = conversation.lastMessageDate ? dayjs(conversation.lastMessageDate) : undefined;
            });
        }
        return res;
    }
}
