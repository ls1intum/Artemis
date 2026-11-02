import { Service, inject } from '@angular/core';
import { Observable, map, of } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { MODULE_FEATURE_ATHENA } from 'app/app.constants';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { AthenaApi } from 'app/openapi/api/athena-api';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';

@Service()
export class AthenaService {
    private readonly athenaApi = inject(AthenaApi);
    private profileService = inject(ProfileService);

    /**
     * Get feedback suggestions for the given submission from Athena
     *
     * @param request the generated request to make when Athena is enabled
     * @return observable that emits the feedback suggestions
     */
    private getFeedbackSuggestions<T>(request: () => Observable<T[]>): Observable<T[]> {
        return this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA) ? request() : of([]);
    }

    /**
     * Find a grading instruction by id in the given exercise
     */
    private findGradingInstruction(exercise: Exercise, id: number): GradingInstruction | undefined {
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
     * with the "FeedbackSuggestion:accepted:" prefix
     */
    public getTextFeedbackSuggestions(exercise: Exercise, submission: TextSubmission): Observable<(TextBlockRef | Feedback)[]> {
        return this.getFeedbackSuggestions(() => this.athenaApi.getTextFeedbackSuggestions(exercise.id!, submission.id!)).pipe(
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
     * @return observable that emits the feedback suggestions as Feedback objects with the "FeedbackSuggestion:accepted:" prefix
     */
    public getProgrammingFeedbackSuggestions(exercise: Exercise, submissionId: number): Observable<Feedback[]> {
        return this.getFeedbackSuggestions(() => this.athenaApi.getProgrammingFeedbackSuggestions(exercise.id!, submissionId)).pipe(
            map((suggestions) => {
                return suggestions.map((suggestion) => {
                    const feedback = new Feedback();
                    feedback.credits = suggestion.credits;
                    // Programming feedback suggestions are automatically accepted, so we can set the text directly:
                    feedback.text = FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER + suggestion.title;
                    feedback.detailText = suggestion.description;
                    if (suggestion.filePath && Number.isInteger(suggestion.lineStart) && suggestion.lineStart! > 0) {
                        // Referenced feedback
                        feedback.type = FeedbackType.MANUAL;
                        const lineEnd = Number.isInteger(suggestion.lineEnd) && suggestion.lineEnd! > suggestion.lineStart! ? suggestion.lineEnd : suggestion.lineStart;
                        feedback.reference = `file:${suggestion.filePath}_line:${suggestion.lineStart}${lineEnd !== suggestion.lineStart ? `-${lineEnd}` : ''}`;
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
     * @return observable that emits the feedback suggestions as Feedback objects with the "FeedbackSuggestion:accepted:" prefix
     */
    public getModelingFeedbackSuggestions(exercise: Exercise, submission: ModelingSubmission): Observable<Feedback[]> {
        return this.getFeedbackSuggestions(() => this.athenaApi.getModelingFeedbackSuggestions(exercise.id!, submission.id!)).pipe(
            map((suggestions) => {
                return suggestions.map((suggestion) => {
                    const feedback = new Feedback();
                    feedback.credits = suggestion.credits;
                    feedback.positive = (suggestion.credits ?? 0) >= 1;

                    // Extract reference details if present
                    const reference = suggestion.reference?.split(':');
                    const [referenceType, referenceId] = reference || [];

                    if (referenceId) {
                        feedback.type = FeedbackType.AUTOMATIC;
                        feedback.text = `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}${suggestion.title}`;
                        feedback.detailText = suggestion.description;
                        feedback.reference = suggestion.reference;
                        feedback.referenceId = referenceId;
                        feedback.referenceType = referenceType;
                    } else {
                        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
                        // Modeling feedback suggestions are automatically accepted, so we can set the text directly:
                        feedback.text = `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}${suggestion.title}`;
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
