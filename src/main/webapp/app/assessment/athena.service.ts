import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, of, switchMap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingFeedbackSuggestion, TextFeedbackSuggestion } from 'app/entities/feedback-suggestion.model';

@Injectable({ providedIn: 'root' })
export class AthenaService {
    public resourceUrl = 'api/athena';

    constructor(
        protected http: HttpClient,
        private profileService: ProfileService,
    ) {}

    /**
     * Get feedback suggestions for the given submission from Athena
     *
     * @param exerciseId the id of the exercise
     * @param submissionId the id of the submission
     * @return observable that emits the feedback suggestions
     */
    private getFeedbackSuggestions<T>(exerciseId: number, submissionId: number): Observable<T[]> {
        return this.profileService.getProfileInfo().pipe(
            switchMap((profileInfo) => {
                if (!profileInfo.activeProfiles.includes('athena')) {
                    return of([] as T[]);
                }
                return this.http
                    .get<T[]>(`${this.resourceUrl}/exercises/${exerciseId}/submissions/${submissionId}/feedback-suggestions`, { observe: 'response' })
                    .pipe(switchMap((res: HttpResponse<T[]>) => of(res.body!)));
            }),
        );
    }

    /**
     * Get feedback suggestions for the given text submission from Athena
     *
     * @param exerciseId the id of the exercise
     * @param submissionId the id of the submission
     * @return observable that emits the feedback suggestions
     */
    public getTextFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<TextFeedbackSuggestion[]> {
        return this.getFeedbackSuggestions<TextFeedbackSuggestion>(exerciseId, submissionId);
    }

    /**
     * Get feedback suggestions for the given programming submission from Athena
     *
     * @param exerciseId the id of the exercise
     * @param submissionId the id of the submission
     * @return observable that emits the feedback suggestions
     */
    public getProgrammingFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<ProgrammingFeedbackSuggestion[]> {
        return this.getFeedbackSuggestions<ProgrammingFeedbackSuggestion>(exerciseId, submissionId);
    }
}
