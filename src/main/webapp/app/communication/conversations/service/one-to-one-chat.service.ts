import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';

@Injectable({ providedIn: 'root' })
export class OneToOneChatService {
    public resourceUrl = '/api/communication/courses/';

    private http = inject(HttpClient);
    private conversationService = inject(ConversationService);

    create(courseId: number, loginOfChatPartner: string): Observable<HttpResponse<OneToOneChatDTO>> {
        return this.http
            .post<OneToOneChatDTO>(`${this.resourceUrl}${courseId}/one-to-one-chats`, [loginOfChatPartner], { observe: 'response' })
            .pipe(map(this.conversationService.convertDateFromServer));
    }

    createWithId(courseId: number, userIdOfChatPartner: number) {
        return this.http
            .post<OneToOneChatDTO>(`${this.resourceUrl}${courseId}/one-to-one-chats/${userIdOfChatPartner}`, null, { observe: 'response' })
            .pipe(map(this.conversationService.convertDateFromServer));
    }
}
