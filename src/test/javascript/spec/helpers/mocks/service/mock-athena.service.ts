import { Feedback } from 'app/entities/feedback.model';
import { Observable, of } from 'rxjs';
import { TextBlockRef } from 'app/entities/text-block-ref.model';

export class MockAthenaService {
    getFeedbackSuggestionsForProgramming(exerciseId: number, submissionId: number): Observable<Feedback[]> {
        return of([]);
    }
    getFeedbackSuggestionsForText(exerciseId: number, submissionId: number): Observable<TextBlockRef[]> {
        return of([]);
    }
}
