import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faExclamation, faExclamationTriangle, faQuestionCircle, faRobot, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-assessment-detail',
    templateUrl: './assessment-detail.component.html',
    styleUrls: ['./assessment-detail.component.scss'],
})
export class AssessmentDetailComponent {
    @Input() public assessment: Feedback;
    @Output() public assessmentChange = new EventEmitter<Feedback>();
    @Output() public deleteAssessment = new EventEmitter<Feedback>();
    @Input() public readOnly: boolean;
    @Input() highlightDifferences: boolean;

    readonly FeedbackType_AUTOMATIC = FeedbackType.AUTOMATIC;
    readonly ButtonSize = ButtonSize;

    // Icons
    faTrashAlt = faTrashAlt;
    faRobot = faRobot;
    faQuestionCircle = faQuestionCircle;
    faExclamation = faExclamation;
    faExclamationTriangle = faExclamationTriangle;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(public structuredGradingCriterionService: StructuredGradingCriterionService) {}

    /**
     * Emits assessment changes to parent component
     */
    public emitChanges(): void {
        if (this.assessment.type === FeedbackType.AUTOMATIC) {
            this.assessment.type = FeedbackType.AUTOMATIC_ADAPTED;
        }
        this.assessmentChange.emit(this.assessment);
    }
    /**
     * Emits the deletion of an assessment
     */
    public delete() {
        this.deleteAssessment.emit(this.assessment);
        this.dialogErrorSource.next('');
    }

    updateAssessmentOnDrop(event: Event) {
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.assessment, event);
        this.assessmentChange.emit(this.assessment);
    }
}
