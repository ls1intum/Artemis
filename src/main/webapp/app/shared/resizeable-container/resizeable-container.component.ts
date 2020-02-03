import { Component, AfterViewInit, Input, HostBinding } from '@angular/core';
import interact from 'interactjs';

/**
 * Resizable Layout with collapsable panel on the right hand side.
 *
 * Usage Example:
 * <jhi-resizeable-container>
 *
 *    <span left-header>Header Left</span>
 *    <p left-body>Body Left</p>
 *
 *    <fa-icon icon="exclamation-triangle" right-header></fa-icon>
 *    <span right-header>Header Right</span>
 *    <p right-body>Body Right</p>
 *
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

    ngAfterViewInit() {
        interact('.expanded')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 215, height: 0 },
                        max: { width: 1000, height: 2000 },
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
}
