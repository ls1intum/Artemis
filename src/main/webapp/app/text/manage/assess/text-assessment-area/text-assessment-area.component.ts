import { Component, HostListener, effect, inject, input, model, output, signal } from '@angular/core';
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
export class TextAssessmentAreaComponent {
    private stringCountService = inject(StringCountService);

    // inputs
    submission = input.required<TextSubmission>();
    textBlockRefs = model.required<TextBlockRef[]>();
    readOnly = input<boolean>(false);
    highlightDifferences = input<boolean>(false);
    criteria = input<GradingCriterion[]>();
    allowManualBlockSelection = input<boolean>(true);

    // outputs
    textBlockRefsAddedRemoved = output<void>();

    // state signals
    autoTextBlockAssessment = signal(true);
    selectedRef = model<TextBlockRef | undefined>(undefined);
    wordCount = signal(0);
    characterCount = signal(0);

    constructor() {
        // Effect to handle submission changes - updates word/character counts
        effect(() => {
            const submissionValue = this.submission();
            if (submissionValue) {
                const { text } = submissionValue;
                this.wordCount.set(this.stringCountService.countWords(text));
                this.characterCount.set(this.stringCountService.countCharacters(text));
            }
        });

        // Effect to ensure textBlockRefs are sorted by startIndex
        effect(() => {
            const refs = this.textBlockRefs();
            if (!refs || refs.length <= 1) {
                return;
            }

            // Create a sorted copy without mutating the original
            const sortedRefs = [...refs].sort((a, b) => a.block!.startIndex! - b.block!.startIndex!);

            // Check if order actually changed by comparing startIndices
            const orderChanged = refs.some((ref, index) => ref.block!.startIndex !== sortedRefs[index].block!.startIndex);

            if (orderChanged) {
                this.textBlockRefs.set(sortedRefs);
            }
        });
    }

    @HostListener('document:keydown.alt', ['$event', 'false'])
    @HostListener('document:keyup.alt', ['$event', 'true'])
    onAltToggle(event: Event, toggleValue: boolean) {
        if (!this.allowManualBlockSelection()) {
            return;
        }
        this.autoTextBlockAssessment.set(toggleValue);
    }

    /**
     * Emit the reference change of text blocks
     */
    textBlockRefsChangeEmit(): void {
        // Trigger model update by setting the same value (forces change detection)
        this.textBlockRefs.set([...this.textBlockRefs()]);
    }

    /**
     * It is called if a text block is added manually.
     * @param ref - added text block
     */
    addTextBlockRef(ref: TextBlockRef): void {
        const currentRefs = this.textBlockRefs();
        this.textBlockRefs.set([...currentRefs, ref]);
        this.textBlockRefsAddedRemoved.emit();
    }

    /**
     * It is called if the assessment for a text block is deleted. So, textBlockRef is deleted
     * @param ref - TextBlockRef that has a deleted assessment(feedback).
     */
    removeTextBlockRef(ref: TextBlockRef): void {
        const currentRefs = this.textBlockRefs();
        const index = currentRefs.findIndex((elem) => elem.block!.id! === ref.block!.id!);
        const newRefs = [...currentRefs];
        newRefs.splice(index, 1);
        this.textBlockRefs.set(newRefs);
        this.textBlockRefsAddedRemoved.emit();
    }

    /**
     * It is called when a text block is selected.
     * @param ref - selected TextBlockRef
     */
    didSelectRef(ref?: TextBlockRef): void {
        this.selectedRef.set(ref);
    }
}
