import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { GroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { Observable, map } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';

@Injectable({ providedIn: 'root' })
export class GroupChatService {
    public resourceUrl = SERVER_API_URL + '/api/courses/';

    constructor(private http: HttpClient, private conversationService: ConversationService, private accountService: AccountService) {}

    create(courseId: number, loginsOfChatPartners: string[]): Observable<HttpResponse<GroupChatDto>> {
        return this.http
            .post<OneToOneChatDTO>(`${this.resourceUrl}${courseId}/group-chats`, loginsOfChatPartners, { observe: 'response' })
            .pipe(map(this.conversationService.convertDateFromServer));
    }

    removeUsersFromGroupChat(courseId: number, groupChatId: number, logins?: string[]): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume self deregistration
        const userLogins = logins ? logins : [this.accountService.userIdentity?.login];
        return this.http.post<void>(`${this.resourceUrl}${courseId}/group-chats/${groupChatId}/deregister`, userLogins, { observe: 'response' });
    }

    addUsersToGroupChat(courseId: number, groupChatId: number, logins?: string[]): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}${courseId}/group-chats/${groupChatId}/register`, logins, { observe: 'response' });
    }
}
