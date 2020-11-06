import { Component, EventEmitter, Input, Output, AfterViewInit } from '@angular/core';
import { HighlightColors } from 'app/exercises/text/assess/highlight-colors';
import { TextBlock } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { convertFromHtmlLinebreaks, sanitize } from 'app/utils/text.utils';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';

@Component({
    selector: 'jhi-assessment-detail',
    templateUrl: './assessment-detail.component.html',
    styleUrls: ['./assessment-detail.component.scss'],
})
export class AssessmentDetailComponent implements AfterViewInit {
    @Input() public assessment: Feedback;
    @Input() public block: TextBlock | undefined;
    @Output() public assessmentChange = new EventEmitter<Feedback>();
    @Input() public highlightColor: HighlightColors.Color;
    @Output() public deleteAssessment = new EventEmitter<Feedback>();
    @Input() public disabled = false;
    @Input() public readOnly: boolean;
    disableEditScore = false;

    public FeedbackType_AUTOMATIC = FeedbackType.AUTOMATIC;
    constructor(public structuredGradingCriterionService: StructuredGradingCriterionService) {}

    ngAfterViewInit(): void {
        if (this.assessment.gradingInstruction && this.assessment.gradingInstruction.usageCount !== 0) {
            this.disableEditScore = true;
        } else {
            this.disableEditScore = false;
        }
    }

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
     * Emits the delete of an assessment
     */
    public delete() {
        const referencedText = convertFromHtmlLinebreaks(this.text);
        const confirmationMessage = referencedText ? `Delete Feedback of "${referencedText}"?` : 'Delete Feedback?';
        const confirmation = confirm(confirmationMessage);
        if (confirmation) {
            this.deleteAssessment.emit(this.assessment);
        }
    }

    private get text(): string {
        return this.block?.text || this.assessment.reference || '';
    }

    public get sanitizedText(): string {
        return sanitize(this.text);
    }

    updateAssessmentOnDrop(event: Event) {
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.assessment, event);
        if (this.assessment.gradingInstruction && this.assessment.gradingInstruction.usageCount !== 0) {
            this.disableEditScore = true;
        } else {
            this.disableEditScore = false;
        }
        this.assessmentChange.emit(this.assessment);
    }
}
