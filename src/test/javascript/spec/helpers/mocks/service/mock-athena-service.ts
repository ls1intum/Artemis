import { Observable, of } from 'rxjs';
import { TextBlockRef } from 'app/entities/text-block-ref.model';

export class MockAthenaService {
    getFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<TextBlockRef[]> {
        return of([] as TextBlockRef[]);
    }
}
