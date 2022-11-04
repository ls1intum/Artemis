import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { map } from 'rxjs/operators';
import { GroupChat } from 'app/entities/metis/conversation/groupChat.model';

type EntityResponseType = HttpResponse<GroupChat>;

@Injectable({ providedIn: 'root' })
export class GroupChatService {
    public resourceUrl = SERVER_API_URL + '/api/courses/';

    constructor(private http: HttpClient, private conversationService: ConversationService) {}

    create(courseId: number, groupChat: GroupChat): Observable<EntityResponseType> {
        const copy = this.conversationService.convertDateFromClient(groupChat);
        return this.http.post<GroupChat>(`${this.resourceUrl}${courseId}/groupchats`, copy, { observe: 'response' }).pipe(map(this.conversationService.convertDateFromServer));
    }
}
