import { Component, Input, Output, EventEmitter } from '@angular/core';
import { SelectionRectangle, TextSelectEvent } from 'app/exercises/text/shared/text-select.directive';
import { convertToHtmlLinebreaks } from 'app/utils/text.utils';
import { TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text-block.model';
import { TextAssessmentAnalytics } from 'app/exercises/text/assess/analytics/text-assesment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-manual-text-selection',
    templateUrl: './manual-text-selection.component.html',
    styleUrls: ['./manual-text-selection.component.scss'],
})
export class ManualTextSelectionComponent {
    @Input() public disabled = false;
    @Input() public positionRelative = false;
    @Output() public assess = new EventEmitter<string>();

    public hostRectangle: SelectionRectangle | undefined;
    public selectedText: string | undefined;

    assessText: string;

    constructor(public textAssessmentAnalytics: TextAssessmentAnalytics, protected route: ActivatedRoute, translateService: TranslateService) {
        textAssessmentAnalytics.setComponentRoute(route);
        this.assessText = translateService.instant('artemisApp.textAssessment.editor.assess');
    }

    /**
     * Handle user's selection of solution text.
     * @param event fired on text selection of type {TextSelectEvent}
     */
    didSelectSolutionText(event: TextSelectEvent): void {
        if (this.disabled) {
            return;
        }

        // If a new selection has been created, the viewport and host rectangles will
        // exist. Or, if a selection is being removed, the rectangles will be null.
        if (event.hostRectangle) {
            this.hostRectangle = event.hostRectangle;
            this.selectedText = convertToHtmlLinebreaks(event.text);
        } else {
            this.hostRectangle = undefined;
            this.selectedText = undefined;
        }
    }

    /**
     * Remove selection from text.
     */
    deselectText(): void {
        document.getSelection()!.removeAllRanges();
        this.hostRectangle = undefined;
        this.selectedText = undefined;
    }

    assessAction(): void {
        if (this.selectedText) {
            this.assess.emit(this.selectedText);
            this.deselectText();
            this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.MANUAL);
        }
    }
}
