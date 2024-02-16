import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map, of, switchMap } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingFeedbackSuggestion, TextFeedbackSuggestion } from 'app/entities/feedback-suggestion.model';
import { Exercise } from 'app/entities/exercise.model';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER, Feedback, FeedbackType } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextSubmission } from 'app/entities/text-submission.model';

@Injectable({ providedIn: 'root' })
export class AthenaService {
    public resourceUrl = 'api/athena';

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
     * Find a grading instruction by id in the given exercise
     */
    private findGradingInstruction(exercise: Exercise, id: number): any | undefined {
        for (const criterium of exercise.gradingCriteria ?? []) {
            for (const instruction of criterium.structuredGradingInstructions) {
                if (instruction.id == id) {
                    return instruction;
                }
            }
        }
        return undefined;
    }

    /**
     * Get feedback suggestions for the given text submission from Athena
     *
     * @param exercise
     * @param submission  the submission
     * @return observable that emits the referenced feedback suggestions as TextBlockRef objects
     * with TextBlocks and the unreferenced feedback suggestions as Feedback objects
     * with the "FeedbackSuggestion:" prefix
     */
    public getTextFeedbackSuggestions(exercise: Exercise, submission: TextSubmission): Observable<(TextBlockRef | Feedback)[]> {
        return this.getFeedbackSuggestions<TextFeedbackSuggestion>(exercise, submission.id!).pipe(
            map((suggestions) => {
                // Convert referenced feedback suggestions to TextBlockRefs for easier handling in the components
                return suggestions.map((suggestion) => {
                    const feedback = new Feedback();
                    feedback.credits = suggestion.credits;
                    // Text feedback suggestions are automatically accepted, so we can set the text directly:
                    feedback.text = FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER + suggestion.title;
                    feedback.detailText = suggestion.description;
                    // Load grading instruction from exercise, if available
                    if (suggestion.structuredGradingInstructionId != undefined) {
                        feedback.gradingInstruction = this.findGradingInstruction(exercise, suggestion.structuredGradingInstructionId);
                    }
                    if (suggestion.indexStart == null) {
                        // Unreferenced feedback, return Feedback object
                        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
                        return feedback;
                    }
                    // Referenced feedback, convert to TextBlockRef
                    feedback.type = FeedbackType.MANUAL;
                    const textBlock = new TextBlock();
                    textBlock.startIndex = suggestion.indexStart;
                    textBlock.endIndex = suggestion.indexEnd;
                    textBlock.setTextFromSubmission(submission);
                    feedback.reference = textBlock.id;
                    return new TextBlockRef(textBlock, feedback);
                });
            }),
        );
    }

    /**
     * Get feedback suggestions for the given programming submission from Athena
     *
     * @param exercise
     * @param submissionId the id of the submission
     * @return observable that emits the feedback suggestions as Feedback objects with the "FeedbackSuggestion:" prefix
     */
    public getProgrammingFeedbackSuggestions(exercise: Exercise, submissionId: number): Observable<Feedback[]> {
        return this.getFeedbackSuggestions<ProgrammingFeedbackSuggestion>(exercise, submissionId).pipe(
            map((suggestions) => {
                return suggestions.map((suggestion) => {
                    const feedback = new Feedback();
                    feedback.credits = suggestion.credits;
                    feedback.text = FEEDBACK_SUGGESTION_IDENTIFIER + suggestion.title;
                    feedback.detailText = suggestion.description;
                    if (suggestion.filePath != undefined && (suggestion.lineEnd ?? suggestion.lineStart) != undefined) {
                        // Referenced feedback
                        feedback.type = FeedbackType.MANUAL;
                        feedback.reference = `file:${suggestion.filePath}_line:${suggestion.lineEnd ?? suggestion.lineStart}`; // Only use a single line for now because Artemis does not support line ranges
                    } else {
                        // Unreferenced feedback
                        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
                        feedback.reference = undefined;
                    }
                    // Load grading instruction from exercise, if available
                    if (suggestion.structuredGradingInstructionId != undefined) {
                        feedback.gradingInstruction = this.findGradingInstruction(exercise, suggestion.structuredGradingInstructionId);
                    }
                    return feedback;
                });
            }),
        );
    }
}
