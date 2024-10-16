import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { GroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { Observable, map } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';

@Injectable({ providedIn: 'root' })
export class GroupChatService {
    private http = inject(HttpClient);
    private conversationService = inject(ConversationService);
    private accountService = inject(AccountService);

    public resourceUrl = 'api/courses/';

    create(courseId: number, loginsOfChatPartners: string[]): Observable<HttpResponse<GroupChatDTO>> {
        return this.http
            .post<OneToOneChatDTO>(`${this.resourceUrl}${courseId}/group-chats`, loginsOfChatPartners, { observe: 'response' })
            .pipe(map(this.conversationService.convertDateFromServer));
    }

    update(courseId: number, groupChatId: number, groupChatDTO: GroupChatDTO): Observable<HttpResponse<GroupChatDTO>> {
        return this.http
            .put<GroupChatDTO>(`${this.resourceUrl}${courseId}/group-chats/${groupChatId}`, groupChatDTO, { observe: 'response' })
            .pipe(map(this.conversationService.convertDateFromServer));
    }

    removeUsersFromGroupChat(courseId: number, groupChatId: number, logins?: string[]): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume self deregistration
        const userLogins = logins ? logins : [this.accountService.userIdentity?.login];
        return this.http.post<void>(`${this.resourceUrl}${courseId}/group-chats/${groupChatId}/deregister`, userLogins, { observe: 'response' });
    }

    addUsersToGroupChat(courseId: number, groupChatId: number, logins: string[]): Observable<HttpResponse<void>> {
        // you can not add yourself to a group chat, therefore logins must contain at least one user
        return this.http.post<void>(`${this.resourceUrl}${courseId}/group-chats/${groupChatId}/register`, logins, { observe: 'response' });
    }
}
