import { AfterViewInit, Component, EventEmitter, Input, Output, input } from '@angular/core';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { Interactable } from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-code-editor-instructions',
    styleUrls: ['./code-editor-instructions.scss'],
    templateUrl: './code-editor-instructions.component.html',
    imports: [NgStyle, FaIconComponent, TranslateDirective],
})
export class CodeEditorInstructionsComponent implements AfterViewInit {
    @Output()
    onToggleCollapse = new EventEmitter<{ event: any; horizontal: boolean; interactable: Interactable; resizableMinWidth?: number; resizableMinHeight?: number }>();

    @Input()
    isAssessmentMode = true;

    // make instructions monaco editor in the main editor not collapsible
    disableCollapse = input(false);
    // different translation for problem statement editor and preview
    isEditor = input(false);
    /** Resizable constants **/
    initialInstructionsWidth: number;
    minInstructionsWidth: number;
    interactResizable: Interactable;
    collapsed = false;

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    farListAlt = faListAlt;

    /**
     * After the view was initialized, we create an interact.js resizable object,
     * designate the edges which can be used to resize the target element and set min and max values.
     * The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.initialInstructionsWidth = window.screen.width - 300 / 2;
        this.minInstructionsWidth = window.screen.width / 4 - 50;
        this.interactResizable = interact('.resizable-instructions');
    }

    /**
     * Calls the parent (editorComponent) toggleCollapse method
     * @param event - any event
     */
    toggleEditorCollapse(event: any) {
        // make instructions monaco editor in the main editor not collapsible
        if (this.disableCollapse()) {
            if (event?.stopPropagation) {
                event.stopPropagation();
            }
            return;
        }
        this.collapsed = !this.collapsed;
        this.onToggleCollapse.emit({ event, horizontal: true, interactable: this.interactResizable, resizableMinWidth: this.minInstructionsWidth });
    }
}
