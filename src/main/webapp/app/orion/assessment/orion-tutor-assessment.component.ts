import { Component, ViewChild, inject } from '@angular/core';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { Feedback } from 'app/entities/feedback.model';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-orion-course-management-exercises',
    templateUrl: './orion-tutor-assessment.component.html',
})
export class OrionTutorAssessmentComponent {
    private orionConnectorService = inject(OrionConnectorService);
    private alertService = inject(AlertService);
    private orionAssessmentService = inject(OrionAssessmentService);

    @ViewChild(CodeEditorTutorAssessmentContainerComponent) container: CodeEditorTutorAssessmentContainerComponent;

    constructor() {
        const orionConnectorService = this.orionConnectorService;

        // Register this component as receiver of updates from Orion
        orionConnectorService.activeAssessmentComponent = this;
    }

    /**
     * Sends all current inline feedback to Orion
     */
    initializeFeedback() {
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        this.orionConnectorService.initializeAssessment(this.container.submission?.id!, this.container.referencedFeedback);
    }

    /**
     * Relays the updated feedback from Orion to the container, if the submissionId matches
     * @param submissionId to validate against
     * @param feedback updated feedback from Orion
     */
    updateFeedback(submissionId: number, feedback: Feedback[]) {
        if (submissionId !== this.container.submission?.id) {
            this.alertService.error('artemisApp.orion.assessment.submissionIdDontMatch');
        } else {
            this.container.onUpdateFeedback(feedback);
        }
    }

    /**
     * Delegates to the {@link OrionAssessmentService} to make Orion load a new submission.
     * Triggered on clicking the "next submission" button
     */
    openNextSubmission(submissionId: number) {
        this.orionAssessmentService.sendSubmissionToOrionCancellable(this.container.exerciseId, submissionId, this.container.correctionRound, this.container.isTestRun);
    }
}
