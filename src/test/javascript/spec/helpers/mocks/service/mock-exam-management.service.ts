import { HttpResponse } from '@angular/common/http';
import { Exam } from 'app/entities/exam.model';
import { Observable, of } from 'rxjs';

export class MockExamManagementService {
    findAllCurrentAndUpcomingExams() {
        return MockExamManagementService.response([{ id: 1 } as Exam, { id: 2 } as Exam]);
    }

    // helper method
    private static response<T>(entity: T) {
        return of({ body: entity }) as Observable<HttpResponse<T>>;
    }
}
