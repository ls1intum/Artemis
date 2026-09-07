import { AlertService } from 'app/foundation/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { FeedbackCorrectionError } from 'app/assessment/shared/entities/feedback.model';
import { onError } from 'app/foundation/util/global.utils';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';

export interface FeedbackMarker {
    markWrongFeedback(errors: FeedbackCorrectionError[]): void;
    markAllFeedbackToCorrect(): void;
}

export class ExampleSubmissionAssessCommand {
    constructor(
        private tutorParticipationService: TutorParticipationService,
        private alertService: AlertService,
        private feedbackMarker: FeedbackMarker,
    ) {}

    assessExampleSubmission(exampleSubmission: ExampleSubmission, exerciseId: number) {
        this.tutorParticipationService.assessExampleSubmission(exampleSubmission, exerciseId).subscribe({
            next: () => this.onSuccess(),
            error: (error: HttpErrorResponse) => this.onFailure(error),
        });
    }

    private onSuccess() {
        this.feedbackMarker.markAllFeedbackToCorrect();
        this.alertService.success('artemisApp.exampleSubmission.correctTutorAssessment');
    }

    private onFailure(error: HttpErrorResponse) {
        const errorKey = error.error?.errorKey;
        const errorType = errorKey ? `error.${errorKey}` : error.headers.get('x-artemisapp-error');

        if (errorType !== 'error.invalid_assessment') {
            onError(this.alertService, error);
            return;
        }

        const correctionErrors = error.error?.errors;
        if (!Array.isArray(correctionErrors)) {
            onError(this.alertService, error);
            return;
        }

        this.feedbackMarker.markAllFeedbackToCorrect();
        this.feedbackMarker.markWrongFeedback(correctionErrors);

        const msg = correctionErrors.length === 0 ? 'artemisApp.exampleSubmission.submissionValidation.missing' : 'artemisApp.exampleSubmission.submissionValidation.wrong';
        this.alertService.error(msg, { mistakeCount: correctionErrors.length });
    }
}
