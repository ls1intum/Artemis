import { Component, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { StringCountService } from 'app/text/overview/service/string-count.service';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { TextBlockAssessmentCardComponent } from '../textblock-assessment-card/text-block-assessment-card.component';
import { ManualTextblockSelectionComponent } from '../manual-textblock-selection/manual-textblock-selection.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-text-assessment-area',
    templateUrl: './text-assessment-area.component.html',
    styles: [
        `
            :host {
                width: 100%;
            }
        `,
    ],
    imports: [TextBlockAssessmentCardComponent, ManualTextblockSelectionComponent, TranslateDirective],
})
export class TextAssessmentAreaComponent implements OnChanges {
    private stringCountService = inject(StringCountService);

    // inputs
    @Input() submission: TextSubmission;
    @Input() textBlockRefs: TextBlockRef[];
    @Input() readOnly: boolean;
    @Input() highlightDifferences: boolean;
    @Input() criteria?: GradingCriterion[];
    @Input() allowManualBlockSelection = true;

    // outputs
    @Output() textBlockRefsChange = new EventEmitter<TextBlockRef[]>();
    @Output() textBlockRefsAddedRemoved = new EventEmitter<void>();
    autoTextBlockAssessment = true;
    selectedRef?: TextBlockRef;
    wordCount = 0;
    characterCount = 0;

    /**
     * Life cycle hook to indicate component change
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.submission && changes.submission.currentValue) {
            const { text } = changes.submission.currentValue as TextSubmission;
            this.wordCount = this.stringCountService.countWords(text);
            this.characterCount = this.stringCountService.countCharacters(text);
        }

        this.textBlockRefs.sort((a, b) => a.block!.startIndex! - b.block!.startIndex!);
    }

    @HostListener('document:keydown.alt', ['$event', 'false'])
    @HostListener('document:keyup.alt', ['$event', 'true'])
    onAltToggle(event: KeyboardEvent, toggleValue: boolean) {
        if (!this.allowManualBlockSelection) {
            return;
        }
        this.autoTextBlockAssessment = toggleValue;
    }

    /**
     * Emit the reference change of text blocks
     */
    textBlockRefsChangeEmit(): void {
        this.textBlockRefsChange.emit(this.textBlockRefs);
    }

    /**
     * It is called if a text block is added manually.
     * @param ref - added text block
     */
    addTextBlockRef(ref: TextBlockRef): void {
        this.textBlockRefs.push(ref);
        this.textBlockRefsAddedRemoved.emit();
    }

    /**
     * It is called if the assessment for a text block is deleted. So, textBlockRef is deleted
     * @param ref - TextBlockRef that has a deleted assessment(feedback).
     */
    removeTextBlockRef(ref: TextBlockRef): void {
        const index = this.textBlockRefs.findIndex((elem) => elem.block!.id! === ref.block!.id!);
        this.textBlockRefs.splice(index, 1);
        this.textBlockRefsAddedRemoved.emit();
    }

    /**
     * It is called when a text block is selected.
     * @param ref - selected TextBlockRef
     */
    didSelectRef(ref?: TextBlockRef): void {
        this.selectedRef = ref;
    }
}
