import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Exam } from 'app/exam/shared/entities/exam.model';

export class MockExamManagementService {
    findAllCurrentAndUpcomingExams() {
        return MockExamManagementService.response([{ id: 1 } as Exam, { id: 2 } as Exam]);
    }

    // helper method
    private static response<T>(entity: T) {
        return of({ body: entity }) as Observable<HttpResponse<T>>;
    }
}
