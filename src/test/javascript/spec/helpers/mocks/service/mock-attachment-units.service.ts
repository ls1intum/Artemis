import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

export class MockAttachmentUnitsService {
    getSplitUnitsData = (lectureId: number, formData: FormData) => of({});

    createUnits = (lectureId: number, formData: FormData) => of({});
}
