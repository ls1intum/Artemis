import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-participant.model';
import { User } from 'app/core/user/user.model';
import { isChannel } from 'app/entities/metis/conversation/channel.model';
import { isGroupChat } from 'app/entities/metis/conversation/groupChat.model';

type EntityArrayResponseType = HttpResponse<Conversation[]>;

export type UserSortDirection = 'asc' | 'desc';
export type UserSortProperty = keyof User;
export type UserSortingParameter = {
    sortProperty: UserSortProperty;
    sortDirection: UserSortDirection;
};

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

        if (isChannel(conversation)) {
            return conversation.name ?? '';
        } else if (isGroupChat(conversation)) {
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

    searchMembersOfConversation(courseId: number, conversationId: number, loginOrName: string, page: number, size: number): Observable<HttpResponse<User[]>> {
        const sortingParameters: UserSortingParameter[] = [
            { sortProperty: 'firstName', sortDirection: 'asc' },
            { sortProperty: 'lastName', sortDirection: 'asc' },
        ];
        const params = this.creatSearchPagingParams(sortingParameters, page, size, loginOrName);
        return this.http.get<User[]>(`${this.resourceUrl}${courseId}/conversations/${conversationId}/members/search`, {
            observe: 'response',
            params,
        });
    }

    getConversationsOfUser(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Conversation[]>(`${this.resourceUrl}${courseId}/conversations`, {
                observe: 'response',
            })
            .pipe(map(this.convertDateArrayFromServer));
    }

    public convertDateFromClient(conversation: Conversation) {
        return {
            ...conversation,
            creationDate: conversation.creationDate && dayjs(conversation.creationDate).isValid() ? dayjs(conversation.creationDate).toJSON() : undefined,
            lastMessageDate: conversation.lastMessageDate && dayjs(conversation.lastMessageDate).isValid() ? dayjs(conversation.lastMessageDate).toJSON() : undefined,
        };
    }

    public convertDateFromServer(res: HttpResponse<Conversation>): HttpResponse<Conversation> {
        if (res.body) {
            res.body.creationDate = res.body.creationDate ? dayjs(res.body.creationDate) : undefined;
            res.body.lastMessageDate = res.body.lastMessageDate ? dayjs(res.body.lastMessageDate) : undefined;
            res.body.conversationParticipants?.forEach((conversationParticipant) => {
                conversationParticipant.lastRead = conversationParticipant.lastRead ? dayjs(conversationParticipant.lastRead) : undefined;
            });
        }
        return res;
    }

    public convertDateArrayFromServer(res: HttpResponse<Conversation[]>): HttpResponse<Conversation[]> {
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

    private creatSearchPagingParams(sortingParameters: UserSortingParameter[], page: number, size: number, loginOrName: string) {
        let params = new HttpParams();
        params = params.set('loginOrName', loginOrName);
        for (const sortParameter of sortingParameters) {
            params = params.append('sort', `${sortParameter.sortProperty},${sortParameter.sortDirection}`);
        }
        params = params.set('page', String(page));
        return params.set('size', String(size));
    }
}
