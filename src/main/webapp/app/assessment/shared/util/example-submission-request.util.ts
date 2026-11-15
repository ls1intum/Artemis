import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import type { ExampleSubmissionRequest } from 'app/openapi/model/example-submission-request';
import type { Feedback as FeedbackRequest } from 'app/openapi/model/feedback';
import type { SubmissionRequest } from 'app/openapi/model/submission-request';

/**
 * Picks the feedback fields the server reads. The client feedback links back to its result, so sending it whole would not serialize.
 */
function toFeedbackRequest(feedback: Feedback): FeedbackRequest {
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
