import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

import { ChatSession } from 'app/entities/metis/chat.session/chat-session.model';
import { chatSessionsOfUser1 } from '../../sample/metis-sample-data';

export class MockChatSessionService {
    create(courseId: number, chatSession: ChatSession): Observable<HttpResponse<ChatSession>> {
        chatSession.id = 3;
        return of({ body: chatSession }) as Observable<HttpResponse<ChatSession>>;
    }

    getChatSessionsOfUser(courseId: number) {
        return of({ body: chatSessionsOfUser1 }) as Observable<HttpResponse<ChatSession[]>>;
    }
}
