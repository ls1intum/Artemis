import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, of, switchMap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';

@Injectable({ providedIn: 'root' })
export class AthenaService {
    public resourceUrl = 'api/athena';

    constructor(
        protected http: HttpClient,
        private profileService: ProfileService,
    ) {}

    // TODO: The following two functions will be merged in a future PR

    /**
     * Get feedback suggestions for the given submission from Athena - for programming exercises
     * Currently, this is separate for programming and text exercises (will be changed)
     *
     * @param exerciseId the id of the exercise
     * @param submissionId the id of the submission
     */
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    getFeedbackSuggestionsForProgramming(exerciseId: number, submissionId: number): Observable<Feedback[]> {
        // For debugging: Return basic feedback suggestions for BubbleSort.java
        const referencedFeedbackSuggestion1 = new Feedback();
        referencedFeedbackSuggestion1.credits = 1;
        referencedFeedbackSuggestion1.text = 'FeedbackSuggestion:';
        referencedFeedbackSuggestion1.detailText = 'This is a referenced feedback suggestion - test test';
        referencedFeedbackSuggestion1.gradingInstruction = undefined;
        referencedFeedbackSuggestion1.reference = 'file:src/de/athena/BubbleSort.java_line:9';
        referencedFeedbackSuggestion1.type = FeedbackType.AUTOMATIC;
        const referencedFeedbackSuggestion2 = new Feedback();
        referencedFeedbackSuggestion2.credits = -1;
        referencedFeedbackSuggestion2.text = 'FeedbackSuggestion:';
        referencedFeedbackSuggestion2.detailText = 'Look at that TODO....';
        referencedFeedbackSuggestion2.gradingInstruction = undefined;
        referencedFeedbackSuggestion2.reference = 'file:src/de/athena/BubbleSort.java_line:13';
        referencedFeedbackSuggestion2.type = FeedbackType.AUTOMATIC;
        const referencedFeedbackSuggestion3 = new Feedback();
        referencedFeedbackSuggestion3.credits = -4;
        referencedFeedbackSuggestion3.text = 'FeedbackSuggestion:';
        referencedFeedbackSuggestion3.detailText = 'You did not implement it correctly';
        referencedFeedbackSuggestion3.gradingInstruction = undefined;
        referencedFeedbackSuggestion3.reference = 'file:src/de/athena/MergeSort.java_line:13';
        referencedFeedbackSuggestion3.type = FeedbackType.AUTOMATIC;
        const unreferencedFeedbackSuggestion = new Feedback();
        unreferencedFeedbackSuggestion.credits = -2;
        unreferencedFeedbackSuggestion.text = 'FeedbackSuggestion:';
        unreferencedFeedbackSuggestion.detailText = 'You did not implement it correctly';
        unreferencedFeedbackSuggestion.gradingInstruction = undefined;
        unreferencedFeedbackSuggestion.reference = undefined;
        unreferencedFeedbackSuggestion.type = FeedbackType.AUTOMATIC;
        return of([referencedFeedbackSuggestion1, referencedFeedbackSuggestion2, referencedFeedbackSuggestion3, unreferencedFeedbackSuggestion]);

        return of([]); // Will be fetched in a future PR
    }

    /**
     * Get feedback suggestions for the given submission from Athena - for text exercises
     * Currently, this is separate for programming and text exercises (will be changed)
     *
     * @param exerciseId the id of the exercise
     * @param submissionId the id of the submission
     */
    getFeedbackSuggestionsForText(exerciseId: number, submissionId: number): Observable<TextBlockRef[]> {
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
