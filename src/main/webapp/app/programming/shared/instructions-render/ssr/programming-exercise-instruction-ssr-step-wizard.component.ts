import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';
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
    /**
     * Whether the bound inputs allow a feedback dialog at all. A step is rendered disabled when this is false, and
     * additionally when the step itself resolved to no test id, since there would be nothing to show for it.
     */
    readonly interactive = input(false);
    readonly taskSelected = output<SsrTask>();

    readonly faTimes = faTimes;
    readonly faCheck = faCheck;
    readonly faCircle = faCircle;
}
