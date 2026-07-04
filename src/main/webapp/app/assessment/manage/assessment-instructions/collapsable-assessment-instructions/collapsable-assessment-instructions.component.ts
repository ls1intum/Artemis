import { Component, input, model } from '@angular/core';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronLeft, faChevronRight, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ResizableDirective } from 'app/shared-ui/directives/resizable.directive';
import { AssessmentInstructionsComponent } from '../assessment-instructions/assessment-instructions.component';

@Component({
    selector: 'jhi-collapsable-assessment-instructions',
    templateUrl: './collapsable-assessment-instructions.component.html',
    styleUrls: ['./collapsable-assessment-instructions.scss'],
    imports: [FaIconComponent, TranslateDirective, AssessmentInstructionsComponent, ResizableDirective],
})
export class CollapsableAssessmentInstructionsComponent {
    readonly isAssessmentTraining = input(false);
    readonly showAssessmentInstructions = input(true);
    readonly exercise = input.required<Exercise>();
    collapsed = model(false);
    readonly readOnly = input.required<boolean>();

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;
    farListAlt = faListAlt;
}
