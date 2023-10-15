import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { UserPublicInfoDTO } from 'app/core/user/user.model';

export class MockConversationService {
    getResponsibleUsersForCodeOfConduct(courseId: number): Observable<HttpResponse<UserPublicInfoDTO[]>> {
        return of(new HttpResponse({ body: [] }));
    }
}
