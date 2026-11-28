import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission-exercise-type.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextBlock, TextBlockType } from 'app/text/shared/entities/text-block.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { Language } from 'app/course/shared/entities/course.model';
import type { ExampleSubmissionDetail } from 'app/openapi/model/example-submission-detail';
import type { SubmissionContent } from 'app/openapi/model/submission-content';
import type { TextBlock as TextBlockResponse } from 'app/openapi/model/text-block';
import type { ExampleSubmissionRequest } from 'app/openapi/model/example-submission-request';
import type { Feedback as FeedbackRequest } from 'app/openapi/model/feedback';
import type { SubmissionRequest } from 'app/openapi/model/submission-request';

/**
 * Picks the feedback fields the server reads. The client feedback links back to its result, so sending it whole would not serialize.
 */
export function toFeedbackRequest(feedback: Feedback): FeedbackRequest {
    return {
        id: feedback.id,
        text: feedback.text,
        detailText: feedback.detailText,
        hasLongFeedbackText: feedback.hasLongFeedbackText,
        reference: feedback.reference,
        credits: feedback.credits,
        positive: feedback.positive,
        type: feedback.type,
        gradingInstruction: feedback.gradingInstruction,
        testCase: feedback.testCase
            ? {
                  id: feedback.testCase.id,
                  testName: feedback.testCase.testName,
                  weight: feedback.testCase.weight,
                  active: feedback.testCase.active,
                  visibility: feedback.testCase.visibility,
                  bonusMultiplier: feedback.testCase.bonusMultiplier,
                  bonusPoints: feedback.testCase.bonusPoints,
                  type: feedback.testCase.type,
              }
            : undefined,
    };
}

function toSubmissionRequest(submission: Submission): SubmissionRequest {
    return {
        id: submission.id,
        text: 'text' in submission && typeof submission.text === 'string' ? submission.text : undefined,
        model: 'model' in submission && typeof submission.model === 'string' ? submission.model : undefined,
        explanationText: 'explanationText' in submission && typeof submission.explanationText === 'string' ? submission.explanationText : undefined,
        results: submission.results?.map((result) => ({ feedbacks: result.feedbacks?.map(toFeedbackRequest) })),
    };
}

/**
 * Builds the request body of the example submission endpoints (create, update, tutor assessment check) from the example submission the editor holds.
 */
export function toExampleSubmissionRequest(exampleSubmission: ExampleSubmission): ExampleSubmissionRequest {
    return {
        id: exampleSubmission.id,
        usedForTutorial: exampleSubmission.usedForTutorial,
        assessmentExplanation: exampleSubmission.assessmentExplanation,
        exercise: exampleSubmission.exercise ? { id: exampleSubmission.exercise.id } : undefined,
        submission: exampleSubmission.submission ? toSubmissionRequest(exampleSubmission.submission) : undefined,
    };
}

const languageByResponseLanguage: Record<NonNullable<SubmissionContent['language']>, Language> = {
    ENGLISH: Language.ENGLISH,
    GERMAN: Language.GERMAN,
};

function toTextBlock(block: TextBlockResponse, submissionId: number | undefined): TextBlock {
    const textBlock = new TextBlock();
    textBlock.submissionId = submissionId;
    textBlock.startIndex = block.startIndex;
    textBlock.endIndex = block.endIndex;
    textBlock.text = block.text;
    textBlock.id = block.id;
    textBlock.type = block.type === 'AUTOMATIC' ? TextBlockType.AUTOMATIC : block.type === 'MANUAL' ? TextBlockType.MANUAL : undefined;
    return textBlock;
}

function toSubmission(content: SubmissionContent): Submission | undefined {
    if (content.submissionExerciseType === SubmissionExerciseType.TEXT) {
        const submission = new TextSubmission();
        submission.id = content.id;
        submission.text = content.text;
        submission.language = content.language ? languageByResponseLanguage[content.language] : undefined;
        submission.blocks = content.blocks?.map((block) => toTextBlock(block, content.id));
        return submission;
    }
    if (content.submissionExerciseType === SubmissionExerciseType.MODELING) {
        const submission = new ModelingSubmission();
        submission.id = content.id;
        submission.model = content.model;
        submission.explanationText = content.explanationText;
        return submission;
    }
    return undefined;
}

/**
 * Builds the editor's example submission from the response of the example submission endpoints (get, create, update, import).
 */
export function toExampleSubmission(detail: ExampleSubmissionDetail): ExampleSubmission {
    return {
        id: detail.id,
        usedForTutorial: detail.usedForTutorial,
        assessmentExplanation: detail.assessmentExplanation,
        submission: detail.submission ? toSubmission(detail.submission) : undefined,
    };
}
