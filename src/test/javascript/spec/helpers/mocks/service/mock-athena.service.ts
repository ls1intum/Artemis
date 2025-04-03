import { Observable, of } from 'rxjs';
import { ModelingFeedbackSuggestion, ProgrammingFeedbackSuggestion, TextFeedbackSuggestion } from 'app/assessment/shared/entities/feedback-suggestion.model';

export class MockAthenaService {
    getProgrammingFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<ProgrammingFeedbackSuggestion[]> {
        return of([]);
    }
    getTextFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<TextFeedbackSuggestion[]> {
        return of([]);
    }
    getModelingFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<ModelingFeedbackSuggestion[]> {
        return of([]);
    }
}
