import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { Observable, map } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { toGroupChatDTO, toGroupChatRequest } from 'app/communication/conversations/service/conversation-dto-mapper';
import { GroupChatApi } from 'app/openapi/api/group-chat-api';
import type { GroupChat } from 'app/openapi/model/group-chat';

@Service()
export class GroupChatService {
    private readonly groupChatApi = inject(GroupChatApi);
    private readonly accountService = inject(AccountService);

    create(courseId: number, loginsOfChatPartners: string[]): Observable<HttpResponse<GroupChatDTO>> {
        return this.groupChatApi.startGroupChat(courseId, { memberLogins: loginsOfChatPartners }).pipe(map(this.toGroupChatResponse));
    }

    update(courseId: number, groupChatId: number, groupChatDTO: GroupChatDTO): Observable<HttpResponse<GroupChatDTO>> {
        return this.groupChatApi.updateGroupChat(courseId, groupChatId, toGroupChatRequest(groupChatDTO)).pipe(map(this.toGroupChatResponse));
    }

    removeUsersFromGroupChat(courseId: number, groupChatId: number, logins?: string[]): Observable<HttpResponse<void>> {
        // The authenticated UI has a login. Without one, there is no user to remove.
        const login = this.accountService.userIdentity()?.login;
        return this.groupChatApi.deregisterUsersFromGroupChat(courseId, groupChatId, logins ?? (login === undefined ? [] : [login]));
    }

    addUsersToGroupChat(courseId: number, groupChatId: number, logins: string[]): Observable<HttpResponse<void>> {
        // you can not add yourself to a group chat, therefore logins must contain at least one user
        return this.groupChatApi.registerUsersToGroupChat(courseId, groupChatId, logins);
    }

    private toGroupChatResponse(response: HttpResponse<GroupChat>): HttpResponse<GroupChatDTO> {
        return response.clone({ body: response.body && toGroupChatDTO(response.body) });
    }
}
