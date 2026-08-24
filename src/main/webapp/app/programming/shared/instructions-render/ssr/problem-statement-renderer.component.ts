import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseInstructionSsrComponent, SsrLiveUpdates } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr.component';

/**
 * Renders a programming exercise problem statement, choosing between the server-side renderer and the legacy
 * client-side pipeline based on the SsrProblemStatement feature toggle. All six read-only hosts (course overview,
 * code editor, repository view, assessment instructions, assessment dashboard, detail overview list) bind this
 * single component instead of picking a renderer themselves.
 *
 * `liveUpdates` is the only mode input: the legacy child's `personalParticipation` is derived from it in the
 * template rather than accepted as a second, independently-bindable input. Two independent inputs would let a host
 * pass a mode and a personal flag that disagree, which is the drift this component exists to remove. Staff-facing
 * views therefore subscribe to the exercise-wide result topic: a personal topic carries no results for a template,
 * a solution or another student's participation, so it could not deliver the live updates it promises.
 */
@Component({
    selector: 'jhi-problem-statement-renderer',
    templateUrl: './problem-statement-renderer.component.html',
    styleUrls: ['./problem-statement-renderer.component.scss'],
    imports: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionSsrComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProblemStatementRendererComponent {
    private featureToggleService = inject(FeatureToggleService);

    // Optional, mirroring both renderers: several hosts bind a class field that is undefined until the exercise has
    // loaded, and `input.required` would not catch that anyway (it only checks that the binding is present).
    readonly exercise = input<ProgrammingExercise>();
    readonly participation = input<Participation>();
    readonly result = input<Result>();
    readonly liveUpdates = input<SsrLiveUpdates>('none');

    readonly onNoInstructionsAvailable = output<void>();

    // No flash of the wrong renderer on a fresh page load: the blocking APP_INITIALIZER in app.config.ts awaits
    // ProfileService.loadProfileInfo(), and with it initializeFeatureToggles(...), before Angular bootstraps any
    // component, so the BehaviorSubject behind this Observable already holds the server-provided toggle state when
    // this subscription is made. `initialValue: false` is therefore never observed in production; it is a
    // fail-closed default (prefer the legacy renderer) and keeps `ssrEnabled` typed as `boolean`.
    readonly ssrEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.SsrProblemStatement), { initialValue: false });

    /**
     * Exam exercises stay on the legacy renderer whatever the toggle says. The exclusion is central here rather than
     * per host because a host cannot see which renderer it ends up with: the student code editor, for one, hides its
     * instructions pane for course exercises and shows it only for exam ones, so its problem statement is an exam
     * problem statement whenever it is visible at all.
     *
     * The marker is `exerciseGroup`, the same positive test the code editor uses to decide that visibility, rather
     * than the absence of a course, which is also what an exercise whose course was not loaded looks like.
     */
    readonly serverRendered = computed(() => this.ssrEnabled() && this.exercise()?.exerciseGroup === undefined);
}
