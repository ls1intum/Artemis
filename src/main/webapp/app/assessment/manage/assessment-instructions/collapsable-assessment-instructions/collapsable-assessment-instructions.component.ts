import { AfterViewInit, Component, input, model } from '@angular/core';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronLeft, faChevronRight, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import interact from 'interactjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AssessmentInstructionsComponent } from '../assessment-instructions/assessment-instructions.component';

@Component({
    selector: 'jhi-collapsable-assessment-instructions',
    templateUrl: './collapsable-assessment-instructions.component.html',
    styleUrls: ['./collapsable-assessment-instructions.scss'],
    imports: [FaIconComponent, TranslateDirective, AssessmentInstructionsComponent],
})
export class CollapsableAssessmentInstructionsComponent implements AfterViewInit {
    readonly isAssessmentTraining = input(false);
    readonly showAssessmentInstructions = input(true);
    readonly exercise = input.required<Exercise>();
    readonly collapsed = model(false);
    readonly readOnly = input.required<boolean>();

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;
    farListAlt = faListAlt;

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
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }
}
