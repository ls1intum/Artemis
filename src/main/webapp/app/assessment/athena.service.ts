import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, of, switchMap } from 'rxjs';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

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
     */
    getFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<TextBlockRef[]> {
        return this.profileService.getProfileInfo().pipe(
            switchMap((profileInfo) => {
                if (!profileInfo.activeProfiles.includes('athena')) {
                    return of([] as TextBlockRef[]);
                }
                return this.http
                    .get<TextBlockRef[]>(`${this.resourceUrl}/exercises/${exerciseId}/submissions/${submissionId}/feedback-suggestions`, { observe: 'response' })
                    .pipe(switchMap((res: HttpResponse<TextBlockRef[]>) => of(res.body!)))
                    .pipe(
                        switchMap((feedbackSuggestions) => {
                            // Make real TextBlockRef objects out of the plain objects
                            return of(feedbackSuggestions.map((feedbackSuggestion) => new TextBlockRef(feedbackSuggestion.block!, feedbackSuggestion.feedback)));
                        }),
                    );
            }),
        );
    }
}
