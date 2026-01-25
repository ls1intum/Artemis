import { Component, effect, input, model, output, signal } from '@angular/core';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { ManualTextSelectionComponent, wordSelection } from 'app/text/manage/assess/manual-text-selection/manual-text-selection.component';
import { TextBlockAssessmentCardComponent } from '../textblock-assessment-card/text-block-assessment-card.component';

@Component({
    selector: 'jhi-manual-textblock-selection',
    templateUrl: './manual-textblock-selection.component.html',
    imports: [TextBlockAssessmentCardComponent, ManualTextSelectionComponent],
})
export class ManualTextblockSelectionComponent {
    textBlockRefs = model.required<TextBlockRef[]>();
    selectedRef = model<TextBlockRef | undefined>(undefined);
    readOnly = input.required<boolean>();
    submission = input.required<TextSubmission>();
    criteria = input<GradingCriterion[]>();

    textBlockRefAdded = output<TextBlockRef>();

    textBlockRefGroups = signal<TextBlockRefGroup[]>([]);

    constructor() {
        // Effect to compute textBlockRefGroups when textBlockRefs changes
        effect(() => {
            const refs = this.textBlockRefs();
            if (refs) {
                this.textBlockRefGroups.set(TextBlockRefGroup.fromTextBlockRefs(refs));
            }
        });
    }

    private getTextBlockRefsFromGroups(): TextBlockRef[] {
        return this.textBlockRefGroups().reduce((previous: TextBlockRef[], group: TextBlockRefGroup) => [...previous, ...group.refs], []);
    }

    textBlockRefsChangeEmit(): void {
        this.textBlockRefs.set(this.getTextBlockRefsFromGroups());
    }

    /**
     * Called by <jhi-manual-text-selection> component.
     * Select Text within text block ref group and emit to parent component if it is indeed a new text block.
     *
     * @param selectedWords first and last word selected received from <jhi-manual-text-selection>.
     */
    handleTextSelection(selectedWords: wordSelection[]): void {
        // create new Text Block for text
        const textBlockRef = TextBlockRef.new();
        const textBlock = textBlockRef.block;

        if (textBlock) {
            textBlock.startIndex = selectedWords[0].index;
            textBlock.endIndex = selectedWords[1].index + selectedWords[1].word.length;
            textBlock.setTextFromSubmission(this.submission());
            const currentRefs = this.getTextBlockRefsFromGroups();
            const existingRef = currentRefs.find((ref) => ref.block?.id === textBlock.id);

            if (existingRef) {
                existingRef.initFeedback();
                this.selectedRef.set(existingRef);
            } else {
                textBlockRef.initFeedback();
                this.textBlockRefAdded.emit(textBlockRef);
            }
        }
    }
}

export class TextBlockRefGroup {
    public refs: TextBlockRef[];

    constructor(textBlockRef: TextBlockRef) {
        this.refs = [textBlockRef];
    }

    get hasFeedback(): boolean {
        return this.refs.length === 1 && !!this.refs[0].feedback;
    }

    get singleRef(): TextBlockRef | null {
        return this.hasFeedback ? this.refs[0] : null;
    }

    get startIndex() {
        return this.refs[0].block?.startIndex;
    }

    private get endIndex() {
        return this.refs.last()?.block?.endIndex;
    }

    getText(submission: TextSubmission): string {
        const textBlock = new TextBlock();
        textBlock.startIndex = this.startIndex;
        textBlock.endIndex = this.endIndex;
        textBlock.setTextFromSubmission(submission);

        return textBlock.text!;
    }

    addRef(textBlockRef: TextBlockRef) {
        this.refs.push(textBlockRef);
    }

    static fromTextBlockRefs = (textBlockRefs: TextBlockRef[]): TextBlockRefGroup[] =>
        textBlockRefs.reduce((groups: TextBlockRefGroup[], elem: TextBlockRef) => {
            const lastGroup = groups.last();
            if (lastGroup && !lastGroup.hasFeedback && !elem.feedback) {
                lastGroup.addRef(elem);
            } else {
                groups.push(new TextBlockRefGroup(elem));
            }
            return groups;
        }, []);
}
