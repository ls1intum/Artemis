import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

export class MockAttachmentUnitsService {
    getSplitUnitsData = (lectureId: number, formData: FormData) => of({});

    createUnits = (lectureId: number, formData: FormData) => of({});
}
