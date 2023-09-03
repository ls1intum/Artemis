import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, of, switchMap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingFeedbackSuggestion, TextFeedbackSuggestion } from 'app/entities/feedback-suggestion.model';
import { Exercise } from 'app/entities/exercise.model';

@Injectable({ providedIn: 'root' })
export class AthenaService {
    public resourceUrl = 'api/public/athena';

    constructor(
        protected http: HttpClient,
        private profileService: ProfileService,
    ) {}

    public isEnabled(): Observable<boolean> {
        return this.profileService.getProfileInfo().pipe(switchMap((profileInfo) => of(profileInfo.activeProfiles.includes('athena'))));
    }

    /**
     * Get feedback suggestions for the given submission from Athena - for programming exercises
     * Currently, this is separate for programming and text exercises (will be changed)
     *
     * @param exercise
     * @param submissionId the id of the submission
     * @return observable that emits the feedback suggestions
     */
    private getFeedbackSuggestions<T>(exercise: Exercise, submissionId: number): Observable<T[]> {
        if (!exercise.feedbackSuggestionsEnabled) {
            return of([]);
        }
        return this.isEnabled().pipe(
            switchMap((isAthenaEnabled) => {
                if (!isAthenaEnabled) {
                    return of([] as T[]);
                }
                return this.http
                    .get<T[]>(`${this.resourceUrl}/${exercise.type}-exercises/${exercise.id}/submissions/${submissionId}/feedback-suggestions`, { observe: 'response' })
                    .pipe(switchMap((res: HttpResponse<T[]>) => of(res.body!)));
            }),
        );
    }

    /**
     * Get feedback suggestions for the given text submission from Athena
     *
     * @param exercise
     * @param submissionId the id of the submission
     * @return observable that emits the feedback suggestions
     */
    public getTextFeedbackSuggestions(exercise: Exercise, submissionId: number): Observable<TextFeedbackSuggestion[]> {
        return this.getFeedbackSuggestions<TextFeedbackSuggestion>(exercise, submissionId);
    }

    /**
     * Get feedback suggestions for the given programming submission from Athena
     *
     * @param exercise
     * @param submissionId the id of the submission
     * @return observable that emits the feedback suggestions
     */
    public getProgrammingFeedbackSuggestions(exercise: Exercise, submissionId: number): Observable<ProgrammingFeedbackSuggestion[]> {
        return this.getFeedbackSuggestions<ProgrammingFeedbackSuggestion>(exercise, submissionId);
    }
}
