import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, of, switchMap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

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
    getFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<Feedback[]> {
        // For debugging: Return basic feedback suggestions for BubbleSort.java
        const referencedFeedbackSuggestion = new Feedback();
        referencedFeedbackSuggestion.id = 1;
        referencedFeedbackSuggestion.credits = 1;
        referencedFeedbackSuggestion.text = 'FeedbackSuggestion:';
        referencedFeedbackSuggestion.detailText = 'This is a referenced feedback suggestion';
        referencedFeedbackSuggestion.gradingInstruction = undefined;
        referencedFeedbackSuggestion.reference = 'file:src/de/athena/BubbleSort.java_line:13';
        referencedFeedbackSuggestion.type = FeedbackType.AUTOMATIC;
        const unreferencedFeedbackSuggestion = new Feedback();
        unreferencedFeedbackSuggestion.id = 2;
        unreferencedFeedbackSuggestion.credits = -1;
        unreferencedFeedbackSuggestion.text = 'FeedbackSuggestion:';
        unreferencedFeedbackSuggestion.detailText = 'This is an unreferenced feedback suggestion';
        unreferencedFeedbackSuggestion.gradingInstruction = undefined;
        unreferencedFeedbackSuggestion.reference = undefined;
        unreferencedFeedbackSuggestion.type = FeedbackType.AUTOMATIC;
        return of([referencedFeedbackSuggestion, unreferencedFeedbackSuggestion]);

        return this.profileService.getProfileInfo().pipe(
            switchMap((profileInfo) => {
                if (!profileInfo.activeProfiles.includes('athena')) {
                    return of([] as Feedback[]);
                }
                return this.http
                    .get<Feedback[]>(`${this.resourceUrl}/exercises/${exerciseId}/submissions/${submissionId}/feedback-suggestions`, { observe: 'response' })
                    .pipe(switchMap((res: HttpResponse<Feedback[]>) => of(res.body!)))
                    .pipe(
                        switchMap((feedbackSuggestions) => {
                            // Make real Feedback objects out of the plain objects
                            return of(
                                feedbackSuggestions.map((feedbackSuggestion) => {
                                    const suggestion = new Feedback();
                                    suggestion.id = feedbackSuggestion.id;
                                    suggestion.credits = feedbackSuggestion.credits;
                                    suggestion.text = feedbackSuggestion.text;
                                    suggestion.detailText = feedbackSuggestion.detailText;
                                    suggestion.gradingInstruction = feedbackSuggestion.gradingInstruction;
                                    suggestion.reference = feedbackSuggestion.reference;
                                    suggestion.type = feedbackSuggestion.type;
                                    return suggestion;
                                }),
                            );
                        }),
                    );
            }),
        );
    }
}
