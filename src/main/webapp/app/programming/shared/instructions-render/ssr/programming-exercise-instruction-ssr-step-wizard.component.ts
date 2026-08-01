import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumUiTooltipDirective } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip.directive';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';

/**
 * The "Tasks:" step wizard for the server-rendered problem statement.
 *
 * Chrome, not server content: it is hosted by the outer SSR component in the light DOM, never inside the
 * shadow-DOM content component (see that component's class comment for why Angular/Tailwind styling cannot
 * cross that boundary). Every circle's colour comes straight from the server-decided `SsrTask.status`; this
 * component never recomputes test status from a `Result`, which is the whole point of the SSR migration.
 */
@Component({
    selector: 'jhi-programming-exercise-instruction-ssr-step-wizard',
    templateUrl: './programming-exercise-instruction-ssr-step-wizard.component.html',
    styleUrls: ['./programming-exercise-instruction-ssr-step-wizard.component.scss'],
    imports: [TranslateDirective, TumUiTooltipDirective, FaIconComponent],
})
export class ProgrammingExerciseInstructionSsrStepWizardComponent {
    readonly tasks = input.required<SsrTask[]>();
    /** Whether a step can currently open a feedback dialog. A step that cannot is rendered disabled. */
    readonly interactive = input(false);
    readonly taskSelected = output<SsrTask>();

    readonly faTimes = faTimes;
    readonly faCheck = faCheck;
    readonly faCircle = faCircle;
}
