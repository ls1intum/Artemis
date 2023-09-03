import { Observable, of } from 'rxjs';
import { ProgrammingFeedbackSuggestion, TextFeedbackSuggestion } from 'app/entities/feedback-suggestion.model';

export class MockAthenaService {
    getProgrammingFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<ProgrammingFeedbackSuggestion[]> {
        return of([]);
    }
    getTextFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<TextFeedbackSuggestion[]> {
        return of([]);
    }
}
