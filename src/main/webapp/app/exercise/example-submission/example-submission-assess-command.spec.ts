import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { FeedbackCorrectionError, FeedbackCorrectionErrorType } from 'app/assessment/shared/entities/feedback.model';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';
import { ExampleSubmissionAssessCommand, FeedbackMarker } from 'app/exercise/example-submission/example-submission-assess-command';

describe('ExampleSubmissionAssessCommand', () => {
    const exerciseId = 42;
    const exampleSubmission = { id: 7 } as ExampleSubmission;

    let tutorParticipationService: TutorParticipationService;
    let alertService: AlertService;
    let feedbackMarker: FeedbackMarker;

    const invalidAssessmentError = (body: unknown) =>
        new HttpErrorResponse({
            status: 400,
            error: body,
            headers: new HttpHeaders(),
        });

    beforeEach(() => {
        tutorParticipationService = { assessExampleSubmission: vi.fn() } as unknown as TutorParticipationService;
        alertService = { success: vi.fn(), error: vi.fn(), addAlert: vi.fn() } as unknown as AlertService;
        feedbackMarker = { markAllFeedbackToCorrect: vi.fn(), markWrongFeedback: vi.fn() };
    });

    const run = () => new ExampleSubmissionAssessCommand(tutorParticipationService, alertService, feedbackMarker).assessExampleSubmission(exampleSubmission, exerciseId);

    it('should mark everything correct on success', () => {
        vi.spyOn(tutorParticipationService, 'assessExampleSubmission').mockReturnValue(of({} as any));

        run();

        expect(feedbackMarker.markAllFeedbackToCorrect).toHaveBeenCalledOnce();
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.exampleSubmission.correctTutorAssessment');
    });

    it('should forward the per-feedback correction errors of an invalid assessment', () => {
        const correctionError: FeedbackCorrectionError = { reference: 'ref-1', type: FeedbackCorrectionErrorType.INCORRECT_SCORE };
        vi.spyOn(tutorParticipationService, 'assessExampleSubmission').mockReturnValue(
            throwError(() => invalidAssessmentError({ errorKey: 'invalid_assessment', errors: [correctionError] })),
        );

        run();

        expect(feedbackMarker.markAllFeedbackToCorrect).toHaveBeenCalledOnce();
        expect(feedbackMarker.markWrongFeedback).toHaveBeenCalledWith([correctionError]);
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.exampleSubmission.submissionValidation.wrong', { mistakeCount: 1 });
    });

    it('should report a missing assessment when the server found no wrong feedback', () => {
        vi.spyOn(tutorParticipationService, 'assessExampleSubmission').mockReturnValue(throwError(() => invalidAssessmentError({ errorKey: 'invalid_assessment', errors: [] })));

        run();

        expect(feedbackMarker.markWrongFeedback).toHaveBeenCalledWith([]);
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.exampleSubmission.submissionValidation.missing', { mistakeCount: 0 });
    });

    it.each([{ errorKey: 'invalid_assessment' }, { timestamp: 'x', status: 400, error: 'Bad Request' }, undefined])('should not throw when the error payload is %j', (body) => {
        vi.spyOn(tutorParticipationService, 'assessExampleSubmission').mockReturnValue(throwError(() => invalidAssessmentError(body)));

        expect(() => run()).not.toThrow();

        expect(feedbackMarker.markWrongFeedback).not.toHaveBeenCalled();
        expect(feedbackMarker.markAllFeedbackToCorrect).not.toHaveBeenCalled();
        expect(alertService.error).toHaveBeenCalledWith('error.http.400');
    });
});
