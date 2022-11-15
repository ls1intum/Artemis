import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { GroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { map, Observable } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class GroupChatService {
    public resourceUrl = SERVER_API_URL + '/api/courses/';

    constructor(private http: HttpClient, private conversationService: ConversationService) {}

    create(courseId: number, loginsOfChatPartners: string[]): Observable<HttpResponse<GroupChatDto>> {
        return this.http
            .post<OneToOneChatDTO>(`${this.resourceUrl}${courseId}/group-chats`, loginsOfChatPartners, { observe: 'response' })
            .pipe(map(this.conversationService.convertDateFromServer));
    }
}
