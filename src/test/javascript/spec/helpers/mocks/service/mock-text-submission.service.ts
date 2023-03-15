import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { TextSubmission } from 'app/entities/text-submission.model';

type EntityResponseType = HttpResponse<TextSubmission>;

export class MockTextSubmissionService {
    update = (textSubmission: TextSubmission, exerciseId: number): Observable<EntityResponseType> => of({ body: textSubmission } as HttpResponse<TextSubmission>);
}
