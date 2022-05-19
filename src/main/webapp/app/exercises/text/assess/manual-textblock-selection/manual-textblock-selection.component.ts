import { Component, Input, Output, EventEmitter } from '@angular/core';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextBlock } from 'app/entities/text-block.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { wordSelection } from 'app/exercises/text/shared/manual-text-selection/manual-text-selection.component';

@Component({
    selector: 'jhi-manual-textblock-selection',
    templateUrl: './manual-textblock-selection.component.html',
})
export class ManualTextblockSelectionComponent {
    @Input() set textBlockRefs(textBlockRefs: TextBlockRef[]) {
        this.textBlockRefGroups = TextBlockRefGroup.fromTextBlockRefs(textBlockRefs);
    }
    get textBlockRefs(): TextBlockRef[] {
        return this.textBlockRefGroups.reduce((previous: TextBlockRef[], group: TextBlockRefGroup) => [...previous, ...group.refs], []);
    }
    @Output() textBlockRefsChange = new EventEmitter<TextBlockRef[]>();
    @Output() textBlockRefAdded = new EventEmitter<TextBlockRef>();
    @Input() selectedRef?: TextBlockRef;
    @Input() readOnly: boolean;
    @Output() selectedRefChange = new EventEmitter<TextBlockRef | undefined>();
    @Input() submission: TextSubmission;
    @Input() criteria?: GradingCriterion[];
    textBlockRefGroups: TextBlockRefGroup[];

    textBlockRefsChangeEmit(): void {
        this.textBlockRefsChange.emit(this.textBlockRefs);
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
            textBlock.setTextFromSubmission(this.submission);
            textBlock.computeId();
            const existingRef = this.textBlockRefs.find((ref) => ref.block?.id === textBlock.id);

            if (existingRef) {
                existingRef.initFeedback();
                this.selectedRefChange.emit(existingRef);
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
