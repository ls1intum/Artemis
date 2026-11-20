import { Component, input, model } from '@angular/core';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faCheckCircle } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiPanelComponent } from '@tumaet/ui-angular';
import { AssessmentInstructionsComponent } from '../assessment-instructions/assessment-instructions.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-collapsable-assessment-instructions',
    templateUrl: './collapsable-assessment-instructions.component.html',
    styleUrls: ['./collapsable-assessment-instructions.scss'],
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, AssessmentInstructionsComponent, TumAetUiPanelComponent],
})
export class CollapsableAssessmentInstructionsComponent {
    readonly isAssessmentTraining = input(false);
    readonly showAssessmentInstructions = input(true);
    readonly showReadCheckmark = input(false);
    readonly exercise = input.required<Exercise>();
    collapsed = model(false);
    readonly readOnly = input.required<boolean>();

    // Icons
    farListAlt = faListAlt;
    faCheckCircle = faCheckCircle;
}
