import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

export class MockScienceService {
    logEvent = (type: string, resourceId?: number) => of({}) as Observable<HttpResponse<void>>;
}
