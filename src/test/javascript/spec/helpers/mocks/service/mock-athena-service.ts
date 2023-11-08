import { Observable, of } from 'rxjs';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { Feedback } from 'app/entities/feedback.model';

export class MockAthenaService {
    getFeedbackSuggestionsForProgramming(exerciseId: number, submissionId: number): Observable<Feedback[]> {
        return of([] as Feedback[]);
    }

    getFeedbackSuggestionsForText(exerciseId: number, submissionId: number): Observable<TextBlockRef[]> {
        return of([] as TextBlockRef[]);
    }
}
