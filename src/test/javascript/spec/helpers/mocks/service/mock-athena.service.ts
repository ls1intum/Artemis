import { Observable, of } from 'rxjs';
import { ModelingFeedback } from 'app/openapi/model/modeling-feedback';
import { ProgrammingFeedback } from 'app/openapi/model/programming-feedback';
import { TextFeedback } from 'app/openapi/model/text-feedback';

export class MockAthenaService {
    getProgrammingFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<ProgrammingFeedback[]> {
        return of([]);
    }
    getTextFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<TextFeedback[]> {
        return of([]);
    }
    getModelingFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<ModelingFeedback[]> {
        return of([]);
    }
}
