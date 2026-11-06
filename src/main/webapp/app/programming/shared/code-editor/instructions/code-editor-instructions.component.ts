import { Component, computed, input, output, signal } from '@angular/core';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CollapsedPanelRailComponent } from 'app/shared-ui/components/collapsed-panel-rail/collapsed-panel-rail.component';

@Component({
    selector: 'jhi-code-editor-instructions',
    styleUrls: ['./code-editor-instructions.scss'],
    templateUrl: './code-editor-instructions.component.html',
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, CollapsedPanelRailComponent],
})
export class CodeEditorInstructionsComponent {
    readonly onToggleCollapse = output<{
        event: MouseEvent;
        horizontal: boolean;
    }>();

    readonly isAssessmentMode = input(true);

    /** Whether the page fills the header itself, through `[instructionsHeader]`, e.g. with tabs, in place of the title. */
    readonly customHeader = input(false);
    // make instructions monaco editor in the main editor not collapsible
    disableCollapse = input(false);
    /** Whether a click on the header collapses; a header the page fills (its tabs) has a button of its own for it. */
    protected readonly headerToggles = computed(() => !this.disableCollapse() && !this.customHeader());
    // different translation for problem statement editor and preview
    isEditor = input(false);
    readonly collapsed = signal(false);

    // Icons
    faChevronRight = faChevronRight;
    farListAlt = faListAlt;

    /** Collapses the instructions to their rail, e.g. when their column was dragged to its narrowest; collapsed or not collapsible ones stay as they are. */
    collapse() {
        if (!this.collapsed() && !this.disableCollapse()) {
            this.toggleEditorCollapse(new MouseEvent('click'));
        }
    }

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
        // A header the page fills is an assessment's: the grid collapses its column to a rail of its own, and this panel stays as it is.
        // Collapsing it as well would leave its own rail where the grid has restored the column.
        if (!this.customHeader()) {
            this.collapsed.update((collapsed) => !collapsed);
        }
        this.onToggleCollapse.emit({ event, horizontal: true });
    }
}
