import { Feedback } from 'app/entities/feedback.model';
import { Observable, of } from 'rxjs';

export class MockAthenaService {
    getFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<Feedback[]> {
        return of([]);
    }
}
