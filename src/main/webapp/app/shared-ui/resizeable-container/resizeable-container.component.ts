import { Component, input, model } from '@angular/core';
import { faChevronLeft, faChevronRight, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgTemplateOutlet } from '@angular/common';
import { ResizableDirective } from 'app/shared-ui/directives/resizable.directive';

/**
 * Resizable Layout with collapsible panel on the right hand side.
 * Usage Example:
 * <jhi-resizeable-container>
 *    <span left-header>Header Left</span>
 *    <p left-body>Body Left</p>
 *    <fa-icon [icon]="faExclamationTriangle" right-header></fa-icon>
 *    <span right-header>Header Right</span>
 *    <p right-body>Body Right</p>
 *  </jhi-resizeable-container>
 */
@Component({
    selector: 'jhi-resizeable-container',
    templateUrl: './resizeable-container.component.html',
    styleUrls: ['./resizeable-container.component.scss'],
    imports: [FaIconComponent, NgTemplateOutlet, ResizableDirective],
    host: {
        class: 'flex-grow-1',
        '(window:resize)': 'onWindowResize($event)',
    },
})
export class ResizeableContainerComponent {
    readonly collapsed = model<boolean>(false);
    readonly isExerciseParticipation = input<boolean>(false);
    readonly examTimeline = input<boolean>(false);
    readonly showRightPanel = input<boolean>(true);

    /**
     * Expected to be set to true while the component is printed as PDF.
     *
     * <i>e.g. the case for printing the exam summary</i>
     */
    readonly isBeingPrinted = input<boolean>(false);

    /**
     * Forces the problem statement to be expanded when the component is printed as PDF
     *
     * <i>e.g. the case for printing the exam summary</i>
     */
    readonly expandProblemStatement = input<boolean>(false);

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;

    // Make right side always expanded for smaller screens
    onWindowResize(event: any) {
        if (event.target.innerWidth <= 992) {
            this.collapsed.set(false);
        }
    }
}
