import { AfterViewInit, Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise';
import interact from 'interactjs';

@Component({
    selector: 'jhi-collapsable-assessment-instructions',
    templateUrl: './collapsable-assessment-instructions.component.html',
    styleUrls: ['./collapsable-assessment-instructions.scss'],
})
export class CollapsableAssessmentInstructionsComponent implements AfterViewInit {
    @Input() exercise: Exercise;
    @Input() collapsed = false;

    /**
     * Configures interact to make instructions expandable
     */
    ngAfterViewInit(): void {
        interact('.expanded-instructions')
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
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function(event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }
}
