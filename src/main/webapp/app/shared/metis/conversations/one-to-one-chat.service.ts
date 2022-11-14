import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { map } from 'rxjs/operators';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';

@Injectable({ providedIn: 'root' })
export class OneToOneChatService {
    public resourceUrl = SERVER_API_URL + '/api/courses/';

    constructor(private http: HttpClient, private conversationService: ConversationService) {}

    create(courseId: number, oneToOneChat: OneToOneChatDTO): Observable<HttpResponse<OneToOneChatDTO>> {
        if (oneToOneChat?.members?.length !== 2) {
            throw new Error('One to one chat must have exactly two members');
        }
        const logins = oneToOneChat.members.map((member) => member.login);
        return this.http
            .post<OneToOneChatDTO>(`${this.resourceUrl}${courseId}/one-to-one-chats`, logins, { observe: 'response' })
            .pipe(map(this.conversationService.convertDateFromServer));
    }
}
