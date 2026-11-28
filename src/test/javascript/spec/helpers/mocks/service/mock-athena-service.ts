import { Observable, of } from 'rxjs';
import { ProgrammingFeedback } from 'app/openapi/model/programming-feedback';
import { TextFeedback } from 'app/openapi/model/text-feedback';

export class MockAthenaService {
    getTextFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<TextFeedback[]> {
        return of([] as TextFeedback[]);
    }

    getProgrammingFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<ProgrammingFeedback[]> {
        return of([] as ProgrammingFeedback[]);
    }
}
