import { Component, ViewChild } from '@angular/core';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { Feedback } from 'app/entities/feedback.model';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';

@Component({
    selector: 'jhi-orion-course-management-exercises',
    templateUrl: './orion-tutor-assessment.component.html',
})
export class OrionTutorAssessmentComponent {
    @ViewChild(CodeEditorTutorAssessmentContainerComponent) container: CodeEditorTutorAssessmentContainerComponent;

    constructor(private orionConnectorService: OrionConnectorService) {
        orionConnectorService.activeAssessmentComponent = this;
    }

    initializeFeedback() {
        console.log(this.container.referencedFeedback);
        this.orionConnectorService.initializeAssessment(this.container.submission?.id!, this.container.referencedFeedback)
    }

    updateFeedback(submissionId: number, feedback: Feedback[]) {
        this.container.onUpdateFeedback(feedback);
    }
}
