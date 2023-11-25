import { Observable, of } from 'rxjs';
import { ProgrammingFeedbackSuggestion, TextFeedbackSuggestion } from 'app/entities/feedback-suggestion.model';

export class MockAthenaService {
    getTextFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<TextFeedbackSuggestion[]> {
        return of([] as TextFeedbackSuggestion[]);
    }

    getProgrammingFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<ProgrammingFeedbackSuggestion[]> {
        return of([] as ProgrammingFeedbackSuggestion[]);
    }
}
