import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Conversation, ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { isOneToOneChatDto } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { getUserLabel } from 'app/overview/course-conversations/other/conversation.util';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';

type EntityArrayResponseType = HttpResponse<ConversationDto[]>;

export type UserSortDirection = 'asc' | 'desc';
export type UserSortProperty = keyof User;
export type UserSortingParameter = {
    sortProperty: UserSortProperty;
    sortDirection: UserSortDirection;
};

export enum ConversationMemberSearchFilter {
    ALL,
    INSTRUCTOR,
    EDITOR,
    TUTOR,
    STUDENT,
    CHANNEL_MODERATOR, // this is a special role that is only used for channels
}
@Injectable({ providedIn: 'root' })
export class ConversationService {
    public resourceUrl = '/api/courses/';

    constructor(
        protected http: HttpClient,
        protected translationService: TranslateService,
        protected accountService: AccountService,
    ) {}

    getConversationName(conversation: ConversationDto | undefined, showLogin = false): string {
        if (!conversation) {
            return '';
        }
        if (isChannelDto(conversation)) {
            let channelName = conversation.name ?? '';
            if (conversation.isArchived) {
                channelName += ' (' + this.translationService.instant('artemisApp.conversationsLayout.archived') + ')';
            }
            return channelName;
        } else if (isOneToOneChatDto(conversation)) {
            const otherUser = conversation.members?.find((user) => user.isRequestingUser === false);
            return otherUser ? getUserLabel(otherUser, showLogin) : '';
        } else if (isGroupChatDto(conversation)) {
            if (conversation.name && conversation.name.length > 0) {
                return conversation.name;
            }
            // fallback to the list of members if no name is set
            const members = conversation.members ?? [];
            const containsCurrentUser = members.some((member) => member.isRequestingUser);
            const membersWithoutUser = members.filter((member) => member.isRequestingUser === false);
            if (membersWithoutUser.length === 0) {
                return containsCurrentUser ? this.translationService.instant('artemisApp.conversationsLayout.onlyYou') : '';
            } else if (membersWithoutUser.length === 1) {
                return getUserLabel(membersWithoutUser[0], showLogin);
            } else if (membersWithoutUser.length === 2) {
                return `${getUserLabel(membersWithoutUser[0], false)}, ${getUserLabel(membersWithoutUser[1], false)}`;
            } else {
                return (
                    `${getUserLabel(membersWithoutUser[0], false)}, ${getUserLabel(membersWithoutUser[1], false)}, ` +
                    this.translationService.instant('artemisApp.conversationsLayout.others', { count: members.length - 2 })
                );
            }
        } else {
            return '';
        }
    }

    searchMembersOfConversation(
        courseId: number,
        conversationId: number,
        loginOrName: string,
        page: number,
        size: number,
        filter: ConversationMemberSearchFilter,
    ): Observable<HttpResponse<ConversationUserDTO[]>> {
        const sortingParameters: UserSortingParameter[] = [
            { sortProperty: 'firstName', sortDirection: 'asc' },
            { sortProperty: 'lastName', sortDirection: 'asc' },
        ];
        const params = this.creatSearchPagingParams(sortingParameters, page, size, loginOrName, filter);
        return this.http.get<ConversationUserDTO[]>(`${this.resourceUrl}${courseId}/conversations/${conversationId}/members/search`, {
            observe: 'response',
            params,
        });
    }

    getConversationsOfUser(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<ConversationDto[]>(`${this.resourceUrl}${courseId}/conversations`, {
                observe: 'response',
            })
            .pipe(map(this.convertDateArrayFromServer));
    }

    changeFavoriteStatus(courseId: number, conversationId: number, isFavorite: boolean): Observable<HttpResponse<void>> {
        let params = new HttpParams();
        params = params.append('isFavorite', isFavorite.toString());

        return this.http.post<void>(`${this.resourceUrl}${courseId}/conversations/${conversationId}/favorite`, null, { observe: 'response', params });
    }

    changeHiddenStatus(courseId: number, conversationId: number, isHidden: boolean): Observable<HttpResponse<void>> {
        let params = new HttpParams();
        params = params.append('isHidden', isHidden.toString());
        return this.http.post<void>(`${this.resourceUrl}${courseId}/conversations/${conversationId}/hidden`, null, { observe: 'response', params });
    }

    checkForUnreadMessages(courseId: number): Observable<HttpResponse<boolean>> {
        return this.http.get<boolean>(`${this.resourceUrl}${courseId}/unread-messages`, { observe: 'response' });
    }

    public convertDateFromClient = (conversation: Conversation) => ({
        ...conversation,
        creationDate: convertDateFromClient(conversation.creationDate),
        lastMessageDate: convertDateFromClient(conversation.lastMessageDate),
    });

    public convertDateFromServer = (res: HttpResponse<ConversationDto>): HttpResponse<ConversationDto> => {
        if (res.body) {
            this.convertServerDates(res.body);
        }
        return res;
    };

    public convertServerDates(conversation: ConversationDto) {
        conversation.creationDate = convertDateFromServer(conversation.creationDate);
        conversation.lastMessageDate = convertDateFromServer(conversation.lastMessageDate);
        conversation.lastReadDate = convertDateFromServer(conversation.lastReadDate);
        return conversation;
    }

    public convertDateArrayFromServer = (res: HttpResponse<ConversationDto[]>): HttpResponse<ConversationDto[]> => {
        if (res.body) {
            res.body.forEach((conversation) => {
                this.convertServerDates(conversation);
            });
        }
        return res;
    };

    private creatSearchPagingParams = (sortingParameters: UserSortingParameter[], page: number, size: number, loginOrName: string, filter: ConversationMemberSearchFilter) => {
        let params = new HttpParams();
        if (`${filter}` !== `${ConversationMemberSearchFilter.ALL}`) {
            params = params.set('filter', ConversationMemberSearchFilter[filter]);
        }
        params = params.set('loginOrName', loginOrName);
        for (const sortParameter of sortingParameters) {
            params = params.append('sort', `${sortParameter.sortProperty},${sortParameter.sortDirection}`);
        }
        params = params.set('page', String(page));
        return params.set('size', String(size));
    };
}
