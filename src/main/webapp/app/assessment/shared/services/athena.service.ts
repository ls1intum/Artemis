import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map, of, switchMap } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER, Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { PROFILE_ATHENA } from 'app/app.constants';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingFeedbackSuggestion, ProgrammingFeedbackSuggestion, TextFeedbackSuggestion } from 'app/assessment/shared/entities/feedback-suggestion.model';

@Injectable({ providedIn: 'root' })
export class AthenaService {
    protected http = inject(HttpClient);
    private profileService = inject(ProfileService);

    public resourceUrl = 'api/athena';

    /**
     * Fetches all available modules for a course and exercise.
     *
     * @param courseId The id of the course for which the feedback suggestion modules should be fetched
     * @param exercise The exercise for which the feedback suggestion modules should be fetched
     */
    public getAvailableModules(courseId: number, exercise: Exercise): Observable<string[]> {
        if (!this.profileService.isProfileActive(PROFILE_ATHENA)) {
            return of([] as string[]);
        }

        return this.http
            .get<string[]>(`${this.resourceUrl}/courses/${courseId}/${exercise.type}-exercises/available-modules`, { observe: 'response' })
            .pipe(switchMap((res: HttpResponse<string[]>) => of(res.body!)));
    }

    /**
     * Get feedback suggestions for the given submission from Athena
     *
     * @param exercise
     * @param submissionId the id of the submission
     * @return observable that emits the feedback suggestions
     */
    private getFeedbackSuggestions<T>(exercise: Exercise, submissionId: number): Observable<T[]> {
        if (!exercise.feedbackSuggestionModule) {
            return of([]);
        }
        if (!this.profileService.isProfileActive(PROFILE_ATHENA)) {
            return of([] as T[]);
        }

        return this.http
            .get<T[]>(`${this.resourceUrl}/${exercise.type}-exercises/${exercise.id}/submissions/${submissionId}/feedback-suggestions`, { observe: 'response' })
            .pipe(switchMap((res: HttpResponse<T[]>) => of(res.body!)));
    }

    /**
     * Find a grading instruction by id in the given exercise
     */
    private findGradingInstruction(exercise: Exercise, id: number): any | undefined {
        for (const criterion of exercise.gradingCriteria ?? []) {
            for (const instruction of criterion.structuredGradingInstructions) {
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

    /**
     * Get feedback suggestions for the given modeling submission from Athena.
     *
     * @param exercise The exercise for which a submission is assessed
     * @param submission The assessed submission
     * @return observable that emits the feedback suggestions as Feedback objects with the "FeedbackSuggestion:" prefix
     */
    public getModelingFeedbackSuggestions(exercise: Exercise, submission: ModelingSubmission): Observable<Feedback[]> {
        return this.getFeedbackSuggestions<ModelingFeedbackSuggestion>(exercise, submission.id!).pipe(
            map((suggestions) => {
                return suggestions.map((suggestion, index) => {
                    const feedback = new Feedback();
                    feedback.id = index;
                    feedback.credits = suggestion.credits;
                    feedback.positive = suggestion.credits >= 1;

                    // Extract reference details if present
                    const reference = suggestion.reference?.split(':');
                    const [referenceType, referenceId] = reference || [];

                    if (referenceId) {
                        feedback.type = FeedbackType.AUTOMATIC;
                        feedback.text = suggestion.description;
                        feedback.reference = suggestion.reference;
                        feedback.referenceId = referenceId;
                        feedback.referenceType = referenceType;
                    } else {
                        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
                        feedback.text = `${FEEDBACK_SUGGESTION_IDENTIFIER}${suggestion.title}`;
                        feedback.detailText = suggestion.description;
                    }

                    // Attach grading instruction if available
                    if (suggestion.structuredGradingInstructionId) {
                        feedback.gradingInstruction = this.findGradingInstruction(exercise, suggestion.structuredGradingInstructionId);
                    }

                    return feedback;
                });
            }),
        );
    }
}
