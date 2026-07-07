import { Component, input, output, signal } from '@angular/core';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-code-editor-instructions',
    styleUrls: ['./code-editor-instructions.scss'],
    templateUrl: './code-editor-instructions.component.html',
    imports: [NgStyle, FaIconComponent, TranslateDirective],
})
export class CodeEditorInstructionsComponent {
    readonly onToggleCollapse = output<{
        event: MouseEvent;
        horizontal: boolean;
    }>();

    readonly isAssessmentMode = input(true);

    // make instructions monaco editor in the main editor not collapsible
    disableCollapse = input(false);
    // different translation for problem statement editor and preview
    isEditor = input(false);
    readonly collapsed = signal(false);

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    farListAlt = faListAlt;

    /**
     * Calls the parent (editorComponent) toggleCollapse method
     * @param event - click event from the collapse header
     */
    toggleEditorCollapse(event: MouseEvent) {
        // make instructions monaco editor in the main editor not collapsible
        if (this.disableCollapse()) {
            event?.stopPropagation();
            return;
        }
        this.collapsed.update((collapsed) => !collapsed);
        this.onToggleCollapse.emit({ event, horizontal: true });
    }
}
