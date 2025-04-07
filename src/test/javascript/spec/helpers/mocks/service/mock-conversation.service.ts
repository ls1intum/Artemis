import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';

export class MockConversationService {
    getResponsibleUsersForCodeOfConduct(courseId: number): Observable<HttpResponse<UserPublicInfoDTO[]>> {
        return of(new HttpResponse({ body: [] }));
    }

    getConversationsOfUser(courseId: number): Observable<HttpResponse<ConversationDTO[]>> {
        const mockConversations: ConversationDTO[] = [];
        return of(new HttpResponse({ body: mockConversations }));
    }
}
