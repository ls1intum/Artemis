import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-participant.model';
import { ConversationType } from 'app/shared/metis/metis.util';

type EntityResponseType = HttpResponse<Conversation>;
type EntityArrayResponseType = HttpResponse<Conversation[]>;

@Injectable({ providedIn: 'root' })
export class ConversationService {
    public resourceUrl = SERVER_API_URL + '/api/courses/';

    constructor(protected http: HttpClient, protected translationService: TranslateService, protected accountService: AccountService) {}

    getNameOfConversation(conversation: Conversation): string {
        if (!conversation) {
            return '';
        }
        const getParticipantName = (participant: ConversationParticipant) => {
            return participant.user.lastName ? `${participant.user.firstName} ${participant.user.lastName}` : participant.user.firstName!;
        };

        if (conversation.type === ConversationType.CHANNEL) {
            return conversation.name ?? '';
        } else if (conversation.type === ConversationType.DIRECT) {
            const userId = this.accountService.userIdentity?.id || 0;
            const participants = conversation.conversationParticipants?.filter((participant) => participant.user?.id !== userId);
            if (!participants || participants.length === 0) {
                return '';
            } else if (participants?.length === 1) {
                return getParticipantName(participants[0]);
            } else if (participants?.length === 2) {
                return `${getParticipantName(participants[0])}, ${getParticipantName(participants[1])}`;
            } else {
                return (
                    `${getParticipantName(participants[0])}, ${getParticipantName(participants[1])}, ` +
                    this.translationService.instant('artemisApp.messages.conversation.others', { count: participants.length - 2 })
                );
            }
        } else {
            return '';
        }
    }

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
            res.body.conversationParticipants?.forEach((conversationParticipant) => {
                conversationParticipant.lastRead = conversationParticipant.lastRead ? dayjs(conversationParticipant.lastRead) : undefined;
            });
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
            res.body.forEach((conversation) => {
                conversation.creationDate = conversation.creationDate ? dayjs(conversation.creationDate) : undefined;
                conversation.lastMessageDate = conversation.lastMessageDate ? dayjs(conversation.lastMessageDate) : undefined;

                conversation.conversationParticipants?.forEach((conversationParticipant) => {
                    conversationParticipant.lastRead = conversationParticipant.lastRead ? dayjs(conversationParticipant.lastRead) : undefined;
                });
            });
        }
        return res;
    }
}
