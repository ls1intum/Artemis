import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { FeedbackCorrectionError } from 'app/assessment/shared/entities/feedback.model';
import { onError } from 'app/shared/util/global.utils';
import { ExampleParticipation } from 'app/exercise/shared/entities/participation/example-participation.model';
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

    assessExampleParticipation(exampleParticipation: ExampleParticipation, exerciseId: number) {
        this.tutorParticipationService.assessExampleParticipation(exampleParticipation, exerciseId).subscribe({
            next: () => this.onSuccess(),
            error: (error: HttpErrorResponse) => this.onFailure(error),
        });
    }

    private onSuccess() {
        this.feedbackMarker.markAllFeedbackToCorrect();
        this.alertService.success('artemisApp.exampleSubmission.correctTutorAssessment');
    }

    private onFailure(error: HttpErrorResponse) {
        const errorType = error.headers.get('x-artemisapp-error');

        if (errorType === 'error.invalid_assessment') {
            this.feedbackMarker.markAllFeedbackToCorrect();

            // Mark all wrongly made feedbacks accordingly.
            const correctionErrors: FeedbackCorrectionError[] = JSON.parse(error['error']['title'])['errors'];
            this.feedbackMarker.markWrongFeedback(correctionErrors);

            const msg = correctionErrors.length === 0 ? 'artemisApp.exampleSubmission.submissionValidation.missing' : 'artemisApp.exampleSubmission.submissionValidation.wrong';
            this.alertService.error(msg, { mistakeCount: correctionErrors.length });
        } else {
            onError(this.alertService, error);
        }
    }
}
