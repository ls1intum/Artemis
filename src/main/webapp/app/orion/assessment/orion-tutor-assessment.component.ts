import { Component, ViewChild } from '@angular/core';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { Feedback } from 'app/entities/feedback.model';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { JhiAlertService } from 'ng-jhipster';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';

@Component({
    selector: 'jhi-orion-course-management-exercises',
    templateUrl: './orion-tutor-assessment.component.html',
})
export class OrionTutorAssessmentComponent {
    @ViewChild(CodeEditorTutorAssessmentContainerComponent) container: CodeEditorTutorAssessmentContainerComponent;

    constructor(private orionConnectorService: OrionConnectorService, private jhiAlertService: JhiAlertService, private orionAssessmentService: OrionAssessmentService) {
        // Register this component as receiver of updates from Orion
        orionConnectorService.activeAssessmentComponent = this;
    }

    /**
     * Sends all current inline feedback to Orion
     */
    initializeFeedback() {
        this.orionConnectorService.initializeAssessment(this.container.submission?.id!, this.container.referencedFeedback);
    }

    /**
     * Relays the updated feedback from Orion to the container, if the submissionId matches
     * @param submissionId to validate against
     * @param feedback updated feedback from Orion
     */
    updateFeedback(submissionId: number, feedback: Feedback[]) {
        if (submissionId !== this.container.submission?.id) {
            this.jhiAlertService.error('artemisApp.orion.assessment.submissionIdDontMatch');
        } else {
            this.container.onUpdateFeedback(feedback);
        }
    }

    /**
     * Delegates to the {@link OrionAssessmentService} to make Orion load a new submission.
     * Triggered on clicking the "next submission" button
     */
    openNextSubmission(submissionId: number) {
        this.orionAssessmentService.sendSubmissionToOrion(this.container.exerciseId, submissionId, this.container.correctionRound);
    }
}
