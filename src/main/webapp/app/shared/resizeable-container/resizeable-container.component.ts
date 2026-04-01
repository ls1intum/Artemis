import { Component, ElementRef, HostBinding, HostListener, Input, OnDestroy, effect, viewChild } from '@angular/core';
import { faChevronLeft, faChevronRight, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { Interactable } from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgTemplateOutlet } from '@angular/common';

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
    imports: [FaIconComponent, NgTemplateOutlet],
})
export class ResizeableContainerComponent implements OnDestroy {
    @HostBinding('class.flex-grow-1') flexGrow1 = true;
    @Input() collapsed = false;
    @Input() isExerciseParticipation = false;
    @Input() examTimeline = false;

    /**
     * Expected to be set to true while the component is printed as PDF.
     *
     * <i>e.g. the case for printing the exam summary</i>
     */
    @Input() isBeingPrinted = false;

    /**
     * Forces the problem statement to be expanded when the component is printed as PDF
     *
     * <i>e.g. the case for printing the exam summary</i>
     */
    @Input() expandProblemStatement = false;

    readonly expandedPanel = viewChild<ElementRef<HTMLElement>>('expandedPanel');

    private interactResizable: Interactable | undefined;

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;

    constructor() {
        effect(() => {
            this.interactResizable?.unset();
            const panel = this.expandedPanel();
            if (!panel) {
                this.interactResizable = undefined;
                return;
            }
            this.interactResizable = this.initializeResizable(panel.nativeElement);
        });
    }

    ngOnDestroy(): void {
        this.interactResizable?.unset();
    }

    private initializeResizable(panelElement: HTMLElement): Interactable {
        return interact(panelElement)
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 215, height: 0 },
                        max: { width: 1500, height: 2000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', (event: any) => {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', (event: any) => {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }

    // Make right side always expanded for smaller screens
    @HostListener('window:resize', ['$event'])
    onWindowResize(event: any) {
        if (event.target.innerWidth <= 992) {
            this.collapsed = false;
        }
    }
}
