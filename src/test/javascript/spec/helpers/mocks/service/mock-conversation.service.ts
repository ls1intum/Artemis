import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

import { conversationsOfUser1 } from '../../sample/metis-sample-data';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

export class MockConversationService {
    create(courseId: number, conversation: Conversation): Observable<HttpResponse<Conversation>> {
        conversation.id = 3;
        return of({ body: conversation }) as Observable<HttpResponse<Conversation>>;
    }

    getConversationsOfUser(courseId: number) {
        return of({ body: conversationsOfUser1 }) as Observable<HttpResponse<Conversation[]>>;
    }
}
