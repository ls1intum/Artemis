import { Component, Input, Output, EventEmitter } from '@angular/core';
import { SelectionRectangle, TextSelectEvent } from 'app/exercises/text/shared/text-select.directive';
import { convertToHtmlLinebreaks } from 'app/utils/text.utils';

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

    /**
     * Handle user's selection of solution text.
     * @param $event fired on text selection of type {TextSelectEvent}
     */
    didSelectSolutionText($event: TextSelectEvent): void {
        if (this.disabled) {
            return;
        }

        // If a new selection has been created, the viewport and host rectangles will
        // exist. Or, if a selection is being removed, the rectangles will be null.
        if ($event.hostRectangle) {
            this.hostRectangle = $event.hostRectangle;
            this.selectedText = convertToHtmlLinebreaks($event.text);
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
        }
    }
}
