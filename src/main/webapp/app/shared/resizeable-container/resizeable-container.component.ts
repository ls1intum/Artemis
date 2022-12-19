import { AfterViewInit, Component, HostBinding, HostListener, Input } from '@angular/core';
import { faChevronLeft, faChevronRight, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import interact from 'interactjs';

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
})
export class ResizeableContainerComponent implements AfterViewInit {
    @HostBinding('class.flex-grow-1') flexGrow1 = true;
    @Input() collapsed = false;
    @Input() isExerciseParticipation = false;

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;

    /**
     * Performed after full initialization of the view.
     * Handles the resizable layout with collapsible panel on the right-hand side.
     */
    ngAfterViewInit() {
        interact('.expanded')
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
